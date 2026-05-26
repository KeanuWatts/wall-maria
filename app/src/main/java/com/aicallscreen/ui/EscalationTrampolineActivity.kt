package com.aicallscreen.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.aicallscreen.di.ServiceLocator

/**
 * Transparent activity launched from the escalation notification so the user can
 * jump into the system in-call UI for an already-connected screened call.
 */
class EscalationTrampolineActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER).orEmpty()
        ServiceLocator.userEscalationManager.stopEscalationRinging()

        try {
            val showDialer = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(showDialer)
            Log.i(TAG, "Opened dialer for screened call $phoneNumber")
        } catch (error: Exception) {
            Log.e(TAG, "Failed to open in-call UI", error)
        }

        finish()
    }

    companion object {
        private const val TAG = "EscalationTrampoline"
        private const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        private const val EXTRA_CALLER_LABEL = "extra_caller_label"

        fun createIntent(context: Context, phoneNumber: String, callerLabel: String): Intent =
            Intent(context, EscalationTrampolineActivity::class.java).apply {
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CALLER_LABEL, callerLabel)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
    }
}
