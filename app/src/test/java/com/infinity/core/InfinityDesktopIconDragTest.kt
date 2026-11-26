package com.infinity.core

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityDesktopIconDragTest {
    @Test fun beginDragCapturesAnImmutableWorkspaceSnapshot() {
        val original = linkedMapOf("files" to Offset(20f, 130f), "notes" to Offset(150f, 130f))
        val tx = infinityBeginDesktopIconDrag(2, original, "files")!!
        original["files"] = Offset(300f, 300f)
        assertEquals(Offset(20f, 130f), tx.originalPositions["files"])
        assertEquals(2, tx.workspace)
    }

    @Test fun moveChangesOnlyDraggedIconAndStaysAboveDockAndHeader() {
        val tx = infinityBeginDesktopIconDrag(1, linkedMapOf(
            "files" to Offset(20f, 130f), "notes" to Offset(150f, 130f)
        ), "files")!!
        val moved = infinityMoveDesktopIconDrag(tx, Offset(40f, 900f), 500f, 500f, 108f, 150f, 118f)
        assertEquals(Offset(60f, 350f), moved.currentPositions["files"])
        assertEquals(Offset(150f, 130f), moved.currentPositions["notes"])
        assertTrue(moved.currentPositions.getValue("files").y >= 118f)
        assertTrue(moved.currentPositions.getValue("files").y + 150f <= 500f)
    }

    @Test fun cancelRestoresExactOriginalPositions() {
        val tx = infinityBeginDesktopIconDrag(3, linkedMapOf(
            "files" to Offset(20f, 130f), "notes" to Offset(150f, 130f)
        ), "files")!!
        val moved = infinityMoveDesktopIconDrag(tx, Offset(70f, 100f), 500f, 500f)
        assertNotEquals(tx.originalPositions, moved.currentPositions)
        assertEquals(tx.originalPositions, infinityCancelDesktopIconDrag(moved))
    }

    @Test fun commitResolvesCollisionsAndKeepsIconsInsideDockSafeArea() {
        val tx = infinityBeginDesktopIconDrag(1, linkedMapOf(
            "files" to Offset(20f, 130f), "notes" to Offset(20f, 130f), "orbit" to Offset(20f, 130f)
        ), "files")!!
        val committed = infinityCommitDesktopIconDrag(tx, listOf("files", "notes", "orbit"), 500f, 600f, 108f, 150f, 12f, 14f, 118f)
        assertEquals(3, committed.size)
        assertEquals(3, committed.values.toSet().size)
        assertTrue(committed.values.all { it.y >= 118f && it.y + 150f <= 600f })
    }

    @Test fun unknownIconCannotStartDrag() {
        assertNull(infinityBeginDesktopIconDrag(1, mapOf("files" to Offset.Zero), "missing"))
    }

    @Test fun persistedCoordinatesUseWorkspaceSpecificKeysForBothAxes() {
        val positions = linkedMapOf("files" to Offset(12f, 34f), "orbit" to Offset(56f, 78f))
        val one = infinityWorkspaceIconPositionPreferences(1, positions)
        val two = infinityWorkspaceIconPositionPreferences(2, positions)
        assertEquals(4, one.size)
        assertEquals(12f, one["workspace_1_files_x"])
        assertEquals(34f, one["workspace_1_files_y"])
        assertEquals(56f, two["workspace_2_orbit_x"])
        assertNotEquals(one.keys, two.keys)
    }
}
