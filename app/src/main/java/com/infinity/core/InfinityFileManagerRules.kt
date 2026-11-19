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
    return when (mime?.lowercase(Locale.ROOT)) {
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
    val name = value.trim()
    return name.isNotEmpty() && name.length <= 255 && name != "." && name != ".." &&
        name.none { it == '/' || it == '\\' || it.isISOControl() }
}
