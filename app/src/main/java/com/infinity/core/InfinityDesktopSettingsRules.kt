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

/**
 * Keeps compact-shell branding below the context-menu hit target.
 * Tablet reference desktops retain the original top alignment.
 */
internal fun infinityDesktopBrandingTopInset(viewportWidthDp: Float): Float =
    if (viewportWidthDp.isFinite() && viewportWidthDp < 600f) 76f else 18f

/**
 * Reserves space for the compact clock and outer margins without forcing
 * branding wider than the viewport on very narrow devices.
 */
internal fun infinityDesktopBrandingMaxWidth(viewportWidthDp: Float): Float =
    if (viewportWidthDp.isFinite()) (viewportWidthDp - 180f).coerceAtLeast(64f) else 120f

/** Responsive title sizing keeps compact desktop branding clear beside the clock. */
internal fun infinityDesktopBrandingTitleSize(viewportWidthDp: Float): Float = when {
    !viewportWidthDp.isFinite() -> 30f
    viewportWidthDp < 360f -> 20f
    viewportWidthDp < 420f -> 24f
    else -> 30f
}

internal fun infinityDesktopBrandingTitleLetterSpacing(viewportWidthDp: Float): Float = when {
    !viewportWidthDp.isFinite() -> 5f
    viewportWidthDp < 360f -> 2f
    viewportWidthDp < 420f -> 3f
    else -> 5f
}

/** Accepts only built-in wallpaper presets; unknown persisted values fall back safely. */
internal fun infinityNormalizeBackgroundPreset(value: String?): String =
    value?.trim()?.lowercase(java.util.Locale.ROOT)
        ?.takeIf { it in INFINITY_BACKGROUND_PRESET_IDS }
        ?: "nebula"

internal val INFINITY_BACKGROUND_PRESET_IDS: Set<String> =
    setOf("nebula", "interstellar", "punk", "aurora")
