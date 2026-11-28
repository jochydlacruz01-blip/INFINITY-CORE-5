package com.infinity.core

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InfinityConnectEnrollmentProofV1Test {
    private fun challenge() = InfinityConnectPairingChallengeV1.Challenge(
        1,
        UUID.fromString("abcdefab-cdef-abcd-efab-cdefabcdefab"),
        UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
        1_700_000_000_123L,
        1_700_000_300_123L,
        ByteArray(32) { it.toByte() },
        ByteArray(32) { (0xA0 + it).toByte() },
        ByteArray(70) { 1 }
    )

    @Test fun matchesSharedEnrollmentProofV1Vector() {
        val actual = InfinityConnectEnrollmentProofV1.signatureInput(
            challenge(),
            UUID.fromString("11111111-2222-3333-4444-555555555555"),
            ByteArray(32) { it.toByte() }
        ).joinToString("") { "%02x".format(it) }
        assertEquals("494e46494e4954592d434f4e4e4543542d454e524f4c4c4d454e542d50524f4f462d563100494e46494e4954592d434f4e4e4543542d50414952494e472d4348414c4c454e47452d5631000001abcdefabcdefabcdefabcdefabcdefabaaaaaaaabbbbccccddddeeeeeeeeeeee0000018bcfe5687b0000018bcfe9fc5b000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1fa0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf11111111222233334444555555555555630dcd2966c4336691125448bbb25b4ff412a49c732db2c8abc1b8581bd710dd", actual)
    }

    @Test fun bindsAndroidIdentityAndCsrDigest() {
        val baseline = InfinityConnectEnrollmentProofV1.signatureInput(
            challenge(), UUID.fromString("11111111-2222-3333-4444-555555555555"), ByteArray(32) { it.toByte() })
        val changedDevice = InfinityConnectEnrollmentProofV1.signatureInput(
            challenge(), UUID.fromString("21111111-2222-3333-4444-555555555555"), ByteArray(32) { it.toByte() })
        val changedCsr = InfinityConnectEnrollmentProofV1.signatureInput(
            challenge(), UUID.fromString("11111111-2222-3333-4444-555555555555"), ByteArray(32) { (it + 1).toByte() })
        assertFalse(baseline.contentEquals(changedDevice))
        assertFalse(baseline.contentEquals(changedCsr))
    }

    @Test fun rejectsEmptyDeviceAndOversizedCsr() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            InfinityConnectEnrollmentProofV1.signatureInput(challenge(), UUID(0, 0), byteArrayOf(1))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            InfinityConnectEnrollmentProofV1.signatureInput(challenge(), UUID.randomUUID(), ByteArray(16 * 1024 + 1))
        }
    }
}
