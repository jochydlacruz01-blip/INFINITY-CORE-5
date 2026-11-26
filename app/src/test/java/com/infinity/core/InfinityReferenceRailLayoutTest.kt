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
        assertTrue(layout.iconSizeDp in 12f..40f)
        assertTrue(bottom <= 600f - 148f + 0.01f)
    }

    @Test
    fun tabletPortraitRailUsesAvailableHeight() {
        val layout = infinityReferenceRailLayout(720f, 900f, 7)
        assertTrue(layout.slotHeightDp in 1f..88f)
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
