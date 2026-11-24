package com.infinity.core

internal enum class InfinityGuardSeverity { INFO, WARNING, ERROR }

internal data class InfinityGuardReport(
    val category: String,
    val severity: InfinityGuardSeverity,
    val userMessage: String
)

/** Convert failures into bounded, privacy-safe diagnostic messages. */
internal fun infinityGuardReport(operation: String, error: Exception): InfinityGuardReport {
    val op = infinityGuardSafeLabel(operation, maxLength = 48, fallback = "Operación")
    val severity = when (error) {
        is java.io.IOException -> InfinityGuardSeverity.WARNING
        is SecurityException -> InfinityGuardSeverity.WARNING
        is IllegalArgumentException -> InfinityGuardSeverity.INFO
        else -> InfinityGuardSeverity.ERROR
    }
    val category = when (error) {
        is java.io.IOException -> "IO"
        is SecurityException -> "PERMISOS"
        is IllegalArgumentException -> "ENTRADA"
        else -> "GENERAL"
    }
    // Never expose exception messages, paths, hosts, or tokens to the UI.
    return InfinityGuardReport(category, severity, "$op no pudo completarse. $category")
}

private fun normalizeInfinityGuardLabel(value: String): String {
    return value.map { char ->
        if (char.isLetterOrDigit() || char == ' ' || char == '-' || char == '_') char else ' '
    }.joinToString("")
        .trim()
        .replace(Regex("\\s+"), " ")
}

internal fun infinityGuardSafeLabel(
    value: String,
    maxLength: Int = 64,
    fallback: String = "Elemento"
): String {
    val limit = maxLength.coerceIn(1, 128)
    val normalized = normalizeInfinityGuardLabel(value).take(limit).trim()
    if (normalized.isNotBlank()) return normalized

    // Custom fallbacks must follow the same filtering rules as the primary label.
    val safeFallback = normalizeInfinityGuardLabel(fallback).take(32).trim()
    return safeFallback.ifBlank { "Elemento" }
}
