package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitAlertAggregationTest {
    @Test fun batteryThresholdsAndInvalidValues() {
        assertEquals(OrbitMetricLevel.CRITICAL, infinityOrbitBatteryLevel(10))
        assertEquals(OrbitMetricLevel.WARNING, infinityOrbitBatteryLevel(20))
        assertEquals(OrbitMetricLevel.NORMAL, infinityOrbitBatteryLevel(21))
        assertEquals(OrbitMetricLevel.UNKNOWN, infinityOrbitBatteryLevel(null))
        assertEquals(OrbitMetricLevel.UNKNOWN, infinityOrbitBatteryLevel(101))
    }

    @Test fun aggregatesAlertsWithCriticalFirstAndStableOrder() {
        val alerts = infinityOrbitAlerts(9, 80, 95, 4)
        assertEquals(listOf("BATERÍA", "ALMACENAMIENTO", "TEMPERATURA", "RAM"), alerts.map { it.metric })
        assertEquals(OrbitMetricLevel.CRITICAL, alerts.first().level)
        assertEquals(OrbitMetricLevel.WARNING, alerts.last().level)
    }

    @Test fun equalSeverityAlertsPreserveMetricCandidateOrder() {\n        val alerts = infinityOrbitAlerts(10, 95, 90, 4)\n        assertEquals(listOf("BATERÍA", "RAM", "ALMACENAMIENTO", "TEMPERATURA"), alerts.map { it.metric })\n        assertEquals(listOf(OrbitMetricLevel.CRITICAL, OrbitMetricLevel.CRITICAL, OrbitMetricLevel.CRITICAL, OrbitMetricLevel.CRITICAL), alerts.map { it.level })\n    }\n\n    @Test fun unknownMetricsDoNotGenerateAlerts() {
        assertTrue(infinityOrbitAlerts(null, null, -1, null).isEmpty())
    }

    @Test fun normalMetricsProduceNoAlerts() {
        assertTrue(infinityOrbitAlerts(80, 50, 30, 1).isEmpty())
    }
}
