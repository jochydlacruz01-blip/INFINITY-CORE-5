package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InfinityWorkspaceRulesTest {
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
    @Test fun phoneAdaptationDoesNotEnablePersistentWindowGeometryGestures() {
        assertEquals(false, infinityWindowCanManipulateGeometry(false, 412f))
        assertEquals(false, infinityWindowCanManipulateGeometry(false, 599.9f))
        assertEquals(true, infinityWindowCanManipulateGeometry(false, 600f))
        assertEquals(true, infinityWindowCanManipulateGeometry(false, 900f))
        assertEquals(false, infinityWindowCanManipulateGeometry(true, 900f))
        assertEquals(false, infinityWindowCanManipulateGeometry(false, Float.NaN))
    }

    @Test fun dockTapMinimizesFocusedTaskButRestoresOtherTasks() {
        assertEquals(true, infinityDockTapMinimizes("files_1", "files_1", false))
        assertEquals(false, infinityDockTapMinimizes("files_1", "notes_1", false))
        assertEquals(false, infinityDockTapMinimizes("files_1", "files_1", true))
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
    @Test fun orbitMinimizePlanIncludesEveryActiveWorkspaceTaskAndPreservesOtherSpaces() {
        val ordered = listOf("notes_1", "files_2", "apps_2", "connect_3")
        val workspaceById = mapOf("notes_1" to 1, "files_2" to 2, "apps_2" to 2, "connect_3" to 3)
        assertEquals(
            setOf("notes_1", "files_2", "apps_2"),
            infinityWorkspaceMinimizePlan(ordered, workspaceById, setOf("notes_1"), 2)
        )
        assertEquals(
            setOf("notes_1", "connect_3"),
            infinityWorkspaceMinimizePlan(ordered, workspaceById, setOf("notes_1"), 99)
        )
        assertEquals(
            setOf("notes_1"),
            infinityWorkspaceMinimizePlan(ordered, workspaceById, setOf("notes_1"), 0)
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

}