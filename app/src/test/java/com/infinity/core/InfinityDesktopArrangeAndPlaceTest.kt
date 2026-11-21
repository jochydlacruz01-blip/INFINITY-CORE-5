package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityDesktopOrderedGridPlacementTest {
    @Test fun orderedSlotsFollowInputIdsAndRemainUnique() {
        val map = infinityDesktopOrderedGridPlacement(listOf("a", "b", "c"), 600f, 100f, 120f)
        assertEquals(listOf("a", "b", "c"), map.keys.toList())
        assertEquals(3, map.values.toSet().size)
    }

    @Test fun duplicateIdsAreCollapsedToSingleSlot() {
        val map = infinityDesktopOrderedGridPlacement(listOf("x", "x", "y"), 600f, 100f, 120f)
        assertEquals(2, map.size)
    }

    @Test fun arrangeAndPlaceUsesNameOrdering() {
        val icons = listOf(
            InfinityDesktopIconRecord("n", "Notas", "doc", 1),
            InfinityDesktopIconRecord("f", "Archivos", "folder", 0),
            InfinityDesktopIconRecord("a", "Aplicaciones", "app", 2)
        )
        val map = infinityDesktopArrangeAndPlace(icons, InfinityIconArrangement.NAME, 500f, 100f, 120f)
        assertEquals(listOf("a", "f", "n"), map.keys.toList())
    }

    @Test fun narrowViewportStillProducesSafePositions() {
        val map = infinityDesktopOrderedGridPlacement(listOf("a", "b"), Float.NaN, 100f, 120f)
        assertTrue(map.values.all { it.x.isFinite() && it.y.isFinite() && it.x >= 0f && it.y >= 0f })
    }
}
