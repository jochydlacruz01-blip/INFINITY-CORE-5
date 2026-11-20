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
        assertEquals("" to "", infinityNotificationContent("Private title", "Private message", true))
    }

    @Test
    fun visibleNotificationContentRemainsBounded() {
        val content = infinityNotificationContent("T".repeat(400), "M".repeat(2_500), false)
        assertEquals(300, content.first.length)
        assertEquals(2_000, content.second.length)
    }

    @Test
    fun textLimitsNeverSplitUnicodeSurrogatePairs() {
        val emoji = "\uD83D\uDE00"

        assertEquals("x".repeat(299), infinityNotificationTitle("x".repeat(299) + emoji))
        assertEquals("x".repeat(298) + emoji, infinityNotificationTitle("x".repeat(298) + emoji))
        assertEquals("m".repeat(1_999), infinityNotificationMessage("m".repeat(1_999) + emoji))
        assertEquals("m".repeat(1_998) + emoji, infinityNotificationMessage("m".repeat(1_998) + emoji))
        assertEquals("a".repeat(199), infinityNotificationAppName("a".repeat(199) + emoji))
        assertEquals("a".repeat(198) + emoji, infinityNotificationAppName("a".repeat(198) + emoji))
    }

    @Test
    fun controlCharactersAreRemovedButLineBreaksAndTabsRemain() {
        assertEquals("ab", infinityNotificationTitle("a\u0000b"))
        assertEquals("a\nb\tc", infinityNotificationMessage("a\nb\tc"))
        assertEquals("AppName", infinityNotificationAppName("App\u0007Name"))
    }
}
