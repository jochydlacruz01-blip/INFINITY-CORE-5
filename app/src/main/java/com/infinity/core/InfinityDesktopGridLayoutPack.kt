package com.infinity.core

import kotlin.math.max

/** Pure layout helper used by the desktop shell when applying grid alignment. */
internal fun infinityDesktopGridOffsets(
    count: Int,
    viewportWidth: Float,
    itemWidth: Float,
    itemHeight: Float,
    leftInset: Float = 16f,
    topInset: Float = 118f,
    horizontalGap: Float = 12f,
    verticalGap: Float = 14f
): List<androidx.compose.ui.geometry.Offset> {
    if (count <= 0) return emptyList()
    val safeItemWidth = itemWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeItemHeight = itemHeight.takeIf { it.isFinite() && it > 0f } ?: 1f
    // Recover from invalid viewport input using the sanitized icon width, not raw geometry.
    val safeWidth = viewportWidth.takeIf { it.isFinite() && it > 0f } ?: safeItemWidth
    val safeGap = horizontalGap.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val safeVerticalGap = verticalGap.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val safeLeft = leftInset.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val safeTop = topInset.takeIf { it.isFinite() && it >= 0f } ?: 0f
    val columns = max(1, ((safeWidth - safeLeft + safeGap) / (safeItemWidth + safeGap)).toInt())
    return List(count) { index ->
        val col = index % columns
        val row = index / columns
        androidx.compose.ui.geometry.Offset(
            safeLeft + col * (safeItemWidth + safeGap),
            safeTop + row * (safeItemHeight + safeVerticalGap)
        )
    }
}

/** User-facing Spanish labels, kept stable for shell menu wiring. */
internal fun infinityDesktopContextActionLabel(action: InfinityDesktopContextAction): String = when (action) {
    InfinityDesktopContextAction.SORT_NAME -> "Ordenar por nombre"
    InfinityDesktopContextAction.SORT_TYPE -> "Ordenar por tipo"
    InfinityDesktopContextAction.SORT_MANUAL -> "Orden manual"
    InfinityDesktopContextAction.ALIGN_GRID -> "Alinear a cuadrícula"
    InfinityDesktopContextAction.SHOW_ICONS -> "Mostrar iconos"
    InfinityDesktopContextAction.HIDE_ICONS -> "Ocultar iconos"
    InfinityDesktopContextAction.REFRESH_LAYOUT -> "Actualizar escritorio"
    InfinityDesktopContextAction.DESKTOP_APPEARANCE -> "Apariencia"
    InfinityDesktopContextAction.WALLPAPER -> "Fondo de escritorio"
    InfinityDesktopContextAction.ICON_SIZE -> "Tamaño de iconos"
    InfinityDesktopContextAction.WORKSPACE -> "Espacios de trabajo"
}
