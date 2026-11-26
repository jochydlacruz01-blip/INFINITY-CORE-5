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
    val iconSizeDp: Float,
    val showLabels: Boolean
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
    val top = 54f
    val dockTop = (viewportHeightDp - dockAndBottomReserve).coerceAtLeast(top)
    val available = (dockTop - top).coerceAtLeast(1f)

    // Never force a 52dp minimum: compact heights must not push the rail into the Dock.
    val slot = (available / itemCount).coerceIn(1f, 88f)
    // Keep the glyph inside its slot even on unusually short landscape viewports.
    // A fixed 12dp minimum made icons overlap when the available slot fell below 12dp.
    val icon = minOf(40f, (slot * .38f).coerceAtLeast(1f), slot)
    val width = if (viewportWidthDp < 720f) 78f else 88f

    return InfinityReferenceRailLayout(
        leftDp = 7f,
        topDp = top,
        itemWidthDp = width,
        slotHeightDp = slot,
        iconSizeDp = icon,
        showLabels = slot >= 40f
    )
}


/** The full masthead needs a wide desktop so it cannot collide with the clock or left launcher rail. */
internal fun infinityReferenceMastheadVisible(viewportWidthDp: Float): Boolean =
    viewportWidthDp.isFinite() && viewportWidthDp >= 1320f
