package com.aicallscreen.screening

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.aicallscreen.audio.CallAudioFocusManager
import com.aicallscreen.escalation.UserEscalationManager
import com.aicallscreen.session.ScreeningSession
import com.aicallscreen.session.ScreeningUiEvent
import com.aicallscreen.tts.OfflineCallTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Stops AI work and leaves the live call connected for the device owner.
 */
class UserTakeoverHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ttsEngine: OfflineCallTtsEngine,
    private val audioFocusManager: CallAudioFocusManager,
    private val userEscalationManager: UserEscalationManager,
) {

    fun executeTakeover(session: ScreeningSession) {
        scope.launch {
            session.requestTakeover()
            ttsEngine.stopSpeaking()
            userEscalationManager.stopEscalationRinging()
            audioFocusManager.exitCallAudioMode()

            session.emit(
                ScreeningUiEvent.SessionEnded(
                    reason = ScreeningUiEvent.EndReason.USER_TAKEOVER,
                    summary = context.getString(
                        com.aicallscreen.R.string.takeover_summary,
                        session.displayLabel,
                    ),
                ),
            )

            openInCallUi(session.phoneNumber)
            Log.i(TAG, "User takeover for ${session.phoneNumber}")
        }
    }

    private fun openInCallUi(phoneNumber: String) {
        val dialer = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(dialer)
    }

    companion object {
        private const val TAG = "UserTakeoverHandler"
    }
}
