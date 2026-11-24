package com.infinity.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Local primitives for the first secure-pairing stage.
 *
 * This code creates short-lived pairing challenges and compares certificate
 * fingerprints. It does not open sockets, issue certificates, or establish
 * a TLS/RDP session. A peer must be authenticated over a separately
 * implemented TLS channel before it can be trusted.
 */
internal data class InfinityPairingChallenge(
    val requestId: String,
    val nonce: String,
    val issuedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long
)

internal object InfinityConnectPairing {
    const val CHALLENGE_LIFETIME_MILLIS = 5 * 60 * 1000L
    private const val NONCE_BYTES = 32
    private val secureRandom = SecureRandom()

    fun createChallenge(nowEpochMillis: Long = System.currentTimeMillis()): InfinityPairingChallenge {
        require(nowEpochMillis >= 0L) { "Clock value must not be negative" }
        val bytes = ByteArray(NONCE_BYTES)
        secureRandom.nextBytes(bytes)
        return InfinityPairingChallenge(
            requestId = UUID.randomUUID().toString(),
            nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            issuedAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = nowEpochMillis + CHALLENGE_LIFETIME_MILLIS
        )
    }

    fun isChallengeValid(
        challenge: InfinityPairingChallenge,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (nowEpochMillis < 0L || challenge.issuedAtEpochMillis < 0L) return false
        if (challenge.expiresAtEpochMillis <= challenge.issuedAtEpochMillis) return false
        if (challenge.expiresAtEpochMillis - challenge.issuedAtEpochMillis != CHALLENGE_LIFETIME_MILLIS) return false
        if (nowEpochMillis < challenge.issuedAtEpochMillis || nowEpochMillis >= challenge.expiresAtEpochMillis) return false
        if (runCatching { UUID.fromString(challenge.requestId) }.isFailure) return false
        val decoded = runCatching { Base64.getUrlDecoder().decode(challenge.nonce) }.getOrNull()
            ?: return false
        return decoded.size == NONCE_BYTES
    }

    /** Accepts SHA-256 fingerprints as 64 hex characters, optionally colon-separated. */
    fun normalizeSha256Fingerprint(value: String): String? {
        val normalized = value.filterNot { it == ':' || it == ' ' }.uppercase()
        if (normalized.length != 64 || normalized.any { it !in '0'..'9' && it !in 'A'..'F' }) {
            return null
        }
        return normalized
    }

    /** Constant-time comparison after strict fingerprint format validation. */
    fun matchesCertificateFingerprint(expected: String, presented: String): Boolean {
        val expectedNormalized = normalizeSha256Fingerprint(expected) ?: return false
        val presentedNormalized = normalizeSha256Fingerprint(presented) ?: return false
        return MessageDigest.isEqual(
            expectedNormalized.toByteArray(Charsets.US_ASCII),
            presentedNormalized.toByteArray(Charsets.US_ASCII)
        )
    }
}
