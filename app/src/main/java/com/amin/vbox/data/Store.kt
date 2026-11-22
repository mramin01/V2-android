package com.amin.vbox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistence via DataStore Preferences + kotlinx-serialization JSON.
 * One JSON blob per collection keeps the code small and avoids Room/kapt.
 * Everything survives restarts; clearing app data wipes it (expected).
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vbox_store")

class Store(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val KEY_CONFIGS = stringPreferencesKey("configs_json")
    private val KEY_SUBSCRIPTIONS = stringPreferencesKey("subscriptions_json")
    private val KEY_SETTINGS = stringPreferencesKey("settings_json")

    // ---- configs ----

    val configsFlow: Flow<List<ProxyConfig>> = context.dataStore.data.map { prefs ->
        prefs[KEY_CONFIGS]?.let { runCatching { json.decodeFromString<List<ProxyConfig>>(it) }.getOrNull() }
            ?: emptyList()
    }

    suspend fun loadConfigs(): List<ProxyConfig> = configsFlow.first()

    suspend fun saveConfigs(list: List<ProxyConfig>) {
        context.dataStore.edit { it[KEY_CONFIGS] = json.encodeToString(list) }
    }

    /** Adds the not-yet-known configs and returns the ones actually added. */
    suspend fun addConfigs(newOnes: List<ProxyConfig>): List<ProxyConfig> {
        val current = loadConfigs().toMutableList()
        val added = mutableListOf<ProxyConfig>()
        for (c in newOnes) {
            // De-dupe on raw link (same link re-imported) or same host+uuid.
            val dup = current.any { it.rawLink == c.rawLink && c.rawLink.isNotBlank() } ||
                current.any { it.address == c.address && it.port == c.port && it.uuid == c.uuid && it.uuid.isNotBlank() }
            if (!dup) {
                current.add(c)
                added += c
            }
        }
        if (added.isNotEmpty()) saveConfigs(current)
        return added
    }

    suspend fun updateConfig(updated: ProxyConfig) {
        val current = loadConfigs().map { if (it.id == updated.id) updated else it }
        saveConfigs(current)
    }

    suspend fun deleteConfig(id: String) {
        saveConfigs(loadConfigs().filterNot { it.id == id })
    }

    suspend fun deleteConfigsOfSubscription(subscriptionId: String) {
        saveConfigs(loadConfigs().filterNot { it.subscriptionId == subscriptionId })
    }

    suspend fun replaceSubscriptionConfigs(subscriptionId: String, fresh: List<ProxyConfig>) {
        val kept = loadConfigs().filterNot { it.subscriptionId == subscriptionId }
        saveConfigs(kept + fresh)
    }

    // ---- subscriptions ----

    val subscriptionsFlow: Flow<List<Subscription>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUBSCRIPTIONS]?.let { runCatching { json.decodeFromString<List<Subscription>>(it) }.getOrNull() }
            ?: emptyList()
    }

    suspend fun loadSubscriptions(): List<Subscription> = subscriptionsFlow.first()

    suspend fun saveSubscriptions(list: List<Subscription>) {
        context.dataStore.edit { it[KEY_SUBSCRIPTIONS] = json.encodeToString(list) }
    }

    suspend fun upsertSubscription(sub: Subscription) {
        val current = loadSubscriptions().toMutableList()
        val idx = current.indexOfFirst { it.id == sub.id }
        if (idx >= 0) current[idx] = sub else current.add(sub)
        saveSubscriptions(current)
    }

    suspend fun deleteSubscription(id: String) {
        saveSubscriptions(loadSubscriptions().filterNot { it.id == id })
        deleteConfigsOfSubscription(id)
    }

    // ---- settings ----

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        prefs[KEY_SETTINGS]?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() }
            ?: AppSettings()
    }

    suspend fun loadSettings(): AppSettings = settingsFlow.first()

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { it[KEY_SETTINGS] = json.encodeToString(settings) }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        saveSettings(transform(loadSettings()))
    }

    /** Wipe everything (Settings > Reset, after confirmation). */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
