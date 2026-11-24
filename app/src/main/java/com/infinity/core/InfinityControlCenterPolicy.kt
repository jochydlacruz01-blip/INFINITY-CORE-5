package com.infinity.core

/**
 * Responsive sizing policy for the floating INFINITY Control Center.
 * Width is capped to the viewport and leaves a small breathing margin.
 */
internal fun infinityControlPanelMaxWidthDp(availableWidthDp: Float): Float {
    if (!availableWidthDp.isFinite() || availableWidthDp <= 0f) return 280f
    return (availableWidthDp - 24f).coerceIn(280f, 350f)
}

/** Avoids an unusably tall panel on compact viewports while preserving desktop space. */
internal fun infinityControlPanelMaxHeightDp(availableHeightDp: Float): Float {
    if (!availableHeightDp.isFinite() || availableHeightDp <= 0f) return 220f
    return (availableHeightDp - 188f).coerceAtLeast(220f)
}

/** Compact layouts use a single column; tablet/desktop layouts can use two. */
internal fun infinityControlPanelColumns(availableWidthDp: Float): Int =
    if (availableWidthDp.isFinite() && availableWidthDp >= 520f) 2 else 1
