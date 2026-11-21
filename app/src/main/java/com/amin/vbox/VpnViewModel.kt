package com.amin.vbox

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amin.vbox.core.CoreEngineFactory
import com.amin.vbox.core.VboxVpnService
import com.amin.vbox.data.ConfigParser
import com.amin.vbox.data.DeviceIdentity
import com.amin.vbox.data.PingTester
import com.amin.vbox.data.ProxyConfig
import com.amin.vbox.data.Store
import com.amin.vbox.data.Subscription
import com.amin.vbox.data.SubscriptionFetcher
import com.amin.vbox.data.AppSettings
import com.amin.vbox.data.ConnectionState
import com.amin.vbox.data.TrafficSample
import com.amin.vbox.i18n.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class UiEvent {
    data class Message(val text: String) : UiEvent()
    data class RequestVpnPermission(val intent: Intent) : UiEvent()
    object ShowCoreNotReady : UiEvent()
    object ShowNoConfig : UiEvent()
    object RequestQrScan : UiEvent()
}

data class UiState(
    val configs: List<ProxyConfig> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val connectionError: String? = null,
    val traffic: TrafficSample = TrafficSample(0L, 0L),
    val publicIp: String? = null,
    val pinging: Boolean = false,
    val busy: Boolean = false
) {
    val selectedConfig: ProxyConfig? get() = configs.firstOrNull { it.id == settings.selectedConfigId }
    val strings get() = Strings.forLanguage(settings.language)
    val localConfigs get() = configs.filter { it.subscriptionId == null }
    fun configsOf(subscriptionId: String?) = configs.filter { it.subscriptionId == subscriptionId }
}

class VpnViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    val deviceIdentity = DeviceIdentity(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(store.configsFlow, store.subscriptionsFlow, store.settingsFlow) { c, s, st ->
                Triple(c, s, st)
            }.collect { (configs, subs, settings) ->
                val current = _state.value
                _state.value = current.copy(
                    configs = sortConfigs(configs, settings.sortMode),
                    subscriptions = subs,
                    settings = settings
                )
            }
        }
    }

    private fun sortConfigs(list: List<ProxyConfig>, mode: String): List<ProxyConfig> = when (mode) {
        "ping" -> list.sortedBy { PingTester.sortKey(it.pingMs) }
        "name" -> list.sortedBy { it.name.lowercase() }
        else -> list
    }

    // ---- connect / disconnect ----

    fun onPowerPressed() {
        val st = _state.value
        if (st.connection == ConnectionState.CONNECTED || st.connection == ConnectionState.CONNECTING) {
            disconnect()
            return
        }
        viewModelScope.launch {
            var selected = st.selectedConfig
            if (selected == null && st.configs.isNotEmpty()) {
                // Nothing selected yet: pick the first (or best ping with Smart Connect).
                selected = pickConfig(st)
                selected?.let { selectConfig(it.id) }
            }
            if (selected == null) {
                _events.emit(UiEvent.ShowNoConfig)
                return@launch
            }
            if (!CoreEngineFactory.create().isAvailable) {
                _events.emit(UiEvent.ShowCoreNotReady)
                return@launch
            }
            val ctx = getApplication<Application>()
            val prepare = VpnService.prepare(ctx)
            if (prepare != null) {
                _events.emit(UiEvent.RequestVpnPermission(prepare))
            } else {
                startService(selected)
            }
        }
    }

    /** Called after the user grants VPN permission. */
    fun onVpnPermissionGranted() {
        val selected = _state.value.selectedConfig ?: return
        startService(selected)
    }

    private suspend fun pickConfig(st: UiState): ProxyConfig? {
        if (!st.settings.smartConnect) return st.configs.firstOrNull()
        val results = PingTester.pingAll(st.configs)
        persistPings(results)
        val best = results.entries.filter { it.value >= 0 }.minByOrNull { it.value }
        return best?.let { id -> st.configs.firstOrNull { c -> c.id == id.key } }
            ?: st.configs.firstOrNull()
    }

    private fun startService(config: ProxyConfig) {
        val ctx = getApplication<Application>()
        _state.value = _state.value.copy(connection = ConnectionState.CONNECTING, connectionError = null)
        ctx.startService(VboxVpnService.startIntent(ctx, config, _state.value.settings))
    }

    fun disconnect() {
        val ctx = getApplication<Application>()
        val traffic = _state.value.traffic
        viewModelScope.launch {
            store.updateSettings {
                it.copy(
                    lastUploadBytes = traffic.uploadBytes,
                    lastDownloadBytes = traffic.downloadBytes
                )
            }
        }
        ctx.stopService(VboxVpnService.stopIntent(ctx))
        _state.value = _state.value.copy(
            connection = ConnectionState.DISCONNECTED,
            traffic = TrafficSample(0L, 0L),
            publicIp = null
        )
    }

    /** Service broadcasts (connected / disconnected / error / core-not-ready). */
    fun onServiceState(state: String, message: String?) {
        when (state) {
            VboxVpnService.STATE_CONNECTED -> {
                _state.value = _state.value.copy(connection = ConnectionState.CONNECTED, connectionError = null)
                viewModelScope.launch {
                    val ip = SubscriptionFetcher.publicIp()
                    _state.value = _state.value.copy(publicIp = ip)
                }
            }
            VboxVpnService.STATE_DISCONNECTED -> {
                if (_state.value.connection != ConnectionState.DISCONNECTED) {
                    _state.value = _state.value.copy(
                        connection = ConnectionState.DISCONNECTED,
                        traffic = TrafficSample(0L, 0L), publicIp = null
                    )
                }
            }
            VboxVpnService.STATE_ERROR -> {
                _state.value = _state.value.copy(connection = ConnectionState.ERROR, connectionError = message)
            }
            VboxVpnService.STATE_CORE_NOT_READY -> {
                _state.value = _state.value.copy(connection = ConnectionState.DISCONNECTED, connectionError = message)
                viewModelScope.launch { _events.emit(UiEvent.ShowCoreNotReady) }
            }
        }
    }

    fun onTraffic(up: Long, down: Long) {
        if (_state.value.connection == ConnectionState.CONNECTED) {
            _state.value = _state.value.copy(traffic = TrafficSample(up, down))
        }
    }

    // ---- selection & ping ----

    fun selectConfig(id: String) {
        viewModelScope.launch { store.updateSettings { it.copy(selectedConfigId = id) } }
    }

    fun pingSelected() {
        val cfg = _state.value.selectedConfig ?: return
        pingConfigs(listOf(cfg))
    }

    fun pingAll(subscriptionId: String? = null) {
        val list = if (subscriptionId == null) _state.value.configs
        else _state.value.configsOf(subscriptionId)
        pingConfigs(list)
    }

    fun pingConfigs(list: List<ProxyConfig>) {
        if (list.isEmpty() || _state.value.pinging) return
        viewModelScope.launch {
            _state.value = _state.value.copy(pinging = true)
            val results = PingTester.pingAll(list)
            persistPings(results)
            _state.value = _state.value.copy(pinging = false)
        }
    }

    private suspend fun persistPings(results: Map<String, Long>) {
        val current = store.loadConfigs()
        val updated = current.map { cfg ->
            val ms = results[cfg.id]
            if (ms != null) cfg.copy(pingMs = ms, pingAt = System.currentTimeMillis()) else cfg
        }
        store.saveConfigs(updated)
    }

    // ---- add configs ----

    fun addFromClipboard(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        viewModelScope.launch {
            val parsed = ConfigParser.parseMany(text)
            if (parsed.isEmpty()) {
                _events.emit(UiEvent.Message(_state.value.strings.noLinksFound))
                return@launch
            }
            val added = store.addConfigs(parsed)
            _events.emit(UiEvent.Message("$added ${_state.value.strings.addedConfigs}"))
        }
    }

    fun addLink(raw: String) {
        viewModelScope.launch {
            val parsed = ConfigParser.parse(raw.trim())
            if (parsed == null) {
                _events.emit(UiEvent.Message(_state.value.strings.invalidLink))
                return@launch
            }
            store.addConfigs(listOf(parsed))
            _events.emit(UiEvent.Message("1 ${_state.value.strings.addedConfigs}"))
        }
    }

    fun addSubscription(name: String, url: String, autoUpdate: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val sub = Subscription(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { url },
                url = url.trim(),
                autoUpdate = autoUpdate
            )
            val result = SubscriptionFetcher.fetch(sub.url, sub.id)
            _state.value = _state.value.copy(busy = false)
            result.onSuccess { fetched ->
                store.upsertSubscription(sub.copy(info = fetched.info, lastUpdatedAt = System.currentTimeMillis()))
                store.replaceSubscriptionConfigs(sub.id, fetched.configs)
                _events.emit(UiEvent.Message("${fetched.configs.size} ${_state.value.strings.addedConfigs}"))
            }.onFailure {
                _events.emit(UiEvent.Message(_state.value.strings.fetchFailed))
            }
        }
    }

    fun refreshSubscription(sub: Subscription) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val result = SubscriptionFetcher.fetch(sub.url, sub.id)
            _state.value = _state.value.copy(busy = false)
            result.onSuccess { fetched ->
                store.upsertSubscription(sub.copy(info = fetched.info, lastUpdatedAt = System.currentTimeMillis()))
                store.replaceSubscriptionConfigs(sub.id, fetched.configs)
                _events.emit(UiEvent.Message("${fetched.configs.size} ${_state.value.strings.addedConfigs}"))
            }.onFailure {
                _events.emit(UiEvent.Message(_state.value.strings.fetchFailed))
            }
        }
    }

    fun deleteSubscription(sub: Subscription) {
        viewModelScope.launch {
            store.deleteSubscription(sub.id)
            if (_state.value.selectedConfig?.subscriptionId == sub.id) {
                store.updateSettings { it.copy(selectedConfigId = null) }
            }
        }
    }

    fun renameSubscription(sub: Subscription, newName: String) {
        viewModelScope.launch { store.upsertSubscription(sub.copy(name = newName)) }
    }

    fun deleteConfig(cfg: ProxyConfig) {
        viewModelScope.launch {
            store.deleteConfig(cfg.id)
            if (_state.value.settings.selectedConfigId == cfg.id) {
                store.updateSettings { it.copy(selectedConfigId = null) }
            }
        }
    }

    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("VBOX", text))
        viewModelScope.launch { _events.emit(UiEvent.Message(_state.value.strings.copied)) }
    }

    // ---- settings ----

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { store.updateSettings(transform) }
    }

    fun setLanguage(lang: String) = updateSettings { it.copy(language = lang) }

    fun setSortMode(mode: String) = updateSettings { it.copy(sortMode = mode) }

    /** Settings > Reset: new identity + wipe all data. */
    fun resetAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { deviceIdentity.reset() }
            store.clearAll()
            _state.value = UiState()
            _events.emit(UiEvent.Message(Strings.en.resetDone))
        }
    }
}
