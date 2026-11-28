package com.infinity.core

import com.infinity.core.advanced.domain.capabilities.DefaultInfinityCapabilityRegistry
import com.infinity.core.advanced.domain.capabilities.InfinityCapabilityId
import com.infinity.core.advanced.domain.capabilities.InfinityNotificationCapability
import com.infinity.core.advanced.domain.capabilities.InfinityNotificationSummary
import com.infinity.core.advanced.domain.usecases.ObserveInfinityAdvancedOverview
import com.infinity.core.advanced.presentation.InfinityAdvancedOverviewPresenter
import com.infinity.core.advanced.presentation.InfinityAdvancedOverviewUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityAdvancedOverviewPresenterTest {
    @Test
    fun publishesContentFromDomainOverview() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val presenter = InfinityAdvancedOverviewPresenter(
                ObserveInfinityAdvancedOverview(DefaultInfinityCapabilityRegistry(emptyMap())),
                scope
            )

            val state = presenter.state.value
            assertTrue(state is InfinityAdvancedOverviewUiState.Content)
            assertEquals(
                "midnight",
                (state as InfinityAdvancedOverviewUiState.Content).snapshot.selectedThemeId
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun exposesSafeUnavailableStateWhenObservationFails() {
        val failingNotifications = object : InfinityNotificationCapability {
            override val notifications = flow<List<InfinityNotificationSummary>> {
                throw IllegalStateException("private upstream detail")
            }
        }
        val registry = DefaultInfinityCapabilityRegistry(
            mapOf(InfinityCapabilityId.NOTIFICATIONS to failingNotifications)
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val presenter = InfinityAdvancedOverviewPresenter(
                ObserveInfinityAdvancedOverview(registry),
                scope
            )

            assertTrue(presenter.state.value is InfinityAdvancedOverviewUiState.Unavailable)
        } finally {
            scope.cancel()
        }
    }
}
