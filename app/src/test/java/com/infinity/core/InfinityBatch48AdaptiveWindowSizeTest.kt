package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityBatch48AdaptiveWindowSizeTest {
    @Test fun phoneWindowFitsAvailableWidthAndHeight() {
        assertEquals(336f to 330f, infinityInitialWindowSize(360f, 700f))
        assertEquals(296f to 90f, infinityInitialWindowSize(320f, 240f))
    }

    @Test fun windowSizeSanitizesNonFiniteValues() {
        assertEquals(1f to 1f, infinityInitialWindowSize(Float.NaN, Float.POSITIVE_INFINITY))
        // Invalid preferred dimensions fall back to the usable viewport; invalid margins become zero.
        assertEquals(100f to 100f, infinityInitialWindowSize(100f, 100f, Float.NaN, Float.NEGATIVE_INFINITY, Float.NaN, Float.NaN))
    }

    @Test fun desktopKeepsEstablishedWindowDimensions() {
        assertEquals(370f to 330f, infinityInitialWindowSize(1200f, 900f))
    }

    @Test fun initialPositionUsesAdaptiveWindowDimensions() {
        val (width, height) = infinityInitialWindowSize(360f, 700f)
        val (x, y) = infinityInitialWindowPosition(0, 360f, 700f, width, height)
        assertEquals(24f, x, 0f)
        assertEquals(75f, y, 0f)
        assertEquals(true, x + width <= 360f)
        assertEquals(true, y + height <= 700f)
    }
}
