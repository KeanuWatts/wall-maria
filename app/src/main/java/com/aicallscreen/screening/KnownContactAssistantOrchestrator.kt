package com.aicallscreen.screening

import android.util.Log
import com.aicallscreen.audio.CallAudioFocusManager
import com.aicallscreen.data.repository.CallLogRepository
import com.aicallscreen.llm.DialogMode
import com.aicallscreen.llm.ScreeningAction
import com.aicallscreen.routing.CallRoute
import com.aicallscreen.session.ScreeningSession
import com.aicallscreen.session.ScreeningSessionManager
import com.aicallscreen.session.ScreeningSessionType
import com.aicallscreen.session.ScreeningUiEvent
import com.aicallscreen.telecom.CallControlCoordinator
import com.aicallscreen.tts.OfflineCallTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class KnownContactAssistantOrchestrator(
    private val scope: CoroutineScope,
    private val audioFocusManager: CallAudioFocusManager,
    private val conversationPipeline: ConversationPipeline,
    private val ttsEngine: OfflineCallTtsEngine,
    private val callLogRepository: CallLogRepository,
    private val callControl: CallControlCoordinator,
) {

    fun runAssistant(
        phoneNumber: String,
        contactDisplayName: String,
        ownerDisplayName: String,
        session: ScreeningSession? = null,
    ) {
        val activeSession = session ?: ScreeningSessionManager.createSession(
            phoneNumber = phoneNumber,
            displayLabel = contactDisplayName,
            sessionType = ScreeningSessionType.KNOWN_ASSISTANT,
            ownerDisplayName = ownerDisplayName,
            contactDisplayName = contactDisplayName,
        )

        val job = scope.launch {
            execute(phoneNumber, contactDisplayName, ownerDisplayName, activeSession)
        }
        activeSession.attachPipelineJob(job)
    }

    private suspend fun execute(
        phoneNumber: String,
        contactDisplayName: String,
        ownerDisplayName: String,
        session: ScreeningSession,
    ) {
        val timestamp = System.currentTimeMillis()
        var ttsText = ""
        var transcript = ""
        var thoughts = ""
        var leftMessage = ""
        var conversationJson = ""

        try {
            audioFocusManager.enterCallAudioMode()
            session.emit(ScreeningUiEvent.Status("Assistant answering for $ownerDisplayName"))

            if (!callControl.answerRingingCall()) {
                Log.w(TAG, "Could not answer known contact for assistant")
                return
            }

            val greeting = "$ownerDisplayName is not available at the moment. " +
                "Can I take a message for them?"

            val result = conversationPipeline.run(
                session = session,
                mode = DialogMode.KNOWN_CONTACT_VOICEMAIL,
                initialAiPrompt = greeting,
                contactDisplayName = contactDisplayName,
                ownerDisplayName = ownerDisplayName,
                maxTurns = 3,
            )

            ttsText = result.allSpokenText
            transcript = result.history
                .filter { it.role == com.aicallscreen.llm.ConversationTurn.Role.CALLER }
                .joinToString(" | ") { it.text }
            leftMessage = transcript
            thoughts = result.finalDecision.thoughts
            conversationJson = result.history.joinToString("\n") { "${it.role}: ${it.text}" }

            if (result.endedByTakeover) {
                return
            }

            ttsEngine.speakToCall(result.finalDecision.replyToSpeak)
            callControl.endCall()
            session.emit(
                ScreeningUiEvent.SessionEnded(
                    ScreeningUiEvent.EndReason.MESSAGE_TAKEN,
                    "Message saved",
                ),
            )
        } catch (error: Exception) {
            Log.e(TAG, "Known contact assistant failed", error)
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
                wasBlocked = false,
                callRoute = CallRoute.KNOWN_CONTACT_NORMAL_RING.name,
                conversationHistory = conversationJson,
                wasConnectedToUser = session.userTakeoverRequested,
                leftMessage = leftMessage,
            )
            ScreeningSessionManager.endSession(session.sessionId)
        }
    }

    companion object {
        private const val TAG = "KnownContactAssistant"
    }
}
