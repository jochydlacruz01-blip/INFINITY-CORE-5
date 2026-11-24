package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityLatestFrameMailboxTest {
    @Test fun schedulesOnlyOneDrainAndKeepsNewestFrame() {
        val mailbox = InfinityLatestFrameMailbox<String>()
        assertTrue(mailbox.offer("frame-1"))
        assertFalse(mailbox.offer("frame-2"))
        assertFalse(mailbox.offer("frame-3"))
        assertEquals("frame-3", mailbox.takeLatest())
        assertNull(mailbox.takeLatest())
        assertTrue(mailbox.offer("frame-4"))
        assertEquals("frame-4", mailbox.takeLatest())
    }

    @Test fun clearDropsPendingFrameAndResetsScheduling() {
        val mailbox = InfinityLatestFrameMailbox<String>()
        assertTrue(mailbox.offer("stale"))
        mailbox.clear()
        assertNull(mailbox.takeLatest())
        assertTrue(mailbox.offer("fresh"))
        assertEquals("fresh", mailbox.takeLatest())
    }

    @Test fun acceptsNewFrameAfterDrain() {
        val mailbox = InfinityLatestFrameMailbox<Int>()
        assertTrue(mailbox.offer(1))
        assertEquals(1, mailbox.takeLatest())
        assertTrue(mailbox.offer(2))
        assertEquals(2, mailbox.takeLatest())
    }
}
