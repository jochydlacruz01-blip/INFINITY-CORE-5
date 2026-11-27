package com.infinity.core.advanced.presentation

import com.infinity.core.advanced.domain.usecases.InfinityAdvancedOverviewSnapshot
import com.infinity.core.advanced.domain.usecases.ObserveInfinityAdvancedOverview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Lifecycle-neutral presentation state holder for ADVANCED.
 * The owner supplies a lifecycle-bound scope; no Android APIs or storage are accessed here.
 */
class InfinityAdvancedOverviewPresenter(
    private val observeOverview: ObserveInfinityAdvancedOverview,
    scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow<InfinityAdvancedOverviewUiState>(
        InfinityAdvancedOverviewUiState.Loading
    )

    val state: StateFlow<InfinityAdvancedOverviewUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            try {
                observeOverview.observe().collect { snapshot ->
                    mutableState.value = InfinityAdvancedOverviewUiState.Content(snapshot)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = InfinityAdvancedOverviewUiState.Unavailable
            }
        }
    }
}

sealed interface InfinityAdvancedOverviewUiState {
    data object Loading : InfinityAdvancedOverviewUiState
    data class Content(
        val snapshot: InfinityAdvancedOverviewSnapshot
    ) : InfinityAdvancedOverviewUiState
    data object Unavailable : InfinityAdvancedOverviewUiState
}
