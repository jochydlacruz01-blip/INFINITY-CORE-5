package com.infinity.core

import androidx.compose.ui.geometry.Offset

/** Keep dragged desktop icons within the visible desktop area. */
internal fun infinityBoundIconOffset(
    current: Offset,
    delta: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
    itemWidth: Float = 110f,
    itemHeight: Float = 150f
): Offset {
    val maxX = (viewportWidth - itemWidth).coerceAtLeast(0f)
    val maxY = (viewportHeight - itemHeight).coerceAtLeast(0f)
    return Offset(
        (current.x + delta.x).coerceIn(0f, maxX),
        (current.y + delta.y).coerceIn(0f, maxY)
    )
}


/** Stable grid defaults for icons when saved coordinates are missing or invalid. */
internal fun infinityDesktopIconGridOffset(
    index: Int,
    viewportWidth: Float,
    itemWidth: Float,
    itemHeight: Float,
    horizontalGap: Float = 12f,
    verticalGap: Float = 14f,
    topInset: Float = 24f
): Offset {
    val safeIndex = index.coerceAtLeast(0)
    val usableWidth = (viewportWidth - horizontalGap).coerceAtLeast(itemWidth)
    val columns = ((usableWidth + horizontalGap) / (itemWidth + horizontalGap)).toInt().coerceAtLeast(1)
    val column = safeIndex % columns
    val row = safeIndex / columns
    return Offset(
        (horizontalGap + column * (itemWidth + horizontalGap)).coerceAtMost((viewportWidth - itemWidth).coerceAtLeast(0f)),
        topInset + row * (itemHeight + verticalGap)
    )
}

/** Computes a protected desktop height above the dock and its touch/navigation inset. */
internal fun infinityDesktopHeightAboveDock(
    viewportHeight: Float,
    dockHeight: Float = 88f,
    dockBottomInset: Float = 24f,
    safetyGap: Float = 56f
): Float = (viewportHeight - dockHeight.coerceAtLeast(0f) -
    dockBottomInset.coerceAtLeast(0f) - safetyGap.coerceAtLeast(0f)).coerceAtLeast(0f)
