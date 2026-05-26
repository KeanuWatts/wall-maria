package com.aicallscreen.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.aicallscreen.di.ServiceLocator
import com.aicallscreen.routing.CallRoute

/**
 * Routes incoming calls:
 * - **Known contacts** → normal ring; assistant if unanswered.
 * - **Unknown numbers** → silent multi-turn AI screening → ring owner or block.
 */
class LocalCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: "unknown"
        Log.i(TAG, "onScreenCall from=$phoneNumber")

        val route = ServiceLocator.callRoutingPolicy.routeFor(phoneNumber)

        when (route) {
            CallRoute.KNOWN_CONTACT_NORMAL_RING -> handleKnownContact(callDetails, phoneNumber)
            CallRoute.UNKNOWN_AI_SCREENING -> handleUnknownCaller(callDetails)
        }
    }

    private fun handleKnownContact(callDetails: Call.Details, phoneNumber: String) {
        val contactName = ServiceLocator.contactResolver.displayNameFor(phoneNumber) ?: phoneNumber
        val ownerName = ServiceLocator.contactResolver.ownerDisplayName()

        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)

        ServiceLocator.knownContactCallWatcher.scheduleNoAnswerAssistant(
            phoneNumber = phoneNumber,
            contactDisplayName = contactName,
            ownerDisplayName = ownerName,
        )
    }

    private fun handleUnknownCaller(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: "unknown"

        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(true)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)

        ServiceLocator.unknownCallerOrchestrator.start(phoneNumber)
    }

    companion object {
        private const val TAG = "LocalCallScreeningService"
    }
}
