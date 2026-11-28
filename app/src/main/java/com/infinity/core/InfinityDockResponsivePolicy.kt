package com.infinity.core

/**
 * On phone-sized viewports the Dock can contain more actions than fit in one row.
 * Keep its primary actions reachable with horizontal scrolling instead of clipping.
 */
internal fun infinityDockRequiresHorizontalScroll(viewportWidthDp: Float): Boolean =
    !viewportWidthDp.isFinite() || viewportWidthDp < 600f

/** Conservative first-frame reservation; replaced by the measured Dock top after layout. */
internal fun infinityDockReservedHeightDp(
    viewportWidthDp: Float,
    futuristicDesktop: Boolean,
    iconPack: String
): Float = when {
    viewportWidthDp >= 600f && futuristicDesktop -> 112f
    iconPack == "windows11" -> 74f
    viewportWidthDp < 600f -> 84f
    else -> 80f
}

/** Bottom inset that places a desktop overlay entirely above the measured Dock. */
internal fun infinityStartPanelBottomPaddingDp(
    viewportHeightDp: Float,
    dockSafeBottomDp: Float
): Float {
    val viewport = viewportHeightDp.takeIf { it.isFinite() && it > 0f } ?: return 0f
    val safeBottom = dockSafeBottomDp.takeIf { it.isFinite() }
        ?: (viewport - 112f).coerceAtLeast(0f)
    return (viewport - safeBottom).coerceIn(0f, viewport)
}

/** Caps the Start panel to the space above the Dock while preserving a small top margin. */
internal fun infinityStartPanelMaxHeightDp(dockSafeBottomDp: Float): Float =
    if (dockSafeBottomDp.isFinite()) (dockSafeBottomDp - 24f).coerceAtLeast(1f) else 1f
