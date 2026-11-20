package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitHealthRulesTest {
    @Test fun usageThresholdsAreBoundedAndConsistent() {
        assertEquals(OrbitMetricLevel.NORMAL, infinityOrbitMetricLevel(74))
        assertEquals(OrbitMetricLevel.WARNING, infinityOrbitMetricLevel(75))
        assertEquals(OrbitMetricLevel.WARNING, infinityOrbitMetricLevel(89))
        assertEquals(OrbitMetricLevel.CRITICAL, infinityOrbitMetricLevel(90))
        assertEquals(OrbitMetricLevel.CRITICAL, infinityOrbitMetricLevel(100))
        assertEquals(OrbitMetricLevel.UNKNOWN, infinityOrbitMetricLevel(null))
        assertEquals(OrbitMetricLevel.UNKNOWN, infinityOrbitMetricLevel(-1))
        assertEquals(OrbitMetricLevel.UNKNOWN, infinityOrbitMetricLevel(101))
    }

    @Test fun thermalThresholdsMapSafely() {
        assertEquals(OrbitMetricLevel.NORMAL, infinityOrbitThermalLevel(0))
        assertEquals(OrbitMetricLevel.NORMAL, infinityOrbitThermalLevel(1))
        assertEquals(OrbitMetricLevel.WARNING, infinityOrbitThermalLevel(2))
        assertEquals(OrbitMetricLevel.WARNING, infinityOrbitThermalLevel(3))
        assertEquals(OrbitMetricLevel.CRITICAL, infinityOrbitThermalLevel(4))
        assertEquals(OrbitMetricLevel.CRITICAL, infinityOrbitThermalLevel(5))
        assertEquals(OrbitMetricLevel.CRITICAL, infinityOrbitThermalLevel(6))
        assertEquals(OrbitMetricLevel.UNKNOWN, infinityOrbitThermalLevel(null))
    }

    @Test fun alertsOnlyForKnownWarningOrCriticalLevels() {
        assertFalse(infinityOrbitNeedsAlert(OrbitMetricLevel.NORMAL))
        assertFalse(infinityOrbitNeedsAlert(OrbitMetricLevel.UNKNOWN))
        assertTrue(infinityOrbitNeedsAlert(OrbitMetricLevel.WARNING))
        assertTrue(infinityOrbitNeedsAlert(OrbitMetricLevel.CRITICAL))
    }
}
