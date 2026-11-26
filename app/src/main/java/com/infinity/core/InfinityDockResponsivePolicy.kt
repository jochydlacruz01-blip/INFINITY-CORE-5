package com.infinity.core

/**
 * On phone-sized viewports the Dock can contain more actions than fit in one row.
 * Keep its primary actions reachable with horizontal scrolling instead of clipping.
 */
internal fun infinityDockRequiresHorizontalScroll(viewportWidthDp: Float): Boolean =
    !viewportWidthDp.isFinite() || viewportWidthDp < 600f


/**
 * Phones use the compact action treatment throughout the mobile width range.
 * The 390–599 dp range must not fall back to the larger tablet-style labels,
 * which otherwise crowd the Dock and force essential controls off-screen.
 */
internal fun infinityDockUsesCompactActions(viewportWidthDp: Float): Boolean =
    !viewportWidthDp.isFinite() || viewportWidthDp < 600f
