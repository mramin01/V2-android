package com.amin.vbox.core

/**
 * sing-box libbox integration.
 *
 * THIS FILE ONLY COMPILES WHEN app/libs/libbox.aar IS PRESENT.
 * The Gradle build adds src/libbox/java to the main source set and the AAR
 * dependency only in that case (see app/build.gradle.kts). A plain CI build
 * never sees this file, so it cannot break the default build.
 *
 * HOW TO ENABLE (see scripts/build-libbox.sh):
 *   1. Build libbox.aar from sing-box (pinned version below) with gomobile.
 *   2. Copy it to app/libs/libbox.aar and rebuild.
 *   3. Fix any API drift against the pinned version — the libbox surface
 *      moves between releases (e.g. the command-server API changed in
 *      1.13). This file targets sing-box 1.11.x as shipped in
 *      SagerNet/sing-box-for-android (SFA): Libbox.setup(),
 *      Libbox.newService(config, platformInterface), Service.start()/close(),
 *      and the PlatformInterface callbacks (openTun /
 *      autoDetectInterfaceControl are the two the service must implement;
 *      the rest are no-ops here). It is UNVERIFIED against a real AAR:
 *      the first build with the AAR present is expected to need a fix-up
 *      pass in this one file only — the UI and VpnService do not change.
 *
 * PINNED VERSION: sing-box v1.11.10 (SFA 1.11.x line).
 */

import android.content.Context
import android.net.VpnService
import com.amin.vbox.data.AppSettings
import com.amin.vbox.data.ProxyConfig
import com.amin.vbox.data.SingBoxConfig
import com.amin.vbox.data.TrafficSample
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.TunOptions
import java.io.File

/**
 * Factory found by reflection from [CoreEngineFactory] — do not rename
 * the class or the `create()` method.
 */
object LibboxEngineProvider {
    @JvmStatic
    fun create(): CoreEngine = LibboxEngine()
}

class LibboxEngine : CoreEngine {

    override var status: CoreStatus = CoreStatus.READY
        private set
    override val isAvailable: Boolean = true
    override var lastError: String? = null
        private set

    private var service: io.nekohasekai.libbox.Service? = null
    private var tunFd: Int = -1
    private var vpnService: VpnService? = null

    /** Receives the running VpnService from [VboxVpnService] (context + protect). */
    override fun attachService(service: VpnService) {
        vpnService = service
    }

    /**
     * Called from [VboxVpnService] with the TUN fd from VpnService.Builder.
     * The platform interface hands that fd to libbox (openTun) and protects
     * the core's own sockets from the tunnel (autoDetectInterfaceControl).
     */
    override fun start(config: ProxyConfig, settings: AppSettings, tunFd: Int): Result<Unit> {
        return runCatching {
            this.tunFd = tunFd
            val vpn = vpnService ?: error("attachService() must be called before start()")
            val ctx = vpn.applicationContext
            val workDir = File(ctx.filesDir, "singbox").apply { mkdirs() }
            Libbox.setup(workDir.absolutePath, ctx.cacheDir.absolutePath)
            val configJson = SingBoxConfig.build(config, settings)
            val platform = VboxPlatformInterface(vpn, tunFd)
            val svc = Libbox.newService(configJson, platform)
            svc.start()
            service = svc
            status = CoreStatus.RUNNING
        }.onFailure {
            lastError = it.message
            status = CoreStatus.ERROR
        }
    }

    override fun stop() {
        runCatching { service?.close() }
        service = null
        status = CoreStatus.READY
    }

    override fun traffic(): TrafficSample {
        // Traffic counters come from libbox's command client in the full
        // integration (Libbox.newCommandClient). Until that is wired to the
        // pinned version, report 0 rather than inventing numbers.
        return TrafficSample(0L, 0L)
    }
}

/**
 * Minimal [PlatformInterface]: only the TUN handoff and socket protection
 * are implemented; discovery/monitoring callbacks are no-ops. Method set
 * follows sing-box 1.11.x — verify against the pinned AAR on first build.
 */
private class VboxPlatformInterface(
    private val vpnService: VpnService,
    private val tunFd: Int
) : PlatformInterface {

    override fun openTun(options: TunOptions?): Int = tunFd

    override fun autoDetectInterfaceControl(fd: Int) {
        // Keep the core's outbound sockets outside the TUN or the tunnel
        // would route its own traffic into itself.
        vpnService.protect(fd)
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun useProcFS(): Boolean = false

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String?,
        sourcePort: Int,
        destinationAddress: String?,
        destinationPort: Int
    ): Int = -1

    override fun packageNameByUid(uid: Int): String? = null

    override fun uidByPackageName(packageName: String?): Int = -1

    override fun getInterfaces(): io.nekohasekai.libbox.NetworkInterfaceIterator? = null

    override fun underNetworkListener(listener: io.nekohasekai.libbox.InterfaceUpdateListener?) {}

    override fun closeNetworkListener(listener: io.nekohasekai.libbox.InterfaceUpdateListener?) {}

    override fun getWiFiState(): io.nekohasekai.libbox.WIFIState? = null

    override fun localDNSTransport(): io.nekohasekai.libbox.LocalDNSTransport? = null

    override fun readWIFIState(listener: io.nekohasekai.libbox.WIFIStateListener?) {}

    override fun startDefaultInterfaceMonitor(listener: io.nekohasekai.libbox.InterfaceUpdateListener?) {}

    override fun sendNotification(notification: io.nekohasekai.libbox.Notification?) {}

    override fun writeLog(message: String?) {}
}
