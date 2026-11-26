package com.infinity.core

import androidx.compose.ui.geometry.Offset

/** Keep dragged desktop icons within the visible desktop area. */
internal fun infinityBoundIconOffset(
    current: Offset,
    delta: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
    itemWidth: Float = 110f,
    itemHeight: Float = 150f,
    topInset: Float = 0f
): Offset {
    fun safe(value: Float, fallback: Float = 0f) = value.takeIf { it.isFinite() } ?: fallback
    val width = safe(viewportWidth).coerceAtLeast(0f)
    val height = safe(viewportHeight).coerceAtLeast(0f)
    val iconWidth = safe(itemWidth, 110f).coerceAtLeast(0f)
    val iconHeight = safe(itemHeight, 150f).coerceAtLeast(0f)
    val maxX = (width - iconWidth).coerceAtLeast(0f)
    val maxY = (height - iconHeight).coerceAtLeast(0f)
    val minY = safe(topInset).coerceIn(0f, maxY)
    val x = safe(current.x) + safe(delta.x)
    val y = safe(current.y) + safe(delta.y)
    return Offset(x.coerceIn(0f, maxX), y.coerceIn(minY, maxY))
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
    fun safe(value: Float, fallback: Float) = value.takeIf { it.isFinite() } ?: fallback
    val width = safe(viewportWidth, 0f).coerceAtLeast(0f)
    val iconWidth = safe(itemWidth, 110f).coerceAtLeast(1f)
    val iconHeight = safe(itemHeight, 150f).coerceAtLeast(0f)
    val gapX = safe(horizontalGap, 12f).coerceAtLeast(0f)
    val gapY = safe(verticalGap, 14f).coerceAtLeast(0f)
    val inset = safe(topInset, 24f).coerceAtLeast(0f)
    val usableWidth = (width - gapX).coerceAtLeast(iconWidth)
    val columns = (((usableWidth + gapX) / (iconWidth + gapX)).toInt()).coerceIn(1, 10_000)
    val safeIndex = index.coerceAtLeast(0)
    val column = safeIndex % columns
    val row = safeIndex / columns
    val x = (gapX.toDouble() + column.toDouble() * (iconWidth + gapX).toDouble())
        .coerceAtMost((width - iconWidth).coerceAtLeast(0f).toDouble()).toFloat()
    val y = (safeTop.toDouble() + row.toDouble() * (iconHeight + gapY).toDouble())
        .coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
    return Offset(x, y)
}

/** Computes a protected desktop height above the dock and its touch/navigation inset. */
internal fun infinityDesktopHeightAboveDock(
    viewportHeight: Float,
    dockHeight: Float = 88f,
    dockBottomInset: Float = 24f,
    safetyGap: Float = 56f
): Float {
    fun safe(value: Float, fallback: Float = 0f) = value.takeIf { it.isFinite() } ?: fallback
    return (safe(viewportHeight) -
        safe(dockHeight, 88f).coerceAtLeast(0f) -
        safe(dockBottomInset, 24f).coerceAtLeast(0f) -
        safe(safetyGap, 56f).coerceAtLeast(0f)).coerceAtLeast(0f)
}

/** Resolve collisions using free grid cells; never clamp different rows onto the same edge. */
internal fun infinityResolveIconOverlaps(
    offsets: List<Offset>, viewportWidth: Float, viewportHeight: Float,
    itemWidth: Float = 116f, itemHeight: Float = 150f,
    horizontalGap: Float = 12f, verticalGap: Float = 14f, topInset: Float = 118f
): List<Offset> {
    if (offsets.isEmpty()) return emptyList()
    fun safe(value: Float, fallback: Float) = value.takeIf { it.isFinite() } ?: fallback
    val width = safe(viewportWidth, 1f).coerceAtLeast(1f)
    val height = safe(viewportHeight, 1f).coerceAtLeast(1f)
    val gapX = safe(horizontalGap, 12f).coerceAtLeast(0f)
    val gapY = safe(verticalGap, 14f).coerceAtLeast(0f)
    val inset = safe(topInset, 118f).coerceIn(0f, height)
    val iconWidth = safe(itemWidth, 116f).coerceAtLeast(1f).coerceAtMost(width)
    val availableHeight = (height - inset).coerceAtLeast(1f)
    val iconHeight = safe(itemHeight, 150f).coerceAtLeast(1f).coerceAtMost(availableHeight)
    val safeTop = inset.coerceAtMost((height - iconHeight).coerceAtLeast(0f))
    val usableHeight = (height - safeTop).coerceAtLeast(iconHeight)
    val columns = (((width + gapX) / (iconWidth + gapX)).toInt()).coerceIn(1, 10_000)
    val rows = (((usableHeight + gapY) / (iconHeight + gapY)).toInt()).coerceIn(1, 10_000)
    val maxSlots = (columns.toLong() * rows.toLong()).coerceAtMost(1_000_000L).toInt()

    fun overlaps(a: Offset, b: Offset) =
        a.x < b.x + iconWidth && a.x + iconWidth > b.x &&
            a.y < b.y + iconHeight && a.y + iconHeight > b.y

    fun slotAt(slot: Int): Offset {
        val column = slot % columns
        val row = slot / columns
        val x = (gapX.toDouble() + column.toDouble() * (iconWidth + gapX).toDouble())
            .coerceAtMost((width - iconWidth).coerceAtLeast(0f).toDouble()).toFloat()
        val y = (safeTop.toDouble() + row.toDouble() * (iconHeight + gapY).toDouble())
            .coerceAtMost((height - iconHeight).coerceAtLeast(safeTop).toDouble()).toFloat()
        return Offset(x, y)
    }

    val resolved = mutableListOf<Offset>()
    offsets.forEach { raw ->
        val bounded = infinityBoundIconOffset(raw, Offset.Zero, width, height, iconWidth, iconHeight, safeTop)
        if (resolved.none { overlaps(bounded, it) }) {
            resolved += bounded
        } else {
            val freeSlot = (0 until maxSlots).asSequence()
                .map(::slotAt)
                .firstOrNull { candidate -> resolved.none { overlaps(candidate, it) } }
            resolved += freeSlot ?: bounded
        }
    }
    return resolved
}

/** Geometry and compact visual sizing that fit every shortcut into the dock-safe desktop area. */
internal data class InfinityDesktopIconLayoutMetrics(
    val safeDesktopHeightPx: Float,
    val itemWidthPx: Float,
    val itemHeightPx: Float,
    val horizontalGapPx: Float,
    val verticalGapPx: Float,
    val topInsetPx: Float,
    val iconSizePx: Float
)

internal fun infinityDesktopIconLayoutMetrics(
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    itemCount: Int,
    density: Float = 1f
): InfinityDesktopIconLayoutMetrics {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    val width = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeHeight = infinityDesktopHeightAboveDock(
        viewportHeightPx,
        88f * safeDensity,
        24f * safeDensity,
        56f * safeDensity
    )
    val count = itemCount.coerceAtLeast(1)
    val gapX = 12f * safeDensity
    val gapY = 10f * safeDensity
    val top = (118f * safeDensity).coerceAtMost((safeHeight - 1f).coerceAtLeast(0f))
    val preferredWidth = 108f * safeDensity
    val columns = (((width + gapX) / (preferredWidth + gapX)).toInt())
        .coerceAtLeast(1).coerceAtMost(count)
    val rows = ((count + columns - 1) / columns).coerceAtLeast(1)
    val itemWidth = minOf(
        preferredWidth,
        ((width - (columns - 1) * gapX) / columns).coerceAtLeast(1f)
    )
    val availableHeight = (safeHeight - top).coerceAtLeast(1f)
    val itemHeight = minOf(
        150f * safeDensity,
        ((availableHeight - (rows - 1) * gapY) / rows).coerceAtLeast(1f)
    )
    val labelAndSpacingBudget = 42f * safeDensity
    val iconSize = (itemHeight - labelAndSpacingBudget)
        .coerceAtLeast(24f * safeDensity)
        .coerceAtMost(62f * safeDensity)
    return InfinityDesktopIconLayoutMetrics(
        safeHeight, itemWidth, itemHeight, gapX, gapY, top, iconSize
    )
}
