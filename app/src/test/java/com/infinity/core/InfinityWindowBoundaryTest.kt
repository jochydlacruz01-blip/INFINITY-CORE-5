package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityWindowBoundaryTest {
    @Test fun initialWindowPositionRecoversFromInvalidGeometry() {
        val position = infinityInitialWindowPosition(
            Int.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY,
            Float.NaN, Float.NEGATIVE_INFINITY
        )
        assertTrue(position.first.isFinite() && position.second.isFinite())
        assertTrue(position.first >= 0f && position.second >= 0f)
    }

    @Test fun initialWindowPositionClampsOversizedWindowsToOrigin() {
        assertEquals(0f to 0f, infinityInitialWindowPosition(0, 320f, 240f, 500f, 400f))
    }

    @Test fun initialWindowSizeRecoversFromInvalidMetrics() {
        val size = infinityInitialWindowSize(
            Float.NaN, Float.POSITIVE_INFINITY,
            Float.NaN, Float.NEGATIVE_INFINITY,
            Float.NaN, Float.POSITIVE_INFINITY
        )
        assertTrue(size.first.isFinite() && size.second.isFinite())
        assertTrue(size.first > 0f && size.second > 0f)
    }

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

    @Test fun movementRecoversFromNonFiniteGeometry() {
        assertEquals(0f to 0f, infinityClampWindowPosition(Float.NaN, Float.POSITIVE_INFINITY, 300f, 200f, 500f, 400f))
        assertEquals(0f to 0f, infinityClampWindowPosition(20f, 30f, Float.NaN, Float.NEGATIVE_INFINITY, 500f, 400f))
        assertEquals(0f to 0f, infinityClampWindowPosition(20f, 30f, 300f, 200f, Float.NaN, Float.NEGATIVE_INFINITY))
    }

    @Test fun restoredBoundsRecoverFromNonFiniteValues() {
        val bounds = infinityNormalizeWindowBounds(
            Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, Float.NEGATIVE_INFINITY,
            500f, 400f
        )
        assertTrue(bounds.x.isFinite() && bounds.y.isFinite())
        assertTrue(bounds.width.isFinite() && bounds.height.isFinite())
        assertTrue(bounds.x >= 0f && bounds.y >= 0f)
        assertTrue(bounds.width > 0f && bounds.height > 0f)
    }

    @Test fun restoredBoundsSanitizeInvalidViewportAndMargins() {
        val bounds = infinityNormalizeWindowBounds(
            20f, 30f, 300f, 200f, Float.NaN, Float.POSITIVE_INFINITY,
            minWidth = Float.NaN, minHeight = Float.NEGATIVE_INFINITY, margin = Float.NaN
        )
        assertTrue(bounds.x.isFinite() && bounds.y.isFinite())
        assertTrue(bounds.width.isFinite() && bounds.height.isFinite())
        assertTrue(bounds.x >= 0f && bounds.y >= 0f)
    }

    @Test fun restoredWindowBoundsAdaptFromLandscapeToPortraitViewport() {
        assertEquals(
            InfinityWindowBounds(16f, 80f, 344f, 300f),
            infinityNormalizeWindowBounds(250f, 80f, 500f, 300f, 360f, 640f)
        )
    }

    @Test fun restoredWindowBoundsAdaptFromPortraitToLandscapeViewport() {
        assertEquals(
            InfinityWindowBounds(40f, 16f, 300f, 484f),
            infinityNormalizeWindowBounds(40f, 120f, 300f, 500f, 900f, 500f)
        )
    }

    @Test fun restoredWindowBoundsStayReachableInExtremelySmallViewport() {
        val bounds = infinityNormalizeWindowBounds(
            -500f, 900f, 700f, 600f, 180f, 120f
        )
        assertEquals(0f, bounds.x, 0.001f)
        assertEquals(0f, bounds.y, 0.001f)
        assertEquals(164f, bounds.width, 0.001f)
        assertEquals(104f, bounds.height, 0.001f)
    }

    @Test fun gestureDeltaConvertsPixelsToDpAtDifferentDensities() {
        assertEquals(10f, infinityGestureDeltaToDp(20f, 2f), 0.001f)
        assertEquals(20f, infinityGestureDeltaToDp(30f, 1.5f), 0.001f)
        assertEquals(40f, infinityGestureDeltaToDp(40f, 1f), 0.001f)
    }

    @Test fun gestureDeltaRecoversFromInvalidInputs() {
        assertEquals(0f, infinityGestureDeltaToDp(Float.NaN, 2f), 0.001f)
        assertEquals(0f, infinityGestureDeltaToDp(Float.POSITIVE_INFINITY, 2f), 0.001f)
        assertEquals(12f, infinityGestureDeltaToDp(12f, Float.NaN), 0.001f)
        assertEquals(12f, infinityGestureDeltaToDp(12f, 0f), 0.001f)
    }

}
