package com.infinity.core

import kotlinx.coroutines.CancellationException
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
        } catch (cancelled: CancellationException) {
            // Preserve structured cancellation; callers must be able to stop CONNECT promptly.
            throw cancelled
        } catch (_: Exception) {
            // Native engines may include hostnames, account names, or protocol details in errors.
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
        try {
            engine.disconnect()
        } catch (cancelled: CancellationException) {
            // Restore a consistent lifecycle while preserving structured cancellation.
            moveToIdle(sink)
            throw cancelled
        } catch (_: Exception) {
            // Native disconnect diagnostics are intentionally not exposed to the UI.
        }
        moveToIdle(sink)
    }

    private fun move(next: InfinityConnectSessionState, sink: (InfinityConnectSessionState, String?) -> Unit) {
        if (lifecycle.transition(next)) sink(lifecycle.state, lifecycle.failureMessage)
    }
    private fun fail(message: String, sink: (InfinityConnectSessionState, String?) -> Unit) {
        // Never surface raw engine text: it may contain endpoint or authentication details.
        val safe = "No se pudo completar la conexión."
        if (lifecycle.transition(InfinityConnectSessionState.FAILED, safe)) sink(lifecycle.state, lifecycle.failureMessage)
    }
    private fun moveToIdle(sink: (InfinityConnectSessionState, String?) -> Unit) {
        val current = lifecycle.state
        if (current == InfinityConnectSessionState.CONNECTED ||
            current == InfinityConnectSessionState.CONNECTING ||
            current == InfinityConnectSessionState.AUTHENTICATING ||
            current == InfinityConnectSessionState.RECONNECTING) {
            lifecycle.transition(InfinityConnectSessionState.DISCONNECTING)
        }
        if (lifecycle.state == InfinityConnectSessionState.DISCONNECTING ||
            lifecycle.state == InfinityConnectSessionState.FAILED) {
            if (lifecycle.transition(InfinityConnectSessionState.IDLE)) sink(lifecycle.state, null)
        }
    }
}
