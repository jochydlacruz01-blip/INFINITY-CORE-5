package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitTelemetryValidationTest {
    @Test fun freshnessRejectsMissingFutureAndExpiredSamples() {
        assertFalse(infinityOrbitTelemetryFreshness(null, 10_000))
        assertFalse(infinityOrbitTelemetryFreshness(11_000, 10_000))
        assertFalse(infinityOrbitTelemetryFreshness(0, 31_001))
        assertTrue(infinityOrbitTelemetryFreshness(1_000, 31_000))
        assertFalse(infinityOrbitTelemetryFreshness(1_000, 1_000, 0))
        assertFalse(infinityOrbitTelemetryFreshness(Long.MIN_VALUE, Long.MAX_VALUE))
    }

    @Test fun freshPercentRejectsInvalidAndStaleData() {
        assertEquals(50, infinityOrbitFreshPercent(OrbitTelemetrySample(50, 10_000), 10_000))
        assertEquals(null, infinityOrbitFreshPercent(OrbitTelemetrySample(-1, 10_000), 10_000))
        assertEquals(null, infinityOrbitFreshPercent(OrbitTelemetrySample(101, 10_000), 10_000))
        assertEquals(null, infinityOrbitFreshPercent(OrbitTelemetrySample(40, 1_000), 40_000))
        assertEquals(null, infinityOrbitFreshPercent(OrbitTelemetrySample(null, 10_000), 10_000))
    }

    @Test fun telemetryLabelNeverShowsInvalidOrStaleValues() {
        assertEquals("60%", infinityOrbitTelemetryLabel(60, true))
        assertEquals("--", infinityOrbitTelemetryLabel(60, false))
        assertEquals("--", infinityOrbitTelemetryLabel(null, true))
        assertEquals("--", infinityOrbitTelemetryLabel(101, true))
    }
}
