package com.infinity.core

import org.junit.Assert.*
import org.junit.Test

class InfinityConnectSessionActivityPolicyTest {
    @Test fun heartbeatRequiresRecentHumanActivity() {
        assertFalse(InfinityConnectSessionActivityPolicy.isHumanActivityRecent(0L, 10_000L, 120_000L))
        assertTrue(InfinityConnectSessionActivityPolicy.isHumanActivityRecent(10_000L, 129_999L, 120_000L))
        assertFalse(InfinityConnectSessionActivityPolicy.isHumanActivityRecent(10_000L, 130_000L, 120_000L))
    }

    @Test fun idleTimeoutUsesLastHumanActivityOrSessionStartGracePeriod() {
        assertFalse(InfinityConnectSessionActivityPolicy.hasIdleTimeoutExpired(0L, 1_000L, 120_999L, 120_000L))
        assertTrue(InfinityConnectSessionActivityPolicy.hasIdleTimeoutExpired(0L, 1_000L, 121_000L, 120_000L))
        assertTrue(InfinityConnectSessionActivityPolicy.hasIdleTimeoutExpired(5_000L, 1_000L, 125_000L, 120_000L))
    }

    @Test fun elapsedRealtimeRollbackFailsClosed() {
        assertFalse(InfinityConnectSessionActivityPolicy.isHumanActivityRecent(20_000L, 10_000L, 120_000L))
        assertFalse(InfinityConnectSessionActivityPolicy.hasIdleTimeoutExpired(20_000L, 1_000L, 10_000L, 120_000L))
    }
}
