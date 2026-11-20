package com.infinity.core

/** Sanitizes telemetry samples before they enter a live dashboard. */
internal data class OrbitTelemetrySample<T>(val value: T?, val observedAtMillis: Long)

/** Unknown, future-dated, and stale samples are never treated as current. */
internal fun infinityOrbitTelemetryFreshness(
    observedAtMillis: Long?,
    nowMillis: Long,
    maxAgeMillis: Long = 30_000L
): Boolean {
    if (observedAtMillis == null || maxAgeMillis <= 0L || nowMillis < observedAtMillis) return false
    // Avoid signed overflow when timestamps span extreme Long values.\n    val age = nowMillis - observedAtMillis\n    if (age < 0L) return false\n    return age <= maxAgeMillis
}

/** Safely accepts only bounded percentages from fresh telemetry. */
internal fun infinityOrbitFreshPercent(
    sample: OrbitTelemetrySample<Int>,
    nowMillis: Long,
    maxAgeMillis: Long = 30_000L
): Int? {
    if (!infinityOrbitTelemetryFreshness(sample.observedAtMillis, nowMillis, maxAgeMillis)) return null
    return sample.value?.takeIf { it in 0..100 }
}

/** Produces a safe UI label for unavailable, stale, or valid metric values. */
internal fun infinityOrbitTelemetryLabel(value: Int?, fresh: Boolean): String =
    if (!fresh || value == null || value !in 0..100) "--" else "$value%"
