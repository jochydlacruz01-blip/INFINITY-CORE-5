package com.infinity.core

import androidx.compose.ui.graphics.Color

/** Shared five-theme palette used by the desktop shell and its window surfaces. */
internal fun infinityThemeAccentColor(themeId: String, lightMode: Boolean = false): Color {
    if (lightMode) return when (themeId) {
        "ocean" -> Color(0xFF0069A8)
        "violet" -> Color(0xFF6840A8)
        "emerald" -> Color(0xFF147A4B)
        "sunset" -> Color(0xFFA64A13)
        else -> Color(0xFF087D74)
    }
    return when (themeId) {
        "ocean" -> Color(0xFF45B7FF)
        "violet" -> Color(0xFFB69CFF)
        "emerald" -> Color(0xFF55D6A4)
        "sunset" -> Color(0xFFFFA66B)
        else -> Color(0xFF56D6C9)
    }
}

/** Background stops intentionally change with the selected theme, not just its label/accent. */
internal fun infinityThemeBackgroundStops(themeId: String, lightMode: Boolean): List<Color> {
    if (lightMode) return when (themeId) {
        "ocean" -> listOf(Color(0xFFEAF7FF), Color(0xFFD2EFFF), Color(0xFFB9DDF4))
        "violet" -> listOf(Color(0xFFF4EFFF), Color(0xFFE6DDF7), Color(0xFFD3C8EC))
        "emerald" -> listOf(Color(0xFFEAF8F0), Color(0xFFD5EBDD), Color(0xFFBDDCCB))
        "sunset" -> listOf(Color(0xFFFFF2E8), Color(0xFFF8E0D0), Color(0xFFECC9B5))
        else -> listOf(Color(0xFFEAF1FA), Color(0xFFD4E2F2), Color(0xFFB9CDE2))
    }
    return when (themeId) {
        "ocean" -> listOf(Color(0xFF031326), Color(0xFF073B68), Color(0xFF07566A))
        "violet" -> listOf(Color(0xFF100B24), Color(0xFF34205D), Color(0xFF1A2448))
        "emerald" -> listOf(Color(0xFF061B19), Color(0xFF0C493D), Color(0xFF123B4B))
        "sunset" -> listOf(Color(0xFF241018), Color(0xFF653128), Color(0xFF49304C))
        else -> listOf(Color(0xFF030A17), Color(0xFF102A49), Color(0xFF08111F))
    }
}
