package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityAuroraWindowStackTest {
    private data class Window(val id: String, val workspace: Int)
    private val bounds = InfinityAuroraSafeBounds(width = 900f, height = 700f)

    @Test fun focusedWindowMovesToTopWithoutChangingOtherOrder() {
        val a = Window("a", 1); val b = Window("b", 1); val c = Window("c", 2)
        val result = infinityBringItemToFront(listOf(a, b, c), b) { "${it.workspace}:${it.id}" }
        assertEquals(listOf("a", "c", "b"), result.map { it.id }); assertSame(b, result.last())
    }

    @Test fun focusingWindowDoesNotMutateOriginalSnapshot() {
        val original = listOf(Window("a", 1), Window("b", 1), Window("c", 2))
        val result = infinityBringItemToFront(original, original[1]) { "${it.workspace}:${it.id}" }
        assertEquals(listOf("a", "b", "c"), original.map { it.id })
        assertEquals(listOf("a", "c", "b"), result.map { it.id })
    }

    @Test fun identicalIdsInDifferentWorkspacesHaveDistinctStackIdentity() {
        val a = Window("editor", 1); val b = Window("editor", 2)
        val result = infinityBringItemToFront(listOf(a, b), a) { "${it.workspace}:${it.id}" }
        assertEquals(listOf(2, 1), result.map { it.workspace })
    }

    @Test fun maximizeAndRestoreOnlyChangeTheMatchingWorkspace() {
        val a = InfinityAuroraWindow("same", 1, 30f, 40f, 420f, 300f)
        val b = InfinityAuroraWindow("same", 2, 70f, 80f, 500f, 360f)
        val maximized = InfinityAuroraWindowState(listOf(a, b)).maximize("same", 1, bounds)
        assertTrue(maximized.windows.first { it.workspace == 1 }.maximized)
        assertEquals(b, maximized.windows.first { it.workspace == 2 })
        val restored = maximized.restore("same", 1)
        assertEquals(a, restored.windows.first { it.workspace == 1 })
        assertEquals(b, restored.windows.first { it.workspace == 2 })
    }

    @Test fun focusAndCloseAreScopedToCompositeIdentity() {
        val a = InfinityAuroraWindow("same", 1, 0f, 0f, 300f, 240f, zIndex = 1)
        val b = InfinityAuroraWindow("same", 2, 0f, 0f, 300f, 240f, zIndex = 2)
        val focused = InfinityAuroraWindowState(listOf(a, b)).focus("same", 1)
        assertEquals(1, focused.activeWindowWorkspace)
        assertEquals(2L, focused.windows.first { it.workspace == 2 }.zIndex)
        assertEquals(listOf(2), focused.close("same", 1).windows.map { it.workspace })
    }

    @Test fun resizePreviewRollsBackWithoutMutatingPersistedState() {
        val original = InfinityAuroraWindow("resize", 1, 20f, 30f, 360f, 280f)
        val state = InfinityAuroraWindowState(listOf(original))
        val tx = state.beginGeometryChange("resize", 1)!!
        val resized = state.updateGeometry(tx, tx.current.copy(width = 510f, height = 390f))
        assertEquals(510f, resized.current.width); assertEquals(original, state.windows.single())
        assertEquals(state, state.cancelGeometry(resized))
    }

    @Test fun geometryCommitRejectsWrongWorkspaceAndCommitsValidResize() {
        val original = InfinityAuroraWindow("move", 1, 20f, 30f, 360f, 280f)
        val state = InfinityAuroraWindowState(listOf(original)); val tx = state.beginGeometryChange("move", 1)!!
        assertEquals(state, state.commitGeometry(tx.copy(current = tx.current.copy(workspace = 2)), bounds))
        val committed = state.commitGeometry(state.updateGeometry(tx, tx.current.copy(x = 120f, width = 510f)), bounds)
        assertEquals(120f, committed.windows.single().x); assertEquals(510f, committed.windows.single().width)
    }

    @Test fun restoreGeometryChangesPersistenceSnapshotToken() {
        val window = InfinityAuroraWindow("restore", 1, 10f, 20f, 400f, 300f, maximized = true)
        val before = infinityAuroraWindowSnapshotToken(window)
        val after = infinityAuroraWindowSnapshotToken(window.copy(restoreGeometry = InfinityAuroraWindowGeometry(44f, 55f, 460f, 340f)))
        assertNotEquals(before, after)
    }

    @Test fun canceledMoveAndResizeLeaveExactOriginalWindow() {
        val original = InfinityAuroraWindow("cancel", 1, 11f, 22f, 333f, 244f)
        val state = InfinityAuroraWindowState(listOf(original)); val tx = state.beginGeometryChange("cancel", 1)!!
        val preview = state.updateGeometry(tx, tx.current.copy(x = 180f, y = 190f, width = 520f, height = 410f))
        assertEquals(state, state.cancelGeometry(preview)); assertEquals(original, state.windows.single())
    }
}
