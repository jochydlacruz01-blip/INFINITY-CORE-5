package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityStartPanelResponsivePolicyTest {
    @Test fun phoneStartPanelUsesNearlyFullWidthAndExpandedMaximum() {
        assertEquals(0.97f, infinityStartPanelWidthFraction(360f), 0.001f)
        assertEquals(460f, infinityStartPanelMaxWidthDp(360f), 0.001f)
        assertEquals(0.97f, infinityStartPanelWidthFraction(599.9f), 0.001f)
    }

    @Test fun tabletStartPanelKeepsBalancedReferenceWidth() {
        assertEquals(0.94f, infinityStartPanelWidthFraction(600f), 0.001f)
        assertEquals(420f, infinityStartPanelMaxWidthDp(600f), 0.001f)
        assertEquals(420f, infinityStartPanelMaxWidthDp(900f), 0.001f)
    }

    @Test fun shortAndTallDisplaysKeepDockClearanceAndBoundedHeight() {
        assertEquals(72f, infinityStartPanelBottomInsetDp(420f), 0.001f)
        assertEquals(84f, infinityStartPanelBottomInsetDp(800f), 0.001f)
        assertEquals(180f, infinityStartPanelMaxHeightDp(240f), 0.001f)
        assertEquals(704f, infinityStartPanelMaxHeightDp(820f), 0.001f)
    }

    @Test fun invalidViewportMetricsFailSafeToCompactPanel() {
        assertEquals(0.97f, infinityStartPanelWidthFraction(Float.NaN), 0.001f)
        assertEquals(460f, infinityStartPanelMaxWidthDp(Float.POSITIVE_INFINITY), 0.001f)
        assertEquals(180f, infinityStartPanelMaxHeightDp(Float.NaN), 0.001f)
    }
}
