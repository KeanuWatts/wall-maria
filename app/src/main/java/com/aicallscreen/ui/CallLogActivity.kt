package com.aicallscreen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aicallscreen.R
import com.aicallscreen.di.ServiceLocator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class CallLogActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* re-bind on grant */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_log)
        logTextView = findViewById(R.id.logTextView)
        title = getString(R.string.call_log_title)

        requestRuntimePermissions()

        lifecycleScope.launch {
            ServiceLocator.screeningRulesRepository.rulesFlow.collectLatest { rules ->
                val modeLabel = when {
                    !rules.aiMasterEnabled -> getString(R.string.rules_status_master_off)
                    rules.globalMode == com.aicallscreen.rules.GlobalScreeningMode.ALLOWLIST_ONLY ->
                        getString(R.string.rules_status_allowlist, rules.allowlist.size)
                    rules.globalMode == com.aicallscreen.rules.GlobalScreeningMode.AI_DISABLED ->
                        getString(R.string.rules_status_disabled)
                    else -> getString(R.string.rules_status_default)
                }
                title = "${getString(R.string.call_log_title)} · $modeLabel"
            }
        }

        lifecycleScope.launch {
            ServiceLocator.callLogRepository.observeAllLogs().collectLatest { logs ->
                val formatted = logs.joinToString(separator = "\n\n") { entry ->
                    buildString {
                        appendLine("Number: ${entry.phoneNumber}")
                        appendLine("Route: ${entry.callRoute}")
                        appendLine("Time: ${DateFormat.getDateTimeInstance().format(Date(entry.timestamp))}")
                        appendLine("TTS: ${entry.ttsGreetingText}")
                        appendLine("Transcript: ${entry.callerTranscript}")
                        appendLine("AI: ${entry.aiThoughts}")
                        appendLine("Blocked: ${entry.wasBlocked}")
                        appendLine("Connected to you: ${entry.wasConnectedToUser}")
                        if (entry.leftMessage.isNotBlank()) {
                            appendLine("Message left: ${entry.leftMessage}")
                        }
                        if (entry.conversationHistory.isNotBlank()) {
                            appendLine("Dialog:")
                            append(entry.conversationHistory)
                        }
                    }
                }
                logTextView.text = formatted.ifBlank { "No screening events logged yet." }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_call_log, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_rules -> {
                startActivity(Intent(this, RulesActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun requestRuntimePermissions() {
        val required = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
