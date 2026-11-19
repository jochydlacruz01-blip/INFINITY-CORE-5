package com.infinity.core

import java.util.Locale

internal fun formatInfinityFileSize(bytes: Long): String {
    if (bytes < 0L) return "Tamaño no disponible"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.ROOT, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.ROOT, "%.2f GB", gb)
}

internal fun formatInfinityMimeType(mime: String?): String {
    if (mime.isNullOrBlank()) return "Tipo no disponible"
    return when (mime.lowercase(Locale.ROOT)) {
        null, "" -> "Tipo no disponible"
        "application/pdf" -> "Documento PDF"
        "text/plain" -> "Texto"
        "application/zip", "application/x-zip-compressed" -> "Archivo ZIP"
        "image/jpeg", "image/jpg" -> "Imagen JPEG"
        "image/png" -> "Imagen PNG"
        "image/webp" -> "Imagen WebP"
        "image/gif" -> "Imagen GIF"
        "audio/mpeg" -> "Audio MP3"
        "audio/ogg" -> "Audio OGG"
        "video/mp4" -> "Vídeo MP4"
        "application/vnd.android.package-archive" -> "Instalador APK"
        else -> mime.substringAfter('/').replace('-', ' ').replace('_', ' ')
            .uppercase(Locale.ROOT)
    }
}

/** Only the latest SAF listing request may clear the shared loading state. */
internal fun infinityShouldClearLoading(requestGeneration: Int, currentGeneration: Int): Boolean =
    requestGeneration == currentGeneration

/** Unknown sizes sort after files with a known size. */
internal fun infinityFileSizeSortRank(bytes: Long): Int = if (bytes < 0L) 1 else 0

/** Prevents no-op copy/move operations within the same parent folder. */
internal fun isSameInfinityFolder(sourceFolderId: String?, targetFolderId: String?): Boolean {
    return !sourceFolderId.isNullOrBlank() &&
        !targetFolderId.isNullOrBlank() &&
        sourceFolderId == targetFolderId
}

/**
 * A missing provider flag column means capability is unknown, not unsupported.
 * In that case the operation may be attempted and Android remains authoritative.
 */
internal fun infinitySupportsDocumentFlag(flags: Long?, requiredFlag: Long): Boolean {
    return flags == null || flags and requiredFlag != 0L
}

/** Conservative portable document-name validation before calling a provider. */
internal fun isValidInfinityDocumentName(value: String): Boolean {
    if (value.any { it.isISOControl() }) return false
    val name = value.trim()
    return name.isNotEmpty() && name.length <= 255 && name != "." && name != ".." &&
        name.none { it == '/' || it == '\\' || it.isISOControl() }
}

/** Keeps only failed move items, preserving their original clipboard order. */
internal fun infinityRemainingMoveIds(ids: List<String>, failedIds: Set<String>): List<String> =
    ids.filter { it in failedIds }

/** Retains only failed deletion targets, keeping the original selection order. */
internal fun infinityRemainingDeleteIds(ids: List<String>, failedIds: Set<String>): List<String> =
    ids.filter { it in failedIds }

/** Retains selected document IDs still present after a refresh. */
internal fun infinityRetainedSelection(selectedIds: Set<String>, visibleIds: Set<String>): Set<String> =
    selectedIds.intersect(visibleIds)

/** Returns the original-order items not confirmed completed after an interrupted batch. */
internal fun infinityUnfinishedOperationIds(ids: List<String>, completedIds: Set<String>): List<String> =
    ids.filter { it !in completedIds }

/** Keeps only failed copy/move items, preserving their original order. */
internal fun infinityRemainingOperationIds(ids: List<String>, failedIds: Set<String>): List<String> =
    ids.filter { it in failedIds }


/** Treats an omitted or blank SAF MIME column as unknown metadata. */
internal fun infinityOptionalMimeType(columnAvailable: Boolean, value: String?): String? =
    if (columnAvailable) value?.takeIf { it.isNotBlank() } else null


/** Prevents malformed providers from creating navigation loops through repeated folder IDs. */
internal fun infinityCanNavigateFolder(currentPathIds: List<String>, candidateId: String): Boolean =
    candidateId.isNotBlank() && candidateId !in currentPathIds

/** Rejects malformed SAF rows that do not contain a usable document identifier. */
internal fun infinityDocumentIdOrNull(value: String?): String? =
    value?.takeIf { it.isNotBlank() }

/** Provides a stable display fallback for providers returning null or blank names. */
internal fun infinityDocumentDisplayName(value: String?): String =
    value?.takeIf { it.isNotBlank() } ?: "Sin nombre"
