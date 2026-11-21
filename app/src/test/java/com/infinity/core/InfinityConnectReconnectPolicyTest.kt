package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityConnectReconnectPolicyTest {
    @Test fun transientFailuresRetryWithBoundedBackoff() {
        val expected = listOf(1000L, 2000L, 4000L, 8000L, 16000L)
        expected.forEachIndexed { index, delay ->
            val decision = InfinityConnectReconnectPolicy.decide(index, InfinityConnectFailureKind.TIMEOUT)
            assertTrue(decision.retry)
            assertEquals(delay, decision.delayMillis)
        }
        assertFalse(InfinityConnectReconnectPolicy.decide(5, InfinityConnectFailureKind.NETWORK).retry)
    }
    @Test fun permanentFailuresNeverRetry() {
        listOf(InfinityConnectFailureKind.AUTHENTICATION, InfinityConnectFailureKind.INVALID_CONFIGURATION, InfinityConnectFailureKind.USER_CANCELLED).forEach {
            assertFalse(InfinityConnectReconnectPolicy.decide(0, it).retry)
        }
    }
    @Test fun maxAttemptsCanDisableRetry() {
        assertFalse(InfinityConnectReconnectPolicy.decide(0, InfinityConnectFailureKind.SERVER, 0).retry)
    }
}
