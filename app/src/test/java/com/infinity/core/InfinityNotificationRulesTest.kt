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
    fun directionalFormattingControlsCannotSpoofNotificationText() {
        val spoofed = "Transferir 100" + '\u202E' + "cod.exe" + '\u202C' + " ahora"
        assertEquals("Transferir 100cod.exe ahora", infinityNotificationMessage(spoofed))
        assertEquals("ab", infinityNotificationTitle("a" + '\u200F' + "b"))
        assertEquals("xy", infinityNotificationAppName("x" + '\u2066' + "y" + '\u2069'))
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

    @Test fun notificationFeedReplacesExistingKeyAndKeepsOrder() {
        val old = InfinityNotification("k","pkg","App","old","",2,false,true,null)
        val newer = InfinityNotification("k","pkg","App","new","",3,false,true,null)
        val other = InfinityNotification("x","pkg","App","","",1,false,true,null)
        val result = infinityNotificationFeedUpdate(listOf(old,other),newer)
        assertEquals(listOf("k","x"),result.map { it.key })
        assertEquals("new",result.first().title)
    }
    @Test fun notificationFeedCapsInsertionsAndHandlesZeroLimit() {
        val items=(1..5).map { InfinityNotification("$it","pkg","App","","",it.toLong(),false,true,null) }
        assertEquals(listOf("n","1","2"),infinityNotificationFeedUpdate(items,InfinityNotification("n","pkg","App","","",6,false,true,null),3).map { it.key })
        assertEquals(emptyList<InfinityNotification>(),infinityNotificationFeedUpdate(items,items.first(),0))
    }

    @Test
    fun updatedNotificationMovesToNewestPositionAndRemovesDuplicateKeys() {
        val newest = InfinityNotification("a", "pkg", "App", "old", "", 1L, false, true, null)
        val middle = InfinityNotification("b", "pkg", "App", "middle", "", 2L, false, true, null)
        val duplicate = newest.copy(title = "stale duplicate")
        val updated = newest.copy(title = "updated", timestamp = 4L)

        val result = infinityNotificationFeedUpdate(listOf(newest, middle, duplicate), updated, 10)

        assertEquals(listOf("a", "b"), result.map { it.key })
        assertEquals("updated", result.first().title)
    }

    @Test
    fun blankNotificationKeyCannotEnterFeedAndRetentionRemainsBounded() {
        val existing = (1..4).map {
            InfinityNotification("$it", "pkg", "App", "", "", it.toLong(), false, true, null)
        }
        val blank = InfinityNotification("", "pkg", "App", "ignored", "", 9L, false, true, null)

        assertEquals(existing.take(2), infinityNotificationFeedUpdate(existing, blank, 2))
        assertEquals(emptyList<InfinityNotification>(), infinityNotificationFeedUpdate(existing, blank, 0))
    }

    @Test
    fun listenerIsEnabledOnlyWhenPermissionIsGrantedAndServiceIsConnected() {
        assertFalse(infinityNotificationAccessEnabled(false, false))
        assertFalse(infinityNotificationAccessEnabled(false, true))
        assertFalse(infinityNotificationAccessEnabled(true, false))
        assertTrue(infinityNotificationAccessEnabled(true, true))
    }

    @Test
    fun permissionRefreshCannotReportDisconnectedServiceAsEnabled() {
        assertFalse(infinityNotificationAccessEnabled(true, false))
        assertFalse(infinityNotificationAccessEnabled(false, true))
        assertTrue(infinityNotificationAccessEnabled(true, true))
    }


    @Test
    fun enablingPrivacyRedactsExistingFeedAndDisablingCannotRestoreErasedText() {
        val private = InfinityNotification(
            key = "notification-1",
            packageName = "com.example.mail",
            appName = "Mail",
            title = "Private subject",
            message = "Private message",
            timestamp = 123L,
            ongoing = false,
            clearable = true,
            contentIntent = null
        )
        val public = private.copy(key = "notification-2", title = "", message = "")
        val original = listOf(private, public)

        val redacted = infinityNotificationApplyPrivacy(original, hideContent = true)

        assertEquals(listOf("notification-1", "notification-2"), redacted.map { it.key })
        assertEquals(listOf("Mail", "Mail"), redacted.map { it.appName })
        assertTrue(redacted.all { it.title.isEmpty() && it.message.isEmpty() })
        assertEquals("Private subject", original.first().title)

        val afterPrivacyDisabled = infinityNotificationApplyPrivacy(redacted, hideContent = false)
        assertEquals(redacted, afterPrivacyDisabled)
        assertTrue(afterPrivacyDisabled.all { it.title.isEmpty() && it.message.isEmpty() })
    }
}
