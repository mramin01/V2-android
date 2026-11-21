package com.amin.vbox.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Real latency measurement: TCP connect to host:port.
 * This is a reachability/latency probe, not ICMP ping (Android apps cannot
 * send raw ICMP without root). Timeout 3 s per the spec. Runs concurrently.
 */
object PingTester {

    const val TIMEOUT_MS = 3000
    const val UNREACHABLE: Long = -1L

    /** Single TCP-connect probe. Returns ms, or UNREACHABLE (-1) on failure/timeout. */
    suspend fun tcpPing(host: String, port: Int, timeoutMs: Int = TIMEOUT_MS): Long =
        withContext(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                val start = System.nanoTime()
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                (System.nanoTime() - start) / 1_000_000L
            } catch (_: Exception) {
                UNREACHABLE
            } finally {
                runCatching { socket?.close() }
            }
        }

    /** Probe every config concurrently; returns id -> ms (or -1). */
    suspend fun pingAll(configs: List<ProxyConfig>): Map<String, Long> =
        withContext(Dispatchers.IO) {
            configs.map { cfg ->
                async { cfg.id to tcpPing(cfg.address, cfg.port) }
            }.awaitAll().toMap()
        }

    /** Human label: "86 ms", "timeout", "—". */
    fun label(ms: Long?): String = when {
        ms == null -> "—"
        ms < 0 -> "timeout"
        else -> "$ms ms"
    }

    /** Sort key: reachable first by latency, unreachable last. */
    fun sortKey(ms: Long?): Long = if (ms == null || ms < 0) Long.MAX_VALUE else ms
}
