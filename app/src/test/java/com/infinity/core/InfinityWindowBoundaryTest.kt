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
}
