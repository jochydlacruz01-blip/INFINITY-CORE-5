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


/** Clamp a window's top-left position after movement, recovering from invalid geometry. */
internal fun infinityClampWindowPosition(
    x: Float,
    y: Float,
    windowWidth: Float,
    windowHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float
): Pair<Float, Float> {
    val safeViewportWidth = viewportWidth.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val safeViewportHeight = viewportHeight.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val safeWindowWidth = windowWidth.takeIf { it.isFinite() && it >= 0f } ?: safeViewportWidth
    val safeWindowHeight = windowHeight.takeIf { it.isFinite() && it >= 0f } ?: safeViewportHeight
    val maxX = (safeViewportWidth - safeWindowWidth).coerceAtLeast(0f)
    val maxY = (safeViewportHeight - safeWindowHeight).coerceAtLeast(0f)
    val safeX = x.takeIf { it.isFinite() } ?: 0f
    val safeY = y.takeIf { it.isFinite() } ?: 0f
    return safeX.coerceIn(0f, maxX) to safeY.coerceIn(0f, maxY)
}

/** Normalized geometry for a restored window, ensuring it remains reachable on a changed viewport. */
internal data class InfinityWindowBounds(val x: Float, val y: Float, val width: Float, val height: Float)

internal fun infinityNormalizeWindowBounds(
    x: Float, y: Float, width: Float, height: Float,
    viewportWidth: Float, viewportHeight: Float,
    minWidth: Float = 240f, minHeight: Float = 180f,
    margin: Float = 16f
): InfinityWindowBounds {
    val availableWidth = (viewportWidth - margin).coerceAtLeast(1f)
    val availableHeight = (viewportHeight - margin).coerceAtLeast(1f)
    val safeWidth = width.coerceIn(minWidth.coerceAtMost(availableWidth), availableWidth)
    val safeHeight = height.coerceIn(minHeight.coerceAtMost(availableHeight), availableHeight)
    val (safeX, safeY) = infinityClampWindowPosition(
        x, y, safeWidth, safeHeight, viewportWidth, viewportHeight
    )
    return InfinityWindowBounds(safeX, safeY, safeWidth, safeHeight)
}
