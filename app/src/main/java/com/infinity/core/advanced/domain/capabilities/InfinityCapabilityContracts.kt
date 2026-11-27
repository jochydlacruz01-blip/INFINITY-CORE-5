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

/**
 * Registry is intentionally read-only for consumers. Composition and registration
 * belong to the application assembly layer, not to ADVANCED feature screens.
 */
interface InfinityCapabilityRegistry {
    fun <T : Any> find(
        id: InfinityCapabilityId,
        contract: Class<T>
    ): T?
}

/**
 * Immutable registry implementation. The constructor validates each binding so a
 * misconfigured adapter is detected during application composition, not at runtime
 * inside a feature screen.
 */
class DefaultInfinityCapabilityRegistry(
    bindings: Map<InfinityCapabilityId, Any>
) : InfinityCapabilityRegistry {

    private val capabilities = bindings.toMap()

    override fun <T : Any> find(
        id: InfinityCapabilityId,
        contract: Class<T>
    ): T? {
        val capability = capabilities[id] ?: return null
        require(contract.isInstance(capability)) {
            "Capability $id does not implement ${contract.name}"
        }
        return contract.cast(capability)
    }
}

/** Shared status model; unavailable capabilities must degrade gracefully. */
sealed interface CapabilityAvailability {
    data object Available : CapabilityAvailability
    data object NotInstalled : CapabilityAvailability
    data class Unavailable(val reason: String) : CapabilityAvailability
}

/** BASIC/INTERMEDIATE notification adapter contract. */
interface InfinityNotificationCapability {
    val notifications: Flow<List<InfinityNotificationSummary>>
    suspend fun publish(title: String, message: String)
}

data class InfinityNotificationSummary(
    val key: String,
    val source: String,
    val title: String?,
    val receivedAtEpochMillis: Long,
    val isSensitive: Boolean
)

/** INTERMEDIATE device-monitoring adapter contract. */
interface InfinityDeviceMonitoringCapability {
    fun observeMetrics(): Flow<InfinityDeviceMetrics>
}

data class InfinityDeviceMetrics(
    val capturedAtEpochMillis: Long,
    val cpuUsagePercent: Float?,
    val memoryUsagePercent: Float?,
    val storageUsagePercent: Float?
) {
    init {
        require(cpuUsagePercent == null || cpuUsagePercent in 0f..100f)
        require(memoryUsagePercent == null || memoryUsagePercent in 0f..100f)
        require(storageUsagePercent == null || storageUsagePercent in 0f..100f)
    }
}

/** BASIC theme system adapter contract; ADVANCED may extend, not replace, themes. */
interface InfinityThemeCapability {
    val selectedThemeId: Flow<String>
    suspend fun selectTheme(themeId: String)
}

/** BASIC application-launcher adapter contract. */
interface InfinityApplicationLauncherCapability {
    suspend fun launch(packageName: String): LaunchCapabilityResult
}

sealed interface LaunchCapabilityResult {
    data object Launched : LaunchCapabilityResult
    data object AppNotFound : LaunchCapabilityResult
    data class Failed(val reason: String) : LaunchCapabilityResult
}

/** Existing connectivity status contract; it does not expose privileged toggles. */
interface InfinityConnectivityCapability {
    val connectionState: Flow<InfinityConnectionState>
}

data class InfinityConnectionState(
    val hasNetwork: Boolean,
    val isMetered: Boolean,
    val transport: String?
)

/** Existing preferences adapter contract. Sensitive credentials are excluded. */
interface InfinityUserPreferencesCapability {
    fun observeString(key: String, defaultValue: String): Flow<String>
    suspend fun putString(key: String, value: String)
}
