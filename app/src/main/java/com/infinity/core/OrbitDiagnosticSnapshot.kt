package com.infinity.core

/** Aggregated, sanitized ORBIT diagnostic snapshot. This is advisory only. */
internal data class OrbitDiagnosticSnapshot(
    val battery: Int?,
    val ram: Int?,
    val storage: Int?,
    val thermalStatus: Int?,
    val collectedAtMillis: Long,
    val missingMetrics: Set<String>,
    val alerts: List<OrbitHealthAlert>,
    val overall: OrbitMetricLevel
)

/** Build a snapshot from validated/fresh values; null metrics remain unknown, never zero. */
internal fun infinityOrbitDiagnosticSnapshot(
    battery: Int?,
    ram: Int?,
    storage: Int?,
    thermalStatus: Int?,
    collectedAtMillis: Long
): OrbitDiagnosticSnapshot {
    val validBattery = battery?.takeIf { it in 0..100 }
    val validRam = ram?.takeIf { it in 0..100 }
    val validStorage = storage?.takeIf { it in 0..100 }
    val validThermal = thermalStatus?.takeIf { it in 0..6 }
    val missing = buildSet {
        if (validBattery == null) add("BATERÍA")
        if (validRam == null) add("RAM")
        if (validStorage == null) add("ALMACENAMIENTO")
        if (validThermal == null) add("TEMPERATURA")
    }
    val alerts = infinityOrbitAlerts(validBattery, validRam, validStorage, validThermal)
    val levels = listOf(
        infinityOrbitBatteryLevel(validBattery), infinityOrbitMetricLevel(validRam),
        infinityOrbitMetricLevel(validStorage), infinityOrbitThermalLevel(validThermal)
    )
    val overall = when {
        levels.any { it == OrbitMetricLevel.CRITICAL } -> OrbitMetricLevel.CRITICAL
        levels.any { it == OrbitMetricLevel.WARNING } -> OrbitMetricLevel.WARNING
        missing.isNotEmpty() -> OrbitMetricLevel.UNKNOWN
        else -> OrbitMetricLevel.NORMAL
    }
    return OrbitDiagnosticSnapshot(validBattery, validRam, validStorage, validThermal,
        collectedAtMillis, missing, alerts, overall)
}

/** Defensive display summary; does not expose raw system data or trigger actions. */
internal fun infinityOrbitDiagnosticSummary(snapshot: OrbitDiagnosticSnapshot): String = when (snapshot.overall) {
    OrbitMetricLevel.CRITICAL -> "Atención crítica: revisar los indicadores destacados."
    OrbitMetricLevel.WARNING -> "Atención: uno o más indicadores requieren revisión."
    OrbitMetricLevel.UNKNOWN -> "Diagnóstico parcial: faltan indicadores."
    OrbitMetricLevel.NORMAL -> "Sistema dentro de los umbrales observados."
}
