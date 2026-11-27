package com.infinity.core

import com.infinity.core.advanced.domain.capabilities.DefaultInfinityCapabilityRegistry
import com.infinity.core.advanced.domain.capabilities.InfinityCapabilityId
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class InfinityCapabilityContractsTest {

    private interface TestCapability
    private class TestCapabilityImpl : TestCapability

    @Test
    fun registryReturnsCapabilityRegisteredForRequestedId() {
        val capability = TestCapabilityImpl()
        val registry = DefaultInfinityCapabilityRegistry(
            mapOf(InfinityCapabilityId.NOTIFICATIONS to capability)
        )

        assertSame(
            capability,
            registry.find(InfinityCapabilityId.NOTIFICATIONS, TestCapability::class.java)
        )
    }

    @Test
    fun registryReturnsNullWhenCapabilityIsNotInstalled() {
        val registry = DefaultInfinityCapabilityRegistry(emptyMap())

        assertNull(
            registry.find(InfinityCapabilityId.DEVICE_MONITORING, TestCapability::class.java)
        )
    }

    @Test
    fun registryRejectsIncorrectContractBinding() {
        val registry = DefaultInfinityCapabilityRegistry(
            mapOf(InfinityCapabilityId.THEME to Any())
        )

        assertThrows(IllegalArgumentException::class.java) {
            registry.find(InfinityCapabilityId.THEME, TestCapability::class.java)
        }
    }

    @Test
    fun registryCopiesBindingsAndDoesNotObserveCallerMutation() {
        val capability = TestCapabilityImpl()
        val bindings = mutableMapOf<InfinityCapabilityId, Any>()
        val registry = DefaultInfinityCapabilityRegistry(bindings)
        bindings[InfinityCapabilityId.NOTIFICATIONS] = capability

        assertNull(
            registry.find(InfinityCapabilityId.NOTIFICATIONS, TestCapability::class.java)
        )
    }

    @Test
    fun metricsAcceptNullValuesForUnsupportedSensors() {
        val metrics = com.infinity.core.advanced.domain.capabilities.InfinityDeviceMetrics(
            capturedAtEpochMillis = 1L,
            cpuUsagePercent = null,
            memoryUsagePercent = 42f,
            storageUsagePercent = null
        )

        assertNotNull(metrics)
    }

    @Test
    fun metricsRejectValuesOutsidePercentRange() {
        assertThrows(IllegalArgumentException::class.java) {
            com.infinity.core.advanced.domain.capabilities.InfinityDeviceMetrics(
                capturedAtEpochMillis = 1L,
                cpuUsagePercent = 101f,
                memoryUsagePercent = null,
                storageUsagePercent = null
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            com.infinity.core.advanced.domain.capabilities.InfinityDeviceMetrics(
                capturedAtEpochMillis = 1L,
                cpuUsagePercent = null,
                memoryUsagePercent = -1f,
                storageUsagePercent = null
            )
        }
    }
}
