package com.infinity.core

/** Explicit session lifecycle for CONNECT; engine implementations must drive these transitions. */
enum class InfinityConnectSessionState {
    IDLE, PREPARING, CONNECTING, AUTHENTICATING, CONNECTED, RECONNECTING, DISCONNECTING, FAILED
}

class InfinityConnectSessionLifecycle {
    var state: InfinityConnectSessionState = InfinityConnectSessionState.IDLE
        private set
    var failureMessage: String? = null
        private set

    fun transition(next: InfinityConnectSessionState, message: String? = null): Boolean {
        val allowed = mapOf(
            InfinityConnectSessionState.IDLE to setOf(InfinityConnectSessionState.PREPARING),
            InfinityConnectSessionState.PREPARING to setOf(InfinityConnectSessionState.CONNECTING, InfinityConnectSessionState.FAILED, InfinityConnectSessionState.IDLE),
            InfinityConnectSessionState.CONNECTING to setOf(InfinityConnectSessionState.AUTHENTICATING, InfinityConnectSessionState.CONNECTED, InfinityConnectSessionState.RECONNECTING, InfinityConnectSessionState.FAILED, InfinityConnectSessionState.DISCONNECTING),
            InfinityConnectSessionState.AUTHENTICATING to setOf(InfinityConnectSessionState.CONNECTED, InfinityConnectSessionState.RECONNECTING, InfinityConnectSessionState.FAILED, InfinityConnectSessionState.DISCONNECTING),
            InfinityConnectSessionState.CONNECTED to setOf(InfinityConnectSessionState.RECONNECTING, InfinityConnectSessionState.DISCONNECTING, InfinityConnectSessionState.FAILED),
            InfinityConnectSessionState.RECONNECTING to setOf(InfinityConnectSessionState.CONNECTING, InfinityConnectSessionState.AUTHENTICATING, InfinityConnectSessionState.CONNECTED, InfinityConnectSessionState.FAILED, InfinityConnectSessionState.DISCONNECTING),
            InfinityConnectSessionState.DISCONNECTING to setOf(InfinityConnectSessionState.IDLE, InfinityConnectSessionState.FAILED),
            InfinityConnectSessionState.FAILED to setOf(InfinityConnectSessionState.PREPARING, InfinityConnectSessionState.IDLE, InfinityConnectSessionState.DISCONNECTING)
        )
        if (next !in (allowed[state] ?: emptySet())) return false
        state = next
        failureMessage = if (next == InfinityConnectSessionState.FAILED) message ?: "Error de conexión" else null
        return true
    }

    fun reset(): Boolean = transition(InfinityConnectSessionState.IDLE)
}
