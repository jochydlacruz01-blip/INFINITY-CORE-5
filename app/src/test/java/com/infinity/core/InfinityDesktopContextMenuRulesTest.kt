package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityDesktopContextMenuRulesTest {
    private val icons = listOf(
        InfinityDesktopIconRecord("b", "Notas", "document", 1),
        InfinityDesktopIconRecord("a", "Archivos", "folder", 0),
        InfinityDesktopIconRecord("c", "CONNECT", "app", 2)
    )
    @Test fun nameSortIsDeterministic() {
        assertEquals(listOf("Archivos", "CONNECT", "Notas"),
            infinityArrangeDesktopIcons(icons, InfinityIconArrangement.NAME).map { it.label })
    }
    @Test fun typeSortUsesLabelAsTieBreaker() {
        val mixed = icons + InfinityDesktopIconRecord("d", "Apps", "app", 3)
        assertEquals(listOf("Apps", "CONNECT", "Notas", "Archivos"),
            infinityArrangeDesktopIcons(mixed, InfinityIconArrangement.TYPE).map { it.label })
    }
    @Test fun manualSortPreservesSavedOrderAndUsesIdForTies() {
        val tied = icons + InfinityDesktopIconRecord("z", "Extra", "app", 1)
        assertEquals(listOf("Archivos", "b", "z", "c"),
            infinityArrangeDesktopIcons(tied, InfinityIconArrangement.MANUAL).map { if (it.label == "Notas") it.id else it.id })
    }
    @Test fun equalLabelsUseStableIdTieBreaker() {
        val same = listOf(
            InfinityDesktopIconRecord("z", "App", "app", 0),
            InfinityDesktopIconRecord("a", "app", "app", 1)
        )
        assertEquals(listOf("a", "z"), infinityArrangeDesktopIcons(same, InfinityIconArrangement.NAME).map { it.id })
    }
    @Test fun menuContainsAppearanceAndWorkspaceControls() {
        val menu = infinityDesktopContextActions()
        assertTrue(menu.contains(InfinityDesktopContextAction.DESKTOP_APPEARANCE))
        assertTrue(menu.contains(InfinityDesktopContextAction.WORKSPACE))
        assertTrue(menu.contains(InfinityDesktopContextAction.WALLPAPER))
    }
}
