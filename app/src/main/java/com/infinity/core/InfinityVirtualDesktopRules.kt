package com.infinity.core

internal const val INFINITY_VIRTUAL_DESKTOP_COUNT = 3

internal data class InfinityVirtualDesktopProfile(
    val id: Int,
    val name: String,
    val wallpaperUri: String? = null,
    val backgroundPreset: String = "interstellar"
)

internal fun infinityDefaultVirtualDesktopName(workspace: Int): String = when (infinityNormalizeWorkspace(workspace)) {
    1 -> "PRINCIPAL"
    2 -> "TRABAJO"
    else -> "PERSONAL"
}

internal fun infinityNormalizeVirtualDesktopName(value: String?, workspace: Int): String {
    val normalized = value.orEmpty().trim().replace(Regex("\\s+"), " ").take(24)
    return normalized.ifBlank { infinityDefaultVirtualDesktopName(workspace) }
}

internal fun infinityVirtualDesktopNameKey(workspace: Int): String =
    "workspace_${infinityNormalizeWorkspace(workspace)}_name"

internal fun infinityVirtualDesktopWallpaperKey(workspace: Int): String =
    "workspace_${infinityNormalizeWorkspace(workspace)}_wallpaper_uri"

internal fun infinityVirtualDesktopBackgroundPresetKey(workspace: Int): String =
    "workspace_${infinityNormalizeWorkspace(workspace)}_background_preset"

internal fun infinityResolveLegacyWorkspaceOneWallpaper(
    workspace: Int,
    workspaceWallpaper: String?,
    legacyGlobalWallpaper: String?
): String? {
    if (infinityNormalizeWorkspace(workspace) != 1) return workspaceWallpaper
    return workspaceWallpaper ?: legacyGlobalWallpaper
}

internal fun infinityResolveWorkspaceSwipe(
    currentWorkspace: Int,
    horizontalDeltaPx: Float,
    verticalDeltaPx: Float,
    viewportWidthPx: Float
): Int {
    val current = infinityNormalizeWorkspace(currentWorkspace)
    if (!horizontalDeltaPx.isFinite() || !verticalDeltaPx.isFinite() ||
        !viewportWidthPx.isFinite() || viewportWidthPx <= 0f
    ) return current
    val threshold = maxOf(72f, viewportWidthPx * 0.18f)
    if (kotlin.math.abs(horizontalDeltaPx) < threshold ||
        kotlin.math.abs(horizontalDeltaPx) < kotlin.math.abs(verticalDeltaPx) * 1.25f
    ) return current
    return when {
        horizontalDeltaPx < 0f -> (current + 1).coerceAtMost(INFINITY_VIRTUAL_DESKTOP_COUNT)
        else -> (current - 1).coerceAtLeast(1)
    }
}

internal fun infinityCopyWorkspaceShortcut(
    sourcePackages: List<String>,
    targetPackages: List<String>,
    packageName: String
): List<String> {
    val normalized = packageName.trim()
    if (normalized.isBlank()) return targetPackages
    return (targetPackages + normalized).distinct()
}

internal fun infinityMoveWorkspaceShortcut(
    sourcePackages: List<String>,
    targetPackages: List<String>,
    packageName: String
): Pair<List<String>, List<String>> {
    val normalized = packageName.trim()
    if (normalized.isBlank()) return sourcePackages to targetPackages
    return sourcePackages.filterNot { it == normalized } to (targetPackages + normalized).distinct()
}

internal fun infinityMoveAuroraWindow(
    window: InfinityAuroraWindow,
    kind: String,
    targetWorkspace: Int
): InfinityAuroraWindow {
    val target = infinityNormalizeWorkspace(targetWorkspace)
    val canonicalKind = infinityCanonicalWindowKind(kind)
        ?: throw IllegalArgumentException("Window kind cannot be blank.")
    return window.copy(id = infinityWindowIdentity(canonicalKind, target), workspace = target)
}

internal fun infinityCanMoveAuroraWindow(
    windows: List<InfinityAuroraWindow>,
    windowId: String,
    kind: String,
    targetWorkspace: Int
): Boolean {
    val targetId = infinitySafeWindowIdentity(kind, targetWorkspace) ?: return false
    val source = windows.firstOrNull { it.id == windowId } ?: return false
    if (source.workspace == infinityNormalizeWorkspace(targetWorkspace)) return false
    return windows.none { it.id == targetId && it.id != windowId }
}
