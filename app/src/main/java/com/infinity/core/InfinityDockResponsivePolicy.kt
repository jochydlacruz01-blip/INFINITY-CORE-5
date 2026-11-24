package com.infinity.core

/**
 * On phone-sized viewports the Dock can contain more actions than fit in one row.
 * Keep its primary actions reachable with horizontal scrolling instead of clipping.
 */
internal fun infinityDockRequiresHorizontalScroll(viewportWidthDp: Float): Boolean =
    !viewportWidthDp.isFinite() || viewportWidthDp < 600f
