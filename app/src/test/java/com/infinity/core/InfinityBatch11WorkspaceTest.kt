package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityBatch11WorkspaceTest {
    @Test fun dockTaskLimitPreservesPrimaryControlsAcrossWidths() {
        assertEquals(1, infinityDockVisibleTaskLimit(390f))
        assertEquals(1, infinityDockVisibleTaskLimit(599.9f))
        assertEquals(2, infinityDockVisibleTaskLimit(600f))
        assertEquals(2, infinityDockVisibleTaskLimit(719.9f))
        assertEquals(3, infinityDockVisibleTaskLimit(720f))
        assertEquals(3, infinityDockVisibleTaskLimit(959.9f))
        assertEquals(5, infinityDockVisibleTaskLimit(960f))
    }

    @Test fun dockTaskLimitHandlesInvalidWidthsSafely() {
        assertEquals(1, infinityDockVisibleTaskLimit(Float.NaN))
        assertEquals(1, infinityDockVisibleTaskLimit(Float.POSITIVE_INFINITY))
        assertEquals(1, infinityDockVisibleTaskLimit(-1f))
    }

    @Test fun dockOverflowCountIsNeverNegative() {
        assertEquals(0, infinityDockHiddenTaskCount(0, 0))
        assertEquals(0, infinityDockHiddenTaskCount(3, 5))
        assertEquals(2, infinityDockHiddenTaskCount(7, 5))
        assertEquals(4, infinityDockHiddenTaskCount(4, 0))
        assertEquals(0, infinityDockHiddenTaskCount(-3, 2))
    }

    @Test fun maximizedAndPhoneWindowsCannotMutateDesktopGeometry() {
        assertEquals(false, infinityWindowCanManipulateGeometry(true, 1280f))
        assertEquals(false, infinityWindowCanManipulateGeometry(false, 599.9f))
        assertEquals(true, infinityWindowCanManipulateGeometry(false, 600f))
        assertEquals(true, infinityWindowCanManipulateGeometry(false, 1024f))
        assertEquals(false, infinityWindowCanManipulateGeometry(false, Float.NaN))
        assertEquals(false, infinityWindowCanManipulateGeometry(false, Float.POSITIVE_INFINITY))
    }

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
