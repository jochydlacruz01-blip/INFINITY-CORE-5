package com.infinity.core

/** Cascades new windows in a repeatable pattern and keeps them within the visible area. */
internal fun infinityInitialWindowPosition(
    index: Int,
    viewportWidth: Float,
    viewportHeight: Float,
    windowWidth: Float = 370f,
    windowHeight: Float = 330f
): Pair<Float, Float> {
    val maxX = (viewportWidth - windowWidth).coerceAtLeast(0f)
    val maxY = (viewportHeight - windowHeight).coerceAtLeast(0f)
    val safeIndex = index.coerceAtLeast(0)
    val step = 22f
    val cascadeCount = 12
    val offset = (safeIndex % cascadeCount) * step
    return (90f + offset).coerceIn(0f, maxX) to (75f + offset).coerceIn(0f, maxY)
}
