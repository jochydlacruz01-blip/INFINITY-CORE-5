package com.infinity.core

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InfinityConnectCredentialAadTest {
    @Test
    fun aadIsBoundToStableProfileAndEndpoint() {
        val first = infinityConnectCredentialAad("profile-001", "pc.example.net:3389")
        val same = infinityConnectCredentialAad("profile-001", "pc.example.net:3389")
        val otherProfile = infinityConnectCredentialAad("profile-002", "pc.example.net:3389")
        val otherEndpoint = infinityConnectCredentialAad("profile-001", "other.example.net:3389")

        assertArrayEquals(first, same)
        assertNotEquals(first.toList(), otherProfile.toList())
        assertNotEquals(first.toList(), otherEndpoint.toList())
    }

    @Test
    fun ciphertextCannotBeMovedToAnotherProfile() {
        val key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
        val profileAad = infinityConnectCredentialAad("profile-A", "pc.example.net:3389")
        val otherProfileAad = infinityConnectCredentialAad("profile-B", "pc.example.net:3389")
        val encrypted = encrypt(key, profileAad, "credential-secret".toByteArray())

        assertEquals("credential-secret", decrypt(key, profileAad, encrypted).decodeToString())
        assertThrows(GeneralSecurityException::class.java) {
            decrypt(key, otherProfileAad, encrypted)
        }
    }

    @Test
    fun ciphertextCannotBeMovedToAnotherEndpointWithinSameProfile() {
        val key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
        val original = infinityConnectCredentialAad("profile-A", "pc-one.example.net:3389")
        val changed = infinityConnectCredentialAad("profile-A", "pc-two.example.net:3389")
        val encrypted = encrypt(key, original, "credential-secret".toByteArray())

        assertThrows(GeneralSecurityException::class.java) {
            decrypt(key, changed, encrypted)
        }
    }

    @Test
    fun ciphertextTamperingIsRejected() {
        val key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
        val aad = infinityConnectCredentialAad("profile-A", "pc.example.net:3389")
        val encrypted = encrypt(key, aad, "credential-secret".toByteArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            decrypt(key, aad, encrypted)
        }
    }

    private fun encrypt(
        key: javax.crypto.SecretKey,
        aad: ByteArray,
        clear: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        return cipher.iv + cipher.doFinal(clear)
    }

    private fun decrypt(
        key: javax.crypto.SecretKey,
        aad: ByteArray,
        payload: ByteArray
    ): ByteArray {
        val iv = payload.copyOfRange(0, 12)
        val ciphertext = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return try {
            cipher.doFinal(ciphertext)
        } catch (error: javax.crypto.AEADBadTagException) {
            throw GeneralSecurityException("Authenticated decryption rejected modified AAD or ciphertext", error)
        }
    }
}
