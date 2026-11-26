package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class InfinityStartCatalogPolicyTest {
    @Test fun queryMatchesLabelAndPackageCaseInsensitively() {
        assertTrue(InfinityStartCatalogPolicy.matches("Google Drive", "com.google.android.apps.docs", " DRIVE ", "all"))
        assertTrue(InfinityStartCatalogPolicy.matches("Maps", "com.google.android.apps.maps", "GOOGLE", "all"))
        assertFalse(InfinityStartCatalogPolicy.matches("Maps", "com.google.android.apps.maps", "calendar", "all"))
    }

    @Test fun searchNormalizationIsIndependentOfDeviceLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(
                InfinityStartCatalogPolicy.matches(
                    "INFINITY Desktop", "com.infinity.desktop", "infinity", "all"
                )
            )
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test fun categoriesClassifyCommonAppFamilies() {
        assertEquals("communication", InfinityStartCatalogPolicy.categoryOf("WhatsApp", "com.whatsapp"))
        assertEquals("media", InfinityStartCatalogPolicy.categoryOf("YouTube", "com.google.android.youtube"))
        assertEquals("games", InfinityStartCatalogPolicy.categoryOf("Game Center", "com.example.game"))
        assertEquals("system", InfinityStartCatalogPolicy.categoryOf("Android Settings", "com.android.settings"))
    }

    @Test fun categoryAndQueryMustBothMatch() {
        assertTrue(InfinityStartCatalogPolicy.matches("WhatsApp", "com.whatsapp", "what", "communication"))
        assertFalse(InfinityStartCatalogPolicy.matches("WhatsApp", "com.whatsapp", "what", "media"))
        assertFalse(InfinityStartCatalogPolicy.matches("WhatsApp", "com.whatsapp", "youtube", "communication"))
    }

    @Test fun unknownCategoryDoesNotAccidentallyMatch() {
        assertFalse(InfinityStartCatalogPolicy.matches("Notes", "com.example.notes", "", "unknown"))
        assertFalse(InfinityStartCatalogPolicy.matchesDesktopTool("notes", "Notas rápidas", "", "unknown"))
    }

    @Test fun internalToolsShareSearchAndCategoryFiltering() {
        assertEquals("productivity", InfinityStartCatalogPolicy.desktopToolCategory("notes"))
        assertEquals("communication", InfinityStartCatalogPolicy.desktopToolCategory("connect"))
        assertTrue(InfinityStartCatalogPolicy.matchesDesktopTool("notes", "Notas rápidas", "NOTAS", "productivity"))
        assertFalse(InfinityStartCatalogPolicy.matchesDesktopTool("notes", "Notas rápidas", "NOTAS", "system"))
        assertTrue(InfinityStartCatalogPolicy.matchesDesktopTool("connect", "CONNECT", "", "communication"))
        assertFalse(InfinityStartCatalogPolicy.matchesDesktopTool("connect", "CONNECT", "files", "communication"))
    }
}
