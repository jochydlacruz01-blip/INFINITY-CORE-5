package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityControlCenterPolicyTest {
    @Test fun panelWidthNeverExceedsCompactViewportAndKeepsDesktopCap() {
        assertEquals(216f, infinityControlPanelMaxWidthDp(240f), 0f)
        assertEquals(336f, infinityControlPanelMaxWidthDp(360f), 0f)
        assertEquals(350f, infinityControlPanelMaxWidthDp(600f), 0f)
        assertEquals(1f, infinityControlPanelMaxWidthDp(12f), 0f)
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

    @Test fun devicePanelFitsCompactViewportAndExpandsForTablet() {
        assertEquals(216f, infinityControlDevicePanelMaxWidthDp(240f), 0f)
        assertEquals(336f, infinityControlDevicePanelMaxWidthDp(360f), 0f)
        assertEquals(476f, infinityControlDevicePanelMaxWidthDp(500f), 0f)
        assertEquals(560f, infinityControlDevicePanelMaxWidthDp(800f), 0f)
        assertEquals(1f, infinityControlDevicePanelMaxWidthDp(12f), 0f)
        assertEquals(280f, infinityControlDevicePanelMaxWidthDp(Float.NaN), 0f)
    }

    @Test fun connectivityTilesDistinguishEnabledDisabledAndAndroidOwnedStates() {
        assertEquals("Activado", infinityControlConnectivityStatus(true))
        assertEquals("Desactivado", infinityControlConnectivityStatus(false))
        assertEquals("Consultar en Android", infinityControlConnectivityStatus(null))
    }

    @Test fun mediaVolumePercentageMapsSafelyToDiscreteAndroidLevels() {
        assertEquals(0, infinityMediaVolumeTargetLevel(15, -20))
        assertEquals(4, infinityMediaVolumeTargetLevel(15, 33))
        assertEquals(15, infinityMediaVolumeTargetLevel(15, 100))
        assertEquals(null as Int?, infinityMediaVolumeTargetLevel(0, 50))
        assertEquals(null as Int?, infinityMediaVolumeTargetLevel(-1, 50))
    }

    @Test fun compactAndExpandedLayoutsChooseAppropriateColumns() {
        assertEquals(1, infinityControlPanelColumns(360f))
        assertEquals(1, infinityControlPanelColumns(519.9f))
        assertEquals(2, infinityControlPanelColumns(520f))
        assertEquals(1, infinityControlPanelColumns(Float.NaN))
    }
}
