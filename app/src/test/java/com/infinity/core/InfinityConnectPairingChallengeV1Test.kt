package com.infinity.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class InfinityConnectPairingChallengeV1Test {
    private val request = "abcdefab-cdef-abcd-efab-cdefabcdefab"
    private val companion = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { it.toByte() })
    private val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(70) { 1 })
    private fun payload(extra: String = "") = """{"protocolVersion":1,"requestId":"$request","companionDeviceId":"$companion","issuedAtUnixMs":1000,"expiresAtUnixMs":301000,"nonce":"$nonce","caSha256":"ABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABAB","signature":"$signature"$extra}""".toByteArray()

    @Test fun parsesValidShapeWithoutClaimingSignatureValid() {
        assertNotNull(InfinityConnectPairingChallengeV1.parse(payload()))
    }
    @Test fun rejectsDuplicateKeys() {
        assertNull(InfinityConnectPairingChallengeV1.parse(payload().toString(Charsets.UTF_8)
            .replace("\"protocolVersion\":1", "\"protocolVersion\":1,\"protocolVersion\":1").toByteArray()))
    }
    @Test fun rejectsUnknownAndMissingFields() {
        assertNull(InfinityConnectPairingChallengeV1.parse(payload(",\"extra\":1")))
        assertNull(InfinityConnectPairingChallengeV1.parse(payload().toString(Charsets.UTF_8)
            .replace(",\"signature\":\"$signature\"", "").toByteArray()))
    }
    @Test fun rejectsMalformedNonceAndPadding() {
        assertNull(InfinityConnectPairingChallengeV1.parse(payload().toString(Charsets.UTF_8)
            .replace("\"nonce\":\"$nonce\"", "\"nonce\":\"$nonce=\"").toByteArray()))
    }
    @Test fun rejectsOversizeAndNoncanonicalUuid() {
        assertNull(InfinityConnectPairingChallengeV1.parse(ByteArray(InfinityConnectPairingChallengeV1.MAX_PAYLOAD_BYTES + 1)))
        assertNull(InfinityConnectPairingChallengeV1.parse(payload().toString(Charsets.UTF_8)
            .replace(request, request.uppercase()).toByteArray()))
    }
    @Test fun rejectsLifetimeOverFiveMinutes() {
        assertNull(InfinityConnectPairingChallengeV1.parse(payload().toString(Charsets.UTF_8)
            .replace("\"expiresAtUnixMs\":301000", "\"expiresAtUnixMs\":301001").toByteArray()))
    }
    @Test fun signatureInputMatchesSharedV1CrossPlatformVector() {
        val challenge = InfinityConnectPairingChallengeV1.Challenge(
            protocolVersion = 1,
            requestId = java.util.UUID.fromString("abcdefab-cdef-abcd-efab-cdefabcdefab"),
            companionDeviceId = java.util.UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
            issuedAtUnixMs = 1_700_000_000_123L,
            expiresAtUnixMs = 1_700_000_300_123L,
            nonce = ByteArray(32) { it.toByte() },
            caSha256 = ByteArray(32) { (0xA0 + it).toByte() },
            signature = ByteArray(70) { 1 }
        )
        val expected = "494e46494e4954592d434f4e4e4543542d50414952494e472d4348414c4c454e47452d5631000001abcdefabcdefabcdefabcdefabcdefabaaaaaaaabbbbccccddddeeeeeeeeeeee0000018bcfe5687b0000018bcfe9fc5b000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1fa0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf"
        val actual = InfinityConnectPairingChallengeV1.signatureInput(challenge)
            .joinToString("") { "%02x".format(it) }
        org.junit.Assert.assertEquals(expected, actual)
    }

}
