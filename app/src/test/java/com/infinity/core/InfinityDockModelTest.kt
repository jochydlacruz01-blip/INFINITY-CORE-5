package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityDockModelTest {
    @Test
    fun normalizesFavoritesWithoutDuplicatesAndFiltersMissingPackages() {
        val normalized = infinityNormalizeDockPreferences(
            InfinityDockPreferences(favoritePackageNames = listOf(" com.a ", "com.b", "com.a", "", "com.missing")),
            installedPackages = setOf("com.a", "com.b")
        )
        assertEquals(listOf("com.a", "com.b"), normalized.favoritePackageNames)
    }

    @Test
    fun clampsInvalidVisualPreferencesToSafeBounds() {
        val normalized = infinityNormalizeDockPreferences(
            InfinityDockPreferences(
                dockScale = Float.NaN,
                iconScale = 9f,
                transparency = -1f,
                itemSpacingDp = Float.POSITIVE_INFINITY
            )
        )
        assertEquals(1f, normalized.dockScale)
        assertEquals(1.4f, normalized.iconScale)
        assertEquals(0f, normalized.transparency)
        assertEquals(8f, normalized.itemSpacingDp)
        assertEquals(InfinityDockPosition.BOTTOM, normalized.position)
    }

    @Test
    fun addingFavoriteRejectsUnknownAppsAndDoesNotDuplicate() {
        val installed = setOf("com.a", "com.b")
        assertEquals(listOf("com.a", "com.b"), infinityDockAddFavorite(listOf("com.a"), " com.b ", installed))
        assertEquals(listOf("com.a"), infinityDockAddFavorite(listOf("com.a"), "com.a", installed))
        assertEquals(listOf("com.a"), infinityDockAddFavorite(listOf("com.a"), "com.missing", installed))
    }

    @Test
    fun removeAndMoveFavoritePreserveDeterministicOrder() {
        val favorites = listOf("com.a", "com.b", "com.c")
        assertEquals(listOf("com.a", "com.c"), infinityDockRemoveFavorite(favorites, "com.b"))
        assertEquals(listOf("com.b", "com.c", "com.a"), infinityDockMoveFavorite(favorites, 0, 2))
        assertEquals(favorites, infinityDockMoveFavorite(favorites, -1, 2))
    }

    @Test
    fun taskProjectionRemovesDuplicateWindowIdsAndPreservesLiveState() {
        val tasks = listOf(
            InfinityDockTask("w1", minimized = false, focused = true),
            InfinityDockTask("w2", minimized = true, focused = false),
            InfinityDockTask("w1", minimized = true, focused = false)
        )
        assertEquals(tasks.take(2), infinityDockVisibleTasks(tasks))
    }

    @Test
    fun viewportCapacityAndOverflowAreBounded() {
        val viewport = InfinityDockViewport(
            widthDp = 360f,
            itemWidthDp = 48f,
            spacingDp = 8f,
            horizontalInsetDp = 12f
        )
        assertEquals(5, viewport.visibleItemCapacity)
        assertEquals(2, infinityDockCountHiddenTasks(total = 7, visible = 5))
        assertTrue(infinityDockSelectVisibleTaskIds(listOf("a", "b", "c", "d"), 2) == listOf("c", "d"))
        assertFalse(infinityDockSelectVisibleTaskIds(listOf("a", "b"), 0).isNotEmpty())
    }
}
