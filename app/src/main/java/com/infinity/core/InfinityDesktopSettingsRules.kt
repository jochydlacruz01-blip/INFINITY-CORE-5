package com.infinity.core

/** Validates persisted desktop preferences so corrupted or legacy values cannot leak into UI state. */
internal fun infinityNormalizeIconSize(value: Float): Float =
    if (value.isFinite()) value.coerceIn(40f, 72f) else 48f

internal fun infinityNormalizeThemeChoice(value: String?): String =
    value?.trim()?.lowercase(java.util.Locale.ROOT)
        ?.takeIf { it in setOf("midnight", "ocean", "violet", "emerald", "sunset") }
        ?: "midnight"

internal fun infinityNormalizeAppearance(value: String?): String =
    value?.trim()?.lowercase(java.util.Locale.ROOT)
        ?.takeIf { it == "dark" || it == "light" }
        ?: "dark"

/** Restricts desktop identities to the supported INFINITY and Windows 11 shells. */
internal fun infinityNormalizeDesktopTheme(value: String?): String =
    value?.trim()?.lowercase(java.util.Locale.ROOT)
        ?.takeIf { it == "windows11" }
        ?: "infinity"
