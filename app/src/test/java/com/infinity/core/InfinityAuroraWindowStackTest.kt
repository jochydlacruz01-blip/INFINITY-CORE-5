package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class InfinityAuroraWindowStackTest {
    private data class Window(val id: String, val workspace: Int)

    @Test fun focusedWindowMovesToTopWithoutChangingOtherOrder() {
        val a = Window("a", 1)
        val b = Window("b", 1)
        val c = Window("c", 2)
        val result = infinityBringItemToFront(listOf(a, b, c), b) { it.id }
        assertEquals(listOf("a", "c", "b"), result.map { it.id })
        assertSame(b, result.last())
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

    @Test fun emptyStackRemainsEmpty() {
        assertEquals(emptyList<Window>(), infinityBringItemToFront(emptyList(), Window("x", 1)) { it.id })
    }
}
