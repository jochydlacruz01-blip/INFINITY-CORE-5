package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun malformedSurrogateCodeUnitsAreDiscarded() {
        val malformed = "a" + '\uD83D' + "b" + '\uDE00' + "c"
        assertEquals("abc", infinityNotificationMessage(malformed))
        assertEquals("😀", infinityNotificationMessage("😀"))
    }

    @Test
    fun controlCharactersAreRemovedButLineBreaksAndTabsRemain() {
        assertEquals("ab", infinityNotificationTitle("a\u0000b"))
        assertEquals("a\nb\tc", infinityNotificationMessage("a\nb\tc"))
        assertEquals("AppName", infinityNotificationAppName("App\u0007Name"))
    }
    @Test
    fun appNameFallbackIsSanitizedAndNeverBlank() {
        assertEquals("com.example.app", infinityNotificationAppNameOrFallback("", "com.example.app"))
        assertEquals("Aplicación", infinityNotificationAppNameOrFallback(null, "\u0000"))
        assertEquals("Safe Label", infinityNotificationAppNameOrFallback("Safe Label", "ignored"))
        assertEquals(200, infinityNotificationAppNameOrFallback(null, "x".repeat(250)).length)
    }

    @Test
    fun sanitizationIsIdempotentForAcceptedUnicodeAndControls() {
        val source = "á漢字😀\u0000\tline\nnext"
        val sanitized = infinityNotificationMessage(source)
        assertEquals(sanitized, infinityNotificationMessage(sanitized))
        assertTrue(sanitized.contains("á漢字😀"))
        assertFalse(sanitized.any { it.isISOControl() && it != '\n' && it != '\t' })
    }

    @Test
    fun redactionDoesNotDependOnMalformedOrOversizedInput() {
        val input = "\uD83D" + "x".repeat(3_000) + "\uDE00"
        assertEquals("" to "", infinityNotificationContent(input, input, true))
        val visible = infinityNotificationContent(input, input, false)
        assertTrue(visible.first.length <= 300)
        assertTrue(visible.second.length <= 2_000)
    }
}
