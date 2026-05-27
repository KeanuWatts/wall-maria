package com.aicallscreen.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicallscreen.R
import com.aicallscreen.di.ServiceLocator
import com.aicallscreen.rules.GlobalScreeningMode
import com.aicallscreen.rules.ScreeningRules
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RulesActivity : AppCompatActivity() {

    private lateinit var switchAiMaster: MaterialSwitch
    private lateinit var radioDefault: MaterialRadioButton
    private lateinit var radioAllowlist: MaterialRadioButton
    private lateinit var radioDisabled: MaterialRadioButton
    private lateinit var allowlistSectionTitle: TextView
    private lateinit var allowlistSectionHint: TextView
    private lateinit var allowlistRecycler: RecyclerView
    private lateinit var statusSummary: TextView
    private val allowlistAdapter = AllowlistAdapter { entry ->
        lifecycleScope.launch {
            ServiceLocator.screeningRulesRepository.removeFromAllowlist(entry.normalizedNumber)
        }
    }

    private var suppressUiCallbacks = false

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact(),
    ) { uri -> uri?.let { importContact(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rules)
        title = getString(R.string.rules_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        switchAiMaster = findViewById(R.id.switchAiMaster)
        radioDefault = findViewById(R.id.radioModeDefault)
        radioAllowlist = findViewById(R.id.radioModeAllowlist)
        radioDisabled = findViewById(R.id.radioModeDisabled)
        allowlistSectionTitle = findViewById(R.id.allowlistSectionTitle)
        allowlistSectionHint = findViewById(R.id.allowlistSectionHint)
        allowlistRecycler = findViewById(R.id.allowlistRecycler)
        statusSummary = findViewById(R.id.rulesStatusSummary)

        allowlistRecycler.layoutManager = LinearLayoutManager(this)
        allowlistRecycler.adapter = allowlistAdapter

        switchAiMaster.setOnCheckedChangeListener { _, checked ->
            if (suppressUiCallbacks) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                ServiceLocator.screeningRulesRepository.setAiMasterEnabled(checked)
            }
        }

        findViewById<RadioGroup>(R.id.radioModeGroup).setOnCheckedChangeListener { _, checkedId ->
            if (suppressUiCallbacks) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.radioModeAllowlist -> GlobalScreeningMode.ALLOWLIST_ONLY
                R.id.radioModeDisabled -> GlobalScreeningMode.AI_DISABLED
                else -> GlobalScreeningMode.DEFAULT
            }
            lifecycleScope.launch {
                ServiceLocator.screeningRulesRepository.setGlobalMode(mode)
            }
        }

        findViewById<MaterialButton>(R.id.buttonAddContact).setOnClickListener {
            pickContactLauncher.launch(null)
        }

        findViewById<MaterialButton>(R.id.buttonAddNumber).setOnClickListener {
            showAddNumberDialog()
        }

        findViewById<MaterialButton>(R.id.buttonResetRules).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.rules_reset_confirm_title)
                .setMessage(R.string.rules_reset_confirm_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch {
                        ServiceLocator.screeningRulesRepository.resetToDefaults()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        lifecycleScope.launch {
            ServiceLocator.screeningRulesRepository.rulesFlow.collectLatest { rules ->
                renderRules(rules)
            }
        }
    }

    private fun renderRules(rules: ScreeningRules) {
        suppressUiCallbacks = true
        switchAiMaster.isChecked = rules.aiMasterEnabled
        when (rules.globalMode) {
            GlobalScreeningMode.DEFAULT -> radioDefault.isChecked = true
            GlobalScreeningMode.ALLOWLIST_ONLY -> radioAllowlist.isChecked = true
            GlobalScreeningMode.AI_DISABLED -> radioDisabled.isChecked = true
        }
        suppressUiCallbacks = false

        val showAllowlist = rules.globalMode == GlobalScreeningMode.ALLOWLIST_ONLY && rules.aiMasterEnabled
        val allowlistVisibility = if (showAllowlist) View.VISIBLE else View.GONE
        allowlistSectionTitle.visibility = allowlistVisibility
        allowlistSectionHint.visibility = allowlistVisibility
        allowlistRecycler.visibility = allowlistVisibility
        findViewById<MaterialButton>(R.id.buttonAddContact).visibility = allowlistVisibility
        findViewById<MaterialButton>(R.id.buttonAddNumber).visibility = allowlistVisibility

        allowlistAdapter.submitList(rules.allowlist)
        statusSummary.text = buildStatusSummary(rules)
    }

    private fun buildStatusSummary(rules: ScreeningRules): String {
        if (!rules.aiMasterEnabled) {
            return getString(R.string.rules_status_master_off)
        }
        return when (rules.globalMode) {
            GlobalScreeningMode.DEFAULT ->
                getString(R.string.rules_status_default)
            GlobalScreeningMode.ALLOWLIST_ONLY ->
                getString(R.string.rules_status_allowlist, rules.allowlist.size)
            GlobalScreeningMode.AI_DISABLED ->
                getString(R.string.rules_status_disabled)
        }
    }

    private fun importContact(contactUri: Uri) {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        )
        contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val number = if (numberIndex >= 0) cursor.getString(numberIndex) else return
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else number
            lifecycleScope.launch {
                ServiceLocator.screeningRulesRepository.addToAllowlist(number, name)
            }
        }
    }

    private fun showAddNumberDialog() {
        val input = TextInputEditText(this).apply {
            hint = getString(R.string.rules_phone_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.rules_add_number)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val number = input.text?.toString().orEmpty()
                if (number.isNotBlank()) {
                    lifecycleScope.launch {
                        ServiceLocator.screeningRulesRepository.addToAllowlist(number, number)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
