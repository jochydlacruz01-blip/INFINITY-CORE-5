package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityReferenceRailLayoutTest {
    @Test
    fun fullMastheadAppearsOnlyWhenItClearsClockAndLauncherRail() {
        assertFalse(infinityReferenceMastheadVisible(600f))
        assertFalse(infinityReferenceMastheadVisible(900f))
        assertFalse(infinityReferenceMastheadVisible(1295f))
        assertTrue(infinityReferenceMastheadVisible(1320f))
        assertFalse(infinityReferenceMastheadVisible(Float.NaN))
        assertFalse(infinityReferenceMastheadVisible(Float.POSITIVE_INFINITY))
    }

    @Test
    fun tabletLandscapeRailFitsAboveDock() {
        val layout = infinityReferenceRailLayout(960f, 600f, 7)
        val bottom = layout.topDp + layout.slotHeightDp * 7
        assertTrue(layout.itemWidthDp in 78f..88f)
        assertTrue(layout.iconSizeDp in 1f..40f)
        assertTrue(layout.iconSizeDp <= layout.slotHeightDp)
        assertTrue(bottom <= 600f - 148f + 0.01f)
    }

    @Test
    fun tabletPortraitRailUsesAvailableHeight() {
        val layout = infinityReferenceRailLayout(720f, 900f, 7)
        assertTrue(layout.slotHeightDp in 1f..88f)
        assertTrue(layout.iconSizeDp <= layout.slotHeightDp)
        assertTrue(layout.topDp + layout.slotHeightDp * 7 <= 900f - 148f + 0.01f)
    }

    @Test
    fun compactLandscapeAt520DpNoLongerOverlapsDock() {
        val layout = infinityReferenceRailLayout(600f, 520f, 7)
        val railBottom = layout.topDp + layout.slotHeightDp * 7
        assertEquals(50.57143f, layout.slotHeightDp, 0.001f)
        assertTrue(railBottom <= 520f - 112f + 0.01f)
        assertTrue(layout.showLabels)
    }

    @Test
    fun reducedHeightUsesCompactIconsAndKeepsRailAboveDock() {
        for (height in listOf(480f, 420f)) {
            val layout = infinityReferenceRailLayout(600f, height, 7)
            val railBottom = layout.topDp + layout.slotHeightDp * 7
            assertTrue("rail overlaps dock at height=$height", railBottom <= height - 112f + 0.01f)
            assertTrue(layout.iconSizeDp <= layout.slotHeightDp * .38f + 0.01f)
            if (height == 420f) assertFalse(layout.showLabels)
        }
    }

    @Test
    fun veryShortViewportNeverLetsRailIconsExceedTheirSlots() {
        for (height in listOf(220f, 240f, 260f)) {
            val layout = infinityReferenceRailLayout(600f, height, 7)
            assertTrue("icon exceeds slot at height=$height", layout.iconSizeDp <= layout.slotHeightDp)
            assertTrue("icon size must remain positive at height=$height", layout.iconSizeDp > 0f)
            assertFalse(layout.showLabels)
        }
    }

    @Test
    fun contextMenuIconSizeScalesInsideRailSlot() {
        val slot = 60f
        val small = infinityReferenceRailIconSize(slot, 24f)
        val normal = infinityReferenceRailIconSize(slot, 48f)
        val large = infinityReferenceRailIconSize(slot, 96f)
        assertTrue(small > 0f)
        assertTrue(small < normal)
        assertTrue(normal < large)
        assertTrue(large <= slot * .48f + 0.001f)
        assertEquals(0f, infinityReferenceRailIconSize(Float.NaN, 48f), 0f)
    }

    @Test
    fun railHonorsContextMenuSortOrder() {
        val records = listOf(
            InfinityDesktopIconRecord("notes", "Notas rápidas", "system", 1),
            InfinityDesktopIconRecord("files", "Este Equipo", "folder", 0),
            InfinityDesktopIconRecord("apps", "Aplicaciones", "app", 2)
        )
        assertEquals(
            listOf("apps", "files", "notes"),
            infinityReferenceRailOrderedRecords(records, InfinityIconArrangement.NAME).map { it.id }
        )
        assertEquals(
            listOf("files", "apps", "notes"),
            infinityReferenceRailOrderedRecords(records, InfinityIconArrangement.TYPE).map { it.id }
        )
        assertEquals(
            listOf("files", "notes", "apps"),
            infinityReferenceRailOrderedRecords(records, InfinityIconArrangement.MANUAL).map { it.id }
        )
    }

    @Test
    fun narrowTallTabletKeepsStableGeometry() {
        val first = infinityReferenceRailLayout(600f, 520f, 7)
        val second = infinityReferenceRailLayout(600f, 520f, 7)
        assertEquals(first, second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyRail() {
        infinityReferenceRailLayout(800f, 600f, 0)
    }
}
