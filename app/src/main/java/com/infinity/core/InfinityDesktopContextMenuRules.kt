package com.infinity.core

/** Desktop right-click actions, deliberately separated from dock/task controls. */
internal enum class InfinityDesktopContextAction {
    SORT_NAME, SORT_TYPE, SORT_MANUAL, ALIGN_GRID, SHOW_ICONS, HIDE_ICONS,
    REFRESH_LAYOUT, DESKTOP_APPEARANCE, WALLPAPER, ICON_SIZE, WORKSPACE
}

internal enum class InfinityIconArrangement { MANUAL, NAME, TYPE, GRID }

/** Sort stable icon records using locale-independent deterministic ordering. */
internal data class InfinityDesktopIconRecord(val id: String, val label: String, val type: String, val order: Int)

internal fun infinityArrangeDesktopIcons(
    icons: List<InfinityDesktopIconRecord>, arrangement: InfinityIconArrangement
): List<InfinityDesktopIconRecord> = when (arrangement) {
    InfinityIconArrangement.MANUAL -> icons.sortedBy { it.order }
    InfinityIconArrangement.NAME -> icons.sortedWith(Comparator { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label) })
    InfinityIconArrangement.TYPE -> icons.sortedWith(Comparator { a, b -> val typeCompare = String.CASE_INSENSITIVE_ORDER.compare(a.type, b.type); if (typeCompare != 0) typeCompare else a.label.lowercase(java.util.Locale.ROOT).compareTo(b.label.lowercase(java.util.Locale.ROOT)) })
    InfinityIconArrangement.GRID -> icons.sortedBy { it.order }
}

/** Context menu policy: desktop shell settings belong on the desktop, not in the Dock. */
internal fun infinityDesktopContextActions(iconsVisible: Boolean = true): List<InfinityDesktopContextAction> = listOf(
    InfinityDesktopContextAction.SORT_NAME,
    InfinityDesktopContextAction.SORT_TYPE,
    InfinityDesktopContextAction.SORT_MANUAL,
    InfinityDesktopContextAction.ALIGN_GRID,
    if (iconsVisible) InfinityDesktopContextAction.HIDE_ICONS else InfinityDesktopContextAction.SHOW_ICONS,
    InfinityDesktopContextAction.REFRESH_LAYOUT,
    InfinityDesktopContextAction.DESKTOP_APPEARANCE,
    InfinityDesktopContextAction.WALLPAPER,
    InfinityDesktopContextAction.ICON_SIZE,
    InfinityDesktopContextAction.WORKSPACE
)
