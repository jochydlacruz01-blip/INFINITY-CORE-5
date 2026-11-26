package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityFuturisticDesktopPresetTest {
    @Test fun catalogContainsLegacyAndFuturisticPresets() {
        assertEquals(2, InfinityDesktopWallpaperPreset.entries.size)
        assertEquals("legacy", InfinityDesktopWallpaperPreset.LEGACY.id)
        assertEquals("futuristic", InfinityDesktopWallpaperPreset.FUTURISTIC.id)
    }
    @Test fun invalidSavedPresetFallsBackSafely() {
        assertEquals(InfinityDesktopWallpaperPreset.LEGACY, infinityNormalizeWallpaperPreset(null))
        assertEquals(InfinityDesktopWallpaperPreset.LEGACY, infinityNormalizeWallpaperPreset("unknown"))
    }
    @Test fun presetIdsResolve() {
        assertEquals(InfinityDesktopWallpaperPreset.FUTURISTIC, infinityNormalizeWallpaperPreset("futuristic"))
    }
    @Test fun layoutContractKeepsReferenceAreas() {
        val layout = infinityFuturisticLayoutContract()
        assertTrue(layout.iconColumn)
        assertTrue(layout.clockTopRight)
        assertTrue(layout.dockBottom)
        assertTrue(layout.systemTrayBottomRight)
        assertTrue(layout.startSearchInDock)
        assertTrue(layout.desktopContextMenu)
    }
}
