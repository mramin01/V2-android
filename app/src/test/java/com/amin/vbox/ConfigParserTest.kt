package com.amin.vbox

import com.amin.vbox.data.ConfigParser
import com.amin.vbox.data.SubscriptionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64 as JBase64

class ConfigParserTest {

    private fun b64(s: String): String =
        JBase64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    @Test
    fun vmessParses() {
        val json = """{"v":"2","ps":"Demo DE","add":"203.0.113.10","port":"443","id":"11111111-2222-3333-4444-555555555555","aid":"0","net":"ws","type":"none","host":"example.com","path":"/ws","tls":"tls","sni":"example.com"}"""
        val cfg = ConfigParser.parse("vmess://" + b64(json))
        assertNotNull(cfg)
        cfg!!
        assertEquals("vmess", cfg.protocol)
        assertEquals("Demo DE", cfg.name)
        assertEquals("203.0.113.10", cfg.address)
        assertEquals(443, cfg.port)
        assertEquals("ws", cfg.network)
        assertTrue(cfg.tls)
        assertEquals("/ws", cfg.path)
    }

    @Test
    fun vmessMalformedReturnsNull() {
        assertNull(ConfigParser.parse("vmess://%%%not-base64%%%"))
        assertNull(ConfigParser.parse("vmess://" + b64("not json")))
    }

    @Test
    fun vlessParses() {
        val cfg = ConfigParser.parse(
            "vless://11111111-2222-3333-4444-555555555555@203.0.113.20:443" +
                "?security=tls&sni=example.com&type=ws&path=%2Fvless&host=example.com#Demo%20NL"
        )
        assertNotNull(cfg)
        cfg!!
        assertEquals("vless", cfg.protocol)
        assertEquals("Demo NL", cfg.name)
        assertEquals("203.0.113.20", cfg.address)
        assertEquals(443, cfg.port)
        assertEquals("ws", cfg.network)
        assertTrue(cfg.tls)
        assertEquals("example.com", cfg.sni)
    }

    @Test
    fun trojanParses() {
        val cfg = ConfigParser.parse("trojan://s3cret@203.0.113.30:443?sni=example.com#Demo%20FR")
        assertNotNull(cfg)
        cfg!!
        assertEquals("trojan", cfg.protocol)
        assertEquals("s3cret", cfg.uuid)
        assertTrue(cfg.tls)
        assertEquals("Demo FR", cfg.name)
    }

    @Test
    fun shadowsocksParsesBothForms() {
        // Whole-body base64 form.
        val whole = ConfigParser.parse("ss://" + b64("aes-256-gcm:pass123@203.0.113.40:8388") + "#Demo%20SS")
        assertNotNull(whole)
        assertEquals("shadowsocks", whole!!.protocol)
        assertEquals("aes-256-gcm", whole.method)
        assertEquals("pass123", whole.uuid)
        assertEquals(8388, whole.port)
        // SIP002 form.
        val sip002 = ConfigParser.parse("ss://" + b64("aes-256-gcm:pass123") + "@203.0.113.40:8388#Demo%20SS2")
        assertNotNull(sip002)
        assertEquals("Demo SS2", sip002!!.name)
    }

    @Test
    fun extractLinksFindsMany() {
        val text = "here vmess://AAAA and vless://uuid@host:1 and some noise trojan://pw@h:2"
        assertEquals(3, ConfigParser.extractLinks(text).size)
    }

    @Test
    fun subscriptionBodyDecodes() {
        val lines = listOf("vmess://AAAA", "vless://u@h:1")
        val body = JBase64.getEncoder().encodeToString(lines.joinToString("\n").toByteArray())
        assertEquals(lines, ConfigParser.decodeSubscriptionBody(body))
        // Plain (non-base64) body also works.
        assertEquals(lines, ConfigParser.decodeSubscriptionBody(lines.joinToString("\n")))
        // URL-safe base64 without padding.
        val urlSafe = JBase64.getUrlEncoder().withoutPadding()
            .encodeToString(lines.joinToString("\n").toByteArray())
        assertEquals(lines, ConfigParser.decodeSubscriptionBody(urlSafe))
    }

    @Test
    fun userInfoParses() {
        val info: SubscriptionInfo? =
            ConfigParser.parseUserInfo("upload=1000; download=2000; total=10000; expire=1893456000")
        assertNotNull(info)
        info!!
        assertEquals(1000L, info.upload)
        assertEquals(2000L, info.download)
        assertEquals(10000L, info.total)
        assertEquals(1893456000L, info.expireEpochSec)
        assertEquals(0.3f, info.usageFraction, 0.001f)
        assertNull(ConfigParser.parseUserInfo(null))
        assertNull(ConfigParser.parseUserInfo("garbage"))
    }
}
