package com.infinity.core

import kotlinx.coroutines.CancellationException
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

    @Test fun controllerClearsPasswordBufferAfterSuccessfulEngineCall() = runBlocking {
        val engine = FakeEngine()
        val controller = InfinityConnectSessionController(engine)
        val secret = charArrayOf('s', 'e', 'c', 'r', 'e', 't')

        controller.connect(InfinityConnectTarget("host", username = "user", password = secret))

        assertArrayEquals(CharArray(secret.size) { '\u0000' }, secret)
        assertEquals(InfinityConnectSessionState.CONNECTED, controller.lifecycle.state)
    }

    @Test fun controllerClearsPasswordBufferWhenEngineThrows() = runBlocking {
        val engine = object : InfinityConnectEngine {
            override val isConnected = false
            override suspend fun connect(target: InfinityConnectTarget, eventSink: (InfinityConnectEngineEvent) -> Unit) {
                throw IllegalStateException("native failure")
            }
            override suspend fun disconnect() {}
        }
        val secret = charArrayOf('p', 'a', 's', 's')
        val controller = InfinityConnectSessionController(engine)

        controller.connect(InfinityConnectTarget("host", username = "user", password = secret))

        assertArrayEquals(CharArray(secret.size) { '\u0000' }, secret)
        assertEquals(InfinityConnectSessionState.FAILED, controller.lifecycle.state)
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
        assertEquals("No se pudo completar la conexión.", controller.lifecycle.failureMessage)
    }

    @Test fun engineFailureDoesNotExposeNativeDiagnosticDetails() = runBlocking {
        val engine = object : InfinityConnectEngine {
            override val isConnected = false
            override suspend fun connect(target: InfinityConnectTarget, eventSink: (InfinityConnectEngineEvent) -> Unit) {
                eventSink(InfinityConnectEngineEvent.Failure("password=secret host=private.example"))
            }
            override suspend fun disconnect() {}
        }
        val controller = InfinityConnectSessionController(engine)
        controller.connect(InfinityConnectTarget("private.example", username = "user"))
        assertEquals("No se pudo completar la conexión.", controller.lifecycle.failureMessage)
        assertFalse(controller.lifecycle.failureMessage!!.contains("secret"))
        assertFalse(controller.lifecycle.failureMessage!!.contains("private.example"))
    }

    @Test fun coroutineCancellationIsPropagatedAndPasswordIsCleared() = runBlocking {
        val engine = object : InfinityConnectEngine {
            override val isConnected = false
            override suspend fun connect(target: InfinityConnectTarget, eventSink: (InfinityConnectEngineEvent) -> Unit) {
                throw CancellationException("cancelled")
            }
            override suspend fun disconnect() {}
        }
        val secret = charArrayOf('s', 'e', 'c', 'r', 'e', 't')
        val controller = InfinityConnectSessionController(engine)
        var propagated = false
        try {
            controller.connect(InfinityConnectTarget("host", username = "user", password = secret))
        } catch (_: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
        assertArrayEquals(CharArray(secret.size) { '\u0000' }, secret)
        assertEquals(InfinityConnectSessionState.IDLE, controller.lifecycle.state)
    }

    @Test fun disconnectCancellationIsPropagatedAndLifecycleReturnsToIdle() = runBlocking {
        val engine = object : InfinityConnectEngine {
            override val isConnected = true
            override suspend fun connect(target: InfinityConnectTarget, eventSink: (InfinityConnectEngineEvent) -> Unit) {
                eventSink(InfinityConnectEngineEvent.Connected)
            }
            override suspend fun disconnect() {
                throw CancellationException("disconnect cancelled")
            }
        }
        val controller = InfinityConnectSessionController(engine)
        controller.connect(InfinityConnectTarget("host", username = "user"))

        var propagated = false
        try {
            controller.disconnect()
        } catch (_: CancellationException) {
            propagated = true
        }

        assertTrue(propagated)
        assertEquals(InfinityConnectSessionState.IDLE, controller.lifecycle.state)
    }
}
