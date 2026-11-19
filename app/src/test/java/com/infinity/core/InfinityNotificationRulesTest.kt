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

    @Test
    fun appLabelsAreNullSafeAndBounded() {
        assertEquals("", infinityNotificationAppName(null))
        assertEquals("App", infinityNotificationAppName("App"))
        assertEquals(200, infinityNotificationAppName("x".repeat(250)).length)
    }

    @Test
    fun privateNotificationContentIsRedactedBeforeStorage() {
        assertEquals(
            "" to "",
            infinityNotificationContent("Private title", "Private message", true)
        )
    }

    @Test
    fun visibleNotificationContentRemainsBounded() {
        val content = infinityNotificationContent(
            "T".repeat(400),
            "M".repeat(2_500),
            false
        )
        assertEquals(300, content.first.length)
        assertEquals(2_000, content.second.length)
    }
}
