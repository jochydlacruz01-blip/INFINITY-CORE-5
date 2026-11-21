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

    private fun id(endpoint: String) = android.util.Base64.encodeToString(
        java.security.MessageDigest.getInstance("SHA-256").digest(endpoint.toByteArray(Charsets.UTF_8)),
        Base64.NO_WRAP
    )

    fun save(endpoint: String, secret: String) {
        if (secret.isEmpty()) { delete(endpoint); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(id(endpoint), Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun retrieve(endpoint: String): String? = try {
        val encoded = prefs.getString(id(endpoint), null) ?: return null
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    } catch (_: Exception) { null }

    fun delete(endpoint: String) { prefs.edit().remove(id(endpoint)).apply() }
}
