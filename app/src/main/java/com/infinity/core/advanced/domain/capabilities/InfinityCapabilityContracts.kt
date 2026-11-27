package com.infinity.core.advanced.domain.capabilities

import kotlinx.coroutines.flow.Flow

/**
 * Stable boundary through which higher-level INFINITY modules consume capabilities
 * already owned by BASIC or INTERMEDIATE.
 *
 * ADVANCED must depend on these contracts, never on another module's UI classes
 * or storage implementation.
 */
enum class InfinityCapabilityId {
    NOTIFICATIONS,
    DEVICE_MONITORING,
    THEME,
    APPLICATION_LAUNCHER,
    CONNECTIVITY,
    USER_PREFERENCES
}

/** Registry is read-only for consumers; registration belongs to app composition. */
interface InfinityCapabilityRegistry {
    fun <T : Any> find(id: InfinityCapabilityId, contract: Class<T>): T?
}

class DefaultInfinityCapabilityRegistry(bindings: Map<InfinityCapabilityId, Any>) : InfinityCapabilityRegistry {
    private val capabilities = bindings.toMap()

    override fun <T : Any> find(id: InfinityCapabilityId, contract: Class<T>): T? {
        val capability = capabilities[id] ?: return null
        require(contract.isInstance(capability)) { "Capability $id does not implement ${contract.name}" }
        return contract.cast(capability)
    }
}

sealed interface CapabilityAvailability {
    data object Available : CapabilityAvailability
    data object NotInstalled : CapabilityAvailability
    data class Unavailable(val reason: String) : CapabilityAvailability
}

/** Read-only view of the notification feed owned by BASIC/INTERMEDIATE. */
interface InfinityNotificationCapability {
    val notifications: Flow<List<InfinityNotificationSummary>>
}

data class InfinityNotificationSummary(
    val key: String,
    val source: String,
    val title: String?,
    val receivedAtEpochMillis: Long,
    val isSensitive: Boolean
)

interface InfinityDeviceMonitoringCapability {
    fun observeMetrics(): Flow<InfinityDeviceMetrics>
}

data class InfinityDeviceMetrics(
    val capturedAtEpochMillis: Long,
    val cpuUsagePercent: Float?,
    val memoryUsagePercent: Float?,
    val storageUsagePercent: Float?,
    val batteryLevelPercent: Float? = null,
    val batteryTemperatureCelsius: Float? = null,
    val isCharging: Boolean? = null,
    val thermalStatus: String? = null
) {
    init {
        require(cpuUsagePercent == null || cpuUsagePercent in 0f..100f)
        require(memoryUsagePercent == null || memoryUsagePercent in 0f..100f)
        require(storageUsagePercent == null || storageUsagePercent in 0f..100f)
        require(batteryLevelPercent == null || batteryLevelPercent in 0f..100f)
        require(batteryTemperatureCelsius == null || batteryTemperatureCelsius in -30f..120f)
        require(thermalStatus == null || thermalStatus in setOf("Normal", "Ligero", "Moderado", "Severo", "Crítico", "Emergencia", "Apagado"))
    }
}

interface InfinityThemeCapability {
    val selectedThemeId: Flow<String>
    suspend fun selectTheme(themeId: String)
}

interface InfinityApplicationLauncherCapability {
    suspend fun launch(packageName: String): LaunchCapabilityResult
}

sealed interface LaunchCapabilityResult {
    data object Launched : LaunchCapabilityResult
    data object AppNotFound : LaunchCapabilityResult
    data class Failed(val reason: String) : LaunchCapabilityResult
}

interface InfinityConnectivityCapability {
    val connectionState: Flow<InfinityConnectionState>
}

data class InfinityConnectionState(
    val hasNetwork: Boolean,
    val isMetered: Boolean,
    val transport: String?
)

interface InfinityUserPreferencesCapability {
    fun observeString(key: String, defaultValue: String): Flow<String>
    suspend fun putString(key: String, value: String)
}
