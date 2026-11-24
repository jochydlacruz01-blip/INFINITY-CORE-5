package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityConnectSessionLifecycleTest {
    @Test fun rejectsSkippingConnectionStages() {
        val lifecycle = InfinityConnectSessionLifecycle()
        assertFalse(lifecycle.transition(InfinityConnectSessionState.CONNECTED))
        assertEquals(InfinityConnectSessionState.IDLE, lifecycle.state)
    }

    @Test fun allowsPreparationAndConnectionStages() {
        val lifecycle = InfinityConnectSessionLifecycle()
        assertTrue(lifecycle.transition(InfinityConnectSessionState.PREPARING))
        assertTrue(lifecycle.transition(InfinityConnectSessionState.CONNECTING))
        assertTrue(lifecycle.transition(InfinityConnectSessionState.AUTHENTICATING))
        assertTrue(lifecycle.transition(InfinityConnectSessionState.CONNECTED))
    }

    @Test fun storesFailureMessageAndCanRecoverToPrepare() {
        val lifecycle = InfinityConnectSessionLifecycle()
        lifecycle.transition(InfinityConnectSessionState.PREPARING)
        lifecycle.transition(InfinityConnectSessionState.CONNECTING)
        assertTrue(lifecycle.transition(InfinityConnectSessionState.FAILED, "Host unreachable"))
        assertEquals("Host unreachable", lifecycle.failureMessage)
        assertTrue(lifecycle.transition(InfinityConnectSessionState.PREPARING))
        assertNull(lifecycle.failureMessage)
    }

    @Test fun disconnectLifecycleReturnsToIdle() {
        val lifecycle = InfinityConnectSessionLifecycle()
        lifecycle.transition(InfinityConnectSessionState.PREPARING)
        lifecycle.transition(InfinityConnectSessionState.CONNECTING)
        lifecycle.transition(InfinityConnectSessionState.DISCONNECTING)
        assertTrue(lifecycle.transition(InfinityConnectSessionState.IDLE))
    }

    @Test fun rejectsInvalidTransitionsWithoutMutatingLifecycle() {
        val lifecycle = InfinityConnectSessionLifecycle()
        assertFalse(lifecycle.transition(InfinityConnectSessionState.AUTHENTICATING))
        assertEquals(InfinityConnectSessionState.IDLE, lifecycle.state)
        assertNull(lifecycle.failureMessage)

        assertTrue(lifecycle.transition(InfinityConnectSessionState.PREPARING))
        assertTrue(lifecycle.transition(InfinityConnectSessionState.CONNECTING))
        assertTrue(lifecycle.transition(InfinityConnectSessionState.FAILED, "safe failure"))
        assertFalse(lifecycle.transition(InfinityConnectSessionState.CONNECTED))
        assertEquals(InfinityConnectSessionState.FAILED, lifecycle.state)
        assertEquals("safe failure", lifecycle.failureMessage)
    }

    @Test fun failedSessionCanBeSafelyDismissedToIdle() {
        val lifecycle = InfinityConnectSessionLifecycle()
        assertTrue(lifecycle.transition(InfinityConnectSessionState.PREPARING))
        assertTrue(lifecycle.transition(InfinityConnectSessionState.CONNECTING))
        assertTrue(lifecycle.transition(InfinityConnectSessionState.FAILED, "safe failure"))
        assertTrue(lifecycle.transition(InfinityConnectSessionState.DISCONNECTING))
        assertTrue(lifecycle.transition(InfinityConnectSessionState.IDLE))
        assertEquals(InfinityConnectSessionState.IDLE, lifecycle.state)
        assertNull(lifecycle.failureMessage)
    }

    @Test fun disconnectCannotStartBeforeConnectionPreparation() {
        val lifecycle = InfinityConnectSessionLifecycle()
        assertFalse(lifecycle.transition(InfinityConnectSessionState.DISCONNECTING))
        assertEquals(InfinityConnectSessionState.IDLE, lifecycle.state)
    }
}
