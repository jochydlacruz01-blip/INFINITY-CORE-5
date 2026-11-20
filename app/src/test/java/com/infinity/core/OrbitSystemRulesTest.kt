package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitSystemRulesTest {
    @Test
    fun usagePercentageIsBoundedAndHandlesUnknownCapacity() {
        assertEquals(50, infinityUsagePercent(100L, 50L))
        assertEquals(0, infinityUsagePercent(100L, 100L))
        assertEquals(100, infinityUsagePercent(100L, 0L))
        assertEquals(100, infinityUsagePercent(100L, -1L))
        assertEquals(0, infinityUsagePercent(100L, 101L))
        assertEquals(null, infinityUsagePercent(0L, 0L))
        assertEquals(null, infinityUsagePercent(-1L, 0L))
    }

    @Test
    fun storageSummaryUsesGibAndHandlesInvalidValues() {
        assertEquals("1.0 / 2.0 GiB", infinityStorageSummary(2_147_483_648L, 1_073_741_824L))
        assertEquals("2.0 / 2.0 GiB", infinityStorageSummary(2_147_483_648L, 0L))
        assertEquals("No disponible", infinityStorageSummary(0L, 0L))
        assertEquals("No disponible", infinityStorageSummary(100L, -1L))
    }

    @Test
    fun batteryTemperatureAcceptsZeroAndRejectsNegativeValues() {
        assertEquals("0.0 °C", infinityBatteryTemperature(0))
        assertEquals("25.5 °C", infinityBatteryTemperature(255))
        assertEquals("--", infinityBatteryTemperature(-1))
    }

    @Test
    fun ramUsageUsesTheSameBoundedCapacityRules() {
        assertEquals(75, infinityRamUsagePercent(400L, 100L))
        assertEquals(null, infinityRamUsagePercent(0L, 0L))
    }
    @Test
    fun usagePercentageHandlesSmallCapacityAndFractionalValues() {
        assertEquals(33, infinityUsagePercent(3L, 2L))
        assertEquals(99, infinityUsagePercent(100L, 1L))
        assertEquals(0, infinityUsagePercent(1L, 1L))
    }

    @Test
    fun storageSummaryBoundsAvailableBytesAndRejectsNegativeTotal() {
        assertEquals("0.0 / 1.0 GiB", infinityStorageSummary(1_073_741_824L, 2_000_000_000L))
        assertEquals("No disponible", infinityStorageSummary(-10L, 0L))
    }

    @Test
    fun thermalStatusMapsEveryAndroidLevelAndUnknownValues() {
        assertEquals("NORMAL", infinityThermalState(0))
        assertEquals("LEVE", infinityThermalState(1))
        assertEquals("MODERADA", infinityThermalState(2))
        assertEquals("ALTA", infinityThermalState(3))
        assertEquals("CRÍTICA", infinityThermalState(4))
        assertEquals("EMERGENCIA", infinityThermalState(5))
        assertEquals("APAGADO", infinityThermalState(6))
        assertEquals("NO DISPONIBLE", infinityThermalState(null))
        assertEquals("NO DISPONIBLE", infinityThermalState(-1))
        assertEquals("NO DISPONIBLE", infinityThermalState(99))
    }

    @Test
    fun batteryTemperatureFormattingRemainsLocaleIndependent() {
        assertEquals("36.6 °C", infinityBatteryTemperature(366))
        assertEquals("--", infinityBatteryTemperature(Int.MIN_VALUE))
    }

    @Test
    fun batteryCapacityIsBoundedAndUnknownValuesStayUnavailable() {
        assertEquals(0, infinityBatteryPercent(0))
        assertEquals(100, infinityBatteryPercent(100))
        assertEquals(42, infinityBatteryPercent(42))
        assertEquals(null, infinityBatteryPercent(null))
        assertEquals(null, infinityBatteryPercent(-1))
        assertEquals(null, infinityBatteryPercent(101))
    }

    @Test
    fun chargingStatusMapsAndroidStatesAndUnknownValues() {
        assertEquals("CARGANDO", infinityChargingState(2))
        assertEquals("EN BATERÍA", infinityChargingState(3))
        assertEquals("SIN CARGA", infinityChargingState(4))
        assertEquals("COMPLETA", infinityChargingState(5))
        assertEquals("DESCONOCIDO", infinityChargingState(null))
        assertEquals("DESCONOCIDO", infinityChargingState(-1))
        assertEquals("DESCONOCIDO", infinityChargingState(99))
    }

    @Test
    fun storageSummaryFormattingIsIndependentOfDefaultLocale() {
        val originalLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("de-DE"))
            assertEquals(
                "1.0 / 2.0 GiB",
                infinityStorageSummary(2_147_483_648L, 1_073_741_824L)
            )
            assertEquals("25.5 °C", infinityBatteryTemperature(255))
        } finally {
            java.util.Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun batteryAndChargingRulesRemainSafeForExtremeUnknownValues() {
        assertEquals(null, infinityBatteryPercent(Int.MIN_VALUE))
        assertEquals(null, infinityBatteryPercent(Int.MAX_VALUE))
        assertEquals("DESCONOCIDO", infinityChargingState(Int.MIN_VALUE))
        assertEquals("DESCONOCIDO", infinityChargingState(Int.MAX_VALUE))
    }

    @Test
    fun usagePercentageHandlesLongBoundaryValuesWithoutOverflow() {
        assertEquals(0, infinityUsagePercent(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(100, infinityUsagePercent(Long.MAX_VALUE, 0L))
        assertEquals(0, infinityUsagePercent(Long.MAX_VALUE, Long.MAX_VALUE - 1L))
        assertEquals(100, infinityUsagePercent(Long.MAX_VALUE, Long.MIN_VALUE))
    }

    @Test
    fun storageSummaryHandlesMaximumCapacityWithoutOverflow() {
        val summary = infinityStorageSummary(Long.MAX_VALUE, 0L)
        assertTrue(summary.endsWith(" / 8589934592.0 GiB"))
        assertEquals("No disponible", infinityStorageSummary(Long.MAX_VALUE, Long.MIN_VALUE))
    }

}