package com.infinity.core

import org.junit.Assert.assertEquals
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

}
