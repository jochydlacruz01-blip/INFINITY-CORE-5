package com.infinity.core

import androidx.compose.ui.geometry.Offset

/** Maps arranged icon IDs to deterministic grid slots without mutating persisted offsets. */
internal fun infinityDesktopOrderedGridPlacement(
    ids: List<String>,
    viewportWidth: Float,
    itemWidth: Float,
    itemHeight: Float,
    leftInset: Float = 16f,
    topInset: Float = 118f,
    horizontalGap: Float = 12f,
    verticalGap: Float = 14f
): Map<String, Offset> {
    val unique = ids.distinct()
    val positions = infinityDesktopGridOffsets(
        unique.size, viewportWidth, itemWidth, itemHeight,
        leftInset, topInset, horizontalGap, verticalGap
    )
    return unique.zip(positions).toMap()
}

/** Reorder IDs from labels/types, then produce placement slots for the chosen mode. */
internal fun infinityDesktopArrangeAndPlace(
    records: List<InfinityDesktopIconRecord>,
    arrangement: InfinityIconArrangement,
    viewportWidth: Float,
    itemWidth: Float,
    itemHeight: Float,
    leftInset: Float = 16f,
    topInset: Float = 118f
): Map<String, Offset> {
    val ordered = infinityArrangeDesktopIcons(records, arrangement).map { it.id }
    return infinityDesktopOrderedGridPlacement(ordered, viewportWidth, itemWidth, itemHeight, leftInset, topInset)
}
