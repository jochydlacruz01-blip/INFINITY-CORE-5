package com.infinity.core

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores one credential per stable CONNECT profile ID.
 *
 * Version 2 authenticates the schema, profile ID and canonical endpoint as AES-GCM AAD.
 * A ciphertext copied to another profile or endpoint therefore fails authentication.
 * Legacy endpoint-keyed entries are migrated on first successful read.
 */
class InfinityConnectCredentialVault(context: Context) {
    private val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val alias = KEY_ALIAS
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun storageId(profileId: String): String = "v2_" + digest(profileId)
    private fun legacyStorageId(endpoint: String): String = digest(endpoint)

    fun save(profileId: String, endpoint: String, secret: String): Boolean {
        if (!isValidIdentity(profileId, endpoint)) return false
        if (secret.isEmpty()) {
            delete(profileId, endpoint)
            return true
        }

        val clear = secret.toByteArray(StandardCharsets.UTF_8)
        if (clear.size > MAX_SECRET_LENGTH) {
            clear.fill(0)
            return false
        }

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(aad(profileId, endpoint))
            val iv = cipher.iv
            val encrypted = cipher.doFinal(clear)
            val payload = byteArrayOf(VERSION) + iv + encrypted
            try {
                // commit() makes a successful return mean the protected value is durable.
                prefs.edit().putString(storageId(profileId), Base64.encodeToString(payload, Base64.NO_WRAP)).commit()
            } finally {
                iv.fill(0)
                encrypted.fill(0)
                payload.fill(0)
            }
        } catch (_: Exception) {
            false
        } finally {
            clear.fill(0)
        }
    }

    /**
     * Retrieves a profile-bound secret. If only a legacy endpoint-bound entry exists,
     * migrate it to v2 before returning it; failed migration returns null (fail closed).
     */
    fun retrieve(profileId: String, endpoint: String): String? {
        if (!isValidIdentity(profileId, endpoint)) return null
        val current = prefs.getString(storageId(profileId), null)
        if (current != null) return decryptV2(profileId, endpoint, current)

        val legacy = prefs.getString(legacyStorageId(endpoint), null) ?: return null
        val migrated = decryptLegacy(legacy) ?: return null
        return try {
            if (save(profileId, endpoint, migrated)) {
                prefs.edit().remove(legacyStorageId(endpoint)).commit()
                migrated
            } else {
                null
            }
        } finally {
            // Strings cannot be zeroed; all byte-array cleartext buffers are wiped in decrypt.
        }
    }

    fun delete(profileId: String, endpoint: String) {
        if (!isValidIdentity(profileId, endpoint)) return
        prefs.edit()
            .remove(storageId(profileId))
            .remove(legacyStorageId(endpoint))
            .commit()
    }

    private fun decryptV2(profileId: String, endpoint: String, encoded: String): String? {
        val payload = decodeBounded(encoded) ?: return null
        try {
            if (payload.size < V2_MIN_PAYLOAD_LENGTH || payload[0] != VERSION) return null
            val iv = payload.copyOfRange(1, 1 + IV_LENGTH)
            val encrypted = payload.copyOfRange(1 + IV_LENGTH, payload.size)
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
                cipher.updateAAD(aad(profileId, endpoint))
                val clear = cipher.doFinal(encrypted)
                return try {
                    String(clear, StandardCharsets.UTF_8)
                } finally {
                    clear.fill(0)
                }
            } finally {
                iv.fill(0)
                encrypted.fill(0)
            }
        } catch (_: AEADBadTagException) {
            return null
        } catch (_: Exception) {
            return null
        } finally {
            payload.fill(0)
        }
    }

    /** Compatibility-only reader for the previous IV || AES-GCM ciphertext format. */
    private fun decryptLegacy(encoded: String): String? {
        val payload = decodeBounded(encoded) ?: return null
        try {
            if (payload.size < LEGACY_MIN_PAYLOAD_LENGTH) return null
            val iv = payload.copyOfRange(0, IV_LENGTH)
            val encrypted = payload.copyOfRange(IV_LENGTH, payload.size)
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val legacyKey = store.getKey(LEGACY_KEY_ALIAS, null) as? SecretKey ?: return null
                cipher.init(Cipher.DECRYPT_MODE, legacyKey, GCMParameterSpec(TAG_BITS, iv))
                val clear = cipher.doFinal(encrypted)
                return try {
                    String(clear, StandardCharsets.UTF_8)
                } finally {
                    clear.fill(0)
                }
            } finally {
                iv.fill(0)
                encrypted.fill(0)
            }
        } catch (_: Exception) {
            return null
        } finally {
            payload.fill(0)
        }
    }

    private fun decodeBounded(encoded: String): ByteArray? = try {
        if (encoded.length > MAX_ENCODED_LENGTH) return null
        Base64.decode(encoded, Base64.NO_WRAP).takeIf {
            it.size in LEGACY_MIN_PAYLOAD_LENGTH..MAX_PAYLOAD_LENGTH
        }
    } catch (_: Exception) {
        null
    }

    private fun isValidIdentity(profileId: String, endpoint: String): Boolean =
        profileId.isNotBlank() && profileId.length <= MAX_PROFILE_ID_LENGTH &&
            endpoint.isNotBlank() && endpoint.length <= MAX_ENDPOINT_LENGTH

    private fun digest(value: String): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)),
        Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
    )

    private companion object {
        const val PREFERENCES = "infinity_connect_secrets"
        const val KEY_ALIAS = "infinity_connect_aes_v2"
        const val LEGACY_KEY_ALIAS = "infinity_connect_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 2
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
        const val TAG_LENGTH = 16
        const val LEGACY_MIN_PAYLOAD_LENGTH = IV_LENGTH + TAG_LENGTH
        const val V2_MIN_PAYLOAD_LENGTH = 1 + LEGACY_MIN_PAYLOAD_LENGTH
        const val MAX_SECRET_LENGTH = 4096
        const val MAX_PAYLOAD_LENGTH = 1 + IV_LENGTH + MAX_SECRET_LENGTH + TAG_LENGTH
        const val MAX_ENCODED_LENGTH = ((MAX_PAYLOAD_LENGTH + 2) / 3) * 4
        const val MAX_PROFILE_ID_LENGTH = 128
        const val MAX_ENDPOINT_LENGTH = 512
    }
}

/** Canonical AAD contract shared by the vault and cryptographic regression tests. */
internal fun infinityConnectCredentialAad(profileId: String, endpoint: String): ByteArray {
    require(profileId.isNotBlank() && endpoint.isNotBlank())
    return "PROJECT-INFINITY|CONNECT|credential|v2|profile=$profileId|endpoint=$endpoint"
        .toByteArray(StandardCharsets.UTF_8)
}
