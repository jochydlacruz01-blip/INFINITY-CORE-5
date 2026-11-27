package com.infinity.core.advanced.domain.automation

/** Pure rolling-hour execution history used to enforce the UI's hourly limit. */
object InfinityAutomationExecutionLedger {
    const val WINDOW_MILLIS = 60L * 60L * 1000L

    fun recentExecutions(timestamps: List<Long>, nowEpochMillis: Long): List<Long> {
        if (nowEpochMillis < 0L) return emptyList()
        val windowStart = (nowEpochMillis - WINDOW_MILLIS).coerceAtLeast(0L)
        return timestamps
            .asSequence()
            .filter { it in windowStart..nowEpochMillis }
            .distinct()
            .sorted()
            .toList()
    }

    fun recordExecution(timestamps: List<Long>, nowEpochMillis: Long): List<Long> {
        require(nowEpochMillis >= 0L) { "Execution timestamp must be non-negative" }
        return (recentExecutions(timestamps, nowEpochMillis) + nowEpochMillis).distinct().sorted()
    }
}
