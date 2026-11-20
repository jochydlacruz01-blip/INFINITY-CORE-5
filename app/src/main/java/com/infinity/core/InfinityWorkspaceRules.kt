package com.infinity.core

/** Keeps workspace selection within the three supported desktop spaces. */
internal fun infinityNormalizeWorkspace(target: Int): Int = target.coerceIn(1, 3)

/** Stable identity prevents duplicate module windows within one workspace. */
internal fun infinityWindowIdentity(kind: String, workspace: Int): String =
    "${kind}_${infinityNormalizeWorkspace(workspace)}"
