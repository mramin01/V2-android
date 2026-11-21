package com.amin.vbox.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches a subscription URL: parses the `subscription-userinfo` header
 * (upload/download/total/expire) and decodes the body into proxy links.
 * Real network, real header parsing — no placeholder numbers.
 */
object SubscriptionFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class FetchResult(
        val configs: List<ProxyConfig>,
        val info: SubscriptionInfo?,
        val rawLines: Int
    )

    suspend fun fetch(url: String, subscriptionId: String): Result<FetchResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "VBOX/1.0")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    val userInfo = ConfigParser.parseUserInfo(
                        response.header("subscription-userinfo")
                    )
                    val body = response.body?.string() ?: ""
                    val lines = ConfigParser.decodeSubscriptionBody(body)
                    val configs = lines.mapNotNull { line ->
                        ConfigParser.parse(line.trim(), subscriptionId)
                    }
                    FetchResult(configs, userInfo, lines.size)
                }
            }
        }

    /** Public IP through the current network (used only while connected). */
    suspend fun publicIp(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.ipify.org")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string()?.trim()?.takeIf { it.isNotEmpty() } else null
            }
        }.getOrNull()
    }
}
