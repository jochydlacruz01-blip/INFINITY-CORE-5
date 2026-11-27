package com.infinity.core

import com.infinity.core.advanced.domain.automation.InfinityAutomationAction
import com.infinity.core.advanced.domain.automation.InfinityAutomationDecision
import com.infinity.core.advanced.domain.automation.InfinityAutomationPolicy
import com.infinity.core.advanced.domain.automation.InfinityAutomationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfinityAutomationPolicyTest {
    private fun request(
        enabled: Boolean = true,
        consent: Boolean = true,
        packageName: String = "com.example.app",
        now: Long = 120_000L,
        last: Long? = null,
        count: Int = 0
    ) = InfinityAutomationRequest(
        ruleId = "rule-1",
        action = InfinityAutomationAction.LAUNCH_APPLICATION,
        enabled = enabled,
        userConsentGranted = consent,
        targetPackageName = packageName,
        nowEpochMillis = now,
        lastExecutionEpochMillis = last,
        executionsInLastHour = count
    )

    @Test fun rejectsDisabledRules() {
        val result = InfinityAutomationPolicy.evaluate(request(enabled = false))
        assertEquals(
            InfinityAutomationDecision.Rejected(InfinityAutomationDecision.Reason.RULE_DISABLED),
            result
        )
    }

    @Test fun requiresExplicitConsent() {
        val result = InfinityAutomationPolicy.evaluate(request(consent = false))
        assertEquals(
            InfinityAutomationDecision.Rejected(InfinityAutomationDecision.Reason.CONSENT_REQUIRED),
            result
        )
    }

    @Test fun allowsValidExplicitLaunch() {
        assertTrue(InfinityAutomationPolicy.evaluate(request()) is InfinityAutomationDecision.Allowed)
    }

    @Test fun rejectsMalformedPackageNames() {
        val result = InfinityAutomationPolicy.evaluate(request(packageName = "com..app"))
        assertEquals(
            InfinityAutomationDecision.Rejected(InfinityAutomationDecision.Reason.UNSUPPORTED_TARGET),
            result
        )
    }

    @Test fun enforcesCooldownAndHourlyLimit() {
        assertEquals(
            InfinityAutomationDecision.Rejected(InfinityAutomationDecision.Reason.COOLDOWN_ACTIVE),
            InfinityAutomationPolicy.evaluate(request(last = 90_000L))
        )
        assertEquals(
            InfinityAutomationDecision.Rejected(InfinityAutomationDecision.Reason.RATE_LIMIT_EXCEEDED),
            InfinityAutomationPolicy.evaluate(request(count = InfinityAutomationPolicy.MAX_EXECUTIONS_PER_HOUR))
        )
    }
}
