package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityConnectPairingTest {
    @Test fun challengeHasThirtyTwoByteNonceAndFiveMinuteLifetime() {
        val challenge = InfinityConnectPairing.createChallenge(1_000L)
        assertEquals(1_000L + 300_000L, challenge.expiresAtEpochMillis)
        assertTrue(InfinityConnectPairing.isChallengeValid(challenge, 1_001L))
    }

    @Test fun challengesUseDifferentNoncesAndRequestIds() {
        val first = InfinityConnectPairing.createChallenge(10_000L)
        val second = InfinityConnectPairing.createChallenge(10_000L)
        assertNotEquals(first.nonce, second.nonce)
        assertNotEquals(first.requestId, second.requestId)
    }

    @Test fun challengeExpiresAtExactDeadline() {
        val challenge = InfinityConnectPairing.createChallenge(5_000L)
        assertFalse(InfinityConnectPairing.isChallengeValid(challenge, challenge.expiresAtEpochMillis))
    }

    @Test fun challengeCannotBeUsedBeforeIssueTime() {
        val challenge = InfinityConnectPairing.createChallenge(5_000L)
        assertFalse(InfinityConnectPairing.isChallengeValid(challenge, 4_999L))
    }

    @Test fun rejectsMalformedNonceAndRequestId() {
        val valid = InfinityConnectPairing.createChallenge(5_000L)
        assertFalse(InfinityConnectPairing.isChallengeValid(valid.copy(nonce = "abc"), 5_001L))
        assertFalse(InfinityConnectPairing.isChallengeValid(valid.copy(requestId = "not-a-uuid"), 5_001L))
    }

    @Test fun normalizesColonSeparatedSha256Fingerprint() {
        val fingerprint = "AA".repeat(32)
        val colonSeparated = (0 until 32).joinToString(":") { "AA" }
        assertEquals(fingerprint, InfinityConnectPairing.normalizeSha256Fingerprint(colonSeparated))
    }

    @Test fun rejectsFingerprintWithWrongLengthOrCharacters() {
        assertNull(InfinityConnectPairing.normalizeSha256Fingerprint("ABCD"))
        assertNull(InfinityConnectPairing.normalizeSha256Fingerprint("G".repeat(64)))
    }

    @Test fun certificateFingerprintComparisonIsStrict() {
        val fingerprint = "0123456789ABCDEF".repeat(4)
        assertTrue(InfinityConnectPairing.matchesCertificateFingerprint(fingerprint, fingerprint.lowercase()))
        assertFalse(InfinityConnectPairing.matchesCertificateFingerprint(fingerprint, "F".repeat(64)))
        assertFalse(InfinityConnectPairing.matchesCertificateFingerprint(fingerprint, "invalid"))
    }

    @Test fun rejectsNegativeClockAndAlteredLifetime() {
        val challenge = InfinityConnectPairing.createChallenge(1_000L)
        assertFalse(InfinityConnectPairing.isChallengeValid(challenge, -1L))
        assertFalse(InfinityConnectPairing.isChallengeValid(
            challenge.copy(expiresAtEpochMillis = challenge.expiresAtEpochMillis + 1L),
            1_001L
        ))
        assertNotNull(challenge.nonce)
    }
}
