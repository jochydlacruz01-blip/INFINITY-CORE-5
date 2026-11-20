package com.infinity.core

import androidx.compose.ui.geometry.Offset

/** Keep dragged desktop icons within the visible desktop area. */
internal fun infinityBoundIconOffset(current: Offset, delta: Offset, viewportWidth: Float, viewportHeight: Float, itemWidth: Float = 110f, itemHeight: Float = 150f): Offset {
    fun safe(value: Float) = value.takeIf { it.isFinite() } ?: 0f
    val maxX = (safe(viewportWidth).coerceAtLeast(0f) - safe(itemWidth).coerceAtLeast(0f)).coerceAtLeast(0f)
    val maxY = (safe(viewportHeight).coerceAtLeast(0f) - safe(itemHeight).coerceAtLeast(0f)).coerceAtLeast(0f)
    val x = (safe(current.x) + safe(delta.x)).takeIf { it.isFinite() }?.coerceIn(0f, maxX) ?: maxX
    val y = (safe(current.y) + safe(delta.y)).takeIf { it.isFinite() }?.coerceIn(0f, maxY) ?: maxY
    return Offset(x, y)
}
