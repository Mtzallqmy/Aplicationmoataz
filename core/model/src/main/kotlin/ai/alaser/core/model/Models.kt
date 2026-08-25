package ai.alaser.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class AgentMode { ASK, PLAN, BUILD, FULL_AGENT, DEBUG, RESEARCH }

@Serializable
enum class AgentState {
    IDLE,
    THINKING,
    PLANNING,
    WAITING_MODEL,
    TOOL_REQUEST,
    WAITING_APPROVAL,
    EXECUTING_TOOL,
    OBSERVING,
    RETRYING,
    COMPACTING_CONTEXT,
    COMPLETED,
    CANCELLED,
    FAILED,
}

@Serializable
enum class RiskLevel { SAFE, WRITE, DANGEROUS, CRITICAL }

@Serializable
enum class ApprovalDecision { ALLOW_ONCE, ALLOW_FOR_SESSION, DENY }

@Serializable
enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val rootPath: String,
    val createdAt: Long,
    val updatedAt: Long,
    val environmentId: String? = null,
    val customInstructions: String = "",
)

@Serializable
data class AgentSession(
    val id: String,
    val workspaceId: String,
    val title: String,
    val modelId: String,
    val providerId: String,
    val mode: AgentMode = AgentMode.BUILD,
    val state: AgentState = AgentState.IDLE,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ProviderConfiguration(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val secretId: String,
    val headers: Map<String, String> = emptyMap(),
    val timeoutSeconds: Long = 90,
    val contextWindow: Int = 128_000,
)

@Serializable
data class ModelDescriptor(
    val id: String,
    val displayName: String = id,
    val providerId: String,
    val contextWindow: Int? = null,
    val supportsTools: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = false,
)

@Serializable
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val parts: List<MessagePart>,
    val createdAt: Long,
) {
    fun textContent(): String = parts.filterIsInstance<MessagePart.Text>()
        .joinToString(separator = "") { it.value }
}

@Serializable
sealed interface MessagePart {
    @Serializable
    @SerialName("text")
    data class Text(val value: String) : MessagePart

    @Serializable
    @SerialName("tool_call")
    data class ToolCall(val id: String, val name: String, val arguments: String) : MessagePart

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(val callId: String, val name: String, val output: String, val error: Boolean = false) : MessagePart

    @Serializable
    @SerialName("command")
    data class Command(val command: String, val output: String, val exitCode: Int? = null) : MessagePart

    @Serializable
    @SerialName("status")
    data class Status(val state: AgentState, val summary: String) : MessagePart

    @Serializable
    @SerialName("approval")
    data class Approval(val tool: String, val detail: String, val risk: RiskLevel) : MessagePart

    @Serializable
    @SerialName("error")
    data class Error(val message: String, val recoverable: Boolean = false) : MessagePart
}

@Serializable
data class ToolDescriptor(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val riskLevel: RiskLevel,
    val timeoutSeconds: Long = 60,
    val supportsStreaming: Boolean = false,
)

@Serializable
data class ToolInvocation(
    val id: String,
    val name: String,
    val arguments: JsonObject,
)

@Serializable
data class ToolExecutionResult(
    val invocationId: String,
    val toolName: String,
    val output: String,
    val isError: Boolean = false,
    val exitCode: Int? = null,
)

@Serializable
data class TelegramBotConfiguration(
    val id: String,
    val name: String,
    val tokenSecretId: String,
    val workspaceId: String,
    val allowedUserIds: Set<Long>,
    val allowedChatIds: Set<Long>,
    val enabled: Boolean = false,
) {
    fun accepts(userId: Long?, chatId: Long): Boolean =
        allowedUserIds.isNotEmpty() &&
            userId != null &&
            userId in allowedUserIds &&
            (allowedChatIds.isEmpty() || chatId in allowedChatIds)
}

@Serializable
data class McpServerConfiguration(
    val id: String,
    val name: String,
    val endpoint: String,
    val enabled: Boolean = false,
    val trusted: Boolean = false,
)
