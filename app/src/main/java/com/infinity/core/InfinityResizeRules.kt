package com.infinity.core

/** Keeps resize gestures within the viewport while respecting minimum size when possible. */
internal fun infinityResizeDimension(current: Float, delta: Float, available: Float, minimum: Float): Float {
    val safeAvailable = available.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    if (safeAvailable <= 0f) return 0f
    val lower = (minimum.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f).coerceAtMost(safeAvailable)
    val safeCurrent = current.takeIf { it.isFinite() } ?: lower
    val safeDelta = delta.takeIf { it.isFinite() } ?: 0f
    val target = (safeCurrent + safeDelta).takeIf { it.isFinite() } ?: safeAvailable
    return target.coerceIn(lower, safeAvailable)
}
