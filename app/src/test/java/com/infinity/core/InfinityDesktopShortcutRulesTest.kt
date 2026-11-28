package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityDesktopShortcutRulesTest {
    @Test fun shortcutListsParseSerializeAndDeduplicateDeterministically() {
        val parsed = infinityParseDesktopShortcutPackages(" com.alpha.app \n\ncom.beta.app\ncom.alpha.app ")
        assertEquals(listOf("com.alpha.app", "com.beta.app"), parsed)
        assertEquals("com.alpha.app\ncom.beta.app", infinitySerializeDesktopShortcutPackages(parsed))
        assertEquals(emptyList<String>(), infinityParseDesktopShortcutPackages(null))
    }

    @Test fun normalizationRemovesUninstalledPackagesWithoutReordering() {
        assertEquals(
            listOf("com.beta.app", "com.alpha.app"),
            infinityNormalizeDesktopShortcutPackages(
                listOf("com.beta.app", "com.missing.app", "com.alpha.app", "com.beta.app"),
                setOf("com.alpha.app", "com.beta.app")
            )
        )
    }

    @Test fun toggleAddsOnlyInstalledPackagesAndRemovesExistingShortcuts() {
        val installed = setOf("com.alpha.app", "com.beta.app")
        assertEquals(
            listOf("com.alpha.app", "com.beta.app"),
            infinityToggleDesktopShortcut(listOf("com.alpha.app"), "com.beta.app", installed)
        )
        assertEquals(
            listOf("com.alpha.app"),
            infinityToggleDesktopShortcut(listOf("com.alpha.app", "com.beta.app"), "com.beta.app", installed)
        )
        assertEquals(
            listOf("com.alpha.app"),
            infinityToggleDesktopShortcut(listOf("com.alpha.app"), "com.missing.app", installed)
        )
    }

    @Test fun workspaceKeysAndShortcutIdsAreStableAndDistinct() {
        assertNotEquals(infinityWorkspaceDesktopShortcutKey(1), infinityWorkspaceDesktopShortcutKey(2))
        val alphaId = infinityDesktopShortcutId("com.alpha.app")
        assertTrue(alphaId.startsWith("app_"))
        assertEquals(alphaId, infinityDesktopShortcutId("com.alpha.app"))
        assertNotEquals(alphaId, infinityDesktopShortcutId("com.beta.app"))
    }
}
