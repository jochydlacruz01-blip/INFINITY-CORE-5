package com.infinity.core

/** Immutable desktop preference state for context-menu commands; UI applies returned state. */
internal data class InfinityDesktopContextState(
    val arrangement: InfinityIconArrangement = InfinityIconArrangement.MANUAL,
    val iconsVisible: Boolean = true,
    val iconSizePx: Float = 48f,
    val refreshToken: Long = 0L
)

/** Pure reducer to keep desktop context-menu transitions testable and side-effect free. */
internal fun infinityReduceDesktopContextState(
    state: InfinityDesktopContextState,
    action: InfinityDesktopContextAction,
    iconSizeStep: Float = 4f
): InfinityDesktopContextState = when (action) {
    InfinityDesktopContextAction.SORT_NAME -> state.copy(arrangement = InfinityIconArrangement.NAME)
    InfinityDesktopContextAction.SORT_TYPE -> state.copy(arrangement = InfinityIconArrangement.TYPE)
    InfinityDesktopContextAction.SORT_MANUAL -> state.copy(arrangement = InfinityIconArrangement.MANUAL)
    InfinityDesktopContextAction.ALIGN_GRID -> state.copy(arrangement = InfinityIconArrangement.GRID)
    InfinityDesktopContextAction.SHOW_ICONS -> state.copy(iconsVisible = true)
    InfinityDesktopContextAction.HIDE_ICONS -> state.copy(iconsVisible = false)
    InfinityDesktopContextAction.REFRESH_LAYOUT -> state.copy(refreshToken = state.refreshToken + 1L)
    InfinityDesktopContextAction.ICON_SIZE -> {
        val step = iconSizeStep.takeIf { it.isFinite() && it > 0f } ?: 4f
        state.copy(iconSizePx = (state.iconSizePx + step).coerceIn(24f, 96f))
    }
    InfinityDesktopContextAction.DESKTOP_APPEARANCE,
    InfinityDesktopContextAction.WALLPAPER,
    InfinityDesktopContextAction.WORKSPACE -> state
}

