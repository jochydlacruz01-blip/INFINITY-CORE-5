package com.infinity.core

/**
 * Deterministic filtering for INFINITY START's installed-app catalog.
 * Classification is intentionally heuristic: Android does not expose a
 * reliable cross-version launcher-app category for every installed app.
 */
internal object InfinityStartCatalogPolicy {
    val categories = listOf("all", "productivity", "communication", "media", "games", "system")

    fun normalizeQuery(query: String): String = query.trim().lowercase()

    fun categoryOf(label: String, packageName: String): String {
        val value = "${label.lowercase()} ${packageName.lowercase()}"
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
            label.lowercase().contains(normalized) ||
            packageName.lowercase().contains(normalized)
        val categoryMatches = selectedCategory == "all" ||
            categoryOf(label, packageName) == selectedCategory
        return queryMatches && categoryMatches
    }
}
