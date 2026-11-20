package com.infinity.core

/** Cascades new windows in a repeatable pattern and keeps them within the visible area. */
internal fun infinityInitialWindowPosition(index: Int, viewportWidth: Float, viewportHeight: Float, windowWidth: Float = 370f, windowHeight: Float = 330f): Pair<Float, Float> {
    fun safe(value: Float) = value.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val maxX = (safe(viewportWidth) - safe(windowWidth)).coerceAtLeast(0f)
    val maxY = (safe(viewportHeight) - safe(windowHeight)).coerceAtLeast(0f)
    val offset = (index.coerceAtLeast(0) % 12) * 22f
    return (90f + offset).coerceIn(0f, maxX) to (75f + offset).coerceIn(0f, maxY)
}

/** Chooses a useful default window size without allowing it to exceed a phone viewport. */
internal fun infinityInitialWindowSize(viewportWidth: Float, viewportHeight: Float, preferredWidth: Float = 370f, preferredHeight: Float = 330f, horizontalMargin: Float = 24f, verticalMargin: Float = 150f): Pair<Float, Float> {
    fun safe(value: Float, fallback: Float = 0f) = value.takeIf { it.isFinite() } ?: fallback
    val availableWidth = (safe(viewportWidth) - safe(horizontalMargin)).coerceAtLeast(1f)
    val availableHeight = (safe(viewportHeight) - safe(verticalMargin)).coerceAtLeast(1f)
    return safe(preferredWidth, 370f).coerceAtLeast(1f).coerceAtMost(availableWidth) to safe(preferredHeight, 330f).coerceAtLeast(1f).coerceAtMost(availableHeight)
}
