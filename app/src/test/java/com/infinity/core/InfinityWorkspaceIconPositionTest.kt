package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InfinityWorkspaceIconPositionTest {
    @Test fun eachWorkspaceGetsAnIndependentCoordinateKey() {
        assertEquals("workspace_1_files_x", infinityWorkspaceIconPositionKey(1, "files", "x"))
        assertEquals("workspace_2_files_x", infinityWorkspaceIconPositionKey(2, "files", "x"))
        assertNotEquals(
            infinityWorkspaceIconPositionKey(1, "files", "x"),
            infinityWorkspaceIconPositionKey(2, "files", "x")
        )
    }

    @Test fun workspaceAndItemIdentityAreNormalized() {
        assertEquals("workspace_3_file-manager_y", infinityWorkspaceIconPositionKey(8, " File-Manager ", "y"))
        assertEquals("workspace_1_orbit_x", infinityWorkspaceIconPositionKey(0, "ORBIT", "x"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankItemIdentityIsRejected() {
        infinityWorkspaceIconPositionKey(1, " !!! ", "x")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedCoordinateAxisIsRejected() {
        infinityWorkspaceIconPositionKey(1, "files", "z")
    }

    @Test fun legacyKeyRemainsStableForFirstWorkspaceMigration() {
        assertEquals("files_x", infinityLegacyWorkspaceIconPositionKey("files", "x"))
        assertEquals("orbit_y", infinityLegacyWorkspaceIconPositionKey("orbit", "y"))
    }
}
