package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class InfinityAuroraWindowStackTest {
    private data class Window(val id: String, val workspace: Int, val width: Float = 370f, val height: Float = 330f)

    @Test fun focusedWindowMovesToTopWithoutChangingOtherOrder() {
        val a = Window("a", 1)
        val b = Window("b", 1)
        val c = Window("c", 2)
        val result = infinityBringItemToFront(listOf(a, b, c), b) { it.id }
        assertEquals(listOf("a", "c", "b"), result.map { it.id })
        assertSame(b, result.last())
    }

    @Test fun focusingWindowDoesNotMutateOriginalSnapshot() {
        val original = listOf(
            Window("a", 1),
            Window("b", 1),
            Window("c", 2)
        )
        val result = infinityBringItemToFront(original, original[1]) { it.id }

        assertEquals(listOf("a", "b", "c"), original.map { it.id })
        assertEquals(listOf("a", "c", "b"), result.map { it.id })
    }

    @Test fun alreadyTopWindowDoesNotChangeOrder() {
        val a = Window("a", 1)
        val b = Window("b", 1)
        val result = infinityBringItemToFront(listOf(a, b), b) { it.id }
        assertEquals(listOf(a, b), result)
    }

    @Test fun unknownWindowLeavesStackUntouched() {
        val a = Window("a", 1)
        val b = Window("b", 1)
        val missing = Window("missing", 1)
        val result = infinityBringItemToFront(listOf(a, b), missing) { it.id }
        assertEquals(listOf(a, b), result)
    }

    @Test fun draggingWindowRaisesItAboveEverySiblingWithoutChangingItsGeometry() {
        val behind = Window("files", 1)
        val active = Window("notes", 1, width = 520f, height = 410f)
        val front = Window("apps", 1)

        val result = infinityRaiseWindowAfterManipulation(listOf(behind, active, front), active) { it.id }

        assertEquals(listOf("files", "apps", "notes"), result.map { it.id })
        assertEquals(520f, result.last().width)
        assertEquals(410f, result.last().height)
    }

    @Test fun resizingWindowRaisesItAboveEverySiblingAndPreservesTheNewDimensions() {
        val behind = Window("files", 1)
        val active = Window("notes", 1, width = 640f, height = 480f)
        val front = Window("apps", 1)

        val result = infinityRaiseWindowAfterManipulation(listOf(behind, active, front), active) { it.id }

        assertEquals(listOf("files", "apps", "notes"), result.map { it.id })
        assertEquals(640f, result.last().width)
        assertEquals(480f, result.last().height)
    }

    @Test fun emptyStackRemainsEmpty() {
        assertEquals(emptyList<Window>(), infinityBringItemToFront(emptyList(), Window("x", 1)) { it.id })
    }
}
