package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityDesktopContextStateReducerTest {
    @Test fun visibilityToggleOffersOppositeActionAndChangesState() {
        val hidden = infinityReduceDesktopContextState(InfinityDesktopContextState(), InfinityDesktopContextAction.HIDE_ICONS)
        assertFalse(hidden.iconsVisible)
        assertTrue(infinityDesktopContextActions(hidden.iconsVisible).contains(InfinityDesktopContextAction.SHOW_ICONS))
        assertFalse(infinityDesktopContextActions(hidden.iconsVisible).contains(InfinityDesktopContextAction.HIDE_ICONS))
    }

    @Test fun sortCommandsChangeArrangementOnly() {
        val initial = InfinityDesktopContextState()
        val sorted = infinityReduceDesktopContextState(initial, InfinityDesktopContextAction.SORT_NAME)
        assertEquals(InfinityIconArrangement.NAME, sorted.arrangement)
        assertEquals(initial.iconsVisible, sorted.iconsVisible)
        assertEquals(initial.iconSizePx, sorted.iconSizePx)
    }

    @Test fun refreshTokenIncrementsAndDoesNotOverflowBehaviorDependOnUi() {
        val refreshed = infinityReduceDesktopContextState(InfinityDesktopContextState(refreshToken = 8), InfinityDesktopContextAction.REFRESH_LAYOUT)
        assertEquals(9L, refreshed.refreshToken)
    }

    @Test fun iconSizeIsBoundedAndInvalidStepFallsBack() {
        val large = infinityReduceDesktopContextState(InfinityDesktopContextState(iconSizePx = 96f), InfinityDesktopContextAction.ICON_SIZE)
        assertEquals(96f, large.iconSizePx)
        val small = infinityReduceDesktopContextState(InfinityDesktopContextState(iconSizePx = 24f), InfinityDesktopContextAction.ICON_SIZE, Float.NaN)
        assertEquals(28f, small.iconSizePx)
    }

    @Test fun appearanceAndWallpaperActionsDoNotMutateReducerState() {
        val initial = InfinityDesktopContextState()
        assertEquals(initial, infinityReduceDesktopContextState(initial, InfinityDesktopContextAction.WALLPAPER))
        assertEquals(initial, infinityReduceDesktopContextState(initial, InfinityDesktopContextAction.DESKTOP_APPEARANCE))
    }
}
