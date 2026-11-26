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


class InfinityAuroraLegacyMigrationTest {
    @Test fun legacyRecordPreservesIdentityGeometryAndWindowFlags() {
        val migrated = infinityAuroraWindowFromLegacy(
            InfinityLegacyWindowRecord(
                id = "notes-w1", workspace = 1, x = 31f, y = 47f,
                width = 510f, height = 360f, minimized = true, maximized = false, zIndex = 9L
            )
        )
        assertEquals("notes-w1", migrated.id)
        assertEquals(1, migrated.workspace)
        assertEquals(31f, migrated.x)
        assertEquals(47f, migrated.y)
        assertEquals(510f, migrated.width)
        assertEquals(360f, migrated.height)
        assertTrue(migrated.minimized)
        assertTrue(!migrated.maximized)
        assertEquals(9L, migrated.zIndex)
        assertNull(migrated.restoreGeometry)
    }

    @Test fun legacyMaximizedRecordUsesPersistedNormalGeometryForExactRestore() {
        val original = InfinityLegacyWindowRecord(
            id = "files-w2", workspace = 2, x = 23f, y = 61f,
            width = 430f, height = 315f, maximized = true
        )
        val state = InfinityAuroraWindowState(
            windows = listOf(infinityAuroraWindowFromLegacy(original))
        )
        val restored = state.restore("files-w2", 2).windows.single()
        assertEquals(original.x, restored.x)
        assertEquals(original.y, restored.y)
        assertEquals(original.width, restored.width)
        assertEquals(original.height, restored.height)
        assertTrue(!restored.maximized)
        assertNull(restored.restoreGeometry)
    }

    @Test fun maximizeCapturesGeometryOnlyOnceAndRestoreReturnsExactValues() {
        val normal = InfinityAuroraWindow("notes-w1", 1, 37f, 52f, 470f, 340f)
        val state = InfinityAuroraWindowState(windows = listOf(normal))
        val bounds = InfinityAuroraSafeBounds(900f, 700f, 30f, 610f)
        val maximized = state.maximize("notes-w1", 1, bounds)
        val maximizedAgain = maximized.maximize("notes-w1", 1, bounds)
        assertEquals(maximized, maximizedAgain)
        val restored = maximized.restore("notes-w1", 1).windows.single()
        assertEquals(normal.x, restored.x)
        assertEquals(normal.y, restored.y)
        assertEquals(normal.width, restored.width)
        assertEquals(normal.height, restored.height)
        assertTrue(!restored.maximized)
        assertNull(restored.restoreGeometry)
    }

    @Test fun legacyMigrationDropsBlankAndDuplicateIdsButPreservesFirstOccurrence() {
        val first = InfinityLegacyWindowRecord("notes-w1", 1, 10f, 20f, 300f, 240f)
        val duplicate = first.copy(x = 99f)
        val blank = first.copy(id = " ")
        val migrated = infinityAuroraWindowsFromLegacy(listOf(first, duplicate, blank))
        assertEquals(1, migrated.size)
        assertEquals(10f, migrated.single().x)
    }
}


class InfinityAuroraDeskWindowAdapterTest {
    @Test fun desktopAdapterDelegatesPersistentStateToAuroraAndPreservesUiMetadata() {
        val original = DeskWindow(
            id = "notes-w1", title = "Notas rápidas", kind = "notes",
            x = 37f, y = 52f, width = 470f, height = 340f, zIndex = 6L
        )

        val moved = original.copy(x = 91f, y = 108f, zIndex = 12L)

        assertEquals("Notas rápidas", moved.title)
        assertEquals("notes", moved.kind)
        assertEquals(91f, moved.x)
        assertEquals(108f, moved.aurora.y)
        assertEquals(12L, moved.aurora.zIndex)
        assertEquals(original.x, original.aurora.x)
    }

    @Test fun desktopAdapterMaximizeAndRestoreUseAuroraLifecycleAndExactGeometry() {
        val original = DeskWindow(
            id = "files-w2", title = "Archivos", kind = "files",
            x = 23f, y = 61f, width = 430f, height = 315f, workspace = 2, zIndex = 4L
        )
        val bounds = InfinityAuroraSafeBounds(900f, 700f, 30f, 610f)

        val maximized = original.maximize(bounds)
        assertTrue(maximized.maximized)
        assertEquals(InfinityAuroraWindowGeometry(23f, 61f, 430f, 315f), maximized.aurora.restoreGeometry)
        assertEquals(8f, maximized.x)
        assertEquals(38f, maximized.y)

        val restored = maximized.restore()
        assertTrue(!restored.maximized)
        assertNull(restored.aurora.restoreGeometry)
        assertEquals(23f, restored.x)
        assertEquals(61f, restored.y)
        assertEquals(430f, restored.width)
        assertEquals(315f, restored.height)
        assertEquals("Archivos", restored.title)
        assertEquals(2, restored.workspace)
    }

    @Test fun desktopAdapterRejectsLifecycleMutationThroughPresentationCopy() {
        val normal = DeskWindow(
            id = "notes-w1", title = "Notas", kind = "notes",
            x = 20f, y = 30f, width = 400f, height = 280f
        )
        var rejected = false
        try {
            normal.copy(maximized = true)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}


class InfinityAuroraGeometryGesturePolicyTest {
    @Test fun normalWindowAllowsGeometryGestures() {
        assertTrue(infinityAuroraCanChangeGeometry(InfinityAuroraWindow("normal", 1, 10f, 20f, 400f, 300f)))
    }

    @Test fun maximizedWindowCannotBeMovedOrResizedDirectly() {
        val window = InfinityAuroraWindow("maximized", 1, 8f, 40f, 784f, 452f, maximized = true)
        assertTrue(!infinityAuroraCanChangeGeometry(window))
    }

    @Test fun minimizedWindowCannotReceiveGeometryGestures() {
        val window = InfinityAuroraWindow("minimized", 1, 10f, 20f, 400f, 300f, minimized = true)
        assertTrue(!infinityAuroraCanChangeGeometry(window))
    }
}

    @Test fun dockClearanceMatchesCompactPhoneLayout() {
        assertEquals(104f, infinityAuroraDockClearanceDp(390f, "infinity", true))
    }

    @Test fun dockClearanceMatchesWindows11CompactLayout() {
        assertEquals(74f, infinityAuroraDockClearanceDp(390f, "windows11", true))
    }

    @Test fun dockClearanceIncludesTabletReferenceRailPadding() {
        assertEquals(112f, infinityAuroraDockClearanceDp(800f, "infinity", true))
    }

    @Test fun dockClearanceUsesCompactClassicLayoutWhenReferenceModeIsOff() {
        assertEquals(104f, infinityAuroraDockClearanceDp(800f, "infinity", false))
    }


class InfinityAuroraWindowLifecycleTest {
    private fun state() = InfinityAuroraWindowState(
        windows = listOf(
            InfinityAuroraWindow("notes", 1, 10f, 20f, 400f, 300f, zIndex = 2L),
            InfinityAuroraWindow("files", 1, 20f, 30f, 400f, 300f, zIndex = 5L),
            InfinityAuroraWindow("other-space", 2, 30f, 40f, 400f, 300f, zIndex = 7L)
        ),
        activeWindowId = "files",
        nextZIndex = 8L
    )

    @Test fun minimizeSelectsTopRemainingVisibleWindowInSameWorkspace() {
        val minimized = state().minimize("files", 1)
        assertTrue(minimized.windows.first { it.id == "files" }.minimized)
        assertEquals("notes", minimized.activeWindowId)
        assertEquals(3, minimized.windows.size)
    }

    @Test fun activateRaisesAnAlreadyVisibleWindowAboveTheCurrentStack() {
        val activated = state().activate("notes", 1)
        assertEquals("notes", activated.activeWindowId)
        assertEquals(8L, activated.windows.first { it.id == "notes" && it.workspace == 1 }.zIndex)
        assertEquals(9L, activated.nextZIndex)
    }

    @Test fun activateRestoresAndRaisesMinimizedWindow() {
        val minimized = state().minimize("files", 1)
        val activated = minimized.activate("files", 1)
        assertTrue(!activated.windows.first { it.id == "files" }.minimized)
        assertEquals("files", activated.activeWindowId)
        assertEquals(8L, activated.windows.first { it.id == "files" }.zIndex)
        assertEquals(9L, activated.nextZIndex)
    }

    @Test fun closeRemovesOnlyTargetAndSelectsNextVisibleWindow() {
        val closed = state().close("files", 1)
        assertEquals(listOf("notes", "other-space"), closed.windows.map { it.id })
        assertEquals("notes", closed.activeWindowId)
    }

    @Test fun closeOrMinimizeUnknownWindowIsNoOp() {
        val original = state()
        assertEquals(original, original.close("unknown", 1))
        assertEquals(original, original.minimize("unknown", 1))
    }

    @Test fun closingWindowInOtherWorkspaceDoesNotChangeCurrentFocus() {
        val original = state()
        val closed = original.close("other-space", 2)
        assertEquals("files", closed.activeWindowId)
        assertEquals(listOf("notes", "files"), closed.windows.map { it.id })
    }
}
