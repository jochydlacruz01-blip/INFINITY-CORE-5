package com.infinity.core

/** Human-readable names for the three persistent ORBIT workspaces. */
internal fun infinityWorkspaceDisplayName(workspace: Int): String = when (infinityNormalizeWorkspace(workspace)) {
    1 -> "Principal"
    2 -> "Trabajo"
    else -> "Personal"
}

/** Accessible selector label; includes the number and the stable workspace name. */
internal fun infinityWorkspaceAccessibilityLabel(workspace: Int): String {
    val normalized = infinityNormalizeWorkspace(workspace)
    return "Espacio $normalized, ${infinityWorkspaceDisplayName(normalized)}"
}

/** Stable preference key for remembering the active ORBIT space. */
internal fun infinityActiveWorkspacePreferenceKey(): String = "active_workspace"
