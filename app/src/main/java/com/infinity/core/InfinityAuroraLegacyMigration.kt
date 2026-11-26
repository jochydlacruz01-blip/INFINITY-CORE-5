package com.infinity.core

/**
 * Data read from the existing saved_windows JSON format.
 * UI metadata (title/kind) remains owned by the desktop layer; this adapter migrates
 * the stable identity and window state without changing the legacy storage contract.
 */
internal data class InfinityLegacyWindowRecord(
    val id: String,
    val workspace: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val minimized: Boolean = false,
    val maximized: Boolean = false,
    val zIndex: Long = 0L
)

/** Convert one validated legacy record into the canonical AURORA window model. */
internal fun infinityAuroraWindowFromLegacy(record: InfinityLegacyWindowRecord): InfinityAuroraWindow {
    val normalGeometry = InfinityAuroraWindowGeometry(
        x = record.x,
        y = record.y,
        width = record.width,
        height = record.height
    )
    return InfinityAuroraWindow(
        id = record.id,
        workspace = infinityNormalizeWorkspace(record.workspace),
        x = record.x,
        y = record.y,
        width = record.width,
        height = record.height,
        minimized = record.minimized,
        maximized = record.maximized,
        zIndex = record.zIndex,
        // The legacy desktop stores normal geometry while maximized geometry is rendered
        // as a temporary copy. Therefore these persisted values are the exact restore bounds.
        restoreGeometry = normalGeometry.takeIf { record.maximized }
    )
}

/** Convert a collection while preserving order and rejecting duplicate stable IDs. */
internal fun infinityAuroraWindowsFromLegacy(records: List<InfinityLegacyWindowRecord>): List<InfinityAuroraWindow> {
    val seenIds = HashSet<String>()
    return records.mapNotNull { record ->
        if (record.id.isBlank() || !seenIds.add(record.id)) null
        else infinityAuroraWindowFromLegacy(record)
    }
}
