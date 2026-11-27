package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InfinityDesktopThemePaletteTest {
    @Test
    fun allFiveThemesHaveDistinctAccentAndDarkBackgroundPalettes() {
        val themes = listOf("midnight", "ocean", "violet", "emerald", "sunset")
        val accents = themes.map(::infinityThemeAccentColor)
        val backgrounds = themes.map { infinityThemeBackgroundStops(it, lightMode = false) }

        assertEquals(5, accents.distinct().size)
        assertEquals(5, backgrounds.distinct().size)
        themes.forEach { theme ->
            assertEquals(3, infinityThemeBackgroundStops(theme, lightMode = false).size)
            assertEquals(3, infinityThemeBackgroundStops(theme, lightMode = true).size)
        }
    }

    @Test
    fun lightAndDarkModesUseDifferentBackgroundPalettes() {
        listOf("midnight", "ocean", "violet", "emerald", "sunset").forEach { theme ->
            assertNotEquals(
                infinityThemeBackgroundStops(theme, false),
                infinityThemeBackgroundStops(theme, true)
            )
        }
    }
}
