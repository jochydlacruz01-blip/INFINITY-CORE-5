package com.infinity.core

import java.util.Locale

/** Returns a bounded whole-number usage percentage, or null for unknown capacity. */
internal fun infinityUsagePercent(totalBytes: Long, availableBytes: Long): Int? {
    if (totalBytes <= 0L) return null
    val available = availableBytes.coerceIn(0L, totalBytes)
    return (((totalBytes - available).toDouble() / totalBytes) * 100.0)
        .toInt()
        .coerceIn(0, 100)
}

/** Formats used and total bytes as GiB, avoiding locale-dependent decimal separators. */
internal fun infinityStorageSummary(totalBytes: Long, availableBytes: Long): String {
    if (totalBytes <= 0L || availableBytes < 0L) return "No disponible"
    val available = availableBytes.coerceAtMost(totalBytes)
    val usedGiB = (totalBytes - available) / 1_073_741_824.0
    val totalGiB = totalBytes / 1_073_741_824.0
    return String.format(Locale.ROOT, "%.1f / %.1f GiB", usedGiB, totalGiB)
}

/** Android reports battery temperature in tenths of a degree Celsius. */
internal fun infinityBatteryTemperature(tenthsCelsius: Int): String {
    // Values outside the physical reporting range are treated as invalid telemetry.
    if (tenthsCelsius !in 0..1_000) return "--"
    return String.format(Locale.ROOT, "%.1f °C", tenthsCelsius / 10.0)
}

/** Returns a bounded RAM usage percentage, or null when Android cannot report capacity. */
internal fun infinityRamUsagePercent(totalBytes: Long, availableBytes: Long): Int? =
    infinityUsagePercent(totalBytes, availableBytes)

/** Maps Android thermal status codes to concise, user-facing Spanish labels. */
internal fun infinityThermalState(status: Int?): String = when (status) {
    0 -> "NORMAL"
    1 -> "LEVE"
    2 -> "MODERADA"
    3 -> "ALTA"
    4 -> "CRÍTICA"
    5 -> "EMERGENCIA"
    6 -> "APAGADO"
    else -> "NO DISPONIBLE"
}

/** Converts Android's battery capacity reading into a display-safe percentage. */
internal fun infinityBatteryPercent(value: Int?): Int? =
    value?.takeIf { it in 0..100 }

/** Maps ACTION_BATTERY_CHANGED status values to stable Spanish labels. */
internal fun infinityChargingState(status: Int?): String = when (status) {
    2 -> "CARGANDO"
    3 -> "EN BATERÍA"
    4 -> "SIN CARGA"
    5 -> "COMPLETA"
    else -> "DESCONOCIDO"
}
