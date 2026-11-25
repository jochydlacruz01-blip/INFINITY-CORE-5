package com.infinity.core

/**
 * Stable responsive geometry for the reference desktop's left-side launcher rail.
 * Values are density-independent so the layout behaves consistently on phones and tablets.
 */
internal data class InfinityReferenceRailLayout(
    val leftDp: Float,
    val topDp: Float,
    val itemWidthDp: Float,
    val slotHeightDp: Float,
    val iconSizeDp: Float
)

internal fun infinityReferenceRailLayout(
    viewportWidthDp: Float,
    viewportHeightDp: Float,
    itemCount: Int
): InfinityReferenceRailLayout {
    require(viewportWidthDp > 0f)
    require(viewportHeightDp > 0f)
    require(itemCount > 0)
    val dockAndBottomReserve = if (viewportHeightDp < 560f) 112f else 148f
    val available = (viewportHeightDp - dockAndBottomReserve - 54f).coerceAtLeast(itemCount * 52f)
    val slot = (available / itemCount).coerceIn(52f, 88f)
    val icon = (slot * .48f).coerceIn(26f, 40f)
    val width = if (viewportWidthDp < 720f) 78f else 88f
    return InfinityReferenceRailLayout(
        leftDp = 7f,
        topDp = 54f,
        itemWidthDp = width,
        slotHeightDp = slot,
        iconSizeDp = icon
    )
}
