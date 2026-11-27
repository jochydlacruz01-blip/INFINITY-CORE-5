package com.infinity.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class InfinityConnectPairingChallengeV1Test {
    private val request = "11111111-2222-3333-4444-555555555555"
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
}
