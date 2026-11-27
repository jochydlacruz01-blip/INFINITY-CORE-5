package com.infinity.core

/**
 * Persistent, platform-independent state for the INFINITY Dock.
 * Android package discovery and window actions stay in the shell; this model
 * owns normalization and deterministic ordering so UI and tests share rules.
 */
internal data class InfinityDockPreferences(
    val favoritePackageNames: List<String> = emptyList(),
    val dockScale: Float = 1f,
    val iconScale: Float = 1f,
    val transparency: Float = 0.12f,
    val itemSpacingDp: Float = 8f,
    val scrollEnabled: Boolean = true,
    val visible: Boolean = true,
    val showRunningIndicators: Boolean = true,
    val position: InfinityDockPosition = InfinityDockPosition.BOTTOM
)

internal enum class InfinityDockPosition { BOTTOM }

internal data class InfinityDockTask(
    val id: String,
    val minimized: Boolean,
    val focused: Boolean
)

internal data class InfinityDockViewport(
    val widthDp: Float,
    val itemWidthDp: Float,
    val spacingDp: Float,
    val horizontalInsetDp: Float
) {
    val visibleItemCapacity: Int
        get() {
            val width = widthDp.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
            val item = itemWidthDp.takeIf { it.isFinite() && it > 0f } ?: return 0
            val spacing = spacingDp.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
            val inset = horizontalInsetDp.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
            val usable = (width - inset * 2f).coerceAtLeast(0f)
            return if (usable < item) 0 else
                (1 + ((usable - item) / (item + spacing).coerceAtLeast(1f)).toInt()).coerceAtLeast(1)
        }
}

internal fun infinityNormalizeDockPreferences(
    preferences: InfinityDockPreferences,
    installedPackages: Set<String>? = null
): InfinityDockPreferences {
    val favorites = preferences.favoritePackageNames
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter { installedPackages == null || it in installedPackages }
        .distinct()
        .take(64)
        .toList()
    return preferences.copy(
        favoritePackageNames = favorites,
        dockScale = preferences.dockScale.takeIf { it.isFinite() }?.coerceIn(0.75f, 1.35f) ?: 1f,
        iconScale = preferences.iconScale.takeIf { it.isFinite() }?.coerceIn(0.7f, 1.4f) ?: 1f,
        transparency = preferences.transparency.takeIf { it.isFinite() }?.coerceIn(0f, 0.7f) ?: 0.12f,
        itemSpacingDp = preferences.itemSpacingDp.takeIf { it.isFinite() }?.coerceIn(2f, 24f) ?: 8f,
        scrollEnabled = preferences.scrollEnabled,
        visible = preferences.visible,
        showRunningIndicators = preferences.showRunningIndicators,
        position = InfinityDockPosition.BOTTOM
    )
}

internal fun infinityDockAddFavorite(
    favorites: List<String>,
    packageName: String,
    installedPackages: Set<String>
): List<String> {
    val candidate = packageName.trim()
    if (candidate.isEmpty() || candidate !in installedPackages) return favorites.distinct()
    return (favorites + candidate).distinct().take(64)
}

internal fun infinityDockRemoveFavorite(favorites: List<String>, packageName: String): List<String> =
    favorites.filterNot { it == packageName }

internal fun infinityDockMoveFavorite(
    favorites: List<String>,
    fromIndex: Int,
    toIndex: Int
): List<String> {
    if (fromIndex !in favorites.indices || toIndex !in favorites.indices || fromIndex == toIndex) {
        return favorites
    }
    return favorites.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

internal fun infinityDockVisibleTasks(tasks: List<InfinityDockTask>): List<InfinityDockTask> =
    tasks.distinctBy { it.id }

internal fun infinityDockVisibleTaskIds(
    taskIds: List<String>,
    visibleLimit: Int
): List<String> {
    val unique = taskIds.distinct()
    if (visibleLimit <= 0) return emptyList()
    return unique.takeLast(visibleLimit)
}

internal fun infinityDockHiddenTaskCount(total: Int, visible: Int): Int =
    (total.coerceAtLeast(0) - visible.coerceAtLeast(0)).coerceAtLeast(0)
