package com.amin.vbox.core

import android.net.VpnService
import com.amin.vbox.data.AppSettings
import com.amin.vbox.data.ProxyConfig
import com.amin.vbox.data.TrafficSample

/**
 * The VPN core boundary.
 *
 * HONEST STATUS (v1.0): the real sing-box core (libbox) is NOT bundled.
 * libbox has no Maven Central artifact — it is built from the sing-box
 * source tree with gomobile (`scripts/build-libbox.sh`, ~93 MB all-ABI AAR,
 * needs Go + Android NDK). Until `app/libs/libbox.aar` is present, the app
 * uses [NoCoreEngine]: connect attempts are refused with a clear
 * "Core not ready" state and the UI NEVER shows Connected or fake traffic.
 *
 * When the AAR is present, the Gradle build wires in
 * `src/libbox/.../LibboxEngine` (same interface, zero UI changes) and
 * [CoreEngineFactory] returns it instead.
 */
enum class CoreStatus { NOT_INSTALLED, READY, RUNNING, ERROR }

interface CoreEngine {
    val status: CoreStatus
    val isAvailable: Boolean
    /** Start the tunnel for [config]. Returns false + message when it cannot start. */
    fun start(config: ProxyConfig, settings: AppSettings, tunFd: Int): Result<Unit>
    fun stop()
    /** Bytes since this session started (0,0 when not running). */
    fun traffic(): TrafficSample
    /** Last error message, if any. */
    val lastError: String?
    /** Give the engine the running VpnService (context + protect(fd)). No-op by default. */
    fun attachService(service: VpnService) {}
}

/** Default engine: no native core bundled. Refuses to fake a connection. */
class NoCoreEngine : CoreEngine {
    override val status: CoreStatus = CoreStatus.NOT_INSTALLED
    override val isAvailable: Boolean = false
    override val lastError: String? =
        "VPN core not installed. Build libbox.aar from sing-box " +
            "with scripts/build-libbox.sh and place it in app/libs/, then rebuild."

    override fun start(config: ProxyConfig, settings: AppSettings, tunFd: Int): Result<Unit> =
        Result.failure(IllegalStateException(lastError))

    override fun stop() { /* nothing running */ }

    override fun traffic(): TrafficSample = TrafficSample(0L, 0L)
}

object CoreEngineFactory {
    /**
     * Returns the libbox engine when the AAR was compiled in, else [NoCoreEngine].
     * The libbox source set defines `LibboxEngineProvider` with the same
     * signature; reflection keeps the main source set compilable without it.
     */
    fun create(): CoreEngine {
        return try {
            val clazz = Class.forName("com.amin.vbox.core.LibboxEngineProvider")
            val method = clazz.getDeclaredMethod("create")
            method.invoke(null) as CoreEngine
        } catch (_: Exception) {
            NoCoreEngine()
        }
    }
}
