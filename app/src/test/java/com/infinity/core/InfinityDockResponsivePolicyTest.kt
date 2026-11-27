package com.infinity.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityDockResponsivePolicyTest {
    @Test fun phoneViewportUsesScrollableDock() {
        assertTrue(infinityDockRequiresHorizontalScroll(360f))
        assertTrue(infinityDockRequiresHorizontalScroll(599.9f))
    }

    @Test fun tabletViewportKeepsDockInItsFullWidthLayout() {
        assertFalse(infinityDockRequiresHorizontalScroll(600f))
        assertFalse(infinityDockRequiresHorizontalScroll(840f))
    }

    @Test fun startPanelBottomTracksTheMeasuredDockAcrossAdaptiveLayouts() {
        assertEquals(112f, infinityStartPanelBottomPaddingDp(800f, 688f), 0f)
        assertEquals(84f, infinityStartPanelBottomPaddingDp(800f, 716f), 0f)
        assertEquals(88f, infinityStartPanelBottomPaddingDp(800f, 712f), 0f)
    }

    @Test fun startPanelHeightIsLimitedToTheAreaAboveTheDock() {
        assertEquals(664f, infinityStartPanelMaxHeightDp(688f), 0f)
        assertEquals(1f, infinityStartPanelMaxHeightDp(10f), 0f)
        assertEquals(1f, infinityStartPanelMaxHeightDp(Float.NaN), 0f)
    }

    @Test fun invalidMeasurementsUseConservativeStartPanelInset() {
        assertEquals(112f, infinityStartPanelBottomPaddingDp(800f, Float.NaN), 0f)
        assertEquals(0f, infinityStartPanelBottomPaddingDp(Float.NaN, 688f), 0f)
        assertEquals(0f, infinityStartPanelBottomPaddingDp(800f, 900f), 0f)
    }

    @Test fun invalidViewportFailsSafeToScrollableDock() {
        assertTrue(infinityDockRequiresHorizontalScroll(Float.NaN))
        assertTrue(infinityDockRequiresHorizontalScroll(Float.POSITIVE_INFINITY))
    }
}
