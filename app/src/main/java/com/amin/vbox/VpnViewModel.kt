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
                // "Auto update" that actually updates: once per launch, refresh
                // the auto-update subscriptions whose data is older than a day
                // (or never fetched). Silent — no snackbars on startup; the
                // empty-fetch guard in applyFetchResult protects the servers.
                if (!autoRefreshDone && subs.isNotEmpty()) {
                    autoRefreshDone = true
                    val stale = subs.filter { sub ->
                        sub.autoUpdate && (sub.lastUpdatedAt == 0L ||
                            System.currentTimeMillis() - sub.lastUpdatedAt > DAY_MS)
                    }
                    for (sub in stale) refreshSubscription(sub, silent = true)
                }
            }
        }
    }

    private var autoRefreshDone = false

    /** Show a snackbar message (also usable from the Activity, e.g. permission denials). */
    fun showMessage(text: String) {
        viewModelScope.launch { _events.emit(UiEvent.Message(text)) }
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
        val cfg = _state.value.selectedConfig
        if (cfg == null) {
            showMessage(_state.value.strings.selectServer)
            return
        }
        pingConfigs(listOf(cfg))
    }

    /** Ping every server in one group ("local" = the Local group). */
    fun pingGroup(groupId: String) {
        val list = if (groupId == "local") _state.value.localConfigs
        else _state.value.configsOf(groupId)
        pingConfigs(list)
    }

    /** Ping every server the app knows. */
    fun pingAll() {
        pingConfigs(_state.value.configs)
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
        val clip = cm?.primaryClip
        if (clip == null || clip.itemCount == 0) {
            showMessage(_state.value.strings.noLinksFound)
            return
        }
        val text = clip.getItemAt(0)?.text?.toString().orEmpty()
        addLinks(text)
    }

    /** Single-link entry point (QR scan, share intent, deep link). */
    fun addLink(raw: String) = addLinks(raw)

    /**
     * Add every proxy link found in [text] (one link, or pasted text with
     * several) and SELECT the first newly added one — adding a config must
     * make it the active server. Re-adding a link that is already stored
     * selects the existing server instead of reporting "0 added".
     */
    fun addLinks(text: String) {
        viewModelScope.launch {
            val trimmed = text.trim()
            // A single clean link keeps its full fragment (#name with spaces);
            // anything else is scanned for all links it contains.
            val parsed = ConfigParser.parse(trimmed)?.let { listOf(it) }
                ?: ConfigParser.parseMany(trimmed)
            if (parsed.isEmpty()) {
                _events.emit(UiEvent.Message(_state.value.strings.noLinksFound))
                return@launch
            }
            val added = store.addConfigs(parsed)
            selectNewOrExisting(added, parsed)
            val msg = if (added.isEmpty()) _state.value.strings.alreadyAdded
            else "${added.size} ${_state.value.strings.addedConfigs}"
            _events.emit(UiEvent.Message(msg))
        }
    }

    /** Select the first added config, or the stored twin of a re-added one. */
    private suspend fun selectNewOrExisting(added: List<ProxyConfig>, candidates: List<ProxyConfig>) {
        val target = added.firstOrNull()
            ?: candidates.firstNotNullOfOrNull { matchConfig(it, _state.value.configs) }
            ?: return
        store.updateSettings { it.copy(selectedConfigId = target.id) }
    }

    /** Find [old] (or an equal server) inside [inList]: same id, link, or host. */
    private fun matchConfig(old: ProxyConfig?, inList: List<ProxyConfig>): ProxyConfig? {
        if (old == null) return null
        return inList.firstOrNull { it.id == old.id }
            ?: inList.firstOrNull { it.rawLink.isNotBlank() && it.rawLink == old.rawLink }
            ?: inList.firstOrNull {
                it.address == old.address && it.port == old.port &&
                    it.uuid.isNotBlank() && it.uuid == old.uuid
            }
    }

    fun addSubscription(name: String, url: String, autoUpdate: Boolean) {
        val fixedUrl = url.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val sub = Subscription(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { fixedUrl },
                url = fixedUrl,
                autoUpdate = autoUpdate
            )
            val result = SubscriptionFetcher.fetch(sub.url, sub.id)
            _state.value = _state.value.copy(busy = false)
            result.onSuccess { fetched ->
                applyFetchResult(sub, fetched, silent = false, selectFirst = true)
            }.onFailure {
                _events.emit(UiEvent.Message(_state.value.strings.fetchFailed))
            }
        }
    }

    fun refreshSubscription(sub: Subscription, silent: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val result = SubscriptionFetcher.fetch(sub.url, sub.id)
            _state.value = _state.value.copy(busy = false)
            result.onSuccess { fetched ->
                applyFetchResult(sub, fetched, silent = silent, selectFirst = !silent)
            }.onFailure {
                if (!silent) _events.emit(UiEvent.Message(_state.value.strings.fetchFailed))
            }
        }
    }

    /**
     * Shared handling of a subscription fetch, for first add and refresh.
     * Two guarantees the old code lacked:
     *  - an empty fetch NEVER wipes the servers already stored (a panel can
     *    return an empty body with a valid usage header — e.g. an expired
     *    subscription — and the old code deleted everything silently);
     *  - the selection survives: the previously selected server of this
     *    subscription is re-selected when it is still in the fresh list,
     *    otherwise the first fresh server becomes active.
     */
    private suspend fun applyFetchResult(
        sub: Subscription,
        fetched: SubscriptionFetcher.FetchResult,
        silent: Boolean,
        selectFirst: Boolean
    ) {
        val s = _state.value.strings
        val previousSelected = _state.value.selectedConfig
        if (fetched.configs.isEmpty()) {
            // Keep the old servers AND the usage info; only warn the user.
            store.upsertSubscription(
                sub.copy(info = fetched.info, lastUpdatedAt = System.currentTimeMillis())
            )
            if (!silent) {
                val base = if (fetched.rawLines > 0) "${fetched.rawLines} ${s.subscriptionUnparseable}"
                else s.subscriptionNoConfigs
                val msg = if (fetched.info?.daysLeft == 0L) "$base ${s.expiredNotice}" else base
                _events.emit(UiEvent.Message(msg))
            }
            return
        }
        store.upsertSubscription(
            sub.copy(info = fetched.info, lastUpdatedAt = System.currentTimeMillis())
        )
        store.replaceSubscriptionConfigs(sub.id, fetched.configs)
        val target = matchConfig(
            previousSelected?.takeIf { it.subscriptionId == sub.id },
            fetched.configs
        ) ?: if (selectFirst) fetched.configs.firstOrNull() else null
        target?.let { cfg -> store.updateSettings { it.copy(selectedConfigId = cfg.id) } }
        if (!silent) _events.emit(UiEvent.Message("${fetched.configs.size} ${s.addedConfigs}"))
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
            // Capture the message BEFORE wiping: resetting the state also
            // resets the language to English, so reading it afterwards
            // would show the wrong language.
            val done = _state.value.strings.resetDone
            val ctx = getApplication<Application>()
            ctx.stopService(VboxVpnService.stopIntent(ctx))
            withContext(Dispatchers.IO) { deviceIdentity.reset() }
            store.clearAll()
            _state.value = UiState()
            _events.emit(UiEvent.Message(done))
        }
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
