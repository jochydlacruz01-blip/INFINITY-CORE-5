package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InfinityWorkspaceFocusMemoryTest {
    @Test fun focusPreferenceKeysAreStableAndWorkspaceScoped() {
        assertEquals("focused_window_id_workspace_1", infinityWorkspaceFocusPreferenceKey(1))
        assertEquals("focused_window_id_workspace_2", infinityWorkspaceFocusPreferenceKey(2))
        assertEquals("focused_window_id_workspace_3", infinityWorkspaceFocusPreferenceKey(99))
    }

    @Test fun rememberedFocusRestoresPerWorkspaceWithoutLeakingAcrossSpaces() {
        val order = listOf("files_1", "notes_1", "files_2", "apps_2")
        val workspaceById = mapOf("files_1" to 1, "notes_1" to 1, "files_2" to 2, "apps_2" to 2)
        assertEquals("files_2", infinityResolveRememberedWorkspaceFocus(
            "files_2", order, workspaceById, emptySet(), 2
        ))
        assertEquals("notes_1", infinityResolveRememberedWorkspaceFocus(
            "files_2", order, workspaceById, emptySet(), 1
        ))
    }

    @Test fun staleOrMinimizedRememberedFocusFallsBackOnlyInsideTargetWorkspace() {
        val order = listOf("files_1", "notes_1", "files_2")
        val workspaceById = mapOf("files_1" to 1, "notes_1" to 1, "files_2" to 2)
        assertEquals("notes_1", infinityResolveRememberedWorkspaceFocus(
            "files_1", order, workspaceById, setOf("files_1"), 1
        ))
        assertNull(infinityResolveRememberedWorkspaceFocus(
            "files_1", order, workspaceById, setOf("files_1", "notes_1"), 1
        ))
    }
}
