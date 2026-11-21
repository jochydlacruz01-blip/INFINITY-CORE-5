package com.infinity.core

/** Bounded retry decisions for transient remote-session failures. No networking is performed. */
enum class InfinityConnectFailureKind { NETWORK, TIMEOUT, SERVER, AUTHENTICATION, INVALID_CONFIGURATION, USER_CANCELLED }

data class InfinityConnectRetryDecision(val retry: Boolean, val delayMillis: Long, val attempt: Int)

object InfinityConnectReconnectPolicy {
    private val delays = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L)
    fun decide(attempt: Int, failure: InfinityConnectFailureKind, maxAttempts: Int = 5): InfinityConnectRetryDecision {
        require(attempt >= 0) { "Attempt cannot be negative." }
        require(maxAttempts in 0..10) { "Maximum attempts must be between 0 and 10." }
        val transient = failure in setOf(InfinityConnectFailureKind.NETWORK, InfinityConnectFailureKind.TIMEOUT, InfinityConnectFailureKind.SERVER)
        val next = attempt + 1
        val allowed = transient && next <= maxAttempts
        return InfinityConnectRetryDecision(allowed, if (allowed) delays[(next - 1).coerceAtMost(delays.lastIndex)] else 0L, next)
    }
}
