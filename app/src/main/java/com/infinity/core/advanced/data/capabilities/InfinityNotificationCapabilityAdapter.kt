package com.infinity.core.advanced.data.capabilities

import androidx.compose.runtime.snapshotFlow
import com.infinity.core.InfinityNotification
import com.infinity.core.InfinityNotificationStore
import com.infinity.core.advanced.domain.capabilities.InfinityNotificationCapability
import com.infinity.core.advanced.domain.capabilities.InfinityNotificationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Maps the existing notification store; it does not create a second feed or persist content. */
internal class InfinityNotificationCapabilityAdapter : InfinityNotificationCapability {
    override val notifications: Flow<List<InfinityNotificationSummary>> =
        snapshotFlow { InfinityNotificationStore.notifications.toList() }
            .map(::mapInfinityNotifications)

    companion object {
        internal fun mapInfinityNotifications(
            items: List<InfinityNotification>
        ): List<InfinityNotificationSummary> = items.map { item ->
            infinityNotificationSummary(item)
        }
    }
}

internal fun infinityNotificationSummary(
    item: InfinityNotification
): InfinityNotificationSummary {
    val visibleTitle = item.title.takeIf { it.isNotBlank() }
    return InfinityNotificationSummary(
        key = item.key,
        source = item.appName,
        title = visibleTitle,
        receivedAtEpochMillis = item.timestamp,
        isSensitive = visibleTitle == null
    )
}
