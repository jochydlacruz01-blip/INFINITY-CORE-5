package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InfinityFreeRdpClientTest {
    @Test fun normalizesOnlySha256Fingerprints() {
        val pin = "A1".repeat(32)
        assertEquals(pin, InfinityFreeRdpClient.normalizeRdpFingerprint(pin))
        assertEquals(pin, InfinityFreeRdpClient.normalizeRdpFingerprint("sha256:" + pin.chunked(2).joinToString(":")))
        assertNull(InfinityFreeRdpClient.normalizeRdpFingerprint("B2".repeat(20)))
        assertNull(InfinityFreeRdpClient.normalizeRdpFingerprint("not-a-certificate"))
    }
}
