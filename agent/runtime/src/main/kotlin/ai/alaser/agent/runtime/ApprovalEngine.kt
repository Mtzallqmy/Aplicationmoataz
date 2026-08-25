package ai.alaser.agent.runtime

import ai.alaser.core.model.AgentMode
import ai.alaser.core.model.ApprovalDecision
import ai.alaser.core.model.RiskLevel
import ai.alaser.core.model.ToolInvocation
import java.util.concurrent.ConcurrentHashMap

data class ApprovalRequest(
    val invocation: ToolInvocation,
    val risk: RiskLevel,
    val detail: String,
)

fun interface ApprovalHandler {
    suspend fun requestApproval(request: ApprovalRequest): ApprovalDecision
}

class ApprovalEngine(private val handler: ApprovalHandler) {
    private val sessionApprovals = ConcurrentHashMap.newKeySet<String>()

    suspend fun authorize(
        invocation: ToolInvocation,
        risk: RiskLevel,
        mode: AgentMode,
        detail: String,
    ): Boolean {
        if (mode == AgentMode.ASK) return false
        if (mode == AgentMode.PLAN && risk != RiskLevel.SAFE) return false
        if (risk == RiskLevel.SAFE) return true
        if (risk != RiskLevel.CRITICAL && invocation.name in sessionApprovals) return true
        return when (handler.requestApproval(ApprovalRequest(invocation, risk, detail))) {
            ApprovalDecision.ALLOW_ONCE -> true
            ApprovalDecision.ALLOW_FOR_SESSION -> {
                if (risk == RiskLevel.CRITICAL) {
                    false
                } else {
                    sessionApprovals += invocation.name
                    true
                }
            }
            ApprovalDecision.DENY -> false
        }
    }

    fun clearSessionApprovals() {
        sessionApprovals.clear()
    }
}
