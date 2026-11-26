package com.infinity.core

/**
 * Gives each desktop workspace its own persisted focus slot. The legacy global
 * focus key remains a migration fallback for installations upgraded in place.
 */
internal fun infinityWorkspaceFocusPreferenceKey(workspace: Int): String =
    "focused_window_id_workspace_${infinityNormalizeWorkspace(workspace)}"

/** Restores only a visible window belonging to the requested workspace. */
internal fun infinityResolveRememberedWorkspaceFocus(
    savedFocusId: String?,
    orderedIds: List<String>,
    workspaceById: Map<String, Int>,
    minimizedIds: Set<String>,
    targetWorkspace: Int
): String? = infinityResolveRestoredWindowFocus(
    savedFocusId, orderedIds, workspaceById, minimizedIds, targetWorkspace
)
