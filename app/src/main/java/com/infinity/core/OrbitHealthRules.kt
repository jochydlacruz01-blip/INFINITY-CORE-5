package com.infinity.core

/** ORBIT health thresholds: telemetry remains advisory and never performs system actions. */
internal enum class OrbitMetricLevel { NORMAL, WARNING, CRITICAL, UNKNOWN }

internal fun infinityOrbitMetricLevel(percent: Int?): OrbitMetricLevel {
    if (percent == null || percent !in 0..100) return OrbitMetricLevel.UNKNOWN
    return when {
        percent >= 90 -> OrbitMetricLevel.CRITICAL
        percent >= 75 -> OrbitMetricLevel.WARNING
        else -> OrbitMetricLevel.NORMAL
    }
}

/** Thermal state labels mapped to a safe dashboard alert level. */
internal fun infinityOrbitThermalLevel(status: Int?): OrbitMetricLevel = when (status) {
    0, 1 -> OrbitMetricLevel.NORMAL
    2, 3 -> OrbitMetricLevel.WARNING
    4, 5, 6 -> OrbitMetricLevel.CRITICAL
    else -> OrbitMetricLevel.UNKNOWN
}

/** Prevents stale or invalid telemetry from being interpreted as an alert. */
internal fun infinityOrbitNeedsAlert(level: OrbitMetricLevel): Boolean =
    level == OrbitMetricLevel.WARNING || level == OrbitMetricLevel.CRITICAL
