package com.infinity.core

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Installs a Windows-issued client certificate chain onto the existing Android Keystore key.
 * The private key remains non-exportable. Trust is anchored to an out-of-band CA fingerprint.
 */
internal class InfinityConnectClientCertificateInstaller(
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) },
    private val now: () -> Date = { Date() }
) {
    fun install(
        alias: String,
        certificateChainDer: ByteArray,
        trustedCaSha256: String
    ) {
        require(InfinityConnectIdentityKeyStore.isValidAlias(alias)) { "Identity key alias is invalid." }
        require(certificateChainDer.isNotEmpty() && certificateChainDer.size <= MAX_CHAIN_BYTES) {
            "Certificate chain size is outside the supported range."
        }
        val expectedCaPin = normalizePin(trustedCaSha256)
            ?: throw IllegalArgumentException("A valid SHA-256 CA fingerprint is required.")

        val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            ?: throw IllegalStateException("The Android Keystore identity key is missing.")
        val factory = CertificateFactory.getInstance("X.509")
        val certificates = factory.generateCertificates(ByteArrayInputStream(certificateChainDer))
            .map { it as? X509Certificate ?: throw IllegalArgumentException("Only X.509 certificates are accepted.") }
        require(certificates.isNotEmpty() && certificates.size <= MAX_CHAIN_CERTIFICATES) {
            "Certificate chain must contain 1-4 certificates."
        }
        val chain = certificates.toTypedArray()
        val leaf = chain.first()
        val root = chain.last()
        val currentTime = now()
        require(currentTime >= leaf.notBefore && currentTime <= leaf.notAfter) {
            "Client certificate is outside its validity period."
        }
        require(leaf.publicKey.encoded.contentEquals(keyStore.getCertificate(alias)?.publicKey?.encoded)) {
            "Issued certificate public key does not match this Android Keystore identity."
        }
        require(leaf.extendedKeyUsage?.contains(CLIENT_AUTH_OID) == true) {
            "Issued certificate does not permit TLS client authentication."
        }
        val keyUsage = leaf.keyUsage
        require(keyUsage == null || keyUsage.isNotEmpty() && keyUsage[0]) {
            "Issued certificate does not permit digital signatures."
        }
        require(sha256Pin(root.encoded) == expectedCaPin) {
            "Certificate chain does not terminate at the user-trusted CA."
        }

        for (index in 0 until chain.lastIndex) {
            val child = chain[index]
            val issuer = chain[index + 1]
            require(child.issuerX500Principal == issuer.subjectX500Principal) {
                "Certificate chain issuer/subject sequence is invalid."
            }
            child.verify(issuer.publicKey)
        }
        for (index in 1 until chain.size) {
            val issuer = chain[index]
            require(issuer.basicConstraints >= 0) { "An issuer certificate is not a CA certificate." }
            val issuerUsage = issuer.keyUsage
            require(issuerUsage == null || issuerUsage.size > 5 && issuerUsage[5]) {
                "An issuer certificate does not permit certificate signing."
            }
            require(currentTime >= issuer.notBefore && currentTime <= issuer.notAfter) {
                "An issuer certificate is outside its validity period."
            }
        }
        require(root.subjectX500Principal == root.issuerX500Principal) {
            "The trusted CA certificate is not self-issued."
        }
        root.verify(root.publicKey)

        keyStore.setKeyEntry(alias, privateKey, null, chain)
    }

    private fun normalizePin(value: String): String? {
        val normalized = value.replace(":", "").replace(" ", "").uppercase()
        return normalized.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'A'..'F' } }
    }

    private fun sha256Pin(encoded: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02X".format(it) }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CLIENT_AUTH_OID = "1.3.6.1.5.5.7.3.2"
        private const val MAX_CHAIN_BYTES = 64 * 1024
        private const val MAX_CHAIN_CERTIFICATES = 4
    }
}
