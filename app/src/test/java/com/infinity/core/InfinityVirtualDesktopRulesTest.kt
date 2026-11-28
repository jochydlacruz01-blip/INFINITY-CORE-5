package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityVirtualDesktopRulesTest {
    @Test fun exactlyThreeStableDesktopIdentitiesAreSupported() {
        assertEquals(3, INFINITY_VIRTUAL_DESKTOP_COUNT)
        assertEquals("PRINCIPAL", infinityDefaultVirtualDesktopName(1))
        assertEquals("TRABAJO", infinityDefaultVirtualDesktopName(2))
        assertEquals("PERSONAL", infinityDefaultVirtualDesktopName(3))
    }

    @Test fun workspaceNamesAreNormalizedAndBounded() {
        assertEquals("MI ESCRITORIO", infinityNormalizeVirtualDesktopName("  MI   ESCRITORIO ", 2))
        assertEquals("TRABAJO", infinityNormalizeVirtualDesktopName("   ", 2))
        assertEquals(24, infinityNormalizeVirtualDesktopName("x".repeat(40), 1).length)
    }

    @Test fun preferenceKeysAreIndependentAndStable() {
        assertEquals("workspace_1_name", infinityVirtualDesktopNameKey(1))
        assertEquals("workspace_2_wallpaper_uri", infinityVirtualDesktopWallpaperKey(2))
        assertEquals("workspace_3_background_preset", infinityVirtualDesktopBackgroundPresetKey(3))
        assertNotEquals(infinityVirtualDesktopWallpaperKey(1), infinityVirtualDesktopWallpaperKey(2))
    }

    @Test fun legacyWallpaperMigratesOnlyIntoWorkspaceOne() {
        assertEquals("content://legacy", infinityResolveLegacyWorkspaceOneWallpaper(1, null, "content://legacy"))
        assertEquals("content://new", infinityResolveLegacyWorkspaceOneWallpaper(1, "content://new", "content://legacy"))
        assertEquals(null, infinityResolveLegacyWorkspaceOneWallpaper(2, null, "content://legacy"))
        assertEquals("content://space3", infinityResolveLegacyWorkspaceOneWallpaper(3, "content://space3", "content://legacy"))
    }

    @Test fun horizontalSwipeChangesOneWorkspaceAndClampsAtEnds() {
        assertEquals(2, infinityResolveWorkspaceSwipe(1, -220f, 12f, 1000f))
        assertEquals(1, infinityResolveWorkspaceSwipe(2, 220f, 12f, 1000f))
        assertEquals(3, infinityResolveWorkspaceSwipe(3, -400f, 0f, 1000f))
        assertEquals(1, infinityResolveWorkspaceSwipe(1, 400f, 0f, 1000f))
    }

    @Test fun swipeRejectsShortVerticalAndInvalidGestures() {
        assertEquals(2, infinityResolveWorkspaceSwipe(2, -40f, 0f, 1000f))
        assertEquals(2, infinityResolveWorkspaceSwipe(2, -200f, 220f, 1000f))
        assertEquals(2, infinityResolveWorkspaceSwipe(2, Float.NaN, 0f, 1000f))
        assertEquals(2, infinityResolveWorkspaceSwipe(2, -200f, 0f, Float.NaN))
    }

    @Test fun copyingShortcutDoesNotRemoveItFromSourceAndAvoidsDuplicates() {
        val source = listOf("com.example.mail")
        val target = infinityCopyWorkspaceShortcut(source, listOf("com.example.maps"), "com.example.mail")
        assertEquals(listOf("com.example.mail"), source)
        assertEquals(listOf("com.example.maps", "com.example.mail"), target)
        assertEquals(target, infinityCopyWorkspaceShortcut(source, target, "com.example.mail"))
    }

    @Test fun movingShortcutRemovesOnlyShortcutFromSource() {
        val (source, target) = infinityMoveWorkspaceShortcut(
            listOf("com.example.mail", "com.example.notes"),
            listOf("com.example.maps"),
            "com.example.mail"
        )
        assertEquals(listOf("com.example.notes"), source)
        assertEquals(listOf("com.example.maps", "com.example.mail"), target)
    }

    @Test fun blankShortcutPackageIsIgnored() {
        assertEquals(emptyList<String>(), infinityCopyWorkspaceShortcut(emptyList(), emptyList(), " "))
        assertEquals(Pair(listOf("a"), listOf("b")), infinityMoveWorkspaceShortcut(listOf("a"), listOf("b"), " "))
    }

    @Test fun movingWindowRekeysItAndPreservesCompleteWindowState() {
        val original = InfinityAuroraWindow(
            id = "notes_1", workspace = 1, x = 42f, y = 80f, width = 500f, height = 400f,
            minimized = true, maximized = true, zIndex = 9L
        )
        val moved = infinityMoveAuroraWindow(original, "notes", 3)
        assertEquals("notes_3", moved.id)
        assertEquals(3, moved.workspace)
        assertEquals(42f, moved.x)
        assertEquals(80f, moved.y)
        assertEquals(500f, moved.width)
        assertEquals(400f, moved.height)
        assertEquals(true, moved.minimized)
        assertEquals(true, moved.maximized)
        assertEquals(9L, moved.zIndex)
    }

    @Test fun movingWindowNormalizesTargetWorkspaceAndCanonicalKind() {
        val original = InfinityAuroraWindow(
            id = "notes_1", workspace = 1, x = 12f, y = 24f, width = 300f, height = 240f
        )
        val moved = infinityMoveAuroraWindow(original, " NOTES ", 99)
        assertEquals("notes_3", moved.id)
        assertEquals(3, moved.workspace)
        assertEquals(original.copy(id = "notes_3", workspace = 3), moved)
    }

    @Test fun movingWindowRejectsBlankKind() {
        val original = InfinityAuroraWindow(
            id = "notes_1", workspace = 1, x = 12f, y = 24f, width = 300f, height = 240f
        )
        assertTrue(runCatching { infinityMoveAuroraWindow(original, "   ", 2) }.isFailure)
    }

    @Test fun windowMoveRejectsMissingSourceSameWorkspaceAndDestinationCollision() {
        val first = InfinityAuroraWindow("notes_1", 1, 0f, 0f, 300f, 200f)
        val second = InfinityAuroraWindow("notes_2", 2, 0f, 0f, 300f, 200f)
        assertFalse(infinityCanMoveAuroraWindow(listOf(first), "missing", "notes", 2))
        assertFalse(infinityCanMoveAuroraWindow(listOf(first), "notes_1", "notes", 1))
        assertFalse(infinityCanMoveAuroraWindow(listOf(first, second), "notes_1", "notes", 2))
        assertTrue(infinityCanMoveAuroraWindow(listOf(first), "notes_1", "notes", 3))
    }

    @Test fun desktopIconCoordinatesUseIndependentWorkspaceKeys() {
        val firstX = infinityWorkspaceIconPositionKey(1, "files", "x")
        val secondX = infinityWorkspaceIconPositionKey(2, "files", "x")
        val thirdX = infinityWorkspaceIconPositionKey(3, "files", "x")
        assertEquals("workspace_1_files_x", firstX)
        assertEquals("workspace_2_files_x", secondX)
        assertEquals("workspace_3_files_x", thirdX)
        assertNotEquals(firstX, secondX)
        assertNotEquals(secondX, thirdX)
        assertNotEquals(
            infinityWorkspaceIconPositionKey(1, "files", "x"),
            infinityWorkspaceIconPositionKey(1, "files", "y")
        )
    }

    @Test fun desktopIconCoordinateKeysNormalizeWorkspaceAndItemIdentity() {
        assertEquals(
            infinityWorkspaceIconPositionKey(1, " Files ", "x"),
            infinityWorkspaceIconPositionKey(0, "files", "x")
        )
        assertEquals(
            infinityWorkspaceIconPositionKey(3, "FILES!", "y"),
            infinityWorkspaceIconPositionKey(9, "files", "y")
        )
        assertEquals("workspace_2_my-app_y", infinityWorkspaceIconPositionKey(2, " My-App ", "y"))
    }

    @Test fun desktopIconCoordinateKeysRejectInvalidIdentityAndAxis() {
        assertTrue(runCatching { infinityWorkspaceIconPositionKey(1, " !!! ", "x") }.isFailure)
        assertTrue(runCatching { infinityWorkspaceIconPositionKey(1, "files", "z") }.isFailure)
    }

}
