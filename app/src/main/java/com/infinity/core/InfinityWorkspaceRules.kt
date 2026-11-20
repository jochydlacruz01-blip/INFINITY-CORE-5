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
