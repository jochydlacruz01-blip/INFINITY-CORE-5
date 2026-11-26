package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityStartCatalogPolicyTest {
    @Test fun queryMatchesLabelAndPackageCaseInsensitively() {
        assertTrue(InfinityStartCatalogPolicy.matches("Google Drive", "com.google.android.apps.docs", " DRIVE ", "all"))
        assertTrue(InfinityStartCatalogPolicy.matches("Maps", "com.google.android.apps.maps", "GOOGLE", "all"))
        assertFalse(InfinityStartCatalogPolicy.matches("Maps", "com.google.android.apps.maps", "calendar", "all"))
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
    }
}
