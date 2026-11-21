package com.infinity.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InfinityConnectSessionControllerTest {
    private class FakeEngine : InfinityConnectEngine {
        var connected = false
        var disconnectCalled = false
        var emitted: ((InfinityConnectEngineEvent) -> Unit)? = null
        override val isConnected get() = connected
        override suspend fun connect(target: InfinityConnectTarget, eventSink: (InfinityConnectEngineEvent) -> Unit) {
            emitted = eventSink
            eventSink(InfinityConnectEngineEvent.Authenticating)
            eventSink(InfinityConnectEngineEvent.Connected)
            connected = true
        }
        override suspend fun disconnect() { disconnectCalled = true; connected = false }
    }

    @Test fun controllerDrivesLifecycleUsingInjectedEngine() = runBlocking {
        val engine = FakeEngine()
        val controller = InfinityConnectSessionController(engine)
        controller.connect(InfinityConnectTarget("host", username = "user"))
        assertEquals(InfinityConnectSessionState.CONNECTED, controller.lifecycle.state)
    }

    @Test fun disconnectReturnsToIdleAndInvokesEngine() = runBlocking {
        val engine = FakeEngine()
        val controller = InfinityConnectSessionController(engine)
        controller.connect(InfinityConnectTarget("host", username = "user"))
        controller.disconnect()
        assertTrue(engine.disconnectCalled)
        assertEquals(InfinityConnectSessionState.IDLE, controller.lifecycle.state)
    }

    @Test fun engineFailureIsRepresentedInLifecycle() = runBlocking {
        val engine = object : InfinityConnectEngine {
            override val isConnected = false
            override suspend fun connect(target: InfinityConnectTarget, eventSink: (InfinityConnectEngineEvent) -> Unit) {
                eventSink(InfinityConnectEngineEvent.Failure("Server rejected"))
            }
            override suspend fun disconnect() {}
        }
        val controller = InfinityConnectSessionController(engine)
        controller.connect(InfinityConnectTarget("host", username = "user"))
        assertEquals(InfinityConnectSessionState.FAILED, controller.lifecycle.state)
        assertEquals("Server rejected", controller.lifecycle.failureMessage)
    }
}
