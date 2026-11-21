package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityWindowBoundaryTest {
    @Test fun movementClampsWindowToViewport() {
        assertEquals(0f to 0f, infinityClampWindowPosition(-20f, -5f, 300f, 200f, 500f, 400f))
        assertEquals(200f to 200f, infinityClampWindowPosition(999f, 999f, 300f, 200f, 500f, 400f))
        assertEquals(0f to 0f, infinityClampWindowPosition(50f, 50f, 900f, 800f, 500f, 400f))
    }

    @Test fun movementPreservesInteriorCoordinates() {
        assertEquals(75f to 120f, infinityClampWindowPosition(75f, 120f, 300f, 200f, 500f, 400f))
    }
    @Test fun restoredWindowBoundsNormalizeSizeAndPositionForSmallViewport() {
        assertEquals(
            InfinityWindowBounds(0f, 0f, 260f, 210f),
            infinityNormalizeWindowBounds(-50f, -90f, 900f, 800f, 276f, 226f)
        )
    }

    @Test fun restoredWindowBoundsPreserveValidGeometry() {
        assertEquals(
            InfinityWindowBounds(60f, 70f, 300f, 220f),
            infinityNormalizeWindowBounds(60f, 70f, 300f, 220f, 600f, 500f)
        )
    }
}
