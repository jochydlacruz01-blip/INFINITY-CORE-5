package com.infinity.core

/**
 * Responsive sizing policy for the floating INFINITY Control Center.
 * Width is capped to the viewport and keeps a 12dp margin on each side.
 */
internal fun infinityControlPanelMaxWidthDp(availableWidthDp: Float): Float {
    if (!availableWidthDp.isFinite() || availableWidthDp <= 0f) return 280f
    return (availableWidthDp - 24f).coerceIn(1f, 350f)
}

/** Avoids an unusably tall panel on compact viewports while preserving desktop space. */
internal fun infinityControlPanelMaxHeightDp(availableHeightDp: Float): Float {
    if (!availableHeightDp.isFinite() || availableHeightDp <= 0f) return 220f
    return (availableHeightDp - 188f).coerceAtLeast(220f)
}

/** Compact layouts use a single column; tablet/desktop layouts can use two. */
internal fun infinityControlPanelColumns(availableWidthDp: Float): Int =
    if (availableWidthDp.isFinite() && availableWidthDp >= 520f) 2 else 1

/** Wider device controls use a two-column layout on tablet and desktop viewports. */
internal fun infinityControlDevicePanelMaxWidthDp(availableWidthDp: Float): Float {
    if (!availableWidthDp.isFinite() || availableWidthDp <= 0f) return 280f
    return (availableWidthDp - 24f).coerceIn(1f, 560f)
}


/** Converts a user percentage to Android's discrete media stream level. */
internal fun infinityMediaVolumeTargetLevel(maxLevel: Int, percent: Int): Int? {
    if (maxLevel <= 0) return null
    return (maxLevel * percent.coerceIn(0, 100) / 100f).toInt().coerceIn(0, maxLevel)
}


/** Displays only states that Android actually exposed; unknown values remain an explicit settings handoff. */
internal fun infinityControlConnectivityStatus(enabled: Boolean?): String = when (enabled) {
    true -> "Activado"
    false -> "Desactivado"
    null -> "Consultar en Android"
}
