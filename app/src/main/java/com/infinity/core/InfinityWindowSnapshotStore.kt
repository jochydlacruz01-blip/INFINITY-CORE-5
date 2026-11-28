package com.infinity.core

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal const val INFINITY_SAVED_WINDOWS_KEY = "saved_windows"
internal const val INFINITY_VALID_WINDOWS_BACKUP_KEY = "saved_windows_valid_backup"
private const val INFINITY_WINDOW_RECOVERY_BACKUP_LIMIT = 5
private val INFINITY_ALLOWED_WINDOW_KINDS = setOf("files", "notes", "apps", "connect", "settings", "about")

internal data class InfinityWindowRecoveryResult(
    val json: String,
    val restoredFromBackup: Boolean,
    val preservedCorruptSnapshot: Boolean
)

/**
 * Strictly validates the complete AURORA snapshot before it is allowed to replace
 * either the live state or the last-known-good recovery copy.
 */
internal fun infinityIsValidWindowSnapshot(raw: String?): Boolean {
    if (raw == null) return false
    return try {
        val array = JSONArray(raw)
        if (array.length() > 64) return false
        val ids = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: return false
            val required = listOf(
                "id", "title", "kind", "x", "y", "width", "height",
                "minimized", "workspace", "maximized"
            )
            if (required.any { !item.has(it) || item.isNull(it) }) return false

            val id = item.get("id") as? String ?: return false
            val title = item.get("title") as? String ?: return false
            val kind = item.get("kind") as? String ?: return false
            val workspaceValue = item.get("workspace") as? Number ?: return false
            val workspace = workspaceValue.toInt()
            if (workspaceValue.toDouble() != workspace.toDouble() || workspace !in 1..3) return false
            if (kind !in INFINITY_ALLOWED_WINDOW_KINDS) return false
            if (id != infinityWindowIdentity(kind, workspace) || !ids.add(id)) return false
            if (title.isBlank() || title.length > 80) return false

            val x = (item.get("x") as? Number)?.toDouble() ?: return false
            val y = (item.get("y") as? Number)?.toDouble() ?: return false
            val width = (item.get("width") as? Number)?.toDouble() ?: return false
            val height = (item.get("height") as? Number)?.toDouble() ?: return false
            if (!x.isFinite() || x !in -500.0..5000.0) return false
            if (!y.isFinite() || y !in -500.0..5000.0) return false
            if (!width.isFinite() || width !in 240.0..900.0) return false
            if (!height.isFinite() || height !in 180.0..900.0) return false
            if (item.get("minimized") !is Boolean || item.get("maximized") !is Boolean) return false
        }
        true
    } catch (_: Exception) {
        false
    }
}

/** Returns a free evidence slot; an existing backup is never overwritten. */
internal fun infinityNextCorruptWindowEvidenceKey(existingKeys: Set<String>): String? {
    for (index in 1..INFINITY_WINDOW_RECOVERY_BACKUP_LIMIT) {
        val key = if (index == 1) "saved_windows_recovery_backup"
        else "saved_windows_recovery_backup_$index"
        if (key !in existingKeys) return key
    }
    return null
}

/**
 * Restores only a fully validated last-known-good snapshot. Corrupt inputs are
 * preserved as bounded evidence and are never promoted to the valid backup.
 */
internal fun infinityRecoverWindowSnapshot(prefs: SharedPreferences): InfinityWindowRecoveryResult {
    val primary = prefs.getString(INFINITY_SAVED_WINDOWS_KEY, null)
    if (infinityIsValidWindowSnapshot(primary)) {
        return InfinityWindowRecoveryResult(primary!!, false, false)
    }

    val backup = prefs.getString(INFINITY_VALID_WINDOWS_BACKUP_KEY, null)
    if (infinityIsValidWindowSnapshot(backup)) {
        val editor = prefs.edit().putString(INFINITY_SAVED_WINDOWS_KEY, backup)
        var preserved = false
        if (!primary.isNullOrBlank()) {
            val evidenceKey = infinityNextCorruptWindowEvidenceKey(prefs.all.keys)
            if (evidenceKey != null) {
                editor.putString(evidenceKey, primary)
                preserved = true
            }
        }
        editor.commit()
        return InfinityWindowRecoveryResult(backup!!, true, preserved)
    }

    // Neither source is valid. Keep the invalid backup untouched, preserve the
    // corrupt primary if a free evidence slot exists, and fall back in memory to
    // an empty window list. Never delete the only remaining unreadable source.
    var preserved = false
    if (!primary.isNullOrBlank()) {
        val evidenceKey = infinityNextCorruptWindowEvidenceKey(prefs.all.keys)
        if (evidenceKey != null) {
            prefs.edit()
                .putString(evidenceKey, primary)
                .putString(INFINITY_SAVED_WINDOWS_KEY, "[]")
                .commit()
            preserved = true
        }
    }
    return InfinityWindowRecoveryResult("[]", false, preserved)
}

/**
 * Atomically commits a new valid snapshot and rotates the previous valid primary
 * into the recovery slot. An invalid current primary is preserved before replace.
 */
internal fun infinityCommitWindowSnapshot(prefs: SharedPreferences, nextJson: String): Boolean {
    if (!infinityIsValidWindowSnapshot(nextJson)) return false
    val current = prefs.getString(INFINITY_SAVED_WINDOWS_KEY, null)
    val editor = prefs.edit()
    if (infinityIsValidWindowSnapshot(current) && current != nextJson) {
        editor.putString(INFINITY_VALID_WINDOWS_BACKUP_KEY, current)
    } else if (!current.isNullOrBlank() && !infinityIsValidWindowSnapshot(current)) {
        val evidenceKey = infinityNextCorruptWindowEvidenceKey(prefs.all.keys)
        // Do not destroy an unreadable primary if no safe evidence slot remains.
        if (evidenceKey == null) return false
        editor.putString(evidenceKey, current)
    }
    editor.putString(INFINITY_SAVED_WINDOWS_KEY, nextJson)
    return editor.commit()
}
