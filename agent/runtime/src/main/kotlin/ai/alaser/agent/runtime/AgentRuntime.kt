package ai.alaser.agent.runtime

import ai.alaser.ai.providers.AiProvider
import ai.alaser.ai.providers.ModelRequest
import ai.alaser.ai.providers.ModelStreamEvent
import ai.alaser.ai.providers.ProviderException
import ai.alaser.core.model.AgentMode
import ai.alaser.core.model.AgentState
import ai.alaser.core.model.ChatMessage
import ai.alaser.core.model.MessagePart
import ai.alaser.core.model.MessageRole
import ai.alaser.core.model.ToolInvocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.UUID

sealed interface AgentEvent {
    data class StateChanged(val state: AgentState, val summary: String) : AgentEvent
    data class TextDelta(val value: String) : AgentEvent
    data class MessageRecorded(val message: ChatMessage) : AgentEvent
    data class ApprovalRequired(val request: ApprovalRequest) : AgentEvent
    data class Failure(val message: String) : AgentEvent
}

class AgentRuntime(
    private val provider: AiProvider,
    private val registry: ToolRegistry,
    private val approvals: ApprovalEngine,
    private val maxIterations: Int = 20,
) {
    private val stateValue = MutableStateFlow(AgentState.IDLE)
    private val eventsValue = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 256)
    private val json = Json { ignoreUnknownKeys = true }

    val state: StateFlow<AgentState> = stateValue
    val events: SharedFlow<AgentEvent> = eventsValue

    suspend fun run(
        sessionId: String,
        modelId: String,
        mode: AgentMode,
        history: List<ChatMessage>,
    ): List<ChatMessage> {
        val conversation = history.toMutableList()
        try {
            repeat(maxIterations) {
                currentCoroutineContext().ensureActive()
                transition(AgentState.WAITING_MODEL, "Waiting for the selected model")
                val text = StringBuilder()
                val pendingCalls = linkedMapOf<Int, PendingToolCall>()

                var retryCount = 0
                while (true) {
                    try {
                        provider.streamChat(
                            ModelRequest(
                                modelId = modelId,
                                messages = conversation,
                                tools = if (mode == AgentMode.ASK) emptyList() else registry.tools,
                            ),
                        ).collect { event ->
                            when (event) {
                                is ModelStreamEvent.TextDelta -> {
                                    text.append(event.text)
                                    eventsValue.emit(AgentEvent.TextDelta(event.text))
                                }
                                is ModelStreamEvent.ToolCallDelta -> {
                                    val pending = pendingCalls.getOrPut(event.index) { PendingToolCall() }
                                    if (event.id != null) pending.id = event.id
                                    if (event.name != null) pending.name = event.name
                                    pending.arguments.append(event.arguments)
                                }
                                is ModelStreamEvent.Usage -> Unit
                                ModelStreamEvent.Completed -> Unit
                            }
                        }
                        break
                    } catch (failure: ProviderException) {
                        // Never replay a partially observed response or an executed tool.
                        if (!failure.retryable || retryCount >= 2 || text.isNotEmpty() || pendingCalls.isNotEmpty()) {
                            throw failure
                        }
                        retryCount += 1
                        transition(AgentState.RETRYING, "Retrying the model after HTTP " + failure.statusCode)
                        delay(250L * (1 shl (retryCount - 1)))
                        transition(AgentState.WAITING_MODEL, "Waiting for the selected model")
                    }
                }

                val toolParts = pendingCalls.values.map { pending ->
                    MessagePart.ToolCall(
                        id = requireNotNull(pending.id) { "The model returned a tool call without an id." },
                        name = requireNotNull(pending.name) { "The model returned a tool call without a name." },
                        arguments = pending.arguments.toString(),
                    )
                }

                val assistantParts = buildList<MessagePart> {
                    if (text.isNotBlank()) add(MessagePart.Text(text.toString()))
                    addAll(toolParts)
                }

                if (assistantParts.isNotEmpty()) {
                    record(conversation, message(sessionId, MessageRole.ASSISTANT, assistantParts))
                }

                if (toolParts.isEmpty()) {
                    transition(AgentState.COMPLETED, "Task completed")
                    return conversation
                }

                for (call in toolParts) {
                    currentCoroutineContext().ensureActive()
                    transition(AgentState.TOOL_REQUEST, "Preparing " + call.name)
                    val invocation = ToolInvocation(
                        id = call.id,
                        name = call.name,
                        arguments = json.parseToJsonElement(call.arguments).jsonObject,
                    )
                    val risk = registry.assess(invocation)
                    if (risk != ai.alaser.core.model.RiskLevel.SAFE) {
                        transition(AgentState.WAITING_APPROVAL, "Approval required for " + call.name)
                        eventsValue.emit(AgentEvent.ApprovalRequired(ApprovalRequest(invocation, risk, call.arguments)))
                    }
                    val approved = approvals.authorize(invocation, risk, mode, call.arguments)
                    val output = if (approved) {
                        transition(AgentState.EXECUTING_TOOL, "Executing " + call.name)
                        registry.execute(invocation)
                    } else {
                        ai.alaser.core.model.ToolExecutionResult(call.id, call.name, "User or policy denied this action.", isError = true)
                    }
                    transition(AgentState.OBSERVING, "Observed result from " + call.name)
                    record(
                        conversation,
                        message(
                            sessionId,
                            MessageRole.TOOL,
                            listOf(MessagePart.ToolResult(call.id, call.name, output.output, output.isError)),
                        ),
                    )
                }
            }
            transition(AgentState.FAILED, "The agent reached its iteration limit")
            error("The agent reached its maximum number of iterations.")
        } catch (cancelled: CancellationException) {
            transition(AgentState.CANCELLED, "Task cancelled")
            throw cancelled
        } catch (exception: Exception) {
            transition(AgentState.FAILED, exception.message ?: "Agent execution failed")
            eventsValue.emit(AgentEvent.Failure(exception.message ?: "Agent execution failed."))
            throw exception
        }
    }

    private suspend fun transition(state: AgentState, summary: String) {
        stateValue.value = state
        eventsValue.emit(AgentEvent.StateChanged(state, summary))
    }

    private suspend fun record(history: MutableList<ChatMessage>, message: ChatMessage) {
        history += message
        eventsValue.emit(AgentEvent.MessageRecorded(message))
    }

    private fun message(sessionId: String, role: MessageRole, parts: List<MessagePart>): ChatMessage =
        ChatMessage(UUID.randomUUID().toString(), sessionId, role, parts, System.currentTimeMillis())

    private class PendingToolCall {
        var id: String? = null
        var name: String? = null
        val arguments = ToolArgumentAccumulator()
    }
}
