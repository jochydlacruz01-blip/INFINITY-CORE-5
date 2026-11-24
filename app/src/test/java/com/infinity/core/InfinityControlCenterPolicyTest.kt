package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityControlCenterPolicyTest {
    @Test fun panelWidthFitsCompactViewportAndKeepsDesktopCap() {
        assertEquals(280f, infinityControlPanelMaxWidthDp(240f), 0f)
        assertEquals(336f, infinityControlPanelMaxWidthDp(360f), 0f)
        assertEquals(350f, infinityControlPanelMaxWidthDp(600f), 0f)
    }

    @Test fun invalidViewportWidthUsesSafeFallback() {
        assertEquals(280f, infinityControlPanelMaxWidthDp(Float.NaN), 0f)
        assertEquals(280f, infinityControlPanelMaxWidthDp(Float.POSITIVE_INFINITY), 0f)
        assertEquals(280f, infinityControlPanelMaxWidthDp(0f), 0f)
    }

    @Test fun panelHeightNeverFallsBelowUsableMinimum() {
        assertEquals(220f, infinityControlPanelMaxHeightDp(300f), 0f)
        assertEquals(612f, infinityControlPanelMaxHeightDp(800f), 0f)
        assertEquals(220f, infinityControlPanelMaxHeightDp(Float.NaN), 0f)
    }

    @Test fun compactAndExpandedLayoutsChooseAppropriateColumns() {
        assertEquals(1, infinityControlPanelColumns(360f))
        assertEquals(1, infinityControlPanelColumns(519.9f))
        assertEquals(2, infinityControlPanelColumns(520f))
        assertEquals(1, infinityControlPanelColumns(Float.NaN))
    }
}
