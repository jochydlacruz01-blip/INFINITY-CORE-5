package com.infinity.core

/** Cascades new windows in a repeatable pattern and keeps them within the visible area. */
internal fun infinityInitialWindowPosition(index: Int, viewportWidth: Float, viewportHeight: Float, windowWidth: Float = 370f, windowHeight: Float = 330f): Pair<Float, Float> {
    if (!viewportWidth.isFinite() || !viewportHeight.isFinite() || !windowWidth.isFinite() || !windowHeight.isFinite()) return 0f to 0f
    val maxX = (viewportWidth.coerceAtLeast(0f) - windowWidth.coerceAtLeast(0f)).coerceAtLeast(0f)
    val maxY = (viewportHeight.coerceAtLeast(0f) - windowHeight.coerceAtLeast(0f)).coerceAtLeast(0f)
    val offset = (index.coerceAtLeast(0) % 12) * 22f
    return (90f + offset).coerceIn(0f, maxX) to (75f + offset).coerceIn(0f, maxY)
}

/** Chooses a useful default window size without allowing it to exceed a phone viewport. */
internal fun infinityInitialWindowSize(viewportWidth: Float, viewportHeight: Float, preferredWidth: Float = 370f, preferredHeight: Float = 330f, horizontalMargin: Float = 24f, verticalMargin: Float = 150f): Pair<Float, Float> {
    fun safe(value: Float) = value.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: Float.POSITIVE_INFINITY
    val availableWidth = if (!viewportWidth.isFinite() || !horizontalMargin.isFinite()) 1f else (viewportWidth.coerceAtLeast(0f) - horizontalMargin.coerceAtLeast(0f)).coerceAtLeast(1f)
    val availableHeight = if (!viewportHeight.isFinite() || !verticalMargin.isFinite()) 1f else (viewportHeight.coerceAtLeast(0f) - verticalMargin.coerceAtLeast(0f)).coerceAtLeast(1f)
    val width = safe(preferredWidth).let { if (it.isFinite()) it.coerceAtLeast(1f) else 1f }.coerceAtMost(availableWidth)
    val height = safe(preferredHeight).let { if (it.isFinite()) it.coerceAtLeast(1f) else 1f }.coerceAtMost(availableHeight)
    return width to height
}
