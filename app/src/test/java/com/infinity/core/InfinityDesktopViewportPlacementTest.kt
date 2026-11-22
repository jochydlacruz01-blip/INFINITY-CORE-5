package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityDesktopViewportPlacementTest {
    @Test fun placesUniqueIconsWithinReservedDesktopArea() {
        val result = infinityDesktopViewportPlacement(
            listOf("a", "b", "c"), 400f, 500f,
            itemWidth = 100f, itemHeight = 100f,
            leftInset = 10f, topInset = 20f, rightInset = 10f, bottomInset = 120f,
            horizontalGap = 10f, verticalGap = 10f
        )
        assertEquals(3, result.positions.size)
        assertTrue(result.positions.values.all { it.x >= 10f && it.y >= 20f && it.y + 100f <= 380f })
        assertTrue(result.overflowIds.isEmpty())
    }

    @Test fun reportsOverflowInsteadOfStackingIcons() {
        val result = infinityDesktopViewportPlacement(
            listOf("a", "b", "c", "d", "e"), 230f, 260f,
            itemWidth = 100f, itemHeight = 100f,
            leftInset = 10f, topInset = 10f, rightInset = 10f, bottomInset = 10f,
            horizontalGap = 10f, verticalGap = 10f
        )
        assertEquals(listOf("a", "b", "c", "d"), result.positions.keys.toList())
        assertEquals(listOf("e"), result.overflowIds)
        assertEquals(result.positions.size, result.positions.values.toSet().size)
    }

    @Test fun duplicateIdsAreCollapsedAndInvalidMetricsAreSanitized() {
        val result = infinityDesktopViewportPlacement(
            listOf("x", "x", "y"), Float.NaN, Float.POSITIVE_INFINITY,
            itemWidth = Float.NaN, itemHeight = -1f,
            leftInset = Float.NaN, topInset = -1f,
            horizontalGap = Float.NaN, verticalGap = -1f
        )
        assertEquals(2, result.positions.size)
        assertTrue(result.positions.values.all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test fun tinyViewportProducesFinitePlacementAndOverflowRatherThanCrash() {
        val result = infinityDesktopViewportPlacement(
            listOf("one", "two", "three"), 1f, 1f,
            itemWidth = 116f, itemHeight = 150f,
            leftInset = 118f, topInset = 118f, rightInset = 168f, bottomInset = 168f
        )
        assertEquals(1, result.positions.size)
        assertEquals(listOf("two", "three"), result.overflowIds)
        assertTrue(result.positions.values.all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test fun excessiveAndNonFiniteInsetsAreClamped() {
        val result = infinityDesktopViewportPlacement(
            listOf("a", "b"), 320f, 480f,
            itemWidth = 80f, itemHeight = 80f,
            leftInset = Float.MAX_VALUE, topInset = Float.NaN,
            rightInset = Float.POSITIVE_INFINITY, bottomInset = Float.MAX_VALUE,
            horizontalGap = Float.POSITIVE_INFINITY, verticalGap = Float.NaN
        )
        assertTrue(result.positions.values.all { it.x.isFinite() && it.y.isFinite() })
        assertEquals(result.positions.size + result.overflowIds.size, 2)
    }

    @Test fun emptyInputReturnsNoPlacementsOrOverflow() {
        assertEquals(
            InfinityDesktopViewportPlacement(emptyMap(), emptyList()),
            infinityDesktopViewportPlacement(emptyList(), 300f, 400f)
        )
    }
}
