package com.infinity.core

/** Pure ORBIT alert aggregation; reports only and never triggers device actions. */
internal data class OrbitHealthAlert(val metric: String, val level: OrbitMetricLevel, val message: String)

internal fun infinityOrbitBatteryLevel(percent: Int?): OrbitMetricLevel {
    if (percent == null || percent !in 0..100) return OrbitMetricLevel.UNKNOWN
    return when {
        percent <= 10 -> OrbitMetricLevel.CRITICAL
        percent <= 20 -> OrbitMetricLevel.WARNING
        else -> OrbitMetricLevel.NORMAL
    }
}

internal fun infinityOrbitAlerts(
    batteryPercent: Int?,
    ramPercent: Int?,
    storagePercent: Int?,
    thermalStatus: Int?
): List<OrbitHealthAlert> {
    val candidates = listOf(
        Triple("BATERÍA", infinityOrbitBatteryLevel(batteryPercent), batteryPercent),
        Triple("RAM", infinityOrbitMetricLevel(ramPercent), ramPercent),
        Triple("ALMACENAMIENTO", infinityOrbitMetricLevel(storagePercent), storagePercent)
    )
    val alerts = candidates.mapNotNull { (metric, level, value) ->
        if (!infinityOrbitNeedsAlert(level)) null else OrbitHealthAlert(
            metric, level, "$metric $value%: ${if (level == OrbitMetricLevel.CRITICAL) "nivel crítico" else "nivel elevado"}"
        )
    }.toMutableList()
    val thermal = infinityOrbitThermalLevel(thermalStatus)
    if (infinityOrbitNeedsAlert(thermal)) {
        alerts += OrbitHealthAlert("TEMPERATURA", thermal, "Estado térmico $thermalStatus: ${if (thermal == OrbitMetricLevel.CRITICAL) "crítico" else "elevado"}")
    }
    return alerts
}
