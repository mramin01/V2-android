package com.amin.vbox.util

import java.util.Locale

/** Human-readable byte counts, e.g. 814 KB / 1.2 MB / 2.48 GB. */
object Format {
    fun bytes(bytes: Long): String {
        val v = bytes.coerceAtLeast(0L)
        return when {
            v < 1024 -> "$v B"
            v < 1024L * 1024 -> String.format(Locale.US, "%.0f KB", v / 1024.0)
            v < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", v / (1024.0 * 1024))
            else -> String.format(Locale.US, "%.2f GB", v / (1024.0 * 1024 * 1024))
        }
    }

    fun bytesShort(bytes: Long): String = bytes(bytes)

    /** Mask an IPv4 address: keep first two + last octet, e.g. 203.0.***.1 */
    fun maskIp(ip: String?): String {
        if (ip.isNullOrBlank()) return "—"
        val parts = ip.trim().split(".")
        if (parts.size != 4) return ip
        return "${parts[0]}.${parts[1]}.***.${parts[3]}"
    }

    /** Flag emoji for a country code or a name containing a country. Best effort. */
    fun flagFor(name: String): String {
        val lower = name.lowercase(Locale.US)
        val byCode = mapOf(
            "de" to "🇩🇪", "nl" to "🇳🇱", "us" to "🇺🇸", "gb" to "🇬🇧", "uk" to "🇬🇧",
            "fr" to "🇫🇷", "tr" to "🇹🇷", "fi" to "🇫🇮", "se" to "🇸🇪",
            "ir" to "🇮🇷", "ca" to "🇨🇦", "jp" to "🇯🇵", "sg" to "🇸🇬",
            "ru" to "🇷🇺", "pl" to "🇵🇱", "ch" to "🇨🇭", "at" to "🇦🇹",
            "es" to "🇪🇸", "it" to "🇮🇹", "ae" to "🇦🇪", "in" to "🇮🇳"
        )
        // [DE] / (DE) / -DE- style tags first.
        Regex("\\[([A-Za-z]{2})\\]|\\(([A-Za-z]{2})\\)").find(name)?.let { m ->
            val code = (m.groupValues[1].ifBlank { m.groupValues[2] }).lowercase()
            byCode[code]?.let { return it }
        }
        val byName = mapOf(
            "germany" to "🇩🇪", "netherlands" to "🇳🇱", "holland" to "🇳🇱",
            "united states" to "🇺🇸", "america" to "🇺🇸", "usa" to "🇺🇸",
            "england" to "🇬🇧", "london" to "🇬🇧", "france" to "🇫🇷",
            "turkey" to "🇹🇷", "turkiye" to "🇹🇷", "finland" to "🇫🇮",
            "sweden" to "🇸🇪", "iran" to "🇮🇷", "canada" to "🇨🇦",
            "japan" to "🇯🇵", "singapore" to "🇸🇬", "russia" to "🇷🇺",
            "poland" to "🇵🇱", "switzerland" to "🇨🇭", "austria" to "🇦🇹",
            "spain" to "🇪🇸", "italy" to "🇮🇹", "dubai" to "🇦🇪", "india" to "🇮🇳"
        )
        for ((word, flag) in byName) if (word in lower) return flag
        return ""
    }
}
