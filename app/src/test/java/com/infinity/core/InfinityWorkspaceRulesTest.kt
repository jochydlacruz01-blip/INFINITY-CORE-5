package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InfinityWorkspaceRulesTest {
    @Test fun focusedWindowPersistenceKeyIsIndependentAndNormalizesWorkspace() {
        assertEquals("workspace_1_focused_window_id", infinityWorkspaceFocusedWindowKey(1))
        assertEquals("workspace_2_focused_window_id", infinityWorkspaceFocusedWindowKey(2))
        assertEquals("workspace_3_focused_window_id", infinityWorkspaceFocusedWindowKey(3))
        assertNotEquals(infinityWorkspaceFocusedWindowKey(1), infinityWorkspaceFocusedWindowKey(2))
        assertEquals(infinityWorkspaceFocusedWindowKey(1), infinityWorkspaceFocusedWindowKey(0))
        assertEquals(infinityWorkspaceFocusedWindowKey(3), infinityWorkspaceFocusedWindowKey(9))
    }

    @Test fun canonicalizationTrimsAndLowercases() {
        assertEquals("file_manager", infinityCanonicalWindowKind(" File_Manager "))
        assertEquals("orbit", infinityCanonicalWindowKind("ORBIT"))
    }

    @Test fun canonicalizationRemovesUnsupportedIdentityCharactersAndBoundsLength() {
        assertEquals("filemanager", infinityCanonicalWindowKind("file manager!"))
        assertEquals(64, infinityCanonicalWindowKind("x".repeat(80))!!.length)
    }

    @Test fun blankAndNullKindsAreRejected() {
        assertNull(infinityCanonicalWindowKind(null))
        assertNull(infinityCanonicalWindowKind("   "))
        assertNull(infinitySafeWindowIdentity("!!!", 2))
    }

    @Test fun safeIdentityUsesNormalizedWorkspaceAndCanonicalKind() {
        assertEquals("orbit_1", infinitySafeWindowIdentity(" ORBIT ", 0))
        assertEquals("file_manager_3", infinitySafeWindowIdentity("File_Manager", 9))
    }

    @Test fun restoredWindowIdsAlwaysMatchCanonicalKindAndWorkspace() {
        assertEquals("files_1", infinityNormalizeRestoredWindowId("files_1", "files", 1))
        assertEquals("files_2", infinityNormalizeRestoredWindowId("legacy-window-42", "files", 2))
        assertEquals("notes_3", infinityNormalizeRestoredWindowId(null, "notes", 3))
        assertEquals("connect_1", infinityNormalizeRestoredWindowId("files_1", "connect", 1))
    }
    @Test fun focusResolverKeepsValidFocusOrFallsBackToMostRecent() {
        assertEquals("b", infinityResolveFocusedWindow("b", listOf("a", "b")))
        assertEquals("c", infinityResolveFocusedWindow("missing", listOf("a", "c")))
        assertEquals("a", infinityResolveFocusedWindow(null, listOf("a")))
        assertNull(infinityResolveFocusedWindow("x", emptyList()))
    }
    @Test fun dockWindowListFiltersUnknownIdsAndPreservesOrder() {
        assertEquals(listOf("files_1", "notes_1"), infinityDockWindowIds(
            listOf("files_1", "notes_1", "bad"),
            mapOf("files_1" to false, "notes_1" to true)
        ))
        assertEquals(emptyList<String>(), infinityDockWindowIds(emptyList(), emptyMap()))
    }
    @Test fun dockTapMinimizesFocusedTaskButRestoresOtherTasks() {
        assertEquals(true, infinityDockTapMinimizes("files_1", "files_1", false))
        assertEquals(false, infinityDockTapMinimizes("files_1", "notes_1", false))
        assertEquals(false, infinityDockTapMinimizes("files_1", "files_1", true))
    }

    @Test fun dockShowsMostRecentlyRaisedTasksAndCountsOverflow() {
        val orderedByAURORAZOrder = listOf("files_1", "apps_1", "notes_1", "connect_1")
        val visible = infinityDockVisibleTaskIds(orderedByAURORAZOrder, 2)
        assertEquals(listOf("notes_1", "connect_1"), visible)
        assertEquals(2, infinityDockHiddenTaskCount(orderedByAURORAZOrder.size, visible.size))
        assertEquals(emptyList<String>(), infinityDockVisibleTaskIds(orderedByAURORAZOrder, 0))
    }

    @Test fun dockTaskLimitAdaptsToAvailableViewportWidth() {
        assertEquals(1, infinityDockVisibleTaskLimit(599f))
        assertEquals(2, infinityDockVisibleTaskLimit(600f))
        assertEquals(3, infinityDockVisibleTaskLimit(720f))
        assertEquals(5, infinityDockVisibleTaskLimit(960f))
        assertEquals(1, infinityDockVisibleTaskLimit(Float.NaN))
    }

    @Test fun windowSnapshotKeyTracksAllPersistedStateAndZOrder() {
        val files = InfinityWindowSnapshotEntry(
            "files_1", "Explorador", "files", 10f, 20f, 370f, 330f,
            minimized = false, workspace = 1, maximized = false
        )
        val notes = files.copy(id = "notes_1", title = "Notas", kind = "notes")
        val baseline = infinityWindowSnapshotKey(listOf(files, notes))

        assertNotEquals(baseline, infinityWindowSnapshotKey(listOf(notes, files)))
        assertNotEquals(baseline, infinityWindowSnapshotKey(listOf(files.copy(title = "Archivos"), notes)))
        assertNotEquals(baseline, infinityWindowSnapshotKey(listOf(files.copy(x = 11f), notes)))
        assertNotEquals(baseline, infinityWindowSnapshotKey(listOf(files.copy(minimized = true), notes)))
        assertNotEquals(baseline, infinityWindowSnapshotKey(listOf(files.copy(workspace = 2), notes)))
        assertNotEquals(baseline, infinityWindowSnapshotKey(listOf(files.copy(maximized = true), notes)))
    }

    @Test fun emptyWindowSnapshotKeyIsStable() {
        assertEquals("", infinityWindowSnapshotKey(emptyList()))
    }

    @Test fun windowGeometryNormalizationIsDisabledOnPhoneAndEnabledOnDesktopWidths() {
        assertEquals(false, infinityWindowGeometryNormalizationEnabled(599.99f))
        assertEquals(true, infinityWindowGeometryNormalizationEnabled(600f))
        assertEquals(true, infinityWindowGeometryNormalizationEnabled(840f))
        assertEquals(false, infinityWindowGeometryNormalizationEnabled(Float.NaN))
        assertEquals(false, infinityWindowGeometryNormalizationEnabled(Float.POSITIVE_INFINITY))
    }

    @Test fun geometryControlsAndManipulationShareTheSameViewportBreakpoint() {
        assertEquals(false, infinityWindowGeometryControlsVisible(599f))
        assertEquals(true, infinityWindowGeometryControlsVisible(600f))
        assertEquals(false, infinityWindowCanManipulateGeometry(false, 599f))
        assertEquals(true, infinityWindowCanManipulateGeometry(false, 600f))
        assertEquals(false, infinityWindowCanManipulateGeometry(true, 800f))
    }

    @Test fun dockLabelsNormalizeWhitespaceAndTruncateSafely() {
        assertEquals("ARCHIVOS", infinityDockLabel(" Archivos "))
        assertEquals("INFINITY…", infinityDockLabel("INFINITY CONNECT", 9))
        assertEquals("A…", infinityDockLabel("Alphabet", 2))
        assertEquals("", infinityDockLabel("   "))
    }

    @Test fun workspaceWindowFilterNormalizesWorkspaceAndPreservesOrder() {
        assertEquals(listOf("b", "d"), infinityWorkspaceWindowIds(listOf("a", "b", "c", "d"), mapOf("a" to 1, "b" to 2, "c" to 3, "d" to 2), 2))
        assertEquals(listOf("a"), infinityWorkspaceWindowIds(listOf("a", "b"), mapOf("a" to 0, "b" to 3), 1))
        assertEquals(emptyList<String>(), infinityWorkspaceWindowIds(listOf("x"), emptyMap(), 3))
    }
    @Test fun workspaceWindowCountIgnoresWindowsInOtherWorkspaces() {
        assertEquals(
            2,
            infinityWorkspaceWindowCount(
                listOf("notes_1", "files_2", "apps_2", "connect_3"),
                mapOf("notes_1" to 1, "files_2" to 2, "apps_2" to 2, "connect_3" to 3),
                2
            )
        )
        assertEquals(
            0,
            infinityWorkspaceWindowCount(
                listOf("notes_1"), mapOf("notes_1" to 1), 3
            )
        )
    }

    @Test fun minimizeAllTargetsOnlyTheActiveWorkspace() {
        assertEquals(
            setOf("notes_2", "files_2"),
            infinityWorkspaceMinimizeTargets(
                listOf("notes_1", "notes_2", "files_2", "apps_3"),
                mapOf("notes_1" to 1, "notes_2" to 2, "files_2" to 2, "apps_3" to 3),
                2
            )
        )
        assertEquals(
            emptySet<String>(),
            infinityWorkspaceMinimizeTargets(listOf("notes_1"), mapOf("notes_1" to 1), 3)
        )
    }

    @Test fun workspaceFocusExcludesMinimizedAndOtherWorkspaceWindows() {
        assertEquals("c", infinityResolveWorkspaceFocus(
            "b", listOf("a", "b", "c", "d"),
            mapOf("a" to 1, "b" to 2, "c" to 2, "d" to 3),
            setOf("b"), 2
        ))
        assertEquals("a", infinityResolveWorkspaceFocus(
            "missing", listOf("a", "b"),
            mapOf("a" to 1, "b" to 2), emptySet(), 1
        ))
        assertNull(infinityResolveWorkspaceFocus(
            null, listOf("a"), mapOf("a" to 1), setOf("a"), 1
        ))
    }


    @Test fun dockActivationRestoresMinimizedAndActivatesUnfocusedTasks() {
        assertEquals("files_1", infinityDockResolveActivation("files_1", "notes_1", true))
        assertEquals("files_1", infinityDockResolveActivation("files_1", "notes_1", false))
        assertNull(infinityDockResolveActivation("files_1", "files_1", false))
    }

    @Test fun workspaceSwitchFocusRetainsOnlyEligibleTargetWorkspaceWindow() {
        assertEquals("b", infinityResolveWorkspaceSwitchFocus("b", listOf("a", "b", "c"), mapOf("a" to 1, "b" to 2, "c" to 2), emptySet(), 2))
        assertEquals("c", infinityResolveWorkspaceSwitchFocus("a", listOf("a", "b", "c"), mapOf("a" to 1, "b" to 2, "c" to 2), setOf("b"), 2))
        assertNull(infinityResolveWorkspaceSwitchFocus(null, listOf("a"), mapOf("a" to 2), setOf("a"), 2))
    }

    @Test fun persistedWindowCoordinatesRecoverFromInvalidAndExtremeValues() {
        assertEquals(24f, infinityNormalizeWindowCoordinate(Float.NaN, 24f), 0f)
        assertEquals(100f, infinityNormalizeWindowCoordinate(Float.POSITIVE_INFINITY, 100f), 0f)
        assertEquals(-500f, infinityNormalizeWindowCoordinate(-9000f, 24f), 0f)
        assertEquals(5000f, infinityNormalizeWindowCoordinate(9000f, 24f), 0f)
        assertEquals(0f, infinityNormalizeWindowCoordinate(Float.NEGATIVE_INFINITY, Float.NaN), 0f)
    }

    @Test fun persistedWindowDimensionsStayWithinSupportedBounds() {
        assertEquals(370f, infinityNormalizeWindowDimension(Float.NaN, 370f, 240f, 900f), 0f)
        assertEquals(240f, infinityNormalizeWindowDimension(10f, 370f, 240f, 900f), 0f)
        assertEquals(900f, infinityNormalizeWindowDimension(1200f, 370f, 240f, 900f), 0f)
        assertEquals(330f, infinityNormalizeWindowDimension(Float.POSITIVE_INFINITY, 330f, 180f, 900f), 0f)
    }

    @Test fun closingFocusedWindowMovesFocusToMostRecentVisibleWindowInSameWorkspace() {
        assertEquals(
            "notes_1",
            infinityResolveFocusAfterWindowDeactivation(
                "files_1", "files_1",
                listOf("files_1", "notes_1", "apps_2"),
                mapOf("files_1" to 1, "notes_1" to 1, "apps_2" to 2),
                emptySet(), 1
            )
        )
    }

    @Test fun minimizingFocusedWindowMovesFocusAndMinimizingAnotherPreservesFocus() {
        assertEquals(
            "notes_1",
            infinityResolveFocusAfterWindowDeactivation(
                "files_1", "files_1",
                listOf("files_1", "notes_1"),
                mapOf("files_1" to 1, "notes_1" to 1),
                setOf("files_1"), 1
            )
        )
        assertEquals(
            "files_1",
            infinityResolveFocusAfterWindowDeactivation(
                "notes_1", "files_1",
                listOf("files_1", "notes_1"),
                mapOf("files_1" to 1, "notes_1" to 1),
                setOf("notes_1"), 1
            )
        )
    }

    @Test fun closingWindowPreservesOtherValidFocusAndNeverFocusesMinimizedTasks() {
        assertEquals(
            "files_1",
            infinityResolveFocusAfterWindowDeactivation(
                "notes_1", "files_1",
                listOf("files_1", "notes_1"),
                mapOf("files_1" to 1, "notes_1" to 1),
                emptySet(), 1
            )
        )
        assertNull(
            infinityResolveFocusAfterWindowDeactivation(
                "files_1", "files_1", listOf("files_1"),
                mapOf("files_1" to 1), setOf("files_1"), 1
            )
        )
    }
    @Test fun restoredFocusUsesSavedVisibleTaskOrFallsBackWithinActiveWorkspace() {
        val order = listOf("files_1", "notes_1", "apps_2")
        val workspaces = mapOf("files_1" to 1, "notes_1" to 1, "apps_2" to 2)
        assertEquals("files_1", infinityResolveRestoredWindowFocus(
            "files_1", order, workspaces, emptySet(), 1
        ))
        assertEquals("notes_1", infinityResolveRestoredWindowFocus(
            "stale_window", order, workspaces, emptySet(), 1
        ))
        assertEquals("notes_1", infinityResolveRestoredWindowFocus(
            "files_1", order, workspaces, setOf("files_1"), 1
        ))
        assertEquals("apps_2", infinityResolveRestoredWindowFocus(
            null, order, workspaces, emptySet(), 2
        ))
        assertNull(infinityResolveRestoredWindowFocus(
            null, order, workspaces, setOf("apps_2"), 2
        ))
    }
    @Test fun malformedWindowSnapshotsUseFreeRecoverySlotsWithoutOverwritingEarlierBackups() {
        assertEquals("saved_windows_recovery_backup", infinityNextWindowRecoveryBackupKey(emptySet()))
        assertEquals(
            "saved_windows_recovery_backup_2",
            infinityNextWindowRecoveryBackupKey(setOf("saved_windows_recovery_backup"))
        )
        assertEquals(
            "saved_windows_recovery_backup_3",
            infinityNextWindowRecoveryBackupKey(
                setOf("saved_windows_recovery_backup", "saved_windows_recovery_backup_2")
            )
        )
        assertEquals(
            null,
            infinityNextWindowRecoveryBackupKey(
                (1..3).map { if (it == 1) "saved_windows_recovery_backup" else "saved_windows_recovery_backup_$it" }.toSet(),
                maxBackups = 3
            )
        )
    }


}