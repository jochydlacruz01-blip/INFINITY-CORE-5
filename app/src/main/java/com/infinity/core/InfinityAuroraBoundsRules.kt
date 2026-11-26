package com.infinity.core

internal data class InfinityAuroraSafeBounds(
    val width: Float,
    val height: Float,
    val topInset: Float = 0f,
    /** Y coordinate of the top edge reserved for the Dock, already excluding bottom system inset. */
    val dockTop: Float = height,
    val minWidth: Float = 240f,
    val minHeight: Float = 180f,
    val margin: Float = 8f
) {
    val safeTop: Float get() = topInset.coerceIn(0f, height.coerceAtLeast(0f))
    val safeBottom: Float get() = dockTop.coerceIn(safeTop, height.coerceAtLeast(safeTop))
    val safeHeight: Float get() = (safeBottom - safeTop).coerceAtLeast(0f)
}

internal fun infinityAuroraBoundWindow(
    window: InfinityAuroraWindow,
    bounds: InfinityAuroraSafeBounds
): InfinityAuroraWindow {
    fun finite(value: Float, fallback: Float) = value.takeIf { it.isFinite() } ?: fallback
    val safeWidth = finite(bounds.width, 0f).coerceAtLeast(0f)
    val safeHeight = bounds.safeHeight
    val margin = finite(bounds.margin, 0f).coerceAtLeast(0f)
    val minWidth = finite(bounds.minWidth, 1f).coerceAtLeast(1f)
        .coerceAtMost((safeWidth - margin * 2f).coerceAtLeast(1f))
    val minHeight = finite(bounds.minHeight, 1f).coerceAtLeast(1f)
        .coerceAtMost((safeHeight - margin * 2f).coerceAtLeast(1f))
    val width = finite(window.width, minWidth).coerceIn(minWidth, (safeWidth - margin * 2f).coerceAtLeast(minWidth))
    val height = finite(window.height, minHeight).coerceIn(minHeight, (safeHeight - margin * 2f).coerceAtLeast(minHeight))
    val x = finite(window.x, margin).coerceIn(margin, (safeWidth - width - margin).coerceAtLeast(margin))
    val y = finite(window.y, bounds.safeTop + margin)
        .coerceIn(bounds.safeTop + margin, (bounds.safeBottom - height - margin).coerceAtLeast(bounds.safeTop + margin))
    return window.copy(x = x, y = y, width = width, height = height)
}

internal fun infinityAuroraMaximizedWindow(
    window: InfinityAuroraWindow,
    bounds: InfinityAuroraSafeBounds
): InfinityAuroraWindow {
    val margin = bounds.margin.coerceAtLeast(0f)
    return window.copy(
        x = margin,
        y = bounds.safeTop + margin,
        width = (bounds.width - margin * 2f).coerceAtLeast(1f),
        height = (bounds.safeBottom - bounds.safeTop - margin * 2f).coerceAtLeast(1f),
        maximized = true
    )
}

/** Maximized and minimized windows are not eligible for direct geometry gestures. */
internal fun infinityAuroraCanChangeGeometry(window: InfinityAuroraWindow): Boolean =
    !window.maximized && !window.minimized

/**
 * Dock vertical footprint, including the outer and inner Compose padding.
 * Keep this policy aligned with the Dock modifier chain in MainActivity.
 */
internal fun infinityAuroraDockClearanceDp(
    viewportWidthDp: Float,
    desktopTheme: String,
    futuristicDesktop: Boolean
): Float = when {
    desktopTheme == "windows11" -> 74f // 60dp row + 7dp inner padding on each side.
    viewportWidthDp >= 600f && futuristicDesktop -> 112f // 72dp row + 8dp inner + 12dp outer padding.
    else -> 104f // 60dp row + 10dp inner + 12dp outer padding.
}
