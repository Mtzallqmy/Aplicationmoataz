package ai.alaser.agent.runtime

import ai.alaser.core.model.AgentMode
import ai.alaser.core.model.ApprovalDecision
import ai.alaser.core.model.RiskLevel
import ai.alaser.core.model.ToolInvocation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalEngineTest {
    private val invocation = ToolInvocation("id", "write_file", buildJsonObject {})

    @Test
    fun planModeBlocksWrites() = runBlocking {
        val engine = ApprovalEngine { ApprovalDecision.ALLOW_ONCE }
        assertFalse(engine.authorize(invocation, RiskLevel.WRITE, AgentMode.PLAN, "write"))
    }

    @Test
    fun buildModeCanApproveWrites() = runBlocking {
        val engine = ApprovalEngine { ApprovalDecision.ALLOW_ONCE }
        assertTrue(engine.authorize(invocation, RiskLevel.WRITE, AgentMode.BUILD, "write"))
    }

    @Test
    fun criticalActionsCannotReceiveBlanketApproval() = runBlocking {
        val engine = ApprovalEngine { ApprovalDecision.ALLOW_FOR_SESSION }
        assertFalse(engine.authorize(invocation, RiskLevel.CRITICAL, AgentMode.BUILD, "secret"))
    }
}
