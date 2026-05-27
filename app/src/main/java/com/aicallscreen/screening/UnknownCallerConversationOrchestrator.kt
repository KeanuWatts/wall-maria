package com.aicallscreen.screening

import android.content.Context
import android.util.Log
import com.aicallscreen.audio.CallAudioFocusManager
import com.aicallscreen.data.repository.CallLogRepository
import com.aicallscreen.escalation.UserEscalationManager
import com.aicallscreen.llm.DialogMode
import com.aicallscreen.llm.ILocalLLMEngine
import com.aicallscreen.llm.ScreeningAction
import com.aicallscreen.routing.CallRoute
import com.aicallscreen.session.ScreeningSession
import com.aicallscreen.session.ScreeningSessionManager
import com.aicallscreen.session.ScreeningSessionType
import com.aicallscreen.session.ScreeningUiEvent
import com.aicallscreen.telecom.CallControlCoordinator
import com.aicallscreen.tts.OfflineCallTtsEngine
import com.aicallscreen.ui.ScreeningUiLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class UnknownCallerConversationOrchestrator(
    private val context: Context,
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
        val session = ScreeningSessionManager.createSession(
            phoneNumber = phoneNumber,
            displayLabel = phoneNumber,
            sessionType = ScreeningSessionType.UNKNOWN_SCREENING,
        )

        ScreeningUiLauncher.showLiveScreening(
            context = context,
            sessionId = session.sessionId,
            displayLabel = phoneNumber,
        )

        val job = scope.launch {
            runUnknownFlow(phoneNumber, session)
        }
        session.attachPipelineJob(job)
    }

    private suspend fun runUnknownFlow(phoneNumber: String, session: ScreeningSession) {
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
                session = session,
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

            if (result.endedByTakeover) {
                wasConnected = true
                return
            }

            when (result.finalDecision.action) {
                ScreeningAction.BLOCK_CALL -> {
                    wasBlocked = true
                    ttsEngine.speakToCall(result.finalDecision.replyToSpeak)
                    callControl.endCall()
                    session.emit(
                        ScreeningUiEvent.SessionEnded(
                            ScreeningUiEvent.EndReason.BLOCKED,
                            "Call blocked",
                        ),
                    )
                }
                ScreeningAction.CONNECT_TO_USER -> {
                    wasConnected = true
                    ttsEngine.speakToCall(result.finalDecision.replyToSpeak)
                    audioFocusManager.exitCallAudioMode()
                    userEscalationManager.startEscalationRinging(phoneNumber, phoneNumber)
                    session.emit(
                        ScreeningUiEvent.SessionEnded(
                            ScreeningUiEvent.EndReason.CONNECTED_TO_USER,
                            "Ringing you — tap Take Over or the notification to answer",
                        ),
                    )
                }
                ScreeningAction.TAKE_MESSAGE_AND_END -> {
                    ttsEngine.speakToCall(result.finalDecision.replyToSpeak)
                    callControl.endCall()
                    session.emit(
                        ScreeningUiEvent.SessionEnded(
                            ScreeningUiEvent.EndReason.MESSAGE_TAKEN,
                            "Message captured",
                        ),
                    )
                }
                ScreeningAction.CONTINUE_DIALOG -> {
                    wasConnected = true
                    ttsEngine.speakToCall("Please hold while I alert them.")
                    audioFocusManager.exitCallAudioMode()
                    userEscalationManager.startEscalationRinging(phoneNumber, phoneNumber)
                    session.emit(
                        ScreeningUiEvent.SessionEnded(
                            ScreeningUiEvent.EndReason.CONNECTED_TO_USER,
                            "Ringing you now",
                        ),
                    )
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unknown caller flow failed", error)
            thoughts = "Error: ${error.message}"
            session.emit(
                ScreeningUiEvent.SessionEnded(
                    ScreeningUiEvent.EndReason.ERROR,
                    error.message ?: "Error",
                ),
            )
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
            ScreeningSessionManager.endSession(session.sessionId)
        }
    }

    companion object {
        private const val TAG = "UnknownCallerOrchestrator"
        private const val MAX_DIALOG_TURNS = 5
    }
}
