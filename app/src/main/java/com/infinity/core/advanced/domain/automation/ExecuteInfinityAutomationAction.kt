package com.infinity.core.advanced.domain.automation

import com.infinity.core.advanced.domain.capabilities.InfinityApplicationLauncherCapability
import com.infinity.core.advanced.domain.capabilities.InfinityCapabilityId
import com.infinity.core.advanced.domain.capabilities.InfinityCapabilityRegistry
import com.infinity.core.advanced.domain.capabilities.LaunchCapabilityResult

/**
 * Executes only an explicitly submitted, consented automation request.
 * It does not schedule work, persist rules, or run in the background.
 */
class ExecuteInfinityAutomationAction(
    registry: InfinityCapabilityRegistry
) {
    private val launcher = registry.find(
        InfinityCapabilityId.APPLICATION_LAUNCHER,
        InfinityApplicationLauncherCapability::class.java
    )

    suspend operator fun invoke(
        request: InfinityAutomationRequest
    ): InfinityAutomationExecutionResult {
        val decision = InfinityAutomationPolicy.evaluate(request)
        if (decision !is InfinityAutomationDecision.Allowed) {
            return InfinityAutomationExecutionResult.Rejected(decision)
        }
        val capability = launcher ?: return InfinityAutomationExecutionResult.CapabilityUnavailable
        return when (capability.launch(request.targetPackageName)) {
            LaunchCapabilityResult.Launched -> InfinityAutomationExecutionResult.Executed
            LaunchCapabilityResult.AppNotFound -> InfinityAutomationExecutionResult.TargetNotFound
            is LaunchCapabilityResult.Failed -> InfinityAutomationExecutionResult.ExecutionFailed
        }
    }
}

sealed interface InfinityAutomationExecutionResult {
    data object Executed : InfinityAutomationExecutionResult
    data object CapabilityUnavailable : InfinityAutomationExecutionResult
    data object TargetNotFound : InfinityAutomationExecutionResult
    data object ExecutionFailed : InfinityAutomationExecutionResult
    data class Rejected(
        val decision: InfinityAutomationDecision
    ) : InfinityAutomationExecutionResult
}
