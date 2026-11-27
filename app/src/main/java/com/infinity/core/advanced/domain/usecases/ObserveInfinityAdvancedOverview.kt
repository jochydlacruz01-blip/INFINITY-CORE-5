package com.infinity.core.advanced.domain.usecases

import com.infinity.core.advanced.domain.capabilities.InfinityCapabilityId
import com.infinity.core.advanced.domain.capabilities.InfinityCapabilityRegistry
import com.infinity.core.advanced.domain.capabilities.InfinityConnectionState
import com.infinity.core.advanced.domain.capabilities.InfinityDeviceMetrics
import com.infinity.core.advanced.domain.capabilities.InfinityDeviceMonitoringCapability
import com.infinity.core.advanced.domain.capabilities.InfinityConnectivityCapability
import com.infinity.core.advanced.domain.capabilities.InfinityNotificationCapability
import com.infinity.core.advanced.domain.capabilities.InfinityNotificationSummary
import com.infinity.core.advanced.domain.capabilities.InfinityThemeCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Read-only aggregation of capabilities already owned by other INFINITY layers.
 * Missing optional capabilities produce safe defaults; no platform APIs or storage
 * are accessed from this domain use case.
 */
class ObserveInfinityAdvancedOverview(
    registry: InfinityCapabilityRegistry
) {
    private val notifications =
        registry.find(InfinityCapabilityId.NOTIFICATIONS, InfinityNotificationCapability::class.java)
            ?.notifications ?: flowOf(emptyList())

    private val metrics: Flow<InfinityDeviceMetrics?> =
        registry.find(InfinityCapabilityId.DEVICE_MONITORING, InfinityDeviceMonitoringCapability::class.java)
            ?.observeMetrics()
            ?.map { it }
            ?: flowOf(null)

    private val connection: Flow<InfinityConnectionState?> =
        registry.find(InfinityCapabilityId.CONNECTIVITY, InfinityConnectivityCapability::class.java)
            ?.connectionState
            ?.map { it }
            ?: flowOf(null)

    private val theme: Flow<String> =
        registry.find(InfinityCapabilityId.THEME, InfinityThemeCapability::class.java)
            ?.selectedThemeId ?: flowOf("midnight")

    fun observe(): Flow<InfinityAdvancedOverviewSnapshot> =
        combine(notifications, metrics, connection, theme) { items, device, network, themeId ->
            InfinityAdvancedOverviewSnapshot(
                notifications = items,
                deviceMetrics = device,
                connectionState = network,
                selectedThemeId = themeId
            )
        }
}

data class InfinityAdvancedOverviewSnapshot(
    val notifications: List<InfinityNotificationSummary>,
    val deviceMetrics: InfinityDeviceMetrics?,
    val connectionState: InfinityConnectionState?,
    val selectedThemeId: String
)
