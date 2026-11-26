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
