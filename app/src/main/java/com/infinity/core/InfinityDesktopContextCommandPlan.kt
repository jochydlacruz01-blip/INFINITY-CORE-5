package com.infinity.core

/**
 * A UI-independent command plan for safely wiring desktop context commands.
 * The shell owns side effects (preferences, dialogs, launching) and consumes this plan.
 */
internal data class InfinityDesktopContextCommandPlan(
    val nextState: InfinityDesktopContextState,
    val placements: Map<String, androidx.compose.ui.geometry.Offset>? = null,
    val requestWallpaperPicker: Boolean = false,
    val requestAppearancePanel: Boolean = false,
    val requestWorkspacePanel: Boolean = false
)

internal fun infinityPlanDesktopContextCommand(
    state: InfinityDesktopContextState,
    action: InfinityDesktopContextAction,
    icons: List<InfinityDesktopIconRecord>,
    viewportWidth: Float,
    itemWidth: Float,
    itemHeight: Float,
    leftInset: Float = 16f,
    topInset: Float = 118f
): InfinityDesktopContextCommandPlan {
    val next = infinityReduceDesktopContextState(state, action)
    val placements = when (action) {
        InfinityDesktopContextAction.SORT_NAME,
        InfinityDesktopContextAction.SORT_TYPE,
        InfinityDesktopContextAction.SORT_MANUAL,
        InfinityDesktopContextAction.ALIGN_GRID ->
            infinityDesktopArrangeAndPlace(icons, next.arrangement, viewportWidth, itemWidth, itemHeight, leftInset, topInset)
        InfinityDesktopContextAction.REFRESH_LAYOUT ->
            infinityDesktopOrderedGridPlacement(icons.map { it.id }, viewportWidth, itemWidth, itemHeight, leftInset, topInset)
        else -> null
    }
    return InfinityDesktopContextCommandPlan(
        nextState = next,
        placements = placements,
        requestWallpaperPicker = action == InfinityDesktopContextAction.WALLPAPER,
        requestAppearancePanel = action == InfinityDesktopContextAction.DESKTOP_APPEARANCE,
        requestWorkspacePanel = action == InfinityDesktopContextAction.WORKSPACE
    )
}
