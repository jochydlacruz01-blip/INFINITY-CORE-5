package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class InfinityConnectCertificateTrustTest {
    @Test fun rejectsEmptyAndOversizedDerCertificate() {
        assertNull(InfinityConnectCertificateTrust.parseDer(byteArrayOf()))
        assertNull(InfinityConnectCertificateTrust.parseDer(ByteArray(65 * 1024)))
    }

    @Test fun rejectsMalformedDerCertificate() {
        assertNull(InfinityConnectCertificateTrust.parseDer("not-a-certificate".toByteArray()))
    }

    @Test fun exposesDistinctServerAndClientAuthenticationPurposes() {
        assertEquals("1.3.6.1.5.5.7.3.1", InfinityConnectCertificateTrust.SERVER_AUTH_EKU)
        assertEquals("1.3.6.1.5.5.7.3.2", InfinityConnectCertificateTrust.CLIENT_AUTH_EKU)
    }

    @Test fun malformedPinIsRejectedBeforeCertificateTrustCanBeGranted() {
        // A certificate fixture is deliberately not fabricated in this test;
        // malformed pins are covered by the shared strict parser.
        assertNull(InfinityConnectPairing.normalizeSha256Fingerprint("not-a-pin"))
        assertFalse(InfinityConnectPairing.matchesCertificateFingerprint("not-a-pin", "A".repeat(64)))
    }
}
