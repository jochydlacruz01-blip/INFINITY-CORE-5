package com.infinity.core

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Strict certificate checks shared by CONNECT enrollment and transport.
 * This helper does not establish TLS and must only supplement platform TLS validation.
 */
internal object InfinityConnectCertificateTrust {
    const val SERVER_AUTH_EKU = "1.3.6.1.5.5.7.3.1"
    const val CLIENT_AUTH_EKU = "1.3.6.1.5.5.7.3.2"

    enum class Purpose { SERVER, CLIENT }

    data class Verification(val trusted: Boolean, val reason: String)

    fun parseDer(encoded: ByteArray): X509Certificate? {
        if (encoded.isEmpty() || encoded.size > MAX_CERTIFICATE_BYTES) return null
        return runCatching {
            val factory = CertificateFactory.getInstance("X.509")
            val input = ByteArrayInputStream(encoded)
            val certificate = factory.generateCertificate(input) as? X509Certificate ?: return null
            if (input.available() != 0) return null
            certificate
        }.getOrNull()
    }

    fun sha256Fingerprint(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02X".format(it) }

    fun verify(
        certificate: X509Certificate,
        expectedSha256: String,
        purpose: Purpose,
        now: Date = Date()
    ): Verification {
        val normalizedPin = InfinityConnectPairing.normalizeSha256Fingerprint(expectedSha256)
            ?: return Verification(false, "pin.malformed")
        val actualPin = runCatching { sha256Fingerprint(certificate) }.getOrNull()
            ?: return Verification(false, "certificate.encoding")
        if (!InfinityConnectPairing.matchesCertificateFingerprint(normalizedPin, actualPin)) {
            return Verification(false, "pin.mismatch")
        }
        if (runCatching { certificate.checkValidity(now) }.isFailure) {
            return Verification(false, "certificate.expired_or_not_yet_valid")
        }
        val requiredEku = when (purpose) {
            Purpose.SERVER -> SERVER_AUTH_EKU
            Purpose.CLIENT -> CLIENT_AUTH_EKU
        }
        val eku = runCatching { certificate.extendedKeyUsage }.getOrNull()
            ?: return Verification(false, "certificate.eku.missing_or_invalid")
        if (requiredEku !in eku) return Verification(false, "certificate.eku.wrong_purpose")
        val keyUsage = certificate.keyUsage
        if (keyUsage != null && (keyUsage.isEmpty() || !keyUsage[0])) {
            return Verification(false, "certificate.key_usage.no_digital_signature")
        }
        return Verification(true, "trusted")
    }

    private const val MAX_CERTIFICATE_BYTES = 64 * 1024
}
