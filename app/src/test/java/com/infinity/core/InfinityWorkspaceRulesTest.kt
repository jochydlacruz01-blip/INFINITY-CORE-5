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

}

    @Test fun dockActivationRestoresMinimizedAndActivatesUnfocusedTasks() {
        assertEquals("files_1", infinityDockResolveActivation("files_1", "notes_1", true))
        assertEquals("files_1", infinityDockResolveActivation("files_1", "notes_1", false))
        assertNull(infinityDockResolveActivation("files_1", "files_1", false))
    }
