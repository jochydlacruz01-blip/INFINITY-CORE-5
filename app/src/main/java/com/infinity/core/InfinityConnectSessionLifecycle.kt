package com.infinity.core

/** Explicit session lifecycle for CONNECT; engine implementations must drive these transitions. */
enum class InfinityConnectSessionState {
    IDLE, PREPARING, CONNECTING, AUTHENTICATING, CONNECTED, RECONNECTING, DISCONNECTING, FAILED
}

class InfinityConnectSessionLifecycle {
    @Volatile
    var state: InfinityConnectSessionState = InfinityConnectSessionState.IDLE
        private set

    @Volatile
    var failureMessage: String? = null
        private set

    /**
     * Engine callbacks may arrive from native/network threads while UI cancellation is in flight.
     * Serialize validation and mutation so two competing events cannot both advance one state.
     */
    @Synchronized
    fun transition(next: InfinityConnectSessionState, message: String? = null): Boolean {
        if (next !in (ALLOWED_TRANSITIONS[state] ?: emptySet())) return false
        state = next
        failureMessage = if (next == InfinityConnectSessionState.FAILED) {
            message ?: "Error de conexión"
        } else null
        return true
    }

    @Synchronized
    fun reset(): Boolean = transition(InfinityConnectSessionState.IDLE)

    private companion object {
        val ALLOWED_TRANSITIONS = mapOf(
            InfinityConnectSessionState.IDLE to setOf(InfinityConnectSessionState.PREPARING),
            InfinityConnectSessionState.PREPARING to setOf(
                InfinityConnectSessionState.CONNECTING,
                InfinityConnectSessionState.FAILED,
                InfinityConnectSessionState.IDLE
            ),
            InfinityConnectSessionState.CONNECTING to setOf(
                InfinityConnectSessionState.AUTHENTICATING,
                InfinityConnectSessionState.CONNECTED,
                InfinityConnectSessionState.RECONNECTING,
                InfinityConnectSessionState.FAILED,
                InfinityConnectSessionState.DISCONNECTING
            ),
            InfinityConnectSessionState.AUTHENTICATING to setOf(
                InfinityConnectSessionState.CONNECTED,
                InfinityConnectSessionState.RECONNECTING,
                InfinityConnectSessionState.FAILED,
                InfinityConnectSessionState.DISCONNECTING
            ),
            InfinityConnectSessionState.CONNECTED to setOf(
                InfinityConnectSessionState.RECONNECTING,
                InfinityConnectSessionState.DISCONNECTING,
                InfinityConnectSessionState.FAILED
            ),
            InfinityConnectSessionState.RECONNECTING to setOf(
                InfinityConnectSessionState.CONNECTING,
                InfinityConnectSessionState.AUTHENTICATING,
                InfinityConnectSessionState.CONNECTED,
                InfinityConnectSessionState.FAILED,
                InfinityConnectSessionState.DISCONNECTING
            ),
            InfinityConnectSessionState.DISCONNECTING to setOf(
                InfinityConnectSessionState.IDLE,
                InfinityConnectSessionState.FAILED
            ),
            InfinityConnectSessionState.FAILED to setOf(
                InfinityConnectSessionState.PREPARING,
                InfinityConnectSessionState.IDLE,
                InfinityConnectSessionState.DISCONNECTING
            )
        )
    }
}
