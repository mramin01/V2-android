package com.amin.vbox.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.amin.vbox.MainActivity
import com.amin.vbox.R
import com.amin.vbox.data.AppSettings
import com.amin.vbox.data.ProxyConfig
import com.amin.vbox.data.SingBoxConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Android VpnService + TUN scaffolding.
 *
 * Real responsibilities (work today, core or not):
 *  - foreground service + persistent notification while a session is active
 *  - builds the TUN interface (172.19.0.1/30, MTU from settings, DNS server)
 *  - "Stop on sleep": when enabled, ACTION_SCREEN_OFF stops the session
 *  - hands the TUN fd to the [CoreEngine] and stops everything on revoke
 *
 * It deliberately does NOT establish a TUN or claim a connection when the
 * core is not installed — an empty TUN would blackhole all traffic while
 * the UI claimed to be connected. [VpnViewModel] refuses the connect
 * attempt before this service is ever started in that case.
 */
class VboxVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null
    private var engine: CoreEngine = NoCoreEngine()
    private var config: ProxyConfig? = null
    private var settings: AppSettings = AppSettings()
    private var stopOnSleep = true

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && stopOnSleep) {
                stopSession("Screen off (Stop on sleep is ON)")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        stopSession("Service destroyed")
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val cfg = intent.getStringExtra(EXTRA_CONFIG_JSON)
                    ?.let { runCatching { configFromJson(it) }.getOrNull() }
                val st = intent.getStringExtra(EXTRA_SETTINGS_JSON)
                    ?.let { runCatching { settingsFromJson(it) }.getOrNull() } ?: AppSettings()
                if (cfg == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                config = cfg
                settings = st
                stopOnSleep = st.stopOnSleep
                engine = CoreEngineFactory.create()
                engine.attachService(this)
                scope.launch { startSession(cfg, st) }
            }
            ACTION_STOP -> stopSession("Stopped by user")
        }
        return START_NOT_STICKY
    }

    private suspend fun startSession(cfg: ProxyConfig, st: AppSettings) {
        if (!engine.isAvailable) {
            // Never fake a tunnel: no core -> no TUN, no notification claim.
            broadcastState(STATE_CORE_NOT_READY, engine.lastError)
            stopSelf()
            return
        }
        startForeground(NOTIFICATION_ID, buildNotification(cfg, connected = false))
        val tunFd = establishTun(st) ?: run {
            broadcastState(STATE_ERROR, "Could not establish TUN interface")
            stopSelf()
            return
        }
        // Validate the generated sing-box config parses as JSON before handing it over.
        runCatching { SingBoxConfig.build(cfg, st) }.onFailure {
            broadcastState(STATE_ERROR, "Invalid config: ${it.message}")
            stopSelf()
            return
        }
        val result = engine.start(cfg, st, tunFd.fd)
        if (result.isFailure) {
            broadcastState(STATE_ERROR, result.exceptionOrNull()?.message ?: "Core failed to start")
            stopSelf()
            return
        }
        startForeground(NOTIFICATION_ID, buildNotification(cfg, connected = true))
        broadcastState(STATE_CONNECTED, null)
        // Live traffic ticker: every 2 s broadcast the core's counters so
        // Home can show real up/down. Stops when the TUN closes.
        scope.launch {
            while (tun != null) {
                val sample = runCatching { engine.traffic() }.getOrNull()
                if (sample != null) {
                    sendBroadcast(
                        Intent(ACTION_TRAFFIC)
                            .putExtra(EXTRA_UP, sample.uploadBytes)
                            .putExtra(EXTRA_DOWN, sample.downloadBytes)
                            .setPackage(packageName)
                    )
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    /** Build the TUN interface. Returns the fd, or null on failure. */
    private fun establishTun(st: AppSettings): ParcelFileDescriptor? {
        return try {
            val builder = Builder()
                .setSession("VBOX")
                .addAddress("172.19.0.1", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(st.dnsServer)
                .setMtu(st.mtu)
            if (st.ipv6) {
                builder.addAddress("fdfe:dcba:9876::1", 126)
                builder.addRoute("::", 0)
            }
            // LAN bypass is a sing-box route rule (ip_is_private -> direct),
            // not a Builder option, so nothing else is needed here.
            val pfd = builder.establish() ?: return null
            tun = pfd
            pfd
        } catch (_: Exception) {
            null
        }
    }

    fun stopSession(reason: String?) {
        runCatching { engine.stop() }
        runCatching { tun?.close() }
        tun = null
        broadcastState(STATE_DISCONNECTED, reason)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopSession("VPN permission revoked")
        super.onRevoke()
    }

    private fun broadcastState(state: String, message: String?) {
        sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .putExtra(EXTRA_STATE, state)
                .putExtra(EXTRA_MESSAGE, message)
                .setPackage(packageName)
        )
    }

    private fun buildNotification(cfg: ProxyConfig, connected: Boolean): Notification {
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (connected) "VBOX connected" else "VBOX connecting…")
            .setContentText(cfg.name)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = getString(R.string.notification_channel_desc) }
            )
        }
    }

    private fun configFromJson(jsonStr: String): ProxyConfig =
        kotlinx.serialization.json.Json.decodeFromString(ProxyConfig.serializer(), jsonStr)

    private fun settingsFromJson(jsonStr: String): AppSettings =
        kotlinx.serialization.json.Json.decodeFromString(AppSettings.serializer(), jsonStr)

    companion object {
        const val ACTION_START = "com.amin.vbox.action.START"
        const val ACTION_STOP = "com.amin.vbox.action.STOP"
        const val ACTION_STATE_CHANGED = "com.amin.vbox.action.STATE_CHANGED"
        const val ACTION_TRAFFIC = "com.amin.vbox.action.TRAFFIC"
        const val EXTRA_CONFIG_JSON = "config_json"
        const val EXTRA_SETTINGS_JSON = "settings_json"
        const val EXTRA_STATE = "state"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_UP = "up_bytes"
        const val EXTRA_DOWN = "down_bytes"

        const val STATE_CONNECTED = "connected"
        const val STATE_DISCONNECTED = "disconnected"
        const val STATE_ERROR = "error"
        const val STATE_CORE_NOT_READY = "core_not_ready"

        const val CHANNEL_ID = "vbox_vpn"
        const val NOTIFICATION_ID = 1

        fun startIntent(context: Context, config: ProxyConfig, settings: AppSettings): Intent =
            Intent(context, VboxVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG_JSON, kotlinx.serialization.json.Json.encodeToString(ProxyConfig.serializer(), config))
                putExtra(EXTRA_SETTINGS_JSON, kotlinx.serialization.json.Json.encodeToString(AppSettings.serializer(), settings))
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, VboxVpnService::class.java).apply { action = ACTION_STOP }

        /** Directory libbox expects for its working files (used by LibboxEngine). */
        fun coreWorkingDir(context: Context): File =
            File(context.filesDir, "singbox").apply { mkdirs() }
    }
}
