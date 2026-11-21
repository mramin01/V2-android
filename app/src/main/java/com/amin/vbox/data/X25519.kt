package com.amin.vbox.data

import java.math.BigInteger

/**
 * X25519 (RFC 7748) in pure Kotlin, using BigInteger field arithmetic.
 * Used so the device keypair does not depend on a provider shipping X25519
 * (Android's Conscrypt support varies by API level). The public key is the
 * uncompressed 32-byte u-coordinate, base64url-encoded without padding —
 * the same shape V2Box shows for its device public key.
 *
 * Verified against the RFC 7748 section 5.2 test vector in X25519Test.
 */
object X25519 {
    private val P = BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
    private val A24 = BigInteger("121666")

    /** X25519(secret 32 bytes, u-coordinate 32 bytes) -> 32-byte shared secret / public key. */
    fun scalarmult(secret: ByteArray, u: ByteArray): ByteArray {
        require(secret.size == 32 && u.size == 32)
        val k = secret.copyOf()
        k[0] = (k[0].toInt() and 248).toByte()
        k[31] = (k[31].toInt() and 127).toByte()
        k[31] = (k[31].toInt() or 64).toByte()
        val x1 = leToBig(u)
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = x1
        var z3 = BigInteger.ONE
        var swap = 0
        for (t in 254 downTo 0) {
            val bit = (k[t / 8].toInt() ushr (t % 8)) and 1
            swap = swap xor bit
            if (swap == 1) {
                val tmp = x2; x2 = x3; x3 = tmp
                val tmpZ = z2; z2 = z3; z3 = tmpZ
            }
            swap = bit
            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)
            x3 = da.add(cb).mod(P).pow(2).mod(P)
            z3 = x1.multiply(da.subtract(cb).mod(P).pow(2).mod(P)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e).mod(P)).mod(P)).mod(P)
        }
        if (swap == 1) {
            val tmp = x2; x2 = x3; x3 = tmp
            val tmpZ = z2; z2 = z3; z3 = tmpZ
        }
        val result = x2.multiply(z2.modInverse(P)).mod(P)
        return bigToLe(result)
    }

    /** Public key for a 32-byte private key (base point u = 9). */
    fun publicKey(privateKey: ByteArray): ByteArray {
        val base = ByteArray(32).also { it[0] = 9 }
        return scalarmult(privateKey, base)
    }

    private fun leToBig(bytes: ByteArray): BigInteger {
        val reversed = bytes.reversedArray()
        return BigInteger(1, reversed)
    }

    private fun bigToLe(value: BigInteger): ByteArray {
        val be = value.toByteArray()
        // Strip a leading zero sign byte if present.
        val trimmed = if (be.size == 33 && be[0] == 0.toByte()) be.copyOfRange(1, 33) else be
        val out = ByteArray(32)
        for (i in trimmed.indices) out[i] = trimmed[trimmed.size - 1 - i]
        return out
    }
}
