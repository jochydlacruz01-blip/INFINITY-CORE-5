package com.infinity.core

/** Catalog and layout contract for the image-faithful INFINITY desktop preset. */
enum class InfinityDesktopWallpaperPreset(val id: String, val title: String, val description: String) {
    LEGACY("legacy", "INFINITY clásico", "Conserva el escritorio y fondos anteriores."),
    FUTURISTIC("futuristic", "INFINITY Futurista", "Referencia: horizonte planetario, ciudad, logo y barra inferior.")
}

fun infinityNormalizeWallpaperPreset(value: String?): InfinityDesktopWallpaperPreset =
    InfinityDesktopWallpaperPreset.entries.firstOrNull { it.id == value }
        ?: InfinityDesktopWallpaperPreset.LEGACY

data class InfinityFuturisticDesktopLayout(
    val iconColumn: Boolean = true,
    val clockTopRight: Boolean = true,
    val dockBottom: Boolean = true,
    val systemTrayBottomRight: Boolean = true,
    val startSearchInDock: Boolean = true,
    val desktopContextMenu: Boolean = true
)

/** Stable layout contract independent of display density; actual rendering is handled by Compose. */
fun infinityFuturisticLayoutContract() = InfinityFuturisticDesktopLayout()

