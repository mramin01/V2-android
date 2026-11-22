package com.amin.vbox.data

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder
import java.util.UUID

/**
 * Parses proxy links: vmess://, vless://, trojan://, ss://.
 * Also decodes subscription bodies (base64, possibly URL-safe, or plain lines).
 * Pure functions — covered by unit tests. No network here.
 */
object ConfigParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun isProxyLink(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("vmess://") || t.startsWith("vless://") ||
            t.startsWith("trojan://") || t.startsWith("ss://")
    }

    /** Extract every proxy link found inside arbitrary pasted text. */
    fun extractLinks(text: String): List<String> {
        val regex = Regex("(vmess|vless|trojan|ss)://[^\\s\"'<>]+")
        return regex.findAll(text).map { it.value.trim() }.toList()
    }

    /** Parse one link. Returns null when the link is malformed. */
    fun parse(link: String, subscriptionId: String? = null): ProxyConfig? {
        return try {
            val trimmed = link.trim()
            when {
                trimmed.startsWith("vmess://") -> parseVmess(trimmed, subscriptionId)
                trimmed.startsWith("vless://") -> parseVless(trimmed, subscriptionId)
                trimmed.startsWith("trojan://") -> parseTrojan(trimmed, subscriptionId)
                trimmed.startsWith("ss://") -> parseShadowsocks(trimmed, subscriptionId)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseMany(text: String, subscriptionId: String? = null): List<ProxyConfig> =
        extractLinks(text).mapNotNull { parse(it, subscriptionId) }

    /**
     * Stable id for a link inside its group. The same link always gets the
     * same id, so a subscription refresh (which re-parses every link) keeps
     * the same servers: the selected server stays selected and saved pings
     * survive the refresh instead of silently pointing at deleted rows.
     */
    private fun stableId(link: String, subscriptionId: String?): String =
        UUID.nameUUIDFromBytes(
            "${subscriptionId ?: "local"}|$link".toByteArray(Charsets.UTF_8)
        ).toString()

    // ---- vmess ----

    private fun parseVmess(link: String, subscriptionId: String?): ProxyConfig? {
        val b64 = link.removePrefix("vmess://").substringBefore("#").trim()
        val decoded = decodeBase64Loose(b64) ?: return null
        val obj = json.parseToJsonElement(decoded).jsonObject
        fun s(key: String) = obj[key]?.jsonPrimitive?.content ?: ""
        val name = link.substringAfter("#", "").let { if (it.isBlank()) s("ps") else urlDecode(it) }
            .ifBlank { s("ps") }.ifBlank { "VMess" }
        val port = s("port").toIntOrNull() ?: return null
        return ProxyConfig(
            id = stableId(link, subscriptionId),
            name = name,
            protocol = "vmess",
            address = s("add"),
            port = port,
            uuid = s("id"),
            alterId = s("aid").toIntOrNull() ?: 0,
            method = s("scy").ifBlank { "auto" },
            network = s("net").ifBlank { "tcp" },
            tls = s("tls") == "tls",
            sni = s("sni").ifBlank { s("host") },
            host = s("host"),
            path = s("path"),
            rawLink = link,
            subscriptionId = subscriptionId
        )
    }

    // ---- vless / trojan (URI form) ----

    private fun parseVless(link: String, subscriptionId: String?): ProxyConfig? {
        // vless://uuid@host:port?query#name
        val rest = link.removePrefix("vless://")
        val uuid = rest.substringBefore("@")
        val afterAt = rest.substringAfter("@", "")
        if (uuid.isBlank() || afterAt.isBlank()) return null
        val hostPort = afterAt.substringBefore("?").substringBefore("#")
        val host = hostPort.substringBeforeLast(":")
        val port = hostPort.substringAfterLast(":").toIntOrNull() ?: return null
        val query = parseQuery(afterAt.substringAfter("?", "").substringBefore("#"))
        val name = link.substringAfter("#", "").let { if (it.isBlank()) host else urlDecode(it) }
        val security = query["security"] ?: ""
        return ProxyConfig(
            id = stableId(link, subscriptionId),
            name = name,
            protocol = "vless",
            address = host,
            port = port,
            uuid = uuid,
            network = query["type"] ?: "tcp",
            tls = security == "tls" || security == "reality",
            sni = query["sni"] ?: "",
            host = query["host"] ?: "",
            path = query["path"] ?: "",
            flow = query["flow"] ?: "",
            rawLink = link,
            subscriptionId = subscriptionId
        )
    }

    private fun parseTrojan(link: String, subscriptionId: String?): ProxyConfig? {
        // trojan://password@host:port?query#name
        val rest = link.removePrefix("trojan://")
        val password = rest.substringBefore("@")
        val afterAt = rest.substringAfter("@", "")
        if (password.isBlank() || afterAt.isBlank()) return null
        val hostPort = afterAt.substringBefore("?").substringBefore("#")
        val host = hostPort.substringBeforeLast(":")
        val port = hostPort.substringAfterLast(":").toIntOrNull() ?: return null
        val query = parseQuery(afterAt.substringAfter("?", "").substringBefore("#"))
        val name = link.substringAfter("#", "").let { if (it.isBlank()) host else urlDecode(it) }
        return ProxyConfig(
            id = stableId(link, subscriptionId),
            name = name,
            protocol = "trojan",
            address = host,
            port = port,
            uuid = urlDecode(password),
            network = query["type"] ?: "tcp",
            tls = true, // trojan is always TLS
            sni = query["sni"] ?: "",
            host = query["host"] ?: "",
            path = query["path"] ?: "",
            rawLink = link,
            subscriptionId = subscriptionId
        )
    }

    // ---- shadowsocks ----

    private fun parseShadowsocks(link: String, subscriptionId: String?): ProxyConfig? {
        // Forms: ss://base64(method:password@host:port)#name
        //        ss://base64(method:password)@host:port#name  (SIP002)
        //        ss://method:password@host:port#name (plain, rare)
        val rest = link.removePrefix("ss://")
        val name = link.substringAfter("#", "").let { if (it.isBlank()) "" else urlDecode(it) }
        val beforeFrag = rest.substringBefore("#")
        val (method, password, host, port) = parseSsBody(beforeFrag) ?: return null
        return ProxyConfig(
            id = stableId(link, subscriptionId),
            name = name.ifBlank { "$host:$port" },
            protocol = "shadowsocks",
            address = host,
            port = port,
            uuid = password,
            method = method,
            rawLink = link,
            subscriptionId = subscriptionId
        )
    }

    private fun parseSsBody(body: String): SsParts? {
        // SIP002: <base64(method:password)>@host:port
        if ("@" in body) {
            val left = body.substringBeforeLast("@")
            val hostPort = body.substringAfterLast("@")
            val host = hostPort.substringBeforeLast(":")
            val port = hostPort.substringAfterLast(":").toIntOrNull() ?: return null
            val userInfo = if (left.contains(":") && !looksBase64(left)) left
            else decodeBase64Loose(left) ?: return null
            val method = userInfo.substringBefore(":")
            val password = userInfo.substringAfter(":")
            if (method.isBlank() || password.isBlank() || host.isBlank()) return null
            return SsParts(method, password, host, port)
        }
        // Whole-body base64: base64(method:password@host:port)
        val decoded = decodeBase64Loose(body) ?: return null
        val at = decoded.lastIndexOf("@")
        if (at < 0) return null
        val userInfo = decoded.substring(0, at)
        val hostPort = decoded.substring(at + 1)
        val host = hostPort.substringBeforeLast(":")
        val port = hostPort.substringAfterLast(":").toIntOrNull() ?: return null
        val method = userInfo.substringBefore(":")
        val password = userInfo.substringAfter(":")
        if (method.isBlank() || password.isBlank() || host.isBlank()) return null
        return SsParts(method, password, host, port)
    }

    private data class SsParts(val method: String, val password: String, val host: String, val port: Int)

    private fun looksBase64(s: String): Boolean =
        s.length % 4 != 1 && s.all { it.isLetterOrDigit() || it in "+/=_-" }

    // ---- subscription body ----

    /**
     * Decode a subscription response body into raw lines.
     * Bodies are usually base64 (standard or URL-safe, with/without padding),
     * sometimes plain text lines. Returns the non-empty lines.
     */
    fun decodeSubscriptionBody(body: String): List<String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        val asBase64 = decodeBase64Loose(trimmed)
        val text = if (asBase64 != null && (asBase64.contains("://") || asBase64.contains("\n"))) asBase64 else trimmed
        return text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Parse a `subscription-userinfo` header value:
     * `upload=123; download=456; total=789; expire=1700000000`
     * Missing keys default to 0. Returns null when nothing parses.
     */
    fun parseUserInfo(header: String?): SubscriptionInfo? {
        if (header.isNullOrBlank()) return null
        var upload = 0L; var download = 0L; var total = 0L; var expire = 0L
        var found = false
        for (part in header.split(";")) {
            val kv = part.trim().split("=", limit = 2)
            if (kv.size != 2) continue
            val value = kv[1].trim().toLongOrNull() ?: continue
            found = true
            when (kv[0].trim().lowercase()) {
                "upload" -> upload = value
                "download" -> download = value
                "total" -> total = value
                "expire" -> expire = value
            }
        }
        return if (found) SubscriptionInfo(upload, download, total, expire) else null
    }

    // ---- helpers ----

    /** Base64 decode tolerating URL-safe alphabet, missing padding, whitespace. */
    fun decodeBase64Loose(input: String): String? =
        decodeBase64Bytes(input)?.toString(Charsets.UTF_8)

    /** Same as [decodeBase64Loose] but returns the raw bytes (for keys). */
    fun decodeBase64Bytes(input: String): ByteArray? {
        return try {
            var s = input.trim().replace("\\s".toRegex(), "")
                .replace('-', '+').replace('_', '/')
            val pad = (4 - s.length % 4) % 4
            s += "=".repeat(pad)
            Base64.getDecoder().decode(s)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeBase64UrlNoPadding(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun urlDecode(s: String): String = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        s
    }

    fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val kv = pair.split("=", limit = 2)
            if (kv.size == 2) out[urlDecode(kv[0])] = urlDecode(kv[1])
        }
        return out
    }
}
