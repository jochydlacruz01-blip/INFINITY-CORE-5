package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class OrbitDiagnosticSnapshotTest {
    @Test fun normalSnapshotRequiresAllValidMetrics() {
        val s = infinityOrbitDiagnosticSnapshot(50, 30, 40, 1, 100L)
        assertEquals(OrbitMetricLevel.NORMAL, s.overall)
        assertTrue(s.missingMetrics.isEmpty())
        assertTrue(s.alerts.isEmpty())
    }

    @Test fun criticalTakesPrecedenceAndAlertOrderIsStable() {
        val s = infinityOrbitDiagnosticSnapshot(5, 95, 99, 4, 200L)
        assertEquals(OrbitMetricLevel.CRITICAL, s.overall)
        assertEquals(listOf("BATERÍA", "RAM", "ALMACENAMIENTO", "TEMPERATURA"), s.alerts.map { it.metric })
    }

    @Test fun missingValuesAreUnknownNotZeroAndPartialSnapshot() {
        val s = infinityOrbitDiagnosticSnapshot(null, 50, null, null, 300L)
        assertEquals(OrbitMetricLevel.UNKNOWN, s.overall)
        assertNull(s.battery)
        assertNull(s.storage)
        assertEquals(setOf("BATERÍA", "ALMACENAMIENTO", "TEMPERATURA"), s.missingMetrics)
        assertEquals("Diagnóstico parcial: faltan indicadores.", infinityOrbitDiagnosticSummary(s))
    }

    @Test fun invalidInputsAreDiscardedAndCriticalAlertOverridesUnknown() {
        val s = infinityOrbitDiagnosticSnapshot(101, -1, 50, 99, 400L)
        assertEquals(OrbitMetricLevel.UNKNOWN, s.overall)
        assertNull(s.battery)
        assertNull(s.ram)
        assertEquals(OrbitMetricLevel.UNKNOWN, infinityOrbitThermalLevel(s.thermalStatus))
    }

    @Test fun warningAndSummaryAreConsistent() {
        val s = infinityOrbitDiagnosticSnapshot(18, 80, 50, 2, 500L)
        assertEquals(OrbitMetricLevel.WARNING, s.overall)
        assertEquals("Atención: uno o más indicadores requieren revisión.", infinityOrbitDiagnosticSummary(s))
    }
}
