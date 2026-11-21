package com.amin.vbox

import com.amin.vbox.data.AppSettings
import com.amin.vbox.data.ProxyConfig
import com.amin.vbox.data.SingBoxConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SingBoxConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun sample() = ProxyConfig(
        id = "x", name = "Demo DE", protocol = "vless",
        address = "203.0.113.20", port = 443,
        uuid = "11111111-2222-3333-4444-555555555555",
        network = "ws", tls = true, sni = "example.com",
        host = "example.com", path = "/vless"
    )

    @Test
    fun vlessProducesValidSingBoxJson() {
        val text = SingBoxConfig.build(sample(), AppSettings())
        val root = json.parseToJsonElement(text).jsonObject
        assertTrue(root.containsKey("inbounds"))
        assertTrue(root.containsKey("outbounds"))
        assertTrue(root.containsKey("route"))
        val outbound = root["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals("vless", outbound["type"]!!.jsonPrimitive.content)
        assertEquals("203.0.113.20", outbound["server"]!!.jsonPrimitive.content)
        assertEquals(443, outbound["server_port"]!!.jsonPrimitive.int)
        assertEquals("proxy", root["route"]!!.jsonObject["final"]!!.jsonPrimitive.content)
        assertEquals("ws", outbound["transport"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("example.com", outbound["tls"]!!.jsonObject["server_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun trojanIsAlwaysTlsAndShadowsocksHasMethod() {
        val trojan = sample().copy(protocol = "trojan", uuid = "s3cret", network = "tcp")
        val t = json.parseToJsonElement(SingBoxConfig.build(trojan, AppSettings())).jsonObject
        val tout = t["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals("trojan", tout["type"]!!.jsonPrimitive.content)
        assertEquals("s3cret", tout["password"]!!.jsonPrimitive.content)

        val ss = sample().copy(protocol = "shadowsocks", method = "aes-256-gcm", uuid = "pw")
        val s = json.parseToJsonElement(SingBoxConfig.build(ss, AppSettings())).jsonObject
        val sout = s["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals("aes-256-gcm", sout["method"]!!.jsonPrimitive.content)
    }

    @Test
    fun bypassLanAddsPrivateRouteRule() {
        val text = SingBoxConfig.build(sample(), AppSettings(allowLanBypass = true))
        assertTrue(text.contains("ip_is_private"))
        val direct = SingBoxConfig.build(sample(), AppSettings(allowLanBypass = false))
        assertTrue(!direct.contains("ip_is_private"))
    }
}
