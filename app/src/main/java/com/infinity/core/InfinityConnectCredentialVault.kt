package com.infinity.core

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts CONNECT secrets with an AES-GCM key held by Android Keystore. */
class InfinityConnectCredentialVault(context: Context) {
    private val prefs = context.getSharedPreferences("infinity_connect_secrets", Context.MODE_PRIVATE)
    private val alias = "infinity_connect_aes_v1"
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            alias, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private fun id(endpoint: String) = Base64.encodeToString(
        java.security.MessageDigest.getInstance("SHA-256").digest(endpoint.toByteArray(Charsets.UTF_8)),
        Base64.NO_WRAP
    )

    fun save(endpoint: String, secret: String): Boolean {
        if (endpoint.isBlank()) return false
        if (secret.isEmpty()) { delete(endpoint); return true }
        val secretBytes = secret.toByteArray(Charsets.UTF_8)
        if (secretBytes.size > MAX_SECRET_LENGTH) {
            secretBytes.fill(0)
            return false
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(secretBytes)
            val payload = iv + encryptedBytes
            try {
                prefs.edit()
                    .putString(id(endpoint), Base64.encodeToString(payload, Base64.NO_WRAP))
                    .apply()
                true
            } finally {
                iv.fill(0)
                encryptedBytes.fill(0)
                payload.fill(0)
            }
        } catch (_: Exception) {
            // Keep any previously saved credential if encrypting the replacement fails.
            // The caller receives false and can warn the user without destroying valid data.
            false
        } finally {
            secretBytes.fill(0)
        }
    }

    fun retrieve(endpoint: String): String? {
        if (endpoint.isBlank()) return null
        return try {
            val encoded = prefs.getString(id(endpoint), null) ?: return null
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            try {
                if (payload.size < MIN_PAYLOAD_LENGTH || payload.size > MAX_PAYLOAD_LENGTH) {
                    return null
                }
                val iv = payload.copyOfRange(0, IV_LENGTH)
                val encrypted = payload.copyOfRange(IV_LENGTH, payload.size)
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                    val clearBytes = cipher.doFinal(encrypted)
                    try {
                        String(clearBytes, Charsets.UTF_8)
                    } finally {
                        clearBytes.fill(0)
                    }
                } finally {
                    iv.fill(0)
                    encrypted.fill(0)
                }
            } finally {
                payload.fill(0)
            }
        } catch (_: Exception) { null }
    }

    fun delete(endpoint: String) {
        if (endpoint.isBlank()) return
        prefs.edit().remove(id(endpoint)).apply()
    }

    private companion object {
        const val IV_LENGTH = 12
        const val MIN_PAYLOAD_LENGTH = IV_LENGTH + 16 // GCM authentication tag
        const val MAX_SECRET_LENGTH = 4096
        const val MAX_PAYLOAD_LENGTH = IV_LENGTH + MAX_SECRET_LENGTH + 16
    }
}
