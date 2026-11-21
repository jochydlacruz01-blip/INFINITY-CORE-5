package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityDesktopContextCommandPlanTest {
    private val records = listOf(
        InfinityDesktopIconRecord("z", "Zeta", "app", 2),
        InfinityDesktopIconRecord("a", "Alpha", "folder", 0),
        InfinityDesktopIconRecord("b", "Beta", "app", 1)
    )

    @Test fun sortCommandsProduceUniquePlacementMapAndState() {
        val plan = infinityPlanDesktopContextCommand(InfinityDesktopContextState(), InfinityDesktopContextAction.SORT_NAME, records, 640f, 100f, 120f)
        assertEquals(InfinityIconArrangement.NAME, plan.nextState.arrangement)
        assertEquals(listOf("a", "b", "z"), plan.placements?.keys?.toList())
        assertEquals(3, plan.placements?.values?.distinct()?.size)
    }

    @Test fun refreshUsesGridPlacementWithoutChangingArrangement() {
        val state = InfinityDesktopContextState(arrangement = InfinityIconArrangement.TYPE, refreshToken = 3)
        val plan = infinityPlanDesktopContextCommand(state, InfinityDesktopContextAction.REFRESH_LAYOUT, records, 320f, 100f, 120f)
        assertEquals(InfinityIconArrangement.TYPE, plan.nextState.arrangement)
        assertEquals(4L, plan.nextState.refreshToken)
        assertEquals(3, plan.placements?.size)
    }

    @Test fun nonPlacementCommandsAreExplicitSideEffectRequests() {
        val wallpaper = infinityPlanDesktopContextCommand(InfinityDesktopContextState(), InfinityDesktopContextAction.WALLPAPER, records, 400f, 100f, 120f)
        assertTrue(wallpaper.requestWallpaperPicker)
        assertNull(wallpaper.placements)
        val appearance = infinityPlanDesktopContextCommand(InfinityDesktopContextState(), InfinityDesktopContextAction.DESKTOP_APPEARANCE, records, 400f, 100f, 120f)
        assertTrue(appearance.requestAppearancePanel)
        val workspace = infinityPlanDesktopContextCommand(InfinityDesktopContextState(), InfinityDesktopContextAction.WORKSPACE, records, 400f, 100f, 120f)
        assertTrue(workspace.requestWorkspacePanel)
    }

    @Test fun duplicateIdsRemainDeduplicatedAndNarrowLayoutDoesNotCrash() {
        val duplicates = records + records.first()
        val plan = infinityPlanDesktopContextCommand(InfinityDesktopContextState(), InfinityDesktopContextAction.ALIGN_GRID, duplicates, 160f, 100f, 120f)
        assertEquals(3, plan.placements?.size)
    }
}
