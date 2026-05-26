package com.aicallscreen.screening

import com.aicallscreen.audio.CallAudioCapture
import com.aicallscreen.llm.ConversationTurn
import com.aicallscreen.llm.DialogMode
import com.aicallscreen.llm.ILocalLLMEngine
import com.aicallscreen.llm.LLMDialogDecision
import com.aicallscreen.llm.ScreeningAction
import com.aicallscreen.stt.ISpeechToTextEngine
import com.aicallscreen.tts.OfflineCallTtsEngine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared multi-turn speak → listen → LLM loop used by unknown screening and known assistant flows.
 */
class ConversationPipeline(
    private val ttsEngine: OfflineCallTtsEngine,
    private val audioCapture: CallAudioCapture,
    private val speechToTextEngine: ISpeechToTextEngine,
    private val llmEngine: ILocalLLMEngine,
) {

    data class ConversationResult(
        val history: List<ConversationTurn>,
        val finalTranscript: String,
        val finalDecision: LLMDialogDecision,
        val allSpokenText: String,
    )

    suspend fun run(
        mode: DialogMode,
        initialAiPrompt: String,
        contactDisplayName: String?,
        ownerDisplayName: String?,
        maxTurns: Int,
        listenWindowMs: Long = LISTEN_WINDOW_MS,
    ): ConversationResult {
        val history = mutableListOf<ConversationTurn>()
        val spokenLines = mutableListOf<String>()
        var lastTranscript = ""
        var lastDecision = LLMDialogDecision(
            action = ScreeningAction.CONTINUE_DIALOG,
            replyToSpeak = initialAiPrompt,
            thoughts = "Initial prompt",
        )

        var aiLine = initialAiPrompt
        repeat(maxTurns) { turnIndex ->
            val spoken = ttsEngine.speakToCall(aiLine, utteranceId = "dialog_ai_$turnIndex")
            spokenLines.add(spoken)
            history.add(ConversationTurn(ConversationTurn.Role.AI, spoken))

            lastTranscript = listenForCaller(listenWindowMs)
            if (lastTranscript.isNotBlank()) {
                history.add(ConversationTurn(ConversationTurn.Role.CALLER, lastTranscript))
            }

            lastDecision = llmEngine.evaluateDialogTurn(
                callerTranscript = lastTranscript,
                history = history,
                mode = mode,
                contactDisplayName = contactDisplayName,
                ownerDisplayName = ownerDisplayName,
            ).await()

            when (lastDecision.action) {
                ScreeningAction.CONTINUE_DIALOG -> {
                    aiLine = lastDecision.replyToSpeak
                }
                ScreeningAction.CONNECT_TO_USER,
                ScreeningAction.BLOCK_CALL,
                ScreeningAction.TAKE_MESSAGE_AND_END,
                -> return ConversationResult(
                    history = history,
                    finalTranscript = lastTranscript,
                    finalDecision = lastDecision,
                    allSpokenText = spokenLines.joinToString("\n"),
                )
            }
        }

        return ConversationResult(
            history = history,
            finalTranscript = lastTranscript,
            finalDecision = lastDecision,
            allSpokenText = spokenLines.joinToString("\n"),
        )
    }

    private suspend fun listenForCaller(windowMs: Long): String {
        val tokens = mutableListOf<String>()
        withTimeoutOrNull(windowMs) {
            audioCapture.capturePcmChunks().collect { chunk ->
                speechToTextEngine.streamAudioToText(chunk).collect { token ->
                    tokens.add(token)
                }
            }
        }
        return tokens.joinToString(" ").trim()
    }

    companion object {
        private const val LISTEN_WINDOW_MS = 12_000L
    }
}
