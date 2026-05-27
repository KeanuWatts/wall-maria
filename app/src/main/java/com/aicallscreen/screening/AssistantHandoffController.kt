package com.aicallscreen.screening

import android.content.Context
import com.aicallscreen.di.ServiceLocator
import com.aicallscreen.session.ScreeningSession
import com.aicallscreen.session.ScreeningSessionManager
import com.aicallscreen.session.ScreeningSessionType
import com.aicallscreen.ui.ScreeningUiLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Sends a ringing known contact to the AI assistant (user-initiated).
 */
class AssistantHandoffController(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    fun sendKnownCallerToAssistant(
        phoneNumber: String,
        contactDisplayName: String,
        ownerDisplayName: String,
        existingSessionId: String? = null,
    ) {
        scope.launch {
            ServiceLocator.knownContactCallWatcher.cancelPending(phoneNumber)
            existingSessionId?.let { ScreeningSessionManager.endSession(it) }

            val session = ScreeningSessionManager.createSession(
                phoneNumber = phoneNumber,
                displayLabel = contactDisplayName,
                sessionType = ScreeningSessionType.KNOWN_ASSISTANT,
                ownerDisplayName = ownerDisplayName,
                contactDisplayName = contactDisplayName,
            )

            ScreeningUiLauncher.showLiveScreening(
                context = context,
                sessionId = session.sessionId,
                displayLabel = contactDisplayName,
            )

            ServiceLocator.knownContactAssistantOrchestrator.runAssistant(
                phoneNumber = phoneNumber,
                contactDisplayName = contactDisplayName,
                ownerDisplayName = ownerDisplayName,
                session = session,
            )
        }
    }
}
