package com.amin.vbox.data

import kotlinx.serialization.Serializable

/** One proxy server, parsed from a vmess:// / vless:// / trojan:// / ss:// link. */
@Serializable
data class ProxyConfig(
    val id: String,                 // stable local id (UUID)
    val name: String,
    val protocol: String,           // "vmess" | "vless" | "trojan" | "shadowsocks"
    val address: String,
    val port: Int,
    val uuid: String = "",          // vmess/vless uuid, trojan password, ss password
    val alterId: Int = 0,           // vmess only
    val method: String = "",        // shadowsocks method; vmess security ("auto"/"aes-128-gcm"...)
    val network: String = "tcp",    // tcp | ws | grpc | h2
    val tls: Boolean = false,
    val sni: String = "",
    val host: String = "",          // ws host / http host
    val path: String = "",
    val flow: String = "",          // vless flow (xtls-rprx-vision ...)
    val rawLink: String = "",
    val subscriptionId: String? = null, // null = Local group
    val pingMs: Long? = null,       // last measured TCP latency, null = never tested
    val pingAt: Long = 0L
) {
    val displayAddress: String get() = "$address:$port"
}

/** A subscription (remote list of configs). */
@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val autoUpdate: Boolean = true,
    val lastUpdatedAt: Long = 0L,
    val info: SubscriptionInfo? = null
)

/** Parsed `subscription-userinfo` response header. All byte counts are raw. */
@Serializable
data class SubscriptionInfo(
    val upload: Long = 0L,
    val download: Long = 0L,
    val total: Long = 0L,
    val expireEpochSec: Long = 0L // 0 = no expiry reported
) {
    val used: Long get() = upload + download
    /** Days left until expiry, null when no expiry was reported. */
    val daysLeft: Long?
        get() = if (expireEpochSec <= 0L) null
        else ((expireEpochSec * 1000L - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0L)
    val usageFraction: Float
        get() = if (total <= 0L) 0f else (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

@Serializable
data class AppSettings(
    val language: String = "en",          // "en" | "fa"
    val stopOnSleep: Boolean = true,      // matches screenshot: ON
    val smartConnect: Boolean = false,    // matches screenshot: OFF
    val routingMode: String = "global",   // global | bypass-lan | custom
    val dnsServer: String = "8.8.8.8",
    val dnsMode: String = "udp",          // udp | doh | dou
    val mtu: Int = 1400,
    val ipv6: Boolean = false,
    val allowLanBypass: Boolean = true,
    val pureBlack: Boolean = true,        // appearance: pure black vs dark grey
    val sortMode: String = "ping",        // ping | name | none
    val selectedConfigId: String? = null,
    val lastUploadBytes: Long = 0L,       // last session totals (shown when disconnected)
    val lastDownloadBytes: Long = 0L
)

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/** One live traffic sample from the core (only meaningful while connected). */
data class TrafficSample(val uploadBytes: Long, val downloadBytes: Long, val atMs: Long = System.currentTimeMillis())
