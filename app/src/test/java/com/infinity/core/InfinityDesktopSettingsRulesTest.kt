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

    @Test fun backgroundPresetAcceptsOnlyBuiltInChoices() {
        assertEquals("interstellar", infinityNormalizeBackgroundPreset(" INTERSTELLAR "))
        assertEquals("punk", infinityNormalizeBackgroundPreset("punk"))
        assertEquals("nebula", infinityNormalizeBackgroundPreset("unknown"))
        assertEquals("nebula", infinityNormalizeBackgroundPreset(null))
        assertEquals("nebula", infinityNormalizeBackgroundPreset(""))
    }

    @Test fun backgroundPresetCatalogMatchesVisibleDesktopChoices() {
        assertEquals(setOf("nebula", "interstellar", "punk", "aurora"), INFINITY_BACKGROUND_PRESET_IDS)
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

    @Test fun compactBrandingClearsContextMenuAndTabletLayoutStaysUnchanged() {
        assertEquals(76f, infinityDesktopBrandingTopInset(320f))
        assertEquals(76f, infinityDesktopBrandingTopInset(599.9f))
        assertEquals(18f, infinityDesktopBrandingTopInset(600f))
        assertEquals(18f, infinityDesktopBrandingTopInset(900f))
    }

    @Test fun compactBrandingMaximumWidthRespectsNarrowViewports() {
        assertEquals(64f, infinityDesktopBrandingMaxWidth(240f))
        assertEquals(100f, infinityDesktopBrandingMaxWidth(280f))
        assertEquals(140f, infinityDesktopBrandingMaxWidth(320f))
        assertEquals(420f, infinityDesktopBrandingMaxWidth(600f))
        assertEquals(120f, infinityDesktopBrandingMaxWidth(Float.NaN))
        assertEquals(120f, infinityDesktopBrandingMaxWidth(Float.POSITIVE_INFINITY))
    }

    @Test fun compactBrandingTitleScalesToAvailableWidth() {
        assertEquals(20f, infinityDesktopBrandingTitleSize(320f))
        assertEquals(24f, infinityDesktopBrandingTitleSize(390f))
        assertEquals(30f, infinityDesktopBrandingTitleSize(600f))
        assertEquals(30f, infinityDesktopBrandingTitleSize(Float.NaN))
    }

    @Test fun compactBrandingLetterSpacingScalesWithTitle() {
        assertEquals(2f, infinityDesktopBrandingTitleLetterSpacing(320f))
        assertEquals(3f, infinityDesktopBrandingTitleLetterSpacing(390f))
        assertEquals(5f, infinityDesktopBrandingTitleLetterSpacing(600f))
        assertEquals(5f, infinityDesktopBrandingTitleLetterSpacing(Float.POSITIVE_INFINITY))
    }

    @Test fun invalidViewportWidthUsesSafeTabletInset() {
        assertEquals(18f, infinityDesktopBrandingTopInset(Float.NaN))
        assertEquals(18f, infinityDesktopBrandingTopInset(Float.POSITIVE_INFINITY))
        assertEquals(18f, infinityDesktopBrandingTopInset(Float.NEGATIVE_INFINITY))
    }
}
