package com.infinity.core

/** Desktop right-click actions, deliberately separated from dock/task controls. */
internal enum class InfinityDesktopContextAction {
    SORT_NAME, SORT_TYPE, SORT_MANUAL, ALIGN_GRID, SHOW_ICONS, HIDE_ICONS,
    REFRESH_LAYOUT, DESKTOP_APPEARANCE, WALLPAPER, ICON_SIZE, WORKSPACE
}

internal enum class InfinityIconArrangement { MANUAL, NAME, TYPE, GRID }

/** Stable desktop records. Sorting uses deterministic tie-breakers to avoid icon jitter. */
internal data class InfinityDesktopIconRecord(val id: String, val label: String, val type: String, val order: Int)

private fun infinityCompareIconLabels(a: InfinityDesktopIconRecord, b: InfinityDesktopIconRecord): Int {
    val insensitive = String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label)
    if (insensitive != 0) return insensitive
    val normalized = a.label.lowercase(java.util.Locale.ROOT).compareTo(b.label.lowercase(java.util.Locale.ROOT))
    if (normalized != 0) return normalized
    return a.id.compareTo(b.id)
}

internal fun infinityArrangeDesktopIcons(
    icons: List<InfinityDesktopIconRecord>, arrangement: InfinityIconArrangement
): List<InfinityDesktopIconRecord> = when (arrangement) {
    InfinityIconArrangement.MANUAL -> icons.sortedWith(compareBy<InfinityDesktopIconRecord> { it.order }.thenBy { it.id })
    InfinityIconArrangement.NAME -> icons.sortedWith(::infinityCompareIconLabels)
    InfinityIconArrangement.TYPE -> icons.sortedWith(Comparator { a, b ->
        val typeCompare = String.CASE_INSENSITIVE_ORDER.compare(a.type, b.type)
        if (typeCompare != 0) typeCompare else infinityCompareIconLabels(a, b)
    })
    InfinityIconArrangement.GRID -> icons.sortedWith(compareBy<InfinityDesktopIconRecord> { it.order }.thenBy { it.id })
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
