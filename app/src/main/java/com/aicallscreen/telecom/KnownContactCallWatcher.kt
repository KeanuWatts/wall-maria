package com.aicallscreen.telecom

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.aicallscreen.contacts.PhoneNumberNormalizer
import com.aicallscreen.screening.KnownContactAssistantOrchestrator
import com.aicallscreen.session.ScreeningSessionManager
import com.aicallscreen.session.ScreeningSessionType
import com.aicallscreen.ui.ScreeningUiLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks known-contact rings and starts the voicemail assistant if the owner does not answer.
 */
class KnownContactCallWatcher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val telephonyManager: TelephonyManager,
    private val assistantOrchestrator: KnownContactAssistantOrchestrator,
) {

    private val pendingAssistants = ConcurrentHashMap<String, PendingKnownCall>()
    private var telephonyCallback: TelephonyCallback? = null
    private var legacyListener: PhoneStateListener? = null

    fun start() {
        registerCallStateListener()
    }

    fun scheduleNoAnswerAssistant(
        phoneNumber: String,
        contactDisplayName: String,
        ownerDisplayName: String,
    ) {
        cancelPending(phoneNumber)

        val job = scope.launch {
            delay(NO_ANSWER_TIMEOUT_MS)
            val pending = pendingAssistants[phoneNumber] ?: return@launch
            if (!pending.cancelled && telephonyManager.callState == TelephonyManager.CALL_STATE_RINGING) {
                Log.i(TAG, "Known contact not answered — starting assistant for $contactDisplayName")
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
                assistantOrchestrator.runAssistant(
                    phoneNumber = phoneNumber,
                    contactDisplayName = contactDisplayName,
                    ownerDisplayName = ownerDisplayName,
                    session = session,
                )
            }
        }

        pendingAssistants[phoneNumber] = PendingKnownCall(
            phoneNumber = phoneNumber,
            contactDisplayName = contactDisplayName,
            ownerDisplayName = ownerDisplayName,
            timeoutJob = job,
        )
    }

    fun cancelPending(phoneNumber: String) {
        val normalized = PhoneNumberNormalizer.normalize(phoneNumber) ?: phoneNumber
        pendingAssistants.remove(normalized)?.cancel()
        pendingAssistants.remove(phoneNumber)?.cancel()
    }

    private fun handleCallStateChanged(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK,
            TelephonyManager.CALL_STATE_IDLE,
            -> {
                pendingAssistants.keys.toList().forEach { cancelPending(it) }
            }
        }
    }

    private fun registerCallStateListener() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallStateChanged(state)
                }
            }
            telephonyCallback = callback
            telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallStateChanged(state)
                }
            }
            legacyListener = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    data class PendingKnownCall(
        val phoneNumber: String,
        val contactDisplayName: String,
        val ownerDisplayName: String,
        val timeoutJob: Job,
        @Volatile var cancelled: Boolean = false,
    ) {
        fun cancel() {
            cancelled = true
            timeoutJob.cancel()
        }
    }

    companion object {
        private const val TAG = "KnownContactCallWatcher"
        const val NO_ANSWER_TIMEOUT_MS = 25_000L
    }
}
