package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityReferenceRailLayoutTest {
    @Test
    fun tabletLandscapeRailFitsAboveDock() {
        val layout = infinityReferenceRailLayout(960f, 600f, 7)
        val bottom = layout.topDp + layout.slotHeightDp * 7
        assertTrue(layout.itemWidthDp in 78f..88f)
        assertTrue(layout.iconSizeDp in 28f..46f)
        assertTrue(bottom <= 600f - 148f + 1f)
    }

    @Test
    fun tabletPortraitRailUsesAvailableHeight() {
        val layout = infinityReferenceRailLayout(720f, 900f, 7)
        assertTrue(layout.slotHeightDp >= 52f)
        assertTrue(layout.topDp + layout.slotHeightDp * 7 < 900f - 148f)
    }

    @Test
    fun narrowTallTabletKeepsTouchTargetsAndStableGeometry() {
        val first = infinityReferenceRailLayout(600f, 520f, 7)
        val second = infinityReferenceRailLayout(600f, 520f, 7)
        assertEquals(first, second)
        assertTrue(first.slotHeightDp >= 52f)
        assertTrue(first.iconSizeDp >= 28f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyRail() {
        infinityReferenceRailLayout(800f, 600f, 0)
    }
}
