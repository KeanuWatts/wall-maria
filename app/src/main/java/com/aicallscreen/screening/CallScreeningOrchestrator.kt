package com.aicallscreen.screening

import android.telecom.Call
import android.util.Log
import com.aicallscreen.audio.CallAudioCapture
import com.aicallscreen.audio.CallAudioFocusManager
import com.aicallscreen.data.repository.CallLogRepository
import com.aicallscreen.llm.ILocalLLMEngine
import com.aicallscreen.llm.LLMDecision
import com.aicallscreen.stt.ISpeechToTextEngine
import com.aicallscreen.telecom.CallControlCoordinator
import com.aicallscreen.tts.OfflineCallTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coordinates TTS greeting, STT capture, LLM evaluation, persistence, and call teardown.
 */
class CallScreeningOrchestrator(
    private val scope: CoroutineScope,
    private val audioFocusManager: CallAudioFocusManager,
    private val audioCapture: CallAudioCapture,
    private val ttsEngine: OfflineCallTtsEngine,
    private val speechToTextEngine: ISpeechToTextEngine,
    private val llmEngine: ILocalLLMEngine,
    private val callLogRepository: CallLogRepository,
    private val callControl: CallControlCoordinator,
) {

    fun handleScreenedCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: "unknown"
        val timestamp = System.currentTimeMillis()

        scope.launch {
            runScreeningPipeline(phoneNumber, timestamp)
        }
    }

    private suspend fun runScreeningPipeline(
        phoneNumber: String,
        timestamp: Long,
    ) = coroutineScope {
        var ttsGreetingText = ""
        var callerTranscript = ""
        var aiThoughts = ""
        var wasBlocked = false

        try {
            audioFocusManager.enterCallAudioMode()

            val answered = callControl.answerRingingCall()
            if (!answered) {
                Log.w(TAG, "Could not answer call programmatically")
            }

            val greetingDeferred = async {
                ttsEngine.speakGreetingToCall()
            }

            val transcriptDeferred = async {
                collectCallerTranscript()
            }

            ttsGreetingText = greetingDeferred.await()
            callerTranscript = transcriptDeferred.await()

            val decision = awaitLlmDecision(callerTranscript)
            aiThoughts = decision.thoughts
            wasBlocked = decision.isSpam

            val logJob = launch {
                callLogRepository.persistScreeningEvent(
                    phoneNumber = phoneNumber,
                    timestamp = timestamp,
                    ttsGreetingText = ttsGreetingText,
                    callerTranscript = callerTranscript,
                    aiThoughts = aiThoughts,
                    wasBlocked = wasBlocked,
                )
            }

            logJob.join()

            if (wasBlocked) {
                val dropped = callControl.endCall()
                Log.i(TAG, "Spam call dropped=$dropped number=$phoneNumber")
            }
        } catch (error: Exception) {
            Log.e(TAG, "Screening pipeline failed", error)
            callLogRepository.persistScreeningEvent(
                phoneNumber = phoneNumber,
                timestamp = timestamp,
                ttsGreetingText = ttsGreetingText,
                callerTranscript = callerTranscript,
                aiThoughts = aiThoughts.ifBlank { "Pipeline error: ${error.message}" },
                wasBlocked = wasBlocked,
            )
        } finally {
            audioFocusManager.exitCallAudioMode()
        }
    }

    private suspend fun collectCallerTranscript(): String {
        val tokens = mutableListOf<String>()
        withTimeoutOrNull(TRANSCRIPT_CAPTURE_WINDOW_MS) {
            audioCapture.capturePcmChunks().collect { chunk ->
                speechToTextEngine.streamAudioToText(chunk).collect { token ->
                    tokens.add(token)
                }
            }
        }
        return tokens.joinToString(separator = " ").trim()
    }

    private suspend fun awaitLlmDecision(transcript: String): LLMDecision {
        val deferred: Deferred<LLMDecision> = llmEngine.evaluateTranscript(transcript)
        return deferred.await()
    }

    companion object {
        private const val TAG = "CallScreeningOrchestrator"
        private const val TRANSCRIPT_CAPTURE_WINDOW_MS = 15_000L
    }
}
