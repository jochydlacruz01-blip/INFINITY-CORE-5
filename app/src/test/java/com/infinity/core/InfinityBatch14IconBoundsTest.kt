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
}
