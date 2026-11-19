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
            .uppercase(Locale.getDefault())
    }
}
