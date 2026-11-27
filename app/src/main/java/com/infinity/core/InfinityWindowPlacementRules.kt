package com.infinity.core

/** Cascades new windows in a repeatable pattern and keeps them within the visible area. */
internal fun infinityInitialWindowPosition(
    index: Int,
    viewportWidth: Float,
    viewportHeight: Float,
    windowWidth: Float = 370f,
    windowHeight: Float = 330f
): Pair<Float, Float> {
    val safeViewportWidth = viewportWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeViewportHeight = viewportHeight.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeWindowWidth = windowWidth.takeIf { it.isFinite() && it > 0f } ?: safeViewportWidth
    val safeWindowHeight = windowHeight.takeIf { it.isFinite() && it > 0f } ?: safeViewportHeight
    val maxX = (safeViewportWidth - safeWindowWidth).coerceAtLeast(0f)
    val maxY = (safeViewportHeight - safeWindowHeight).coerceAtLeast(0f)
    val safeIndex = index.coerceAtLeast(0)
    val step = 22f
    val cascadeCount = 12
    val offset = (safeIndex % cascadeCount) * step
    return (90f + offset).coerceIn(0f, maxX) to (75f + offset).coerceIn(0f, maxY)
}

/** Chooses a useful default window size without allowing it to exceed a phone viewport. */
internal fun infinityInitialWindowSize(
    viewportWidth: Float,
    viewportHeight: Float,
    preferredWidth: Float = 370f,
    preferredHeight: Float = 330f,
    horizontalMargin: Float = 24f,
    verticalMargin: Float = 150f
): Pair<Float, Float> {
    val safeViewportWidth = viewportWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeViewportHeight = viewportHeight.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeHorizontalMargin = horizontalMargin.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val safeVerticalMargin = verticalMargin.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val availableWidth = (safeViewportWidth - safeHorizontalMargin).coerceAtLeast(1f)
    val availableHeight = (safeViewportHeight - safeVerticalMargin).coerceAtLeast(1f)
    val safePreferredWidth = preferredWidth.takeIf { it.isFinite() && it > 0f } ?: availableWidth
    val safePreferredHeight = preferredHeight.takeIf { it.isFinite() && it > 0f } ?: availableHeight
    return safePreferredWidth.coerceAtMost(availableWidth) to
        safePreferredHeight.coerceAtMost(availableHeight)
}

/** Clamp a window's top-left position after movement, recovering from invalid geometry. */
internal fun infinityClampWindowPosition(
    x: Float,
    y: Float,
    windowWidth: Float,
    windowHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    safeBottom: Float = viewportHeight
): Pair<Float, Float> {
    val safeViewportWidth = viewportWidth.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val safeViewportHeight = viewportHeight.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val safeDockTop = safeBottom.takeIf { it.isFinite() }?.coerceIn(0f, safeViewportHeight)
        ?: safeViewportHeight
    val safeWindowWidth = windowWidth.takeIf { it.isFinite() && it >= 0f } ?: safeViewportWidth
    val safeWindowHeight = windowHeight.takeIf { it.isFinite() && it >= 0f } ?: safeViewportHeight
    val maxX = (safeViewportWidth - safeWindowWidth).coerceAtLeast(0f)
    val maxY = (safeDockTop - safeWindowHeight).coerceAtLeast(0f)
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
    margin: Float = 16f,
    safeBottom: Float = viewportHeight
): InfinityWindowBounds {
    val safeViewportWidth = viewportWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeViewportHeight = viewportHeight.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeMargin = margin.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val safeDockTop = safeBottom.takeIf { it.isFinite() }?.coerceIn(0f, safeViewportHeight)
        ?: safeViewportHeight
    val availableWidth = (safeViewportWidth - safeMargin).coerceAtLeast(1f)
    val availableHeight = (safeDockTop - safeMargin).coerceAtLeast(1f)
    val safeMinWidth = minWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeMinHeight = minHeight.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeRequestedWidth = width.takeIf { it.isFinite() && it > 0f } ?: safeMinWidth
    val safeRequestedHeight = height.takeIf { it.isFinite() && it > 0f } ?: safeMinHeight
    val normalizedWidth = safeRequestedWidth.coerceIn(safeMinWidth.coerceAtMost(availableWidth), availableWidth)
    val normalizedHeight = safeRequestedHeight.coerceIn(safeMinHeight.coerceAtMost(availableHeight), availableHeight)
    val (safeX, safeY) = infinityClampWindowPosition(
        x, y, normalizedWidth, normalizedHeight, safeViewportWidth, safeViewportHeight, safeDockTop
    )
    return InfinityWindowBounds(safeX, safeY, normalizedWidth, normalizedHeight)
}
/** Converts a Compose pointer delta in physical pixels to the dp unit used by window geometry. */
internal fun infinityGestureDeltaToDp(deltaPx: Float, density: Float): Float {
    val safeDelta = deltaPx.takeIf { it.isFinite() } ?: 0f
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    return safeDelta / safeDensity
}
