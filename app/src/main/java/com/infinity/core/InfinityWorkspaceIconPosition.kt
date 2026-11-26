package com.infinity.core

/**
 * Namespaces desktop icon coordinates by workspace so ORBIT spaces keep
 * independent layouts instead of sharing one global set of positions.
 */
internal fun infinityWorkspaceIconPositionKey(workspace: Int, itemId: String, axis: String): String {
    val normalizedId = itemId.trim().lowercase(java.util.Locale.ROOT)
        .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        .take(64)
    require(normalizedId.isNotBlank()) { "Desktop item identity cannot be blank." }
    require(axis == "x" || axis == "y") { "Coordinate axis must be x or y." }
    return "workspace_${infinityNormalizeWorkspace(workspace)}_${normalizedId}_$axis"
}

/** Legacy coordinates are read only for workspace 1 during migration. */
internal fun infinityLegacyWorkspaceIconPositionKey(itemId: String, axis: String): String {
    require(axis == "x" || axis == "y") { "Coordinate axis must be x or y." }
    return "${itemId}_$axis"
}
