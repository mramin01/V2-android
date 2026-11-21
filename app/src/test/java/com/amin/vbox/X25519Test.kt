package com.amin.vbox

import com.amin.vbox.data.ConfigParser
import com.amin.vbox.data.X25519
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RFC 7748 section 5.2 test vector (Alice):
 * private 77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a
 * public  8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a
 */
class X25519Test {

    private fun hex(s: String): ByteArray {
        val out = ByteArray(s.length / 2)
        for (i in out.indices) out[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return out
    }

    @Test
    fun rfc7748AliceVector() {
        val priv = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val expected = hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
        assertArrayEquals(expected, X25519.publicKey(priv))
    }

    @Test
    fun sharedSecretIsSymmetric() {
        val alicePriv = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val bobPriv = hex("5dab087ebb8e8c0f2a6c2b3e3b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c")
        val alicePub = X25519.publicKey(alicePriv)
        val bobPub = X25519.publicKey(bobPriv)
        val ab = X25519.scalarmult(alicePriv, bobPub)
        val ba = X25519.scalarmult(bobPriv, alicePub)
        assertArrayEquals(ab, ba)
        assertEquals(32, ab.size)
    }

    @Test
    fun publicKeyRoundTripsThroughBase64Url() {
        val priv = ByteArray(32) { (it * 7 + 1).toByte() }
        val pub = X25519.publicKey(priv)
        val encoded = ConfigParser.encodeBase64UrlNoPadding(pub)
        assertEquals(43, encoded.length)
        val decoded = ConfigParser.decodeBase64Bytes(encoded)
        assertArrayEquals(pub, decoded)
    }
}
