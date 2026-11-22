package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityDesktopGridLayoutPackTest {
    @Test fun invalidViewportAndItemDimensionsRecoverToFiniteCoordinates() {
        val points = infinityDesktopGridOffsets(4, Float.NaN, Float.NaN, Float.POSITIVE_INFINITY)
        assertEquals(4, points.size)
        assertTrue(points.all { it.x.isFinite() && it.y.isFinite() && it.x >= 0f && it.y >= 0f })
    }

    @Test fun nonPositiveCountReturnsEmpty() {
        assertEquals(emptyList<androidx.compose.ui.geometry.Offset>(), infinityDesktopGridOffsets(0, 400f, 100f, 100f))
        assertEquals(emptyList<androidx.compose.ui.geometry.Offset>(), infinityDesktopGridOffsets(-1, 400f, 100f, 100f))
    }

    @Test fun narrowViewportUsesOneColumn() {
        val points = infinityDesktopGridOffsets(3, 80f, 100f, 100f)
        assertEquals(16f, points[0].x, 0f)
        assertEquals(16f, points[1].x, 0f)
        assertTrue(points[1].y > points[0].y && points[2].y > points[1].y)
    }
}
