package com.infinity.core

import androidx.compose.ui.geometry.Offset
import kotlin.math.floor

/**
 * Bounded desktop placement result. Icons beyond the visible capacity are reported
 * separately so a caller can expose them through scrolling/overflow rather than
 * silently stacking them on top of other icons.
 */
internal data class InfinityDesktopViewportPlacement(
    val positions: Map<String, Offset>,
    val overflowIds: List<String>
)

internal fun infinityDesktopViewportPlacement(
    ids: List<String>,
    viewportWidth: Float,
    viewportHeight: Float,
    itemWidth: Float = 116f,
    itemHeight: Float = 150f,
    leftInset: Float = 16f,
    topInset: Float = 118f,
    rightInset: Float = 16f,
    bottomInset: Float = 168f,
    horizontalGap: Float = 12f,
    verticalGap: Float = 14f
): InfinityDesktopViewportPlacement {
    val uniqueIds = ids.distinct()
    if (uniqueIds.isEmpty()) return InfinityDesktopViewportPlacement(emptyMap(), emptyList())

    fun safe(value: Float, fallback: Float, min: Float = 0f) =
        value.takeIf { it.isFinite() && it >= min } ?: fallback

    val width = safe(viewportWidth, 1f, 1f)
    val height = safe(viewportHeight, 1f, 1f)
    val left = safe(leftInset, 0f).coerceAtMost(width - 1f)
    val top = safe(topInset, 0f).coerceAtMost(height - 1f)
    val right = safe(rightInset, 0f).coerceAtMost((width - left - 1f).coerceAtLeast(0f))
    val bottom = safe(bottomInset, 0f).coerceAtMost((height - top - 1f).coerceAtLeast(0f))
    val gapX = safe(horizontalGap, 0f)
    val gapY = safe(verticalGap, 0f)
    val availableWidth = (width - left - right).coerceAtLeast(1f)
    val availableHeight = (height - top - bottom).coerceAtLeast(1f)
    val cellWidth = safe(itemWidth, 1f, 1f).coerceAtMost(availableWidth)
    val cellHeight = safe(itemHeight, 1f, 1f).coerceAtMost(availableHeight)
    val columns = floor((availableWidth + gapX) / (cellWidth + gapX))
        .toInt().coerceAtLeast(1).coerceAtMost(uniqueIds.size)
    val rows = floor((availableHeight + gapY) / (cellHeight + gapY))
        .toInt().coerceAtLeast(1).coerceAtMost(uniqueIds.size)
    val capacity = (columns.toLong() * rows.toLong())
        .coerceAtMost(uniqueIds.size.toLong()).toInt()
    val visibleIds = uniqueIds.take(capacity)
    val positions = visibleIds.mapIndexed { index, id ->
        val rawX = left + (index % columns) * (cellWidth + gapX)
        val rawY = top + (index / columns) * (cellHeight + gapY)
        // Keep the final geometry finite even when caller-supplied metrics are extreme.
        val x = rawX.takeIf { it.isFinite() } ?: left
        val y = rawY.takeIf { it.isFinite() } ?: top
        id to Offset(x, y)
    }.toMap()
    return InfinityDesktopViewportPlacement(positions, uniqueIds.drop(capacity))
}
