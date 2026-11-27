package com.infinity.core

import com.infinity.core.advanced.data.capabilities.infinityDeviceUsagePercent
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
    fun metricsAllowUnavailableCpuTelemetry() {
        val metrics = InfinityDeviceMetrics(
            capturedAtEpochMillis = 1L,
            cpuUsagePercent = null,
            memoryUsagePercent = 50f,
            storageUsagePercent = null
        )
        assertNull(metrics.cpuUsagePercent)
        assertEquals(50f, metrics.memoryUsagePercent!!, 0f)
    }
}
