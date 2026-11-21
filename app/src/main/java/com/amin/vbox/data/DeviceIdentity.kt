package com.amin.vbox.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.util.UUID

/**
 * Device identity, mirroring the V2Box Settings screen:
 *  - Device ID (HWID): a UUID generated once, persisted, regenerated on Reset
 *    (and therefore on re-install, since EncryptedSharedPreferences is wiped).
 *  - X25519 keypair: private key generated once and kept in
 *    EncryptedSharedPreferences; public key shown (base64url, no padding).
 *    Never copy a HWID / public key from a screenshot — these are fresh
 *    per install.
 */
class DeviceIdentity(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "vbox_device_identity",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val hwid: String
        get() = prefs.getString(KEY_HWID, null) ?: generateHwid()

    /** Base64url (no padding) public key, 43 chars for a 32-byte key. */
    val publicKey: String
        get() {
            val priv = privateKeyBytes()
            return ConfigParser.encodeBase64UrlNoPadding(X25519.publicKey(priv))
        }

    /** Full public key for copy-to-clipboard (same value, untruncated). */
    val publicKeyFull: String get() = publicKey

    private fun generateHwid(): String {
        val id = UUID.randomUUID().toString().uppercase()
        prefs.edit().putString(KEY_HWID, id).apply()
        return id
    }

    private fun privateKeyBytes(): ByteArray {
        val stored = prefs.getString(KEY_PRIVATE, null)
        if (stored != null) {
            val bytes = ConfigParser.decodeBase64Bytes(stored)
            if (bytes != null && bytes.size == 32) return bytes
        }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PRIVATE, ConfigParser.encodeBase64UrlNoPadding(fresh))
            .apply()
        return fresh
    }

    /** Reset identity: new HWID + new keypair (Settings > Reset). */
    fun reset() {
        prefs.edit().clear().apply()
        generateHwid()
        privateKeyBytes()
    }

    companion object {
        private const val KEY_HWID = "hwid"
        private const val KEY_PRIVATE = "x25519_private_b64url"
    }
}
