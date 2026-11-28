package com.infinity.core

/** Pure elapsed-realtime policy: only recent human input can authorize a heartbeat. */
internal object InfinityConnectSessionActivityPolicy {
    fun isHumanActivityRecent(lastActivityElapsed: Long, nowElapsed: Long, idleLimitMillis: Long): Boolean {
        require(idleLimitMillis > 0L)
        return lastActivityElapsed > 0L && nowElapsed >= lastActivityElapsed &&
            nowElapsed - lastActivityElapsed < idleLimitMillis
    }

    fun hasIdleTimeoutExpired(lastActivityElapsed: Long, sessionStartedElapsed: Long, nowElapsed: Long, idleLimitMillis: Long): Boolean {
        require(idleLimitMillis > 0L)
        val baseline = if (lastActivityElapsed > 0L) lastActivityElapsed else sessionStartedElapsed
        return baseline > 0L && nowElapsed >= baseline && nowElapsed - baseline >= idleLimitMillis
    }
}
