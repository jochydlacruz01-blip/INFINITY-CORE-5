package com.infinity.core

import java.util.Locale

/**
 * Deterministic filtering for INFINITY START's installed-app catalog.
 * Classification is heuristic because Android does not expose a reliable
 * cross-version launcher category for every installed application.
 */
internal object InfinityStartCatalogPolicy {
    val categories = listOf("all", "productivity", "communication", "media", "games", "system")

    fun normalizeQuery(query: String): String = query.trim().lowercase(Locale.ROOT)

    fun categoryOf(label: String, packageName: String): String {
        val value = "${label.lowercase(Locale.ROOT)} ${packageName.lowercase(Locale.ROOT)}"
        return when {
            listOf("game", "games", "playgames", "gaming").any(value::contains) -> "games"
            listOf("camera", "photo", "gallery", "video", "music", "audio", "media", "youtube", "netflix").any(value::contains) -> "media"
            listOf("mail", "message", "messaging", "chat", "whatsapp", "telegram", "signal", "discord", "phone", "contacts").any(value::contains) -> "communication"
            listOf("settings", "system", "launcher", "permission", "files", "packageinstaller", "com.android").any(value::contains) -> "system"
            listOf("office", "document", "calendar", "notes", "drive", "task", "browser", "chrome", "calculator", "editor").any(value::contains) -> "productivity"
            else -> "productivity"
        }
    }

    fun matches(
        label: String,
        packageName: String,
        query: String,
        selectedCategory: String
    ): Boolean {
        val normalized = normalizeQuery(query)
        val queryMatches = normalized.isEmpty() ||
            label.lowercase(Locale.ROOT).contains(normalized) ||
            packageName.lowercase(Locale.ROOT).contains(normalized)
        val categoryMatches = selectedCategory == "all" ||
            (selectedCategory in categories && categoryOf(label, packageName) == selectedCategory)
        return queryMatches && categoryMatches
    }

    /** Internal desktop tools use the same search and category contract as Android apps. */
    fun desktopToolCategory(id: String): String = when (id) {
        "notes", "apps" -> "productivity"
        "connect" -> "communication"
        "files", "about", "settings", "orbit" -> "system"
        else -> "system"
    }

    fun matchesDesktopTool(
        id: String,
        label: String,
        query: String,
        selectedCategory: String
    ): Boolean {
        val normalized = normalizeQuery(query)
        val queryMatches = normalized.isEmpty() || label.lowercase(Locale.ROOT).contains(normalized) || id.lowercase(Locale.ROOT).contains(normalized)
        val categoryMatches = selectedCategory == "all" ||
            (selectedCategory in categories && desktopToolCategory(id) == selectedCategory)
        return queryMatches && categoryMatches
    }
}
