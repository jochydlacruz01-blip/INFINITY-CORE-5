package com.infinity.core.advanced.presentation

import com.infinity.core.advanced.domain.usecases.InfinityAdvancedOverviewSnapshot
import java.util.Locale

/**
 * Builds the only payload permitted for external sharing.
 *
 * Deliberately excludes notification content/keys/sources, package names,
 * device identifiers, file paths, timestamps and raw platform diagnostics.
 */
internal fun buildInfinityAdvancedShareReport(snapshot: InfinityAdvancedOverviewSnapshot): String {
    val metrics = snapshot.deviceMetrics
    val connection = snapshot.connectionState
    return buildString {
        appendLine("INFINITY ADVANCED — INFORME DEL SISTEMA")
        appendLine("Memoria: ${metrics?.memoryUsagePercent?.let { "%.1f%%".format(Locale.ROOT, it) } ?: "No disponible"}")
        appendLine("Almacenamiento: ${metrics?.storageUsagePercent?.let { "%.1f%%".format(Locale.ROOT, it) } ?: "No disponible"}")
        appendLine("Batería: ${metrics?.batteryLevelPercent?.let { "%.1f%%".format(Locale.ROOT, it) } ?: "No disponible"}")
        appendLine("Carga: ${when (metrics?.isCharging) { true -> "Cargando"; false -> "En batería"; null -> "No disponible" }}")
        appendLine("Temperatura de batería: ${metrics?.batteryTemperatureCelsius?.let { "%.1f °C".format(Locale.ROOT, it) } ?: "No disponible"}")
        appendLine("Estado térmico: ${metrics?.thermalStatus ?: "No disponible"}")
        appendLine("Red: ${when { connection == null -> "No disponible"; !connection.hasNetwork -> "Sin conexión"; else -> "${connection.transport ?: "Conectada"} · ${if (connection.isMetered) "medida" else "no medida"}" }}")
        appendLine("Tema: ${snapshot.selectedThemeId}")
        appendLine("Privacidad: no se incluyen notificaciones ni identificadores del dispositivo.")
    }
}
