package com.amin.vbox.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds a sing-box JSON config for one selected [ProxyConfig].
 * Consumed by the libbox core (see core/LibboxEngine in src/libbox).
 * Kept in the main source set so it is unit-tested even without the AAR.
 *
 * Layout follows sing-box 1.11: log, dns, inbounds (tun), outbounds
 * (proxy / direct / block), route (final = proxy, DNS hijack to local DNS).
 */
object SingBoxConfig {

    private val json = Json { prettyPrint = true }

    fun build(config: ProxyConfig, settings: AppSettings): String {
        val outbound = buildOutbound(config)
        val doc = buildJsonObject {
            put("log", buildJsonObject { put("level", "warn") })
            put("dns", buildJsonObject {
                put("servers", buildJsonArray {
                    add(buildJsonObject {
                        put("tag", "remote")
                        put("address", dnsAddress(settings))
                        put("detour", "proxy")
                    })
                    add(buildJsonObject {
                        put("tag", "local")
                        put("address", "local")
                        put("detour", "direct")
                    })
                })
                put("rules", buildJsonArray {
                    add(buildJsonObject {
                        put("outbound", "any")
                        put("server", "local")
                    })
                })
                put("final", "remote")
            })
            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("type", "tun")
                    put("tag", "tun-in")
                    put("interface_name", "tun0")
                    put("address", buildJsonArray {
                        add(JsonPrimitive("172.19.0.1/30"))
                        if (settings.ipv6) add(JsonPrimitive("fdfe:dcba:9876::1/126"))
                    })
                    put("mtu", settings.mtu)
                    put("auto_route", true)
                    put("strict_route", true)
                    put("stack", "mixed")
                    put("sniff", true)
                })
            })
            put("outbounds", buildJsonArray {
                add(outbound)
                add(buildJsonObject {
                    put("type", "direct")
                    put("tag", "direct")
                })
                add(buildJsonObject {
                    put("type", "block")
                    put("tag", "block")
                })
                add(buildJsonObject {
                    put("type", "dns")
                    put("tag", "dns-out")
                })
            })
            put("route", buildJsonObject {
                put("rules", buildJsonArray {
                    add(buildJsonObject {
                        put("protocol", "dns")
                        put("outbound", "dns-out")
                    })
                    if (settings.allowLanBypass) {
                        add(buildJsonObject {
                            put("ip_is_private", true)
                            put("outbound", "direct")
                        })
                    }
                    if (settings.routingMode == "bypass-lan") {
                        add(buildJsonObject {
                            put("domain_suffix", buildJsonArray { add(JsonPrimitive("ir")) })
                            put("outbound", "direct")
                        })
                    }
                })
                put("final", "proxy")
                put("auto_detect_interface", true)
            })
        }
        return json.encodeToString(JsonObject.serializer(), doc)
    }

    private fun dnsAddress(settings: AppSettings): String = when (settings.dnsMode) {
        "doh" -> "https://dns.google/dns-query"
        "dou" -> "https://${settings.dnsServer}/dns-query"
        else -> settings.dnsServer
    }

    /** One sing-box outbound object for the selected proxy protocol. */
    fun buildOutbound(config: ProxyConfig): JsonObject = buildJsonObject {
        when (config.protocol) {
            "vmess" -> {
                put("type", "vmess")
                put("tag", "proxy")
                put("server", config.address)
                put("server_port", config.port)
                put("uuid", config.uuid)
                put("alter_id", config.alterId)
                put("security", config.method.ifBlank { "auto" })
                putTransport(this, config)
            }
            "vless" -> {
                put("type", "vless")
                put("tag", "proxy")
                put("server", config.address)
                put("server_port", config.port)
                put("uuid", config.uuid)
                if (config.flow.isNotBlank()) put("flow", config.flow)
                putTransport(this, config)
            }
            "trojan" -> {
                put("type", "trojan")
                put("tag", "proxy")
                put("server", config.address)
                put("server_port", config.port)
                put("password", config.uuid)
                putTransport(this, config)
            }
            "shadowsocks" -> {
                put("type", "shadowsocks")
                put("tag", "proxy")
                put("server", config.address)
                put("server_port", config.port)
                put("method", config.method.ifBlank { "aes-256-gcm" })
                put("password", config.uuid)
            }
            else -> {
                put("type", "direct")
                put("tag", "proxy")
            }
        }
    }

    private fun putTransport(builder: kotlinx.serialization.json.JsonObjectBuilder, config: ProxyConfig) {
        with(builder) {
            if (config.tls) {
                put("tls", buildJsonObject {
                    put("enabled", true)
                    if (config.sni.isNotBlank()) put("server_name", config.sni)
                    put("insecure", false)
                })
            }
            when (config.network) {
                "ws" -> put("transport", buildJsonObject {
                    put("type", "ws")
                    if (config.path.isNotBlank()) put("path", config.path)
                    if (config.host.isNotBlank()) {
                        put("headers", buildJsonObject { put("Host", config.host) })
                    }
                })
                "grpc" -> put("transport", buildJsonObject {
                    put("type", "grpc")
                    if (config.path.isNotBlank()) put("service_name", config.path)
                })
                "h2" -> put("transport", buildJsonObject { put("type", "http") })
                else -> { /* no transport (plain TCP) */ }
            }
        }
    }
}
