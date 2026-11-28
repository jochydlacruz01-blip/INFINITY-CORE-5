package com.infinity.core

import com.infinity.core.advanced.domain.automation.ExecuteInfinityAutomationAction
import com.infinity.core.advanced.domain.automation.InfinityAutomationAction
import com.infinity.core.advanced.domain.automation.InfinityAutomationDecision
import com.infinity.core.advanced.domain.automation.InfinityAutomationExecutionResult
import com.infinity.core.advanced.domain.automation.InfinityAutomationRequest
import com.infinity.core.advanced.domain.capabilities.DefaultInfinityCapabilityRegistry
import com.infinity.core.advanced.domain.capabilities.InfinityApplicationLauncherCapability
import com.infinity.core.advanced.domain.capabilities.InfinityCapabilityId
import com.infinity.core.advanced.domain.capabilities.LaunchCapabilityResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecuteInfinityAutomationActionTest {
    private class FakeLauncher(
        private val result: LaunchCapabilityResult
    ) : InfinityApplicationLauncherCapability {
        var calls = 0
        var lastPackage: String? = null

        override suspend fun launch(packageName: String): LaunchCapabilityResult {
            calls++
            lastPackage = packageName
            return result
        }
    }

    private fun request(consent: Boolean = true) = InfinityAutomationRequest(
        ruleId = "rule-01",
        action = InfinityAutomationAction.LAUNCH_APPLICATION,
        enabled = true,
        userConsentGranted = consent,
        targetPackageName = "com.example.notes",
        nowEpochMillis = 120_000L
    )

    @Test fun doesNotInvokeLauncherWithoutConsent() = runBlocking {
        val launcher = FakeLauncher(LaunchCapabilityResult.Launched)
        val useCase = ExecuteInfinityAutomationAction(
            DefaultInfinityCapabilityRegistry(mapOf(InfinityCapabilityId.APPLICATION_LAUNCHER to launcher))
        )

        val result = useCase(request(consent = false))

        assertTrue(result is InfinityAutomationExecutionResult.Rejected)
        assertEquals(0, launcher.calls)
    }

    @Test fun invokesOnlyRegisteredLauncherAfterPolicyAllows() = runBlocking {
        val launcher = FakeLauncher(LaunchCapabilityResult.Launched)
        val useCase = ExecuteInfinityAutomationAction(
            DefaultInfinityCapabilityRegistry(mapOf(InfinityCapabilityId.APPLICATION_LAUNCHER to launcher))
        )

        val result = useCase(request())

        assertEquals(InfinityAutomationExecutionResult.Executed, result)
        assertEquals(1, launcher.calls)
        assertEquals("com.example.notes", launcher.lastPackage)
    }

    @Test fun reportsMissingCapabilityWithoutLaunching() = runBlocking {
        val useCase = ExecuteInfinityAutomationAction(DefaultInfinityCapabilityRegistry(emptyMap()))

        assertEquals(InfinityAutomationExecutionResult.CapabilityUnavailable, useCase(request()))
    }

    @Test fun mapsMissingTargetAndPlatformFailureToSafeResults() = runBlocking {
        val missing = FakeLauncher(LaunchCapabilityResult.AppNotFound)
        val failed = FakeLauncher(LaunchCapabilityResult.Failed("private platform detail"))

        val missingResult = ExecuteInfinityAutomationAction(
            DefaultInfinityCapabilityRegistry(mapOf(InfinityCapabilityId.APPLICATION_LAUNCHER to missing))
        )(request())
        val failedResult = ExecuteInfinityAutomationAction(
            DefaultInfinityCapabilityRegistry(mapOf(InfinityCapabilityId.APPLICATION_LAUNCHER to failed))
        )(request())

        assertEquals(InfinityAutomationExecutionResult.TargetNotFound, missingResult)
        assertEquals(InfinityAutomationExecutionResult.ExecutionFailed, failedResult)
        assertFalse(failedResult.toString().contains("private platform detail"))
    }
}
