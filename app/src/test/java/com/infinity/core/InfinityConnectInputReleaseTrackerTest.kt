package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityConnectInputReleaseTrackerTest {
    @Test fun releasesKeysAndButtonsAndClearsState() {
        val tracker = InfinityConnectInputReleaseTracker()
        tracker.keyDown(ConnectKey.CTRL)
        tracker.keyDown(ConnectKey.ENTER)
        tracker.buttonDown(ConnectMouseButton.LEFT)
        tracker.buttonDown(ConnectMouseButton.RIGHT)
        assertTrue(tracker.hasHeldInputs())
        assertEquals(listOf(
            ConnectInputRelease.MouseUp(ConnectMouseButton.RIGHT),
            ConnectInputRelease.MouseUp(ConnectMouseButton.LEFT),
            ConnectInputRelease.KeyUp(ConnectKey.ENTER),
            ConnectInputRelease.KeyUp(ConnectKey.CTRL)
        ), tracker.releaseAll())
        assertFalse(tracker.hasHeldInputs())
        assertTrue(tracker.releaseAll().isEmpty())
    }

    @Test fun releaseOnlyKnownHeldInputs() {
        val tracker = InfinityConnectInputReleaseTracker()
        tracker.keyDown(ConnectKey.SHIFT)
        tracker.keyUp(ConnectKey.SHIFT)
        tracker.buttonDown(ConnectMouseButton.MIDDLE)
        tracker.buttonUp(ConnectMouseButton.MIDDLE)
        assertTrue(tracker.releaseAll().isEmpty())
    }

    @Test fun repeatedDownDoesNotDuplicateRelease() {
        val tracker = InfinityConnectInputReleaseTracker()
        tracker.keyDown(ConnectKey.ESC)
        tracker.keyDown(ConnectKey.ESC)
        assertEquals(listOf(ConnectInputRelease.KeyUp(ConnectKey.ESC)), tracker.releaseAll())
    }
}
