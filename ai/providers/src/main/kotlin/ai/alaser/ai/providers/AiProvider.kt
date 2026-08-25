package ai.alaser.ai.providers

import ai.alaser.core.model.ChatMessage
import ai.alaser.core.model.ModelDescriptor
import ai.alaser.core.model.ToolDescriptor
import kotlinx.coroutines.flow.Flow

data class ModelRequest(
    val modelId: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolDescriptor> = emptyList(),
    val maxOutputTokens: Int = 4096,
)

data class ProviderConnectionResult(
    val success: Boolean,
    val latencyMilliseconds: Long,
    val statusCode: Int,
    val models: List<ModelDescriptor> = emptyList(),
    val detail: String? = null,
)

sealed interface ModelStreamEvent {
    data class TextDelta(val text: String) : ModelStreamEvent
    data class ToolCallDelta(val index: Int, val id: String?, val name: String?, val arguments: String) : ModelStreamEvent
    data class Usage(val inputTokens: Int, val outputTokens: Int) : ModelStreamEvent
    data object Completed : ModelStreamEvent
}

interface AiProvider {
    val id: String
    val displayName: String

    suspend fun listModels(): List<ModelDescriptor>
    suspend fun testConnection(): ProviderConnectionResult
    fun streamChat(request: ModelRequest): Flow<ModelStreamEvent>
}

class ProviderException(
    message: String,
    val statusCode: Int? = null,
    val retryable: Boolean = false,
) : Exception(message)
