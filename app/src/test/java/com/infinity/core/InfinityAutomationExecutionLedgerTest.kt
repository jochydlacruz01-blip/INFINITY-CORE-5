package com.infinity.core

import com.infinity.core.advanced.domain.automation.InfinityAutomationExecutionLedger
import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityAutomationExecutionLedgerTest {
    @Test
    fun keepsOnlyExecutionsInsideTheRollingHour() {
        val now = 10L * InfinityAutomationExecutionLedger.WINDOW_MILLIS
        val values = listOf(now - 3_600_001L, now - 3_600_000L, now - 60_000L, now + 1L, now - 60_000L)

        assertEquals(
            listOf(now - 3_600_000L, now - 60_000L),
            InfinityAutomationExecutionLedger.recentExecutions(values, now)
        )
    }

    @Test
    fun recordExecutionPrunesOldEntriesAndAddsCurrentTimeOnce() {
        val now = 9_000_000L
        val old = now - InfinityAutomationExecutionLedger.WINDOW_MILLIS - 1L

        assertEquals(
            listOf(now - 1_000L, now),
            InfinityAutomationExecutionLedger.recordExecution(listOf(old, now - 1_000L), now)
        )
    }

    @Test
    fun invalidClockDoesNotExposeFutureEntries() {
        assertEquals(emptyList<Long>(), InfinityAutomationExecutionLedger.recentExecutions(listOf(1L, 2L), -1L))
    }
}
