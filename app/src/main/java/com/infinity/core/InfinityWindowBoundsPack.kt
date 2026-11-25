package com.infinity.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/** Keeps a desktop window's top-left position reachable within the usable viewport. */
internal fun infinityClampWindowPosition(
    position: Offset,
    windowSize: Size,
    viewportSize: Size,
    minimumVisible: Float = 48f
): Offset {
    val vw = viewportSize.width.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val vh = viewportSize.height.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val ww = windowSize.width.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val wh = windowSize.height.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val visible = minimumVisible.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val maxX = (vw - minOf(ww, vw) + visible).coerceAtLeast(0f)
    val maxY = (vh - minOf(wh, vh) + visible).coerceAtLeast(0f)
    val x = position.x.takeIf { it.isFinite() } ?: 0f
    val y = position.y.takeIf { it.isFinite() } ?: 0f
    return Offset(x.coerceIn(0f, maxX), y.coerceIn(0f, maxY))
}
