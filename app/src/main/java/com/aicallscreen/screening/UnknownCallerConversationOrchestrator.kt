package com.aicallscreen.screening

import android.util.Log
import com.aicallscreen.audio.CallAudioFocusManager
import com.aicallscreen.data.repository.CallLogRepository
import com.aicallscreen.escalation.UserEscalationManager
import com.aicallscreen.llm.DialogMode
import com.aicallscreen.llm.ILocalLLMEngine
import com.aicallscreen.llm.ScreeningAction
import com.aicallscreen.routing.CallRoute
import com.aicallscreen.telecom.CallControlCoordinator
import com.aicallscreen.tts.OfflineCallTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Unknown-number flow: silent answer → multi-turn AI dialog → ring owner or drop spam.
 */
class UnknownCallerConversationOrchestrator(
    private val scope: CoroutineScope,
    private val audioFocusManager: CallAudioFocusManager,
    private val conversationPipeline: ConversationPipeline,
    private val ttsEngine: OfflineCallTtsEngine,
    private val llmEngine: ILocalLLMEngine,
    private val callLogRepository: CallLogRepository,
    private val callControl: CallControlCoordinator,
    private val userEscalationManager: UserEscalationManager,
) {

    fun start(phoneNumber: String) {
        scope.launch {
            runUnknownFlow(phoneNumber)
        }
    }

    private suspend fun runUnknownFlow(phoneNumber: String) {
        val timestamp = System.currentTimeMillis()
        var wasBlocked = false
        var wasConnected = false
        var ttsText = ""
        var transcript = ""
        var thoughts = ""
        var conversationJson = ""

        try {
            audioFocusManager.enterCallAudioMode()

            if (!callControl.answerRingingCall()) {
                Log.w(TAG, "Unable to auto-answer unknown caller for screening")
            }

            val initialPrompt = "Hi, you've reached an AI call screener. This call is being recorded " +
                "to protect the person you're trying to reach. Who are you and why are you calling?"

            val result = conversationPipeline.run(
                mode = DialogMode.UNKNOWN_SCREENING,
                initialAiPrompt = initialPrompt,
                contactDisplayName = null,
                ownerDisplayName = null,
                maxTurns = MAX_DIALOG_TURNS,
            )

            ttsText = result.allSpokenText
            transcript = result.history
                .filter { it.role == com.aicallscreen.llm.ConversationTurn.Role.CALLER }
                .joinToString(" | ") { it.text }
            thoughts = result.finalDecision.thoughts
            conversationJson = result.history.joinToString("\n") { "${it.role}: ${it.text}" }

            when (result.finalDecision.action) {
                ScreeningAction.BLOCK_CALL -> {
                    wasBlocked = true
                    ttsEngine.speakToCall(result.finalDecision.replyToSpeak)
                    callControl.endCall()
                }
                ScreeningAction.CONNECT_TO_USER -> {
                    wasConnected = true
                    ttsEngine.speakToCall(result.finalDecision.replyToSpeak)
                    audioFocusManager.exitCallAudioMode()
                    userEscalationManager.startEscalationRinging(
                        phoneNumber = phoneNumber,
                        callerLabel = phoneNumber,
                    )
                }
                ScreeningAction.TAKE_MESSAGE_AND_END -> {
                    ttsEngine.speakToCall(result.finalDecision.replyToSpeak)
                    callControl.endCall()
                }
                ScreeningAction.CONTINUE_DIALOG -> {
                    // Max turns exhausted without terminal action — connect by default.
                    wasConnected = true
                    ttsEngine.speakToCall("Please hold while I alert them.")
                    audioFocusManager.exitCallAudioMode()
                    userEscalationManager.startEscalationRinging(phoneNumber, phoneNumber)
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unknown caller flow failed", error)
            thoughts = "Error: ${error.message}"
        } finally {
            audioFocusManager.exitCallAudioMode()
            callLogRepository.persistScreeningEvent(
                phoneNumber = phoneNumber,
                timestamp = timestamp,
                ttsGreetingText = ttsText,
                callerTranscript = transcript,
                aiThoughts = thoughts,
                wasBlocked = wasBlocked,
                callRoute = CallRoute.UNKNOWN_AI_SCREENING.name,
                conversationHistory = conversationJson,
                wasConnectedToUser = wasConnected,
                leftMessage = "",
            )
        }
    }

    companion object {
        private const val TAG = "UnknownCallerOrchestrator"
        private const val MAX_DIALOG_TURNS = 5
    }
}
