package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityAuroraWindowBoundsTest {
    private val bounds = InfinityAuroraSafeBounds(width = 800f, height = 600f, topInset = 32f, dockTop = 500f)

    @Test fun dragBoundsKeepWindowBelowStatusBarAndAboveDock() {
        val bounded = infinityAuroraBoundWindow(window(x = -200f, y = -100f, width = 420f, height = 300f), bounds)
        assertTrue(bounded.x >= 8f)
        assertTrue(bounded.y >= 40f)
        assertTrue(bounded.y + bounded.height <= 492f)
    }

    @Test fun maximizeUsesOnlySafeDesktopRectangle() {
        val maximized = infinityAuroraMaximizedWindow(window(), bounds)
        assertEquals(40f, maximized.y)
        assertEquals(452f, maximized.height)
        assertTrue(maximized.y + maximized.height <= bounds.dockTop - bounds.margin)
    }

    @Test fun narrowViewportsReduceMinimumSizeWithoutCrossingSafeBounds() {
        val narrow = infinityAuroraBoundWindow(window(width = 900f, height = 900f), InfinityAuroraSafeBounds(280f, 300f, 24f, 240f))
        assertTrue(narrow.width <= 264f)
        assertTrue(narrow.height <= 200f)
        assertTrue(narrow.y + narrow.height <= 232f)
    }

    private fun window(x: Float = 40f, y: Float = 60f, width: Float = 420f, height: Float = 300f) =
        InfinityAuroraWindow("notes-w1", 1, x, y, width, height)
}

class InfinityAuroraWindowStateTest {
    private fun sample() = InfinityAuroraWindowState(
        windows = listOf(
            InfinityAuroraWindow("notes-w1", 1, 20f, 40f, 400f, 300f, zIndex = 1),
            InfinityAuroraWindow("files-w1", 1, 30f, 50f, 400f, 300f, zIndex = 2),
            InfinityAuroraWindow("notes-w2", 2, 20f, 40f, 400f, 300f, zIndex = 3)
        ), activeWindowId = "files-w1", nextZIndex = 4
    )

    @Test fun workspaceSwitchNeverReturnsWindowsFromOtherSpaces() {
        assertEquals(listOf("notes-w1", "files-w1"), sample().visibleWindows(1).map { it.id })
        assertEquals(listOf("notes-w2"), sample().visibleWindows(2).map { it.id })
        assertEquals(emptyList<InfinityAuroraWindow>(), sample().visibleWindows(3))
    }

    @Test fun focusRaisesWindowDeterministicallyAndUpdatesActiveId() {
        val focused = sample().focus("notes-w1", 1)
        assertEquals("notes-w1", focused.activeWindowId)
        assertTrue(focused.visibleWindows(1).last().id == "notes-w1")
        assertEquals(4L, focused.windows.first { it.id == "notes-w1" }.zIndex)
    }

    @Test fun geometryGestureCommitsOnlyTargetWorkspaceAndCancelIsRollback() {
        val state = sample()
        val tx = state.beginGeometryChange("notes-w1", 1)!!
        val moved = state.updateGeometry(tx, tx.current.copy(x = 250f, y = 180f))
        assertEquals(20f, state.windows.first { it.id == "notes-w1" }.x)
        val committed = state.commitGeometry(moved, InfinityAuroraSafeBounds(800f, 600f, 32f, 500f))
        assertEquals(250f, committed.windows.first { it.id == "notes-w1" }.x)
        assertEquals(20f, committed.windows.first { it.id == "notes-w2" }.x)
        assertEquals(state, state.cancelGeometry(moved))
    }

    @Test fun minimizedOrForeignWorkspaceWindowCannotBeDragged() {
        assertNull(sample().beginGeometryChange("missing", 1))
        assertNull(sample().beginGeometryChange("notes-w2", 1))
    }

    @Test fun persistedKeysAreWorkspaceScoped() {
        assertNotEquals(
            infinityAuroraWindowPositionKey(1, "notes-w1", "x"),
            infinityAuroraWindowPositionKey(2, "notes-w2", "x")
        )
        assertEquals("aurora_workspace_2_notes-w2_width", infinityAuroraWindowPositionKey(2, "notes-w2", "width"))
    }
}
