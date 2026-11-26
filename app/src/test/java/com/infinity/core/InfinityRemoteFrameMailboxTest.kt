package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityRemoteFrameMailboxTest {
    private fun frame(sequence: Long, bytes: ByteArray = byteArrayOf(1, 2, 3)) =
        InfinityRemoteFramePacket(sequence, 640, 360, InfinityRemoteFramePacket.Encoding.JPEG, bytes, 1000L + sequence)

    @Test fun acceptsFirstFrameAndReturnsItOnce() {
        val mailbox = InfinityRemoteFrameMailbox()
        assertEquals(InfinityFrameOfferResult.ACCEPTED, mailbox.offer(frame(1)))
        assertEquals(1L, mailbox.takeLatest()?.sequence)
        assertNull(mailbox.takeLatest())
    }

    @Test fun newestFrameReplacesUnreadFrame() {
        val mailbox = InfinityRemoteFrameMailbox()
        mailbox.offer(frame(1))
        mailbox.offer(frame(2))
        assertEquals(2L, mailbox.takeLatest()?.sequence)
        assertEquals(1L, mailbox.snapshot().replacedCount)
    }

    @Test fun rejectsDuplicateAndOutOfOrderSequences() {
        val mailbox = InfinityRemoteFrameMailbox()
        mailbox.offer(frame(8))
        assertEquals(InfinityFrameOfferResult.STALE_SEQUENCE, mailbox.offer(frame(8)))
        assertEquals(InfinityFrameOfferResult.STALE_SEQUENCE, mailbox.offer(frame(7)))
        assertEquals(8L, mailbox.takeLatest()?.sequence)
    }

    @Test fun payloadIsDefensivelyCopiedOnInputAndOutput() {
        val source = byteArrayOf(1, 2, 3)
        val packet = frame(1, source)
        source[0] = 9
        val returned = packet.payload
        returned[1] = 9
        assertArrayEquals(byteArrayOf(1, 2, 3), packet.payload)
    }

    @Test fun rejectsOversizedDimensionsAndPayload() {
        assertThrows(IllegalArgumentException::class.java) {
            InfinityRemoteFramePacket(1, 9000, 1, InfinityRemoteFramePacket.Encoding.JPEG, byteArrayOf(1), 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InfinityRemoteFramePacket(1, 8192, 8192, InfinityRemoteFramePacket.Encoding.PNG, byteArrayOf(1), 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            InfinityRemoteFramePacket(1, 1, 1, InfinityRemoteFramePacket.Encoding.JPEG,
                ByteArray(InfinityRemoteFramePacket.MAX_PAYLOAD_BYTES + 1), 1)
        }
    }

    @Test fun rgbaPayloadMustMatchDimensions() {
        assertThrows(IllegalArgumentException::class.java) {
            InfinityRemoteFramePacket(1, 2, 2, InfinityRemoteFramePacket.Encoding.RGBA_8888, ByteArray(15), 1)
        }
        val valid = InfinityRemoteFramePacket(1, 2, 2, InfinityRemoteFramePacket.Encoding.RGBA_8888, ByteArray(16), 1)
        assertEquals(16, valid.payloadSize)
    }

    @Test fun clearDropsPendingFrameAndCanResetSequence() {
        val mailbox = InfinityRemoteFrameMailbox()
        mailbox.offer(frame(12))
        mailbox.clear()
        assertNull(mailbox.takeLatest())
        assertEquals(InfinityFrameOfferResult.STALE_SEQUENCE, mailbox.offer(frame(11)))
        mailbox.clear(resetSequence = true)
        assertEquals(InfinityFrameOfferResult.ACCEPTED, mailbox.offer(frame(1)))
    }

    @Test fun statsReportAcceptedAndReplacedFrames() {
        val mailbox = InfinityRemoteFrameMailbox()
        mailbox.offer(frame(1))
        mailbox.offer(frame(2))
        val stats = mailbox.snapshot()
        assertTrue(stats.hasPendingFrame)
        assertEquals(2L, stats.acceptedCount)
        assertEquals(1L, stats.replacedCount)
        assertEquals(2L, stats.lastAcceptedSequence)
    }
}
