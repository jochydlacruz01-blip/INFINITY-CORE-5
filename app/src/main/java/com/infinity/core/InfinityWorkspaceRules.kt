package com.infinity.core

/** Keeps workspace selection within the three supported desktop spaces. */
internal fun infinityNormalizeWorkspace(target: Int): Int = target.coerceIn(1, 3)

/** Stable identity prevents duplicate module windows within one workspace. */
internal fun infinityWindowIdentity(kind: String, workspace: Int): String =
    "${kind}_${infinityNormalizeWorkspace(workspace)}"

/** Rejects blank window types and canonicalizes identity tokens for stable matching. */
internal fun infinityCanonicalWindowKind(kind: String?): String? {
    val normalized = kind?.trim()?.lowercase(java.util.Locale.ROOT)
        ?.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        ?.take(64)
        ?.takeIf { it.isNotBlank() }
    return normalized
}

/** Builds a stable identity only from a usable canonical window kind. */
internal fun infinitySafeWindowIdentity(kind: String?, workspace: Int): String? =
    infinityCanonicalWindowKind(kind)?.let { infinityWindowIdentity(it, workspace) }

/** Keep current focus when valid; otherwise activate the most recently listed eligible window. */
internal fun infinityResolveFocusedWindow(currentId: String?, eligibleIds: List<String>): String? =
    currentId?.takeIf { it in eligibleIds } ?: eligibleIds.lastOrNull()

/** Dock task list only includes windows belonging to the active workspace. */
internal fun infinityDockWindowIds(windowIds: List<String>, minimizedById: Map<String, Boolean>): List<String> =
    windowIds.filter { it in minimizedById.keys }

/** Limits visible task shortcuts so primary controls and the clock remain on-screen. */
internal fun infinityDockVisibleTaskLimit(availableWidthDp: Float): Int = when {
    !availableWidthDp.isFinite() || availableWidthDp < 600f -> 1
    availableWidthDp < 720f -> 2
    availableWidthDp < 960f -> 3
    else -> 5
}

/** Counts hidden task shortcuts without allowing invalid or negative counts. */
internal fun infinityDockHiddenTaskCount(totalTasks: Int, visibleTasks: Int): Int =
    (totalTasks.coerceAtLeast(0) - visibleTasks.coerceAtLeast(0)).coerceAtLeast(0)

/** Window geometry controls are available only when a desktop/tablet viewport can use them. */
internal fun infinityWindowGeometryControlsVisible(availableWidthDp: Float): Boolean =
    availableWidthDp.isFinite() && availableWidthDp >= 600f

/** Maximized and phone-adapted windows must not mutate their saved desktop geometry. */
internal fun infinityWindowCanManipulateGeometry(isMaximized: Boolean, availableWidthDp: Float): Boolean =
    !isMaximized && availableWidthDp.isFinite() && availableWidthDp >= 600f

/** Dock tap toggles minimization only for the currently focused, visible task. */
internal fun infinityDockTapMinimizes(taskId: String, currentFocusId: String?, minimized: Boolean): Boolean =
    !minimized && taskId == currentFocusId

/** Compact, readable Dock labels: normalize whitespace and add an ellipsis when needed. */
internal fun infinityDockLabel(title: String, maxLength: Int = 10): String {
    val clean = title.trim().replace(Regex("\\s+"), " ")
    val limit = maxLength.coerceAtLeast(2)
    return if (clean.length <= limit) clean.uppercase(java.util.Locale.ROOT)
    else clean.take(limit - 1).trimEnd().uppercase(java.util.Locale.ROOT) + "…"
}

/** Filters desktop window IDs to the requested normalized workspace. */
internal fun infinityWorkspaceWindowIds(windowIds: List<String>, workspaceById: Map<String, Int>, activeWorkspace: Int): List<String> {
    val target = infinityNormalizeWorkspace(activeWorkspace)
    return windowIds.filter { id -> workspaceById[id]?.let(::infinityNormalizeWorkspace) == target }
}

/** Selects focus only among non-minimized windows in the normalized target workspace. */
internal fun infinityResolveWorkspaceFocus(
    currentId: String?,
    orderedIds: List<String>,
    workspaceById: Map<String, Int>,
    minimizedIds: Set<String>,
    targetWorkspace: Int
): String? {
    val eligible = infinityWorkspaceWindowIds(orderedIds, workspaceById, targetWorkspace)
        .filterNot { it in minimizedIds }
    return infinityResolveFocusedWindow(currentId, eligible)
}

/** Dock activation policy: restore minimized or unfocused tasks; minimize only the focused visible task. */
internal fun infinityDockResolveActivation(taskId: String, currentFocusId: String?, minimized: Boolean): String? =
    if (minimized || taskId != currentFocusId) taskId else null

/** Focus fallback for workspace switching: retain focus only if eligible in the target space. */
internal fun infinityResolveWorkspaceSwitchFocus(
    currentId: String?, orderedIds: List<String>, workspaceById: Map<String, Int>, minimizedIds: Set<String>, targetWorkspace: Int
): String? = infinityResolveWorkspaceFocus(currentId, orderedIds, workspaceById, minimizedIds, targetWorkspace)


/** Recovers persisted window coordinates from corrupt, non-finite or extreme values. */
internal fun infinityNormalizeWindowCoordinate(
    value: Float,
    fallback: Float,
    minimum: Float = -500f,
    maximum: Float = 5000f
): Float {
    val safeFallback = fallback.takeIf { it.isFinite() } ?: 0f
    val lower = minimum.takeIf { it.isFinite() } ?: -500f
    val upper = maximum.takeIf { it.isFinite() && it >= lower } ?: lower
    return (value.takeIf { it.isFinite() } ?: safeFallback).coerceIn(lower, upper)
}

/** Restores valid persisted window dimensions while enforcing the supported size range. */
internal fun infinityNormalizeWindowDimension(
    value: Float,
    fallback: Float,
    minimum: Float,
    maximum: Float
): Float {
    val lower = minimum.takeIf { it.isFinite() && it > 0f } ?: 1f
    val upper = maximum.takeIf { it.isFinite() && it >= lower } ?: lower
    val safeFallback = fallback.takeIf { it.isFinite() } ?: lower
    return (value.takeIf { it.isFinite() } ?: safeFallback).coerceIn(lower, upper)
}


/** Keeps focus valid after closing a window, choosing the most recent visible task in the active workspace. */
internal fun infinityResolveFocusAfterWindowDeactivation(
    removedId: String,
    currentId: String?,
    orderedIds: List<String>,
    workspaceById: Map<String, Int>,
    minimizedIds: Set<String>,
    activeWorkspace: Int
): String? {
    val preferred = currentId?.takeIf { it != removedId }
    return infinityResolveWorkspaceFocus(
        preferred, orderedIds, workspaceById, minimizedIds, activeWorkspace
    )
}

/** Restores the last focused task only when it is visible in the active workspace. */
internal fun infinityResolveRestoredWindowFocus(
    savedFocusId: String?,
    orderedIds: List<String>,
    workspaceById: Map<String, Int>,
    minimizedIds: Set<String>,
    activeWorkspace: Int
): String? = infinityResolveWorkspaceFocus(
    savedFocusId, orderedIds, workspaceById, minimizedIds, activeWorkspace
)
