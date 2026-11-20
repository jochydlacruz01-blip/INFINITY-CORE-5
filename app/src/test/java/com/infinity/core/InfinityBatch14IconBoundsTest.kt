package com.infinity.core

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityBatch14IconBoundsTest {
    @Test fun iconDragClampsToDesktopEdges() {
        assertEquals(Offset(0f, 0f), infinityBoundIconOffset(Offset(20f, 30f), Offset(-50f, -60f), 500f, 600f))
        assertEquals(Offset(390f, 450f), infinityBoundIconOffset(Offset(390f, 450f), Offset(40f, 60f), 500f, 600f))
    }

    @Test fun smallerViewportKeepsIconOriginWithinBounds() {
        assertEquals(Offset(0f, 0f), infinityBoundIconOffset(Offset(20f, 30f), Offset(5f, 5f), 80f, 100f))
    }

    @Test fun restoredIconPositionIsClampedWhenViewportShrinks() {
        assertEquals(
            Offset(390f, 450f),
            infinityBoundIconOffset(Offset(820f, 940f), Offset.Zero, 500f, 600f)
        )
    }

    @Test fun restoredIconPositionIsPreservedWhenStillVisible() {
        assertEquals(
            Offset(120f, 160f),
            infinityBoundIconOffset(Offset(120f, 160f), Offset.Zero, 500f, 600f)
        )
    }
    @Test fun iconGridStartsInColumnsAndMovesDownByRows() {
        assertEquals(Offset(12f, 24f), infinityDesktopIconGridOffset(0, 400f, 100f, 120f))
        assertEquals(Offset(124f, 24f), infinityDesktopIconGridOffset(1, 400f, 100f, 120f))
        assertEquals(Offset(236f, 24f), infinityDesktopIconGridOffset(2, 400f, 100f, 120f))
        assertEquals(Offset(12f, 158f), infinityDesktopIconGridOffset(3, 400f, 100f, 120f))
    }

    @Test fun gridIconPlacementLimitsColumnOnNarrowScreen() {
        assertEquals(Offset(0f, 158f), infinityDesktopIconGridOffset(1, 90f, 100f, 120f, horizontalGap = 12f))
    }
    @Test fun dockSafeZoneSubtractsDockInsetsAndGap() {
        assertEquals(432f, infinityDesktopHeightAboveDock(600f), 0.001f)
    }

    @Test fun dockSafeZoneNeverReturnsNegativeHeight() {
        assertEquals(0f, infinityDesktopHeightAboveDock(80f, 100f, 30f, 50f), 0.001f)
    }
    @Test fun resolverMovesOverlappingIconsIntoSeparateSlots() {
        val result = infinityResolveIconOverlaps(
            listOf(Offset(10f, 10f), Offset(10f, 10f)),
            500f, 600f
        )
        assertEquals(Offset(10f, 10f), result[0])
        assertEquals(false, result[0] == result[1])
    }

    @Test fun resolverPreservesNonOverlappingPositions() {
        val result = infinityResolveIconOverlaps(
            listOf(Offset(10f, 10f), Offset(150f, 10f)),
            500f, 600f
        )
        assertEquals(Offset(10f, 10f), result[0])
        assertEquals(Offset(150f, 10f), result[1])
    }

    @Test fun resolverSeparatesDenseIconsAcrossAvailableGridCells() {
        val result = infinityResolveIconOverlaps(List(6) { Offset(10f, 10f) }, 280f, 650f, 110f, 120f, topInset = 20f)
        assertEquals(6, result.distinct().size)
        for (i in result.indices) for (j in i + 1 until result.size) {
            val a = result[i]; val b = result[j]
            assertEquals(false, a.x < b.x + 110f && a.x + 110f > b.x && a.y < b.y + 120f && a.y + 120f > b.y)
        }
    }
}
