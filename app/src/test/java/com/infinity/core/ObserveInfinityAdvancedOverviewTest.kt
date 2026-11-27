package com.infinity.core

import com.infinity.core.advanced.domain.capabilities.DefaultInfinityCapabilityRegistry
import com.infinity.core.advanced.domain.capabilities.InfinityCapabilityId
import com.infinity.core.advanced.domain.capabilities.InfinityConnectionState
import com.infinity.core.advanced.domain.capabilities.InfinityDeviceMetrics
import com.infinity.core.advanced.domain.capabilities.InfinityDeviceMonitoringCapability
import com.infinity.core.advanced.domain.capabilities.InfinityConnectivityCapability
import com.infinity.core.advanced.domain.capabilities.InfinityNotificationCapability
import com.infinity.core.advanced.domain.capabilities.InfinityNotificationSummary
import com.infinity.core.advanced.domain.capabilities.InfinityThemeCapability
import com.infinity.core.advanced.domain.usecases.ObserveInfinityAdvancedOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObserveInfinityAdvancedOverviewTest {
    @Test
    fun combinesRegisteredCapabilitiesIntoOneSnapshot() = runBlocking {
        val notification = InfinityNotificationSummary(
            key = "n-1",
            source = "system",
            title = "Update",
            receivedAtEpochMillis = 100L,
            isSensitive = false
        )
        val metrics = InfinityDeviceMetrics(200L, null, 45f, 62f)
        val connection = InfinityConnectionState(true, true, "cellular")
        val registry = DefaultInfinityCapabilityRegistry(
            mapOf(
                InfinityCapabilityId.NOTIFICATIONS to object : InfinityNotificationCapability {
                    override val notifications = flowOf(listOf(notification))
                },
                InfinityCapabilityId.DEVICE_MONITORING to object : InfinityDeviceMonitoringCapability {
                    override fun observeMetrics(): Flow<InfinityDeviceMetrics> = flowOf(metrics)
                },
                InfinityCapabilityId.CONNECTIVITY to object : InfinityConnectivityCapability {
                    override val connectionState = flowOf(connection)
                },
                InfinityCapabilityId.THEME to object : InfinityThemeCapability {
                    override val selectedThemeId = flowOf("ocean")
                    override suspend fun selectTheme(themeId: String) = Unit
                }
            )
        )

        val snapshot = ObserveInfinityAdvancedOverview(registry).observe().first()

        assertEquals(listOf(notification), snapshot.notifications)
        assertEquals(metrics, snapshot.deviceMetrics)
        assertEquals(connection, snapshot.connectionState)
        assertEquals("ocean", snapshot.selectedThemeId)
    }

    @Test
    fun missingCapabilitiesUseSafeDefaults() = runBlocking {
        val snapshot = ObserveInfinityAdvancedOverview(
            DefaultInfinityCapabilityRegistry(emptyMap())
        ).observe().first()

        assertEquals(emptyList<InfinityNotificationSummary>(), snapshot.notifications)
        assertNull(snapshot.deviceMetrics)
        assertNull(snapshot.connectionState)
        assertEquals("midnight", snapshot.selectedThemeId)
    }
}
