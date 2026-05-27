package com.aicallscreen.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.aicallscreen.di.ServiceLocator
import com.aicallscreen.routing.CallRoute
import com.aicallscreen.session.ScreeningSessionManager
import com.aicallscreen.session.ScreeningSessionType
import com.aicallscreen.ui.ScreeningUiLauncher

class LocalCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: "unknown"
        Log.i(TAG, "onScreenCall from=$phoneNumber")

        when (ServiceLocator.callRoutingPolicy.routeFor(phoneNumber)) {
            CallRoute.KNOWN_CONTACT_NORMAL_RING -> handleKnownContact(callDetails, phoneNumber)
            CallRoute.UNKNOWN_AI_SCREENING -> handleUnknownCaller(callDetails)
            CallRoute.PASS_THROUGH -> handlePassThrough(callDetails)
        }
    }

    /** Rules/testing bypass — do not silence, answer, or start any AI pipeline. */
    private fun handlePassThrough(callDetails: Call.Details) {
        Log.i(TAG, "PASS_THROUGH — AI rules skipped for ${callDetails.handle}")
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipNotification(false)
                .build(),
        )
    }

    private fun handleKnownContact(callDetails: Call.Details, phoneNumber: String) {
        val contactName = ServiceLocator.contactResolver.displayNameFor(phoneNumber) ?: phoneNumber
        val ownerName = ServiceLocator.contactResolver.ownerDisplayName()

        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipNotification(false)
                .build(),
        )

        val session = ScreeningSessionManager.createSession(
            phoneNumber = phoneNumber,
            displayLabel = contactName,
            sessionType = ScreeningSessionType.INCOMING_KNOWN_RINGING,
            ownerDisplayName = ownerName,
            contactDisplayName = contactName,
        )

        ScreeningUiLauncher.showIncomingCallScreen(
            context = applicationContext,
            sessionId = session.sessionId,
            phoneNumber = phoneNumber,
            displayName = contactName,
            ownerDisplayName = ownerName,
        )

        ServiceLocator.knownContactCallWatcher.scheduleNoAnswerAssistant(
            phoneNumber = phoneNumber,
            contactDisplayName = contactName,
            ownerDisplayName = ownerName,
        )
    }

    private fun handleUnknownCaller(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: "unknown"

        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(true)
                .setSkipNotification(false)
                .build(),
        )

        ServiceLocator.unknownCallerOrchestrator.start(phoneNumber)
    }

    companion object {
        private const val TAG = "LocalCallScreeningService"
    }
}
