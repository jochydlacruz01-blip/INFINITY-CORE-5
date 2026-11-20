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
}

