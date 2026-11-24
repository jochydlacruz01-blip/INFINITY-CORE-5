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

    @Test fun registryConsumesChallengeOnlyOnce() {
        val registry = InfinityPairingChallengeRegistry()
        val challenge = InfinityConnectPairing.createChallenge(10_000L)
        assertTrue(registry.register(challenge, 10_001L))
        assertTrue(registry.consume(challenge.requestId, challenge.nonce, 10_002L))
        assertFalse(registry.consume(challenge.requestId, challenge.nonce, 10_003L))
        assertEquals(0, registry.outstandingCount(10_003L))
    }

    @Test fun registryRejectsWrongNonceWithoutConsumingValidChallenge() {
        val registry = InfinityPairingChallengeRegistry()
        val challenge = InfinityConnectPairing.createChallenge(20_000L)
        assertTrue(registry.register(challenge, 20_001L))
        val wrongNonce = InfinityConnectPairing.createChallenge(20_000L).nonce
        assertFalse(registry.consume(challenge.requestId, wrongNonce, 20_002L))
        assertTrue(registry.consume(challenge.requestId, challenge.nonce, 20_003L))
    }

    @Test fun registryRejectsExpiredAndDuplicateChallenges() {
        val registry = InfinityPairingChallengeRegistry()
        val challenge = InfinityConnectPairing.createChallenge(30_000L)
        assertTrue(registry.register(challenge, 30_001L))
        assertFalse(registry.register(challenge, 30_002L))
        assertFalse(registry.consume(challenge.requestId, challenge.nonce, challenge.expiresAtEpochMillis))
        assertEquals(0, registry.outstandingCount(challenge.expiresAtEpochMillis))
    }

    @Test fun registryEnforcesCapacityAndCanBeCleared() {
        val registry = InfinityPairingChallengeRegistry(maxOutstanding = 1)
        val first = InfinityConnectPairing.createChallenge(40_000L)
        val second = InfinityConnectPairing.createChallenge(40_000L)
        assertTrue(registry.register(first, 40_001L))
        assertFalse(registry.register(second, 40_002L))
        registry.clear()
        assertEquals(0, registry.outstandingCount(40_003L))
        assertTrue(registry.register(second, 40_004L))
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
