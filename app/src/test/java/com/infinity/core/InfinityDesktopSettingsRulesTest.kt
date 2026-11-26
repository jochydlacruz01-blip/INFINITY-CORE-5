package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityDesktopSettingsRulesTest {
    @Test fun iconSizeIsBounded() {
        assertEquals(40f, infinityNormalizeIconSize(1f))
        assertEquals(72f, infinityNormalizeIconSize(100f))
        assertEquals(56f, infinityNormalizeIconSize(56f))
    }

    @Test fun invalidNonFiniteIconSizesUseSafeDefault() {
        assertEquals(48f, infinityNormalizeIconSize(Float.NaN))
        assertEquals(48f, infinityNormalizeIconSize(Float.POSITIVE_INFINITY))
        assertEquals(48f, infinityNormalizeIconSize(Float.NEGATIVE_INFINITY))
    }

    @Test fun themeChoiceAcceptsOnlyKnownThemes() {
        assertEquals("ocean", infinityNormalizeThemeChoice(" OCEAN "))
        assertEquals("midnight", infinityNormalizeThemeChoice("unknown"))
        assertEquals("midnight", infinityNormalizeThemeChoice(null))
    }

    @Test fun desktopIdentityAllowsOnlyInfinityAndWindows11() {
        assertEquals("windows11", infinityNormalizeDesktopTheme(" WINDOWS11 "))
        assertEquals("infinity", infinityNormalizeDesktopTheme("infinity"))
        assertEquals("infinity", infinityNormalizeDesktopTheme("apple"))
        assertEquals("infinity", infinityNormalizeDesktopTheme("unsupported"))
        assertEquals("infinity", infinityNormalizeDesktopTheme(null))
    }

    @Test fun appearanceAcceptsOnlyDarkOrLight() {
        assertEquals("light", infinityNormalizeAppearance(" LIGHT "))
        assertEquals("dark", infinityNormalizeAppearance("invalid"))
        assertEquals("dark", infinityNormalizeAppearance(null))
    }
}
