package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityNotificationRulesTest {
    @Test
    fun notificationTextIsBoundedAndNullSafe() {
        assertEquals("", infinityNotificationTitle(null))
        assertEquals("", infinityNotificationMessage(null))
        assertEquals("title", infinityNotificationTitle("title"))
        assertEquals("message", infinityNotificationMessage("message"))
        assertEquals(300, infinityNotificationTitle("x".repeat(500)).length)
        assertEquals(2_000, infinityNotificationMessage("x".repeat(2_500)).length)
    }
}
