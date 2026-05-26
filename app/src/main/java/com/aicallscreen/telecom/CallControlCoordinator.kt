package com.aicallscreen.telecom

import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Thin wrapper around [TelecomManager] for answering and rejecting screened calls.
 */
class CallControlCoordinator(
    context: Context,
) {

    private val telecomManager: TelecomManager? =
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    @RequiresApi(Build.VERSION_CODES.O)
    fun answerRingingCall(): Boolean {
        return try {
            telecomManager?.acceptRingingCall()
            true
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Missing ANSWER_PHONE_CALLS permission", securityException)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun endCall(): Boolean {
        return try {
            telecomManager?.endCall() == true
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Unable to end call", securityException)
            false
        }
    }

    companion object {
        private const val TAG = "CallControlCoordinator"
    }
}
