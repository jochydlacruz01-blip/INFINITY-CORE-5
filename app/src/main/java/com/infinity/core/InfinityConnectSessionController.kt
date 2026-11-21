package com.infinity.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates lifecycle and engine events. Requires a real engine supplied by the caller;
 * this class does not itself establish an RDP connection.
 */
class InfinityConnectSessionController(private val engine: InfinityConnectEngine) {
    private val mutex = Mutex()
    val lifecycle = InfinityConnectSessionLifecycle()

    suspend fun connect(target: InfinityConnectTarget, sink: (InfinityConnectSessionState, String?) -> Unit = { _, _ -> }) {
        mutex.withLock {
            if (lifecycle.state != InfinityConnectSessionState.IDLE &&
                lifecycle.state != InfinityConnectSessionState.FAILED) return
            if (lifecycle.state == InfinityConnectSessionState.FAILED) lifecycle.transition(InfinityConnectSessionState.IDLE)
            lifecycle.transition(InfinityConnectSessionState.PREPARING)
            sink(lifecycle.state, null)
            if (!lifecycle.transition(InfinityConnectSessionState.CONNECTING)) return
            sink(lifecycle.state, null)
        }
        try {
            engine.connect(target) { event ->
                when (event) {
                    InfinityConnectEngineEvent.Connecting -> move(InfinityConnectSessionState.CONNECTING, sink)
                    InfinityConnectEngineEvent.Authenticating -> move(InfinityConnectSessionState.AUTHENTICATING, sink)
                    InfinityConnectEngineEvent.Connected -> move(InfinityConnectSessionState.CONNECTED, sink)
                    is InfinityConnectEngineEvent.Failure -> fail(event.message, sink)
                    InfinityConnectEngineEvent.Disconnected -> moveToIdle(sink)
                }
            }
        } catch (e: Exception) {
            fail("No se pudo completar la conexión.", sink)
        } finally {
            target.password?.fill('\u0000')
        }
    }

    suspend fun disconnect(sink: (InfinityConnectSessionState, String?) -> Unit = { _, _ -> }) {
        mutex.withLock {
            val state = lifecycle.state
            if (state == InfinityConnectSessionState.IDLE) return
            if (state != InfinityConnectSessionState.DISCONNECTING) {
                if (!lifecycle.transition(InfinityConnectSessionState.DISCONNECTING)) {
                    if (state == InfinityConnectSessionState.FAILED) {
                        lifecycle.transition(InfinityConnectSessionState.IDLE)
                        sink(lifecycle.state, null)
                        return
                    }
                    return
                }
                sink(lifecycle.state, null)
            }
        }
        try { engine.disconnect() } catch (_: Exception) { /* best-effort cleanup */ }
        moveToIdle(sink)
    }

    private fun move(next: InfinityConnectSessionState, sink: (InfinityConnectSessionState, String?) -> Unit) {
        if (lifecycle.transition(next)) sink(lifecycle.state, lifecycle.failureMessage)
    }
    private fun fail(message: String, sink: (InfinityConnectSessionState, String?) -> Unit) {
        val safe = message.take(240).ifBlank { "Error de conexión." }
        if (lifecycle.transition(InfinityConnectSessionState.FAILED, safe)) sink(lifecycle.state, lifecycle.failureMessage)
    }
    private fun moveToIdle(sink: (InfinityConnectSessionState, String?) -> Unit) {
        if (lifecycle.state == InfinityConnectSessionState.DISCONNECTING ||
            lifecycle.state == InfinityConnectSessionState.FAILED) {
            if (lifecycle.transition(InfinityConnectSessionState.IDLE)) sink(lifecycle.state, null)
        }
    }
}
