package com.infinity.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID

/**
 * Deterministic Enrollment Proof v1 transcript builder.
 * The caller must verify the Windows challenge and create the CSR with the same Keystore alias.
 */
internal object InfinityConnectEnrollmentProofV1 {
    private const val MAX_CSR_BYTES = 16 * 1024
    private const val DOMAIN = "INFINITY-CONNECT-ENROLLMENT-PROOF-V1\u0000"

    fun signatureInput(
        challenge: InfinityConnectPairingChallengeV1.Challenge,
        androidDeviceId: UUID,
        csrDer: ByteArray
    ): ByteArray {
        require(androidDeviceId != UUID(0, 0)) { "Android device ID must not be empty." }
        require(csrDer.isNotEmpty() && csrDer.size <= MAX_CSR_BYTES) {
            "CSR is empty or exceeds 16 KiB."
        }
        val challengeInput = InfinityConnectPairingChallengeV1.signatureInput(challenge)
        val csrDigest = MessageDigest.getInstance("SHA-256").digest(csrDer)
        val domain = DOMAIN.toByteArray(Charsets.US_ASCII)
        return ByteBuffer.allocate(domain.size + challengeInput.size + 16 + csrDigest.size)
            .order(ByteOrder.BIG_ENDIAN).apply {
                put(domain)
                put(challengeInput)
                putLong(androidDeviceId.mostSignificantBits)
                putLong(androidDeviceId.leastSignificantBits)
                put(csrDigest)
            }.array()
    }
}
