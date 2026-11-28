package com.infinity.core

/** Workspace-scoped persistence key for user-pinned desktop application shortcuts. */
internal fun infinityWorkspaceDesktopShortcutKey(workspace: Int): String =
    "workspace_${infinityNormalizeWorkspace(workspace)}_desktop_shortcuts"

/** Stable IDs avoid using package names directly as layout preference keys. */
internal fun infinityDesktopShortcutId(packageName: String): String {
    val normalized = packageName.trim()
    require(normalized.isNotBlank()) { "Application package name cannot be blank." }
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
    val encoded = digest.take(16).joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
    return "app_$encoded"
}

/** Reads a tolerant newline-delimited shortcut list from SharedPreferences. */
internal fun infinityParseDesktopShortcutPackages(serialized: String?): List<String> =
    serialized.orEmpty().lineSequence()
        .map { it.trim() }
        .filter { value -> value.isNotEmpty() && value.length <= 255 && !value.any { character -> character.isWhitespace() } }
        .distinct()
        .take(48)
        .toList()

/** Removes stale/uninstalled entries and preserves the user's stable shortcut order. */
internal fun infinityNormalizeDesktopShortcutPackages(
    shortcuts: List<String>,
    installedPackages: Set<String>
): List<String> = shortcuts.asSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() && it in installedPackages }
    .distinct()
    .take(48)
    .toList()

/** Adds or removes one installed application without changing other shortcut order. */
internal fun infinityToggleDesktopShortcut(
    shortcuts: List<String>,
    packageName: String,
    installedPackages: Set<String>
): List<String> {
    val target = packageName.trim()
    val normalized = shortcuts.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(48)
    if (target.isEmpty()) return normalized
    return if (target in normalized) {
        normalized.filterNot { it == target }
    } else if (target in installedPackages) {
        (normalized + target).distinct().take(48)
    } else {
        normalized
    }
}

/** Stable newline serialization keeps the workspace's shortcut order intact. */
internal fun infinitySerializeDesktopShortcutPackages(shortcuts: List<String>): String =
    shortcuts.map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(48)
        .joinToString("\n")
