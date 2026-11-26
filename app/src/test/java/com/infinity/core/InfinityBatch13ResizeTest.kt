package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityBatch13ResizeTest {
    @Test fun resizeRespectsBoundsAndMinimumWhenPossible() {
        assertEquals(320f, infinityResizeDimension(300f, 20f, 500f, 280f), 0f)
        assertEquals(280f, infinityResizeDimension(300f, -50f, 500f, 280f), 0f)
        assertEquals(500f, infinityResizeDimension(490f, 40f, 500f, 280f), 0f)
    }

    @Test fun resizeHandlesTinyOrZeroAvailableSpace() {
        assertEquals(120f, infinityResizeDimension(80f, 50f, 120f, 280f), 0f)
        assertEquals(0f, infinityResizeDimension(50f, 10f, 0f, 280f), 0f)
        assertEquals(0f, infinityResizeDimension(50f, -10f, -30f, 280f), 0f)
    }

    @Test fun resizeSanitizesNonFiniteInputs() {
        assertEquals(0f, infinityResizeDimension(Float.NaN, 20f, Float.NaN, 100f), 0f)
        assertEquals(100f, infinityResizeDimension(Float.NaN, Float.NaN, 200f, 100f), 0f)
        assertEquals(200f, infinityResizeDimension(Float.POSITIVE_INFINITY, 50f, 200f, 100f), 0f)
        assertEquals(100f, infinityResizeDimension(150f, Float.NEGATIVE_INFINITY, 200f, 100f), 0f)
    }

    @Test fun resizeAllowsMinimumToBeReducedForViewport() {
        assertEquals(150f, infinityResizeDimension(120f, -100f, 150f, 280f), 0f)
    }
}
