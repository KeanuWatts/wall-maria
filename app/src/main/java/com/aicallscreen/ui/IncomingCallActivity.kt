package com.aicallscreen.ui

import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aicallscreen.R
import com.aicallscreen.di.ServiceLocator
import com.aicallscreen.service.ScreeningForegroundService
import com.aicallscreen.session.ScreeningSessionManager
import com.aicallscreen.session.ScreeningUiEvent
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Custom incoming call UI for saved contacts: Answer, Decline, or Send to Assistant.
 */
class IncomingCallActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var sessionId: String
    private lateinit var phoneNumber: String
    private lateinit var displayName: String
    private lateinit var ownerDisplayName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLockScreenDisplay()
        setContentView(R.layout.activity_incoming_call)

        sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER).orEmpty()
        displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
        ownerDisplayName = intent.getStringExtra(EXTRA_OWNER_NAME).orEmpty()

        findViewById<TextView>(R.id.incomingCallerName).text = displayName
        findViewById<TextView>(R.id.incomingCallerNumber).text = phoneNumber

        findViewById<MaterialButton>(R.id.buttonAnswer).setOnClickListener {
            ServiceLocator.knownContactCallWatcher.cancelPending(phoneNumber)
            ServiceLocator.callControl.answerRingingCall()
            ScreeningSessionManager.getSession(sessionId)?.let { session ->
                scope.launch {
                    session.emit(
                        ScreeningUiEvent.SessionEnded(
                            ScreeningUiEvent.EndReason.COMPLETED,
                            getString(R.string.incoming_answered_summary),
                        ),
                    )
                }
            }
            ScreeningSessionManager.endSession(sessionId)
            finish()
        }

        findViewById<MaterialButton>(R.id.buttonDecline).setOnClickListener {
            ServiceLocator.knownContactCallWatcher.cancelPending(phoneNumber)
            ServiceLocator.callControl.endCall()
            ScreeningSessionManager.endSession(sessionId)
            finish()
        }

        findViewById<MaterialButton>(R.id.buttonSendToAssistant).setOnClickListener {
            ServiceLocator.assistantHandoffController.sendKnownCallerToAssistant(
                phoneNumber = phoneNumber,
                contactDisplayName = displayName,
                ownerDisplayName = ownerDisplayName,
                existingSessionId = sessionId,
            )
            finish()
        }
    }

    private fun enableLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    override fun onDestroy() {
        ScreeningForegroundService.stop(this)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        private const val EXTRA_DISPLAY_NAME = "extra_display_name"
        private const val EXTRA_OWNER_NAME = "extra_owner_name"

        fun createIntent(
            context: android.content.Context,
            sessionId: String,
            phoneNumber: String,
            displayName: String,
            ownerDisplayName: String,
        ) = android.content.Intent(context, IncomingCallActivity::class.java).apply {
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(EXTRA_DISPLAY_NAME, displayName)
            putExtra(EXTRA_OWNER_NAME, ownerDisplayName)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }
}
