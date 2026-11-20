package com.infinity.core

/** Keeps resize gestures within the viewport while respecting minimum size when possible. */
internal fun infinityResizeDimension(current: Float, delta: Float, available: Float, minimum: Float): Float {
    val safeAvailable = available.coerceAtLeast(0f)
    if (safeAvailable <= 0f) return 0f
    val lower = minimum.coerceAtLeast(0f).coerceAtMost(safeAvailable)
    return (current + delta).coerceIn(lower, safeAvailable)
}
