package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Integrated edge-case checks across shared desktop and module state rules. */
class InfinityIntegratedStabilityTest {
    @Test
    fun desktopPreferenceNormalizationIsStableAndBounded() {
        val iconSize = infinityNormalizeIconSize(Float.NaN)
        assertEquals(48f, iconSize)
        assertEquals(40f, infinityNormalizeIconSize(-Float.MAX_VALUE))
        assertEquals(72f, infinityNormalizeIconSize(Float.MAX_VALUE))
        assertEquals(iconSize, infinityNormalizeIconSize(iconSize))
        assertEquals("midnight", infinityNormalizeThemeChoice("unsupported"))
        assertEquals("light", infinityNormalizeAppearance(" LIGHT "))
    }

    @Test
    fun workspaceWindowIdentityRemainsCanonicalAcrossInvalidInputs() {
        assertEquals("orbit_1", infinitySafeWindowIdentity(" ORBIT ", 0))
        assertEquals("file_manager_3", infinitySafeWindowIdentity("File_Manager", 99))
        assertEquals(null, infinitySafeWindowIdentity("!!!", 2))
        assertEquals(null, infinitySafeWindowIdentity(null, 2))
    }

    @Test
    fun resizeRulesNeverExceedAvailableSpaceOrNegativeBounds() {
        assertEquals(0f, infinityResizeDimension(50f, 10f, -1f, 20f), 0f)
        assertEquals(100f, infinityResizeDimension(90f, 100f, 100f, 30f), 0f)
        assertEquals(30f, infinityResizeDimension(10f, -100f, 100f, 30f), 0f)
        assertEquals(20f, infinityResizeDimension(10f, 10f, 20f, 50f), 0f)
    }

    @Test
    fun notificationFeedUpdatesKeysAndHonorsRetentionCap() {
        val first = InfinityNotification("a", "com.example.app", "App", "Title", "Message", 1L, false, true, null)
        val second = InfinityNotification("b", "com.example.app", "App", "Title", "Message", 2L, false, true, null)
        val updated = first.copy(title = "Updated", timestamp = 3L)
        assertEquals(listOf("b", "a"), infinityNotificationFeedUpdate(listOf(first), second, 2).map { it.key })
        assertEquals("Updated", infinityNotificationFeedUpdate(listOf(first, second), updated, 2).first { it.key == "a" }.title)
        assertEquals(emptyList<InfinityNotification>(), infinityNotificationFeedUpdate(listOf(first), second, 0))
    }

    @Test
    fun initialWindowPlacementRemainsInsideTinyAndNormalViewports() {
        val tiny = infinityInitialWindowPosition(500, 100f, 80f)
        assertEquals(0f, tiny.first, 0f)
        assertEquals(0f, tiny.second, 0f)
        val normal = infinityInitialWindowPosition(0, 1000f, 800f)
        assertTrue(normal.first >= 0f && normal.second >= 0f)
        assertFalse(normal.first > 630f || normal.second > 470f)
    }
}
