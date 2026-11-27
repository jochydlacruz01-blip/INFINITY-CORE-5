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

class InfinityDesktopIconLayoutMetricsTest {
    @Test fun sevenShortcutsFitPhonePortraitWithoutOverlappingCells() {
        val metrics = infinityDesktopIconLayoutMetrics(360f, 720f, 7, 1f)
        assertEquals(3, ((360f + metrics.horizontalGapPx) / (metrics.itemWidthPx + metrics.horizontalGapPx)).toInt())
        assertTrue(metrics.itemHeightPx * 3f + metrics.verticalGapPx * 2f <= metrics.safeDesktopHeightPx - metrics.topInsetPx + 0.001f)
        val positions = infinityResolveIconOverlaps(
            List(7) { Offset.Zero },
            360f, metrics.safeDesktopHeightPx,
            metrics.itemWidthPx, metrics.itemHeightPx,
            metrics.horizontalGapPx, metrics.verticalGapPx, metrics.topInsetPx
        )
        assertEquals(7, positions.size)
        for (i in positions.indices) for (j in i + 1 until positions.size) {
            val a = positions[i]; val b = positions[j]
            assertTrue(!(a.x < b.x + metrics.itemWidthPx && a.x + metrics.itemWidthPx > b.x &&
                a.y < b.y + metrics.itemHeightPx && a.y + metrics.itemHeightPx > b.y))
        }
    }

    @Test fun compactViewportShrinksCellsInsteadOfPlacingIconsBehindDock() {
        val metrics = infinityDesktopIconLayoutMetrics(320f, 640f, 7, 1f)
        assertTrue(metrics.itemHeightPx < 150f)
        assertTrue(metrics.iconSizePx >= 24f)
        val positions = infinityResolveIconOverlaps(
            List(7) { Offset.Zero },
            320f, metrics.safeDesktopHeightPx,
            metrics.itemWidthPx, metrics.itemHeightPx,
            metrics.horizontalGapPx, metrics.verticalGapPx, metrics.topInsetPx
        )
        assertEquals(7, positions.size)
        assertTrue(positions.all { it.y >= metrics.topInsetPx && it.y + metrics.itemHeightPx <= metrics.safeDesktopHeightPx + 0.001f })
    }
}
