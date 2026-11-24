package com.infinity.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Non-exportable Android Keystore identity-key lifecycle for the future CONNECT enrollment.
 *
 * This creates a signing key only. It does not issue a certificate, establish trust,
 * perform TLS client authentication, or enroll the device with Windows.
 */
internal class InfinityConnectIdentityKeyStore(
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
) {
    data class PublicIdentityKey(val alias: String, val algorithm: String, val encodedPublicKey: ByteArray)

    fun getOrCreate(alias: String): PublicIdentityKey {
        require(isValidAlias(alias)) { "Identity key alias is invalid." }
        val existing = keyStore.getKey(alias, null)
        if (existing != null) {
            val certificate = keyStore.getCertificate(alias)
                ?: throw IllegalStateException("Identity key has no public certificate.")
            require(existing.algorithm.equals("EC", ignoreCase = true)) {
                "Existing identity key uses an unsupported algorithm."
            }
            return PublicIdentityKey(alias, existing.algorithm, certificate.publicKey.encoded.copyOf())
        }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        val pair: KeyPair = generator.generateKeyPair()
        return PublicIdentityKey(alias, pair.public.algorithm, pair.public.encoded.copyOf())
    }

    /** Signs a bounded, domain-separated enrollment transcript; private key bytes never leave Keystore. */
    fun signEnrollmentTranscript(alias: String, transcript: ByteArray): ByteArray {
        require(isValidAlias(alias)) { "Identity key alias is invalid." }
        require(transcript.isNotEmpty() && transcript.size <= MAX_TRANSCRIPT_BYTES) {
            "Enrollment transcript size is outside the supported range."
        }
        val privateKey = keyStore.getKey(alias, null)
            ?: throw IllegalStateException("Identity key has not been provisioned.")
        require(privateKey.algorithm.equals("EC", ignoreCase = true)) {
            "Identity key uses an unsupported algorithm."
        }
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey as java.security.PrivateKey)
        signature.update(DOMAIN_SEPARATOR)
        signature.update(transcript)
        return signature.sign()
    }

    /** Signs the exact PKCS##10 CertificationRequestInfo bytes using the non-exportable key. */
    fun signCertificationRequestInfo(alias: String, certificationRequestInfo: ByteArray): ByteArray {
        require(isValidAlias(alias)) { "Identity key alias is invalid." }
        require(certificationRequestInfo.isNotEmpty() && certificationRequestInfo.size <= MAX_TRANSCRIPT_BYTES) {
            "Certification request information exceeds the supported size."
        }
        val privateKey = keyStore.getKey(alias, null)
            ?: throw IllegalStateException("Identity key has not been provisioned.")
        require(privateKey.algorithm.equals("EC", ignoreCase = true)) {
            "Identity key uses an unsupported algorithm."
        }
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey as java.security.PrivateKey)
        signature.update(certificationRequestInfo)
        return signature.sign()
    }

    fun delete(alias: String) {
        require(isValidAlias(alias)) { "Identity key alias is invalid." }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    companion object {
        /** Android Keystore aliases are deliberately restricted to printable ASCII. */
        internal fun isValidAlias(alias: String): Boolean =
            alias.length in 1..MAX_ALIAS_LENGTH &&
                alias.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' ||
                    it == '.' || it == '_' || it == '-' }

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MAX_ALIAS_LENGTH = 80
        private const val MAX_TRANSCRIPT_BYTES = 16 * 1024
        private val DOMAIN_SEPARATOR = "INFINITY-CONNECT-ENROLLMENT-V1\u0000".toByteArray(Charsets.US_ASCII)
    }
}
