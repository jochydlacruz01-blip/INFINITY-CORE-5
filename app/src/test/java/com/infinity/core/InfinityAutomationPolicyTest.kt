package com.infinity.core

import com.infinity.core.advanced.domain.automation.InfinityAutomationAction
import com.infinity.core.advanced.domain.automation.InfinityAutomationDecision
import com.infinity.core.advanced.domain.automation.InfinityAutomationPolicy
import com.infinity.core.advanced.domain.automation.InfinityAutomationRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityAutomationPolicyTest {
    private fun request(
        enabled: Boolean = true,
        consent: Boolean = true,
        packageName: String = "com.example.notes",
        now: Long = 120_000L,
        last: Long? = null,
        count: Int = 0,
        ruleId: String = "rule-01"
    ) = InfinityAutomationRequest(
        ruleId = ruleId,
        action = InfinityAutomationAction.LAUNCH_APPLICATION,
        enabled = enabled,
        userConsentGranted = consent,
        targetPackageName = packageName,
        nowEpochMillis = now,
        lastExecutionEpochMillis = last,
        executionsInLastHour = count
    )

    @Test fun allowsValidConsentedAppLaunch() {
        assertEquals(InfinityAutomationDecision.Allowed, InfinityAutomationPolicy.evaluate(request()))
    }

    @Test fun rejectsDisabledRuleBeforeAnyExecution() {
        assertRejected(InfinityAutomationDecision.Reason.RULE_DISABLED, request(enabled = false))
    }

    @Test fun requiresExplicitUserConsent() {
        assertRejected(InfinityAutomationDecision.Reason.CONSENT_REQUIRED, request(consent = false))
    }

    @Test fun rejectsMalformedRuleIdAndPackageName() {
        assertRejected(InfinityAutomationDecision.Reason.INVALID_RULE_ID, request(ruleId = "bad rule"))
        assertRejected(InfinityAutomationDecision.Reason.UNSUPPORTED_TARGET, request(packageName = "not-a-package"))
        assertRejected(InfinityAutomationDecision.Reason.UNSUPPORTED_TARGET, request(packageName = "com.example;rm"))
    }

    @Test fun rejectsInvalidOrFutureTimestampsAndNegativeCounters() {
        assertRejected(InfinityAutomationDecision.Reason.INVALID_TIMESTAMP, request(now = -1))
        assertRejected(InfinityAutomationDecision.Reason.INVALID_TIMESTAMP, request(now = 10, last = 11))
        assertRejected(InfinityAutomationDecision.Reason.INVALID_TIMESTAMP, request(count = -1))
    }

    @Test fun enforcesHourlyExecutionLimit() {
        assertRejected(
            InfinityAutomationDecision.Reason.RATE_LIMIT_EXCEEDED,
            request(count = InfinityAutomationPolicy.MAX_EXECUTIONS_PER_HOUR)
        )
    }

    @Test fun enforcesCooldownBetweenExecutions() {
        assertRejected(InfinityAutomationDecision.Reason.COOLDOWN_ACTIVE, request(now = 120_000, last = 60_001))
    }

    @Test fun allowsExecutionAtCooldownBoundary() {
        assertEquals(
            InfinityAutomationDecision.Allowed,
            InfinityAutomationPolicy.evaluate(request(now = 120_000, last = 60_000))
        )
    }

    private fun assertRejected(
        expected: InfinityAutomationDecision.Reason,
        request: InfinityAutomationRequest
    ) {
        assertEquals(InfinityAutomationDecision.Rejected(expected), InfinityAutomationPolicy.evaluate(request))
    }
}
