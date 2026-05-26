package com.aicallscreen.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aicallscreen.R
import com.aicallscreen.di.ServiceLocator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Minimal history viewer for locally persisted screening events.
 */
class CallLogActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_log)
        logTextView = findViewById(R.id.logTextView)
        title = getString(R.string.call_log_title)

        lifecycleScope.launch {
            ServiceLocator.callLogRepository.observeAllLogs().collectLatest { logs ->
                val formatted = logs.joinToString(separator = "\n\n") { entry ->
                    buildString {
                        appendLine("Number: ${entry.phoneNumber}")
                        appendLine("Time: ${DateFormat.getDateTimeInstance().format(Date(entry.timestamp))}")
                        appendLine("TTS: ${entry.ttsGreetingText}")
                        appendLine("Transcript: ${entry.callerTranscript}")
                        appendLine("AI: ${entry.aiThoughts}")
                        appendLine("Blocked: ${entry.wasBlocked}")
                    }
                }
                logTextView.text = formatted.ifBlank { "No screening events logged yet." }
            }
        }
    }
}
