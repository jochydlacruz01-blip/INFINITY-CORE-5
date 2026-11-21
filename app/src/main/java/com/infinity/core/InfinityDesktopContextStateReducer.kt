package com.infinity.core

/** Immutable desktop preference state for context-menu commands; UI applies returned state. */
internal data class InfinityDesktopContextState(
    val arrangement: InfinityIconArrangement = InfinityIconArrangement.MANUAL,
    val iconsVisible: Boolean = true,
    val iconSizePx: Float = 48f,
    val refreshToken: Long = 0L
)

/** Sanitizes persisted values before applying desktop commands. */
internal fun infinityNormalizeDesktopContextState(state: InfinityDesktopContextState): InfinityDesktopContextState =
    state.copy(
        iconSizePx = state.iconSizePx.takeIf { it.isFinite() }?.coerceIn(24f, 96f) ?: 48f,
        refreshToken = state.refreshToken.coerceAtLeast(0L)
    )

/** Pure reducer to keep desktop context-menu transitions testable and side-effect free. */
internal fun infinityReduceDesktopContextState(
    state: InfinityDesktopContextState,
    action: InfinityDesktopContextAction,
    iconSizeStep: Float = 4f
): InfinityDesktopContextState {
    val safe = infinityNormalizeDesktopContextState(state)
    return when (action) {
    InfinityDesktopContextAction.SORT_NAME -> safe.copy(arrangement = InfinityIconArrangement.NAME)
    InfinityDesktopContextAction.SORT_TYPE -> safe.copy(arrangement = InfinityIconArrangement.TYPE)
    InfinityDesktopContextAction.SORT_MANUAL -> safe.copy(arrangement = InfinityIconArrangement.MANUAL)
    InfinityDesktopContextAction.ALIGN_GRID -> safe.copy(arrangement = InfinityIconArrangement.GRID)
    InfinityDesktopContextAction.SHOW_ICONS -> safe.copy(iconsVisible = true)
    InfinityDesktopContextAction.HIDE_ICONS -> safe.copy(iconsVisible = false)
    InfinityDesktopContextAction.REFRESH_LAYOUT -> safe.copy(refreshToken = if (safe.refreshToken == Long.MAX_VALUE) Long.MAX_VALUE else safe.refreshToken + 1L)
    InfinityDesktopContextAction.ICON_SIZE -> {
        val step = iconSizeStep.takeIf { it.isFinite() && it > 0f } ?: 4f
        safe.copy(iconSizePx = (safe.iconSizePx + step).coerceIn(24f, 96f))
    }
    InfinityDesktopContextAction.DESKTOP_APPEARANCE,
    InfinityDesktopContextAction.WALLPAPER,
    InfinityDesktopContextAction.WORKSPACE -> safe
    }
}

