package com.infinity.core

import com.infinity.core.advanced.data.capabilities.infinityDeviceUsagePercent
import com.infinity.core.advanced.data.capabilities.infinityBatteryLevelPercent
import com.infinity.core.advanced.data.capabilities.infinityThermalStatusLabel
import com.infinity.core.advanced.domain.capabilities.InfinityDeviceMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InfinityAndroidDeviceMonitoringAdapterTest {
    @Test
    fun usagePercentCalculatesUsedCapacity() {
        assertEquals(75f, infinityDeviceUsagePercent(100L, 25L)!!, 0.001f)
    }

    @Test
    fun usagePercentClampsAvailableBytesAboveTotal() {
        assertEquals(0f, infinityDeviceUsagePercent(100L, 150L)!!, 0.001f)
    }

    @Test
    fun usagePercentReturnsNullForInvalidCapacity() {
        assertNull(infinityDeviceUsagePercent(0L, 0L))
        assertNull(infinityDeviceUsagePercent(100L, -1L))
    }

    @Test
    fun batteryPercentageValidatesAndroidLevelAndScale() {
        assertEquals(50f, infinityBatteryLevelPercent(50, 100)!!, 0.001f)
        assertEquals(100f, infinityBatteryLevelPercent(100, 100)!!, 0.001f)
        assertNull(infinityBatteryLevelPercent(-1, 100))
        assertNull(infinityBatteryLevelPercent(101, 100))
        assertNull(infinityBatteryLevelPercent(1, 0))
    }

    @Test
    fun thermalStatusMapsKnownAndroidStatesAndRejectsUnknownValues() {
        assertEquals("Normal", infinityThermalStatusLabel(0))
        assertEquals("Ligero", infinityThermalStatusLabel(1))
        assertEquals("Moderado", infinityThermalStatusLabel(2))
        assertEquals("Severo", infinityThermalStatusLabel(3))
        assertEquals("Crítico", infinityThermalStatusLabel(4))
        assertEquals("Emergencia", infinityThermalStatusLabel(5))
        assertEquals("Apagado", infinityThermalStatusLabel(6))
        assertNull(infinityThermalStatusLabel(99))
    }

    @Test
    fun metricsExposeBatteryAndThermalTelemetry() {
        val metrics = InfinityDeviceMetrics(
            capturedAtEpochMillis = 1L,
            cpuUsagePercent = null,
            memoryUsagePercent = 50f,
            storageUsagePercent = 75f,
            batteryLevelPercent = 83f,
            batteryTemperatureCelsius = 34.5f,
            isCharging = true,
            thermalStatus = "Normal"
        )
        assertNull(metrics.cpuUsagePercent)
        assertEquals(50f, metrics.memoryUsagePercent!!, 0f)
        assertEquals(83f, metrics.batteryLevelPercent!!, 0f)
        assertEquals(34.5f, metrics.batteryTemperatureCelsius!!, 0f)
        assertEquals(true, metrics.isCharging)
        assertEquals("Normal", metrics.thermalStatus)
    }

    @Test(expected = IllegalArgumentException::class)
    fun metricsRejectBatteryPercentageOutsideRange() {
        InfinityDeviceMetrics(
            capturedAtEpochMillis = 1L,
            cpuUsagePercent = null,
            memoryUsagePercent = null,
            storageUsagePercent = null,
            batteryLevelPercent = 101f
        )
    }
}
