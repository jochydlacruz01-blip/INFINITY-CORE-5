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

/** Moves overlapping saved desktop icons into deterministic grid slots, preserving non-overlapping placements. */
internal fun infinityResolveIconOverlaps(
    offsets: List<Offset>,
    viewportWidth: Float,
    viewportHeight: Float,
    itemWidth: Float = 116f,
    itemHeight: Float = 150f,
    horizontalGap: Float = 12f,
    verticalGap: Float = 14f,
    topInset: Float = 118f
): List<Offset> {
    val resolved = mutableListOf<Offset>()
    offsets.forEachIndexed { index, raw ->
        var candidate = infinityBoundIconOffset(raw, Offset.Zero, viewportWidth, viewportHeight, itemWidth, itemHeight)
        fun overlaps(a: Offset, b: Offset): Boolean =
            a.x < b.x + itemWidth && a.x + itemWidth > b.x &&
            a.y < b.y + itemHeight && a.y + itemHeight > b.y
        if (resolved.any { overlaps(candidate, it) }) {
            var slot = index
            do {
                candidate = infinityDesktopIconGridOffset(slot, viewportWidth, itemWidth, itemHeight, horizontalGap, verticalGap, topInset)
                candidate = infinityBoundIconOffset(candidate, Offset.Zero, viewportWidth, viewportHeight, itemWidth, itemHeight)
                slot++
            } while (resolved.any { overlaps(candidate, it) } && slot < index + offsets.size + 64)
        }
        resolved.add(candidate)
    }
    return resolved
}
