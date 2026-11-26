package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityDockSafeAreaTest {
    @Test fun draggingCannotMoveWindowBehindDock() {
        val (x, y) = infinityClampWindowPosition(900f, 700f, 400f, 260f, 1200f, 800f, 680f)
        assertEquals(800f, x, 0f)
        assertEquals(420f, y, 0f)
        assertTrue(y + 260f <= 680f)
    }

    @Test fun restoredWindowShrinksToFitAboveDock() {
        val bounds = infinityNormalizeWindowBounds(
            20f, 600f, 500f, 900f, 900f, 800f, safeBottom = 320f
        )
        assertEquals(304f, bounds.height, 0f)
        assertEquals(20f, bounds.x, 0f)
        assertEquals(16f, bounds.y, 0f)
        assertTrue(bounds.y + bounds.height <= 320f)
    }

    @Test fun invalidDockMeasurementFallsBackToViewportBottom() {
        val result = infinityClampWindowPosition(10f, 700f, 200f, 200f, 600f, 800f, Float.NaN)
        assertEquals(10f, result.first, 0f)
        assertEquals(600f, result.second, 0f)
    }

    @Test fun dockScrollIsReservedForPhoneWidths() {
        assertTrue(infinityDockRequiresHorizontalScroll(599.99f))
        assertTrue(!infinityDockRequiresHorizontalScroll(600f))
    }

    @Test fun initialDockReservationMatchesAdaptiveLayouts() {
        assertEquals(112f, infinityDockReservedHeightDp(800f, true, "infinity"), 0f)
        assertEquals(74f, infinityDockReservedHeightDp(800f, false, "windows11"), 0f)
        assertEquals(84f, infinityDockReservedHeightDp(390f, false, "infinity"), 0f)
    }
}
