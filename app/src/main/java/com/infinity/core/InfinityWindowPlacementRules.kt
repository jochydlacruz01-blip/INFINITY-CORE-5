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

/**
 * Chooses a useful default window size without allowing it to exceed a phone viewport.
 * Larger desktop/tablet layouts retain the established 370 x 330 dp window size.
 */
internal fun infinityInitialWindowSize(
    viewportWidth: Float,
    viewportHeight: Float,
    preferredWidth: Float = 370f,
    preferredHeight: Float = 330f,
    horizontalMargin: Float = 24f,
    verticalMargin: Float = 150f
): Pair<Float, Float> {
    val availableWidth = (viewportWidth - horizontalMargin).coerceAtLeast(1f)
    val availableHeight = (viewportHeight - verticalMargin).coerceAtLeast(1f)
    return preferredWidth.coerceAtMost(availableWidth) to
        preferredHeight.coerceAtMost(availableHeight)
}


/** Clamp a window's top-left position after movement to keep its full bounds visible. */
internal fun infinityClampWindowPosition(
    x: Float,
    y: Float,
    windowWidth: Float,
    windowHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float
): Pair<Float, Float> {
    val maxX = (viewportWidth - windowWidth).coerceAtLeast(0f)
    val maxY = (viewportHeight - windowHeight).coerceAtLeast(0f)
    return x.coerceIn(0f, maxX) to y.coerceIn(0f, maxY)
}
