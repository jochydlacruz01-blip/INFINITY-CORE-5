package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityAuroraWindowManagerTest {
    private fun windows() = mutableListOf(
        DeskWindow("files-w1", "Files", "files", 10f, 20f, workspace = 1),
        DeskWindow("notes-w1", "Notes", "notes", 30f, 40f, workspace = 1, minimized = true),
        DeskWindow("notes-w2", "Notes", "notes", 50f, 60f, workspace = 2)
    )

    @Test fun managerMutatesTheSingleBackingCollectionAndRaisesFocusedWindow() {
        val backing = windows()
        val manager = InfinityAuroraWindowManager(backing)
        assertTrue(manager.restore("notes-w1"))
        assertEquals(listOf("files-w1", "notes-w2", "notes-w1"), backing.map { it.id })
        assertFalse(backing.last().minimized)
    }

    @Test fun minimizeAndRestorePreserveGeometryAndWorkspace() {
        val backing = windows()
        val manager = InfinityAuroraWindowManager(backing)
        assertTrue(manager.restore("notes-w1"))
        val restored = backing.first { it.id == "notes-w1" }
        assertTrue(manager.minimize("notes-w1"))
        assertEquals(restored.copy(minimized = true), backing.first { it.id == "notes-w1" })
        assertTrue(manager.restore("notes-w1"))
        assertEquals(restored, backing.first { it.id == "notes-w1" })
    }

    @Test fun maximizeToggleDoesNotAffectOtherWindows() {
        val backing = windows()
        val manager = InfinityAuroraWindowManager(backing)
        assertTrue(manager.toggleMaximized("files-w1"))
        assertTrue(backing.first { it.id == "files-w1" }.maximized)
        assertFalse(backing.first { it.id == "notes-w2" }.maximized)
        assertTrue(manager.toggleMaximized("files-w1"))
        assertFalse(backing.first { it.id == "files-w1" }.maximized)
    }

    @Test fun closeRemovesOnlyTheRequestedWindow() {
        val backing = windows()
        val manager = InfinityAuroraWindowManager(backing)
        assertTrue(manager.close("notes-w1"))
        assertEquals(listOf("files-w1", "notes-w2"), backing.map { it.id })
        assertFalse(manager.close("missing"))
    }

    @Test fun minimizeAllHonorsRequestedIdsAcrossWorkspaces() {
        val backing = windows()
        val manager = InfinityAuroraWindowManager(backing)
        assertEquals(2, manager.minimizeAll(setOf("files-w1", "notes-w2")))
        assertTrue(backing.all { it.minimized })
        assertEquals(0, manager.minimizeAll(setOf("files-w1", "notes-w2")))
    }

    @Test fun workspaceQueryNeverLeaksWindowsFromAnotherSpace() {
        val manager = InfinityAuroraWindowManager(windows())
        assertEquals(listOf("files-w1", "notes-w1"), manager.windowsInWorkspace(1).map { it.id })
        assertEquals(listOf("notes-w2"), manager.windowsInWorkspace(2).map { it.id })
        assertTrue(manager.windowsInWorkspace(3).isEmpty())
    }
}
