package com.infinity.core

/** Responsive sizing contract for the INFINITY START surface. */
internal fun infinityStartPanelWidthFraction(viewportWidthDp: Float): Float =
    if (!viewportWidthDp.isFinite() || viewportWidthDp < 600f) 0.97f else 0.94f

internal fun infinityStartPanelMaxWidthDp(viewportWidthDp: Float): Float =
    if (!viewportWidthDp.isFinite() || viewportWidthDp < 600f) 460f else 420f

internal fun infinityStartPanelBottomInsetDp(viewportHeightDp: Float): Float =
    if (!viewportHeightDp.isFinite()) 72f else if (viewportHeightDp < 600f) 72f else 84f

internal fun infinityStartPanelMaxHeightDp(viewportHeightDp: Float): Float =
    if (!viewportHeightDp.isFinite()) 180f
    else (viewportHeightDp - if (viewportHeightDp < 600f) 96f else 116f).coerceAtLeast(180f)
