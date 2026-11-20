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
}
