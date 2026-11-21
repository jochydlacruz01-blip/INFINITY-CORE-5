package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityDesktopGridLayoutPackTest {
    @Test fun emptyCountProducesNoPositions() {
        assertTrue(infinityDesktopGridOffsets(0, 600f, 100f, 120f).isEmpty())
    }

    @Test fun gridWrapsAfterAvailableColumns() {
        val offsets = infinityDesktopGridOffsets(4, 260f, 100f, 120f, leftInset = 10f, topInset = 20f, horizontalGap = 10f, verticalGap = 5f)
        assertEquals(10f, offsets[0].x, 0f)
        assertEquals(20f, offsets[0].y, 0f)
        assertEquals(120f, offsets[1].x, 0f)
        assertEquals(20f, offsets[1].y, 0f)
        assertEquals(10f, offsets[2].x, 0f)
        assertEquals(145f, offsets[2].y, 0f)
    }

    @Test fun invalidViewportAndGapsAreSafelyNormalized() {
        val offsets = infinityDesktopGridOffsets(2, Float.NaN, 80f, 90f, horizontalGap = Float.NaN, verticalGap = -5f)
        assertEquals(0f, offsets[0].x, 0f)
        assertEquals(118f, offsets[0].y, 0f)
        assertEquals(0f, offsets[1].x, 0f)
        assertTrue(offsets[1].y > offsets[0].y)
    }

    @Test fun allContextActionsHaveSpanishLabels() {
        infinityDesktopContextActions().forEach { action ->
            assertTrue(infinityDesktopContextActionLabel(action).isNotBlank())
        }
    }
}
