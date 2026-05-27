package com.aicallscreen.screening

import com.aicallscreen.audio.CallAudioCapture
import com.aicallscreen.llm.ConversationTurn
import com.aicallscreen.llm.DialogMode
import com.aicallscreen.llm.ILocalLLMEngine
import com.aicallscreen.llm.LLMDialogDecision
import com.aicallscreen.llm.ScreeningAction
import com.aicallscreen.session.ScreeningSession
import com.aicallscreen.session.ScreeningUiEvent
import com.aicallscreen.stt.ISpeechToTextEngine
import com.aicallscreen.tts.OfflineCallTtsEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/**
 * Shared multi-turn speak → listen → LLM loop with live UI streaming via [ScreeningSession].
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
        val endedByTakeover: Boolean = false,
        val endedEarly: Boolean = false,
    )

    suspend fun run(
        session: ScreeningSession?,
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

        session?.emit(ScreeningUiEvent.Status("AI screening started"))

        var aiLine = initialAiPrompt
        repeat(maxTurns) { turnIndex ->
            if (!isSessionActive(session)) {
                return buildResult(
                    history, lastTranscript, lastDecision, spokenLines,
                    endedByTakeover = session?.userTakeoverRequested == true,
                    endedEarly = true,
                )
            }

            session?.emit(ScreeningUiEvent.AiSpeaking(aiLine))
            val spoken = ttsEngine.speakToCall(aiLine, utteranceId = "dialog_ai_$turnIndex")
            spokenLines.add(spoken)
            history.add(ConversationTurn(ConversationTurn.Role.AI, spoken))

            if (!isSessionActive(session)) {
                return buildResult(
                    history, lastTranscript, lastDecision, spokenLines,
                    endedByTakeover = session?.userTakeoverRequested == true,
                    endedEarly = true,
                )
            }

            lastTranscript = listenForCaller(session, listenWindowMs)
            if (lastTranscript.isNotBlank()) {
                history.add(ConversationTurn(ConversationTurn.Role.CALLER, lastTranscript))
                session?.emit(ScreeningUiEvent.CallerHeard(lastTranscript))
            }

            if (!isSessionActive(session)) {
                return buildResult(
                    history, lastTranscript, lastDecision, spokenLines,
                    endedByTakeover = session?.userTakeoverRequested == true,
                    endedEarly = true,
                )
            }

            session?.emit(ScreeningUiEvent.LlmThinking())
            lastDecision = llmEngine.evaluateDialogTurn(
                callerTranscript = lastTranscript,
                history = history,
                mode = mode,
                contactDisplayName = contactDisplayName,
                ownerDisplayName = ownerDisplayName,
            ).await()

            session?.emit(
                ScreeningUiEvent.LlmDecision(
                    action = lastDecision.action,
                    thoughts = lastDecision.thoughts,
                ),
            )

            when (lastDecision.action) {
                ScreeningAction.CONTINUE_DIALOG -> {
                    aiLine = lastDecision.replyToSpeak
                }
                ScreeningAction.CONNECT_TO_USER,
                ScreeningAction.BLOCK_CALL,
                ScreeningAction.TAKE_MESSAGE_AND_END,
                -> return buildResult(history, lastTranscript, lastDecision, spokenLines)
            }
        }

        return buildResult(history, lastTranscript, lastDecision, spokenLines)
    }

    private suspend fun listenForCaller(session: ScreeningSession?, windowMs: Long): String {
        val tokens = mutableListOf<String>()
        try {
            withTimeoutOrNull(windowMs) {
                audioCapture.capturePcmChunks().collect { chunk ->
                    if (!coroutineContext.isActive || !isSessionActive(session)) return@collect
                    speechToTextEngine.streamAudioToText(chunk).collect { token ->
                        tokens.add(token)
                        val partial = tokens.joinToString(" ").trim()
                        if (partial.isNotBlank()) {
                            session?.emit(ScreeningUiEvent.CallerPartial(partial))
                        }
                    }
                }
            }
        } catch (_: CancellationException) {
            // Takeover or job cancel
        }
        return tokens.joinToString(" ").trim()
    }

    private fun isSessionActive(session: ScreeningSession?): Boolean =
        session == null || session.isPipelineActive()

    private fun buildResult(
        history: List<ConversationTurn>,
        lastTranscript: String,
        lastDecision: LLMDialogDecision,
        spokenLines: List<String>,
        endedByTakeover: Boolean = false,
        endedEarly: Boolean = false,
    ): ConversationResult = ConversationResult(
        history = history,
        finalTranscript = lastTranscript,
        finalDecision = lastDecision,
        allSpokenText = spokenLines.joinToString("\n"),
        endedByTakeover = endedByTakeover,
        endedEarly = endedEarly,
    )

    companion object {
        private const val LISTEN_WINDOW_MS = 12_000L
    }
}
