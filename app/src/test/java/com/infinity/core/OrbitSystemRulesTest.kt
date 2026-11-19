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
}
