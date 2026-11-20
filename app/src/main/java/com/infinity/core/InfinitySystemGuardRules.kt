package com.infinity.core

internal enum class InfinityGuardSeverity { INFO, WARNING, ERROR }

internal data class InfinityGuardReport(
    val category: String,
    val severity: InfinityGuardSeverity,
    val userMessage: String
)

/** Convert failures into bounded, privacy-safe diagnostic messages. */
internal fun infinityGuardReport(operation: String, error: Exception): InfinityGuardReport {
    val op = operation.trim().take(48).ifBlank { "Operación" }
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

internal fun infinityGuardSafeLabel(value: String, maxLength: Int = 64): String {
    val limit = maxLength.coerceIn(1, 128)
    return value.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
        .trim().take(limit).ifBlank { "Elemento" }
}
