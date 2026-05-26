package com.aicallscreen.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.aicallscreen.di.ServiceLocator
import com.aicallscreen.screening.CallScreeningOrchestrator
import com.aicallscreen.telecom.CallControlCoordinator

/**
 * Telecom [CallScreeningService] entry point. Silences the ringer, allows the call for
 * programmatic answering, and delegates AI screening to [CallScreeningOrchestrator].
 *
 * Prerequisites:
 * - App holds the Call Screening role (RoleManager.ROLE_CALL_SCREENING)
 * - [android.permission.ANSWER_PHONE_CALLS] granted at runtime on Android 8+
 */
class LocalCallScreeningService : CallScreeningService() {

    private val orchestrator: CallScreeningOrchestrator by lazy {
        CallScreeningOrchestrator(
            scope = ServiceLocator.screeningScope,
            audioFocusManager = ServiceLocator.audioFocusManager,
            audioCapture = ServiceLocator.callAudioCapture,
            ttsEngine = ServiceLocator.ttsEngine,
            speechToTextEngine = ServiceLocator.speechToTextEngine,
            llmEngine = ServiceLocator.llmProvider.activeEngine(),
            callLogRepository = ServiceLocator.callLogRepository,
            callControl = CallControlCoordinator(applicationContext),
        )
    }

    override fun onScreenCall(callDetails: Call.Details) {
        Log.i(TAG, "onScreenCall from=${callDetails.handle}")

        val response = CallResponse.Builder()
            // Do not block at the OS level yet — we evaluate after answering silently.
            .setDisallowCall(false)
            .setRejectCall(false)
            // Prevent audible ringing while screening proceeds in the background.
            .setSilenceCall(true)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)

        orchestrator.handleScreenedCall(callDetails)
    }

    companion object {
        private const val TAG = "LocalCallScreeningService"
    }
}
