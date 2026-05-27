package com.aicallscreen.rules

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aicallscreen.contacts.PhoneNumberNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.rulesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "screening_rules",
)

class ScreeningRulesRepository(
    private val context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true }

    val rulesFlow: Flow<ScreeningRules> = context.rulesDataStore.data.map { prefs ->
        ScreeningRules(
            aiMasterEnabled = prefs[KEY_AI_MASTER] ?: true,
            globalMode = GlobalScreeningMode.valueOf(
                prefs[KEY_GLOBAL_MODE] ?: GlobalScreeningMode.DEFAULT.name,
            ),
            allowlist = decodeAllowlist(prefs[KEY_ALLOWLIST_JSON]),
        )
    }

    /** Synchronous read for [CallScreeningService] on a binder thread. */
    fun currentRules(): ScreeningRules = runBlocking { rulesFlow.first() }

    suspend fun setAiMasterEnabled(enabled: Boolean) {
        context.rulesDataStore.edit { it[KEY_AI_MASTER] = enabled }
    }

    suspend fun setGlobalMode(mode: GlobalScreeningMode) {
        context.rulesDataStore.edit { it[KEY_GLOBAL_MODE] = mode.name }
    }

    suspend fun addToAllowlist(phoneNumber: String, displayLabel: String) {
        val normalized = PhoneNumberNormalizer.normalize(phoneNumber) ?: return
        context.rulesDataStore.edit { prefs ->
            val current = decodeAllowlist(prefs[KEY_ALLOWLIST_JSON]).toMutableList()
            current.removeAll { it.normalizedNumber == normalized }
            current.add(AllowlistEntry(normalized, displayLabel))
            prefs[KEY_ALLOWLIST_JSON] = json.encodeToString(current)
        }
    }

    suspend fun removeFromAllowlist(normalizedNumber: String) {
        context.rulesDataStore.edit { prefs ->
            val current = decodeAllowlist(prefs[KEY_ALLOWLIST_JSON])
                .filterNot { it.normalizedNumber == normalizedNumber }
            prefs[KEY_ALLOWLIST_JSON] = json.encodeToString(current)
        }
    }

    suspend fun resetToDefaults() {
        context.rulesDataStore.edit { prefs ->
            prefs[KEY_AI_MASTER] = true
            prefs[KEY_GLOBAL_MODE] = GlobalScreeningMode.DEFAULT.name
            prefs[KEY_ALLOWLIST_JSON] = "[]"
        }
    }

    private fun decodeAllowlist(raw: String?): List<AllowlistEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private val KEY_AI_MASTER = booleanPreferencesKey("ai_master_enabled")
        private val KEY_GLOBAL_MODE = stringPreferencesKey("global_mode")
        private val KEY_ALLOWLIST_JSON = stringPreferencesKey("allowlist_json")
    }
}
