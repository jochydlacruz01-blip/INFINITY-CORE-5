package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityOrbitWorkspacePresentationTest {
    @Test fun workspaceNamesAreStableAndDistinct() {
        assertEquals("Principal", infinityWorkspaceDisplayName(1))
        assertEquals("Trabajo", infinityWorkspaceDisplayName(2))
        assertEquals("Personal", infinityWorkspaceDisplayName(3))
    }
    @Test fun outOfRangeWorkspaceUsesNormalizedName() {
        assertEquals("Principal", infinityWorkspaceDisplayName(0))
        assertEquals("Personal", infinityWorkspaceDisplayName(99))
    }
    @Test fun selectorAccessibilityIncludesNumberAndName() {
        assertEquals("Espacio 1, Principal", infinityWorkspaceAccessibilityLabel(1))
        assertEquals("Espacio 2, Trabajo", infinityWorkspaceAccessibilityLabel(2))
        assertEquals("Espacio 3, Personal", infinityWorkspaceAccessibilityLabel(3))
        assertEquals("Espacio 1, Principal", infinityWorkspaceAccessibilityLabel(-1))
    }
    @Test fun activeWorkspacePreferenceKeyRemainsCompatible() {
        assertEquals("active_workspace", infinityActiveWorkspacePreferenceKey())
    }
}
