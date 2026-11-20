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

/** Resolve collisions using free grid cells; never clamp different rows onto the same edge. */
internal fun infinityResolveIconOverlaps(
    offsets: List<Offset>, viewportWidth: Float, viewportHeight: Float,
    itemWidth: Float = 116f, itemHeight: Float = 150f,
    horizontalGap: Float = 12f, verticalGap: Float = 14f, topInset: Float = 118f
): List<Offset> {
    if (offsets.isEmpty()) return emptyList()
    val width = itemWidth.coerceAtMost(viewportWidth.coerceAtLeast(1f))
    val height = itemHeight.coerceAtMost((viewportHeight - topInset).coerceAtLeast(1f))
    val gapX = horizontalGap.coerceAtLeast(0f); val gapY = verticalGap.coerceAtLeast(0f)
    val columns = (((viewportWidth.coerceAtLeast(width) + gapX) / (width + gapX)).toInt()).coerceAtLeast(1)
    val rows = (((viewportHeight - topInset).coerceAtLeast(height) + gapY) / (height + gapY)).toInt().coerceAtLeast(1)
    val slots = (0 until columns * rows).map { slot ->
        Offset((gapX + (slot % columns) * (width + gapX)).coerceAtMost((viewportWidth - width).coerceAtLeast(0f)),
            (topInset + (slot / columns) * (height + gapY)).coerceAtMost((viewportHeight - height).coerceAtLeast(0f)))
    }
    fun overlaps(a: Offset, b: Offset) = a.x < b.x + width && a.x + width > b.x && a.y < b.y + height && a.y + height > b.y
    val resolved = mutableListOf<Offset>()
    offsets.forEach { raw ->
        val bounded = infinityBoundIconOffset(raw, Offset.Zero, viewportWidth, viewportHeight, width, height)
        resolved += if (resolved.none { overlaps(bounded, it) }) bounded else slots.firstOrNull { s -> resolved.none { overlaps(s, it) } } ?: bounded
    }
    return resolved
}
