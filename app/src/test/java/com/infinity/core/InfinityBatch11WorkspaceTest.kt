package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityBatch11WorkspaceTest {
    @Test fun workspaceSelectionIsBounded() {
        assertEquals(1, infinityNormalizeWorkspace(Int.MIN_VALUE))
        assertEquals(1, infinityNormalizeWorkspace(0))
        assertEquals(2, infinityNormalizeWorkspace(2))
        assertEquals(3, infinityNormalizeWorkspace(3))
        assertEquals(3, infinityNormalizeWorkspace(Int.MAX_VALUE))
    }

    @Test fun windowIdentityIsStableAndWorkspaceBounded() {
        assertEquals("files_1", infinityWindowIdentity("files", 0))
        assertEquals("files_2", infinityWindowIdentity("files", 2))
        assertEquals("files_3", infinityWindowIdentity("files", 99))
        assertEquals("notes_2", infinityWindowIdentity("notes", 2))
    }
}
