package com.infinity.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityWindowBoundsPackTest {
    @Test fun oversizedPositionIsClampedAndWindowRemainsReachable() {
        val p = infinityClampWindowPosition(Offset(900f, 800f), Size(300f, 250f), Size(500f, 400f))
        assertEquals(248f, p.x, 0f)
        assertEquals(198f, p.y, 0f)
    }

    @Test fun negativeCoordinatesRecoverToOrigin() {
        val p = infinityClampWindowPosition(Offset(-100f, -50f), Size(200f, 100f), Size(400f, 300f))
        assertEquals(0f, p.x, 0f)
        assertEquals(0f, p.y, 0f)
    }

    @Test fun invalidGeometryReturnsFinitePosition() {
        val p = infinityClampWindowPosition(Offset(Float.NaN, Float.POSITIVE_INFINITY), Size(Float.NaN, -1f), Size(Float.NaN, Float.POSITIVE_INFINITY))
        assertTrue(p.x.isFinite() && p.y.isFinite())
        assertEquals(0f, p.x, 0f)
        assertEquals(0f, p.y, 0f)
    }
}
