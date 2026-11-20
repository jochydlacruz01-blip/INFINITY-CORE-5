package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityBatch12WindowPlacementTest {
    @Test fun placementStaysWithinSmallAndLargeViewports() {
        val small = infinityInitialWindowPosition(50, 320f, 240f)
        assertEquals(0f, small.first, 0f)
        assertEquals(0f, small.second, 0f)
        val large = infinityInitialWindowPosition(0, 1200f, 900f)
        assertEquals(90f, large.first, 0f)
        assertEquals(75f, large.second, 0f)
        val edge = infinityInitialWindowPosition(11, 600f, 500f)
        assertEquals(230f, edge.first, 0f)
        assertEquals(170f, edge.second, 0f)
    }

    @Test fun cascadePatternRepeatsWithoutGrowingUnbounded() {
        assertEquals(
            infinityInitialWindowPosition(1, 1000f, 800f),
            infinityInitialWindowPosition(13, 1000f, 800f)
        )
        assertEquals(
            infinityInitialWindowPosition(0, 1000f, 800f),
            infinityInitialWindowPosition(-10, 1000f, 800f)
        )
    }
}
