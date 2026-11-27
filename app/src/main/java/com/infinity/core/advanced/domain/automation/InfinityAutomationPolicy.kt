package com.infinity.core.advanced.domain.automation

/**
 * First ADVANCED automation boundary. This policy only decides whether a narrowly
 * scoped action may be offered for execution; it never performs the action.
 */
enum class InfinityAutomationAction {
    LAUNCH_APPLICATION
}

data class InfinityAutomationRequest(
    val ruleId: String,
    val action: InfinityAutomationAction,
    val enabled: Boolean,
    val userConsentGranted: Boolean,
    val targetPackageName: String,
    val nowEpochMillis: Long,
    val lastExecutionEpochMillis: Long? = null,
    val executionsInLastHour: Int = 0
)

sealed interface InfinityAutomationDecision {
    data object Allowed : InfinityAutomationDecision
    data class Rejected(val reason: Reason) : InfinityAutomationDecision

    enum class Reason {
        RULE_DISABLED,
        CONSENT_REQUIRED,
        INVALID_RULE_ID,
        UNSUPPORTED_TARGET,
        INVALID_TIMESTAMP,
        RATE_LIMIT_EXCEEDED,
        COOLDOWN_ACTIVE
    }
}

/**
 * Consent-first eligibility checks for the initial, low-risk app-launch action.
 * No background execution, accessibility, network, settings, or file actions
 * are supported by this first increment.
 */
object InfinityAutomationPolicy {
    const val MAX_EXECUTIONS_PER_HOUR = 10
    const val MIN_INTERVAL_MILLIS = 60_000L

    private val packageNamePattern =
        Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun evaluate(request: InfinityAutomationRequest): InfinityAutomationDecision {
        if (!request.enabled) return rejected(InfinityAutomationDecision.Reason.RULE_DISABLED)
        if (!request.userConsentGranted) return rejected(InfinityAutomationDecision.Reason.CONSENT_REQUIRED)
        if (request.ruleId.isBlank() || request.ruleId.length > 64 ||
            request.ruleId.any { it.isWhitespace() }) {
            return rejected(InfinityAutomationDecision.Reason.INVALID_RULE_ID)
        }
        if (request.action != InfinityAutomationAction.LAUNCH_APPLICATION ||
            !packageNamePattern.matches(request.targetPackageName)) {
            return rejected(InfinityAutomationDecision.Reason.UNSUPPORTED_TARGET)
        }
        if (request.nowEpochMillis < 0 || request.executionsInLastHour < 0 ||
            request.lastExecutionEpochMillis?.let {
                it < 0 || it > request.nowEpochMillis
            } == true) {
            return rejected(InfinityAutomationDecision.Reason.INVALID_TIMESTAMP)
        }
        if (request.executionsInLastHour >= MAX_EXECUTIONS_PER_HOUR) {
            return rejected(InfinityAutomationDecision.Reason.RATE_LIMIT_EXCEEDED)
        }
        if (request.lastExecutionEpochMillis?.let {
                request.nowEpochMillis - it < MIN_INTERVAL_MILLIS
            } == true) {
            return rejected(InfinityAutomationDecision.Reason.COOLDOWN_ACTIVE)
        }
        return InfinityAutomationDecision.Allowed
    }

    private fun rejected(reason: InfinityAutomationDecision.Reason) =
        InfinityAutomationDecision.Rejected(reason)
}
