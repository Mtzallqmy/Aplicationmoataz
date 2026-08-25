package ai.alaser.ai.providers

import ai.alaser.core.model.ChatMessage
import ai.alaser.core.model.MessagePart
import ai.alaser.core.model.MessageRole
import ai.alaser.core.model.ModelDescriptor
import ai.alaser.core.model.ProviderConfiguration
import ai.alaser.core.model.ToolDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiCompatibleProvider(
    private val configuration: ProviderConfiguration,
    private val apiKey: () -> String,
    baseClient: OkHttpClient = OkHttpClient(),
) : AiProvider {
    override val id: String = configuration.id
    override val displayName: String = configuration.name

    private val json = Json { ignoreUnknownKeys = true }
    private val client = baseClient.newBuilder()
        .callTimeout(configuration.timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(configuration.timeoutSeconds, TimeUnit.SECONDS)
        .build()

    override suspend fun listModels(): List<ModelDescriptor> = withContext(Dispatchers.IO) {
        client.newCall(request("/models").build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw error(response.code, body)
            val root = json.parseToJsonElement(body).jsonObject
            root["data"]?.jsonArray.orEmpty().mapNotNull { item ->
                val identifier = item.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ModelDescriptor(id = identifier, providerId = id)
            }.sortedBy { it.displayName.lowercase() }
        }
    }

    override suspend fun testConnection(): ProviderConnectionResult {
        val started = System.nanoTime()
        return try {
            val models = listModels()
            ProviderConnectionResult(
                success = true,
                latencyMilliseconds = (System.nanoTime() - started) / 1_000_000,
                statusCode = 200,
                models = models,
            )
        } catch (exception: ProviderException) {
            ProviderConnectionResult(
                success = false,
                latencyMilliseconds = (System.nanoTime() - started) / 1_000_000,
                statusCode = exception.statusCode ?: 0,
                detail = exception.message,
            )
        } catch (exception: IOException) {
            ProviderConnectionResult(
                success = false,
                latencyMilliseconds = (System.nanoTime() - started) / 1_000_000,
                statusCode = 0,
                detail = exception.message ?: "The provider connection failed.",
            )
        }
    }

    override fun streamChat(request: ModelRequest): Flow<ModelStreamEvent> = flow {
        val payload = createRequestPayload(request).toString()
        val call = client.newCall(
            request("/chat/completions")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build(),
        )
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw error(response.code, response.body?.string().orEmpty())
                }
                val source = response.body?.source() ?: throw ProviderException("The provider returned an empty stream.")
                while (!source.exhausted()) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        emit(ModelStreamEvent.Completed)
                        break
                    }
                    if (data.isBlank()) continue
                    parseServerSentEvent(data).forEach { emit(it) }
                }
            }
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    internal fun createRequestPayload(request: ModelRequest): JsonObject = buildJsonObject {
        put("model", request.modelId)
        put("stream", true)
        put("max_tokens", request.maxOutputTokens)
        put("messages", buildJsonArray { request.messages.forEach { add(messageJson(it)) } })
        if (request.tools.isNotEmpty()) {
            put("tools", buildJsonArray { request.tools.forEach { add(toolJson(it)) } })
            put("tool_choice", "auto")
        }
    }

    internal fun parseServerSentEvent(raw: String): List<ModelStreamEvent> {
        val root = json.parseToJsonElement(raw).jsonObject
        val events = mutableListOf<ModelStreamEvent>()
        root["choices"]?.jsonArray.orEmpty().forEach { choice ->
            val delta = choice.jsonObject["delta"]?.jsonObject ?: return@forEach
            delta["content"]?.jsonPrimitive?.contentOrNull?.let { events += ModelStreamEvent.TextDelta(it) }
            delta["tool_calls"]?.jsonArray.orEmpty().forEach { element ->
                val tool = element.jsonObject
                val function = tool["function"]?.jsonObject
                events += ModelStreamEvent.ToolCallDelta(
                    index = tool["index"]?.jsonPrimitive?.intOrNull ?: 0,
                    id = tool["id"]?.jsonPrimitive?.contentOrNull,
                    name = function?.get("name")?.jsonPrimitive?.contentOrNull,
                    arguments = function?.get("arguments")?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }
        root["usage"]?.jsonObject?.let { usage ->
            events += ModelStreamEvent.Usage(
                inputTokens = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                outputTokens = usage["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
        return events
    }

    private fun messageJson(message: ChatMessage): JsonObject = buildJsonObject {
        put("role", message.role.name.lowercase())
        val calls = message.parts.filterIsInstance<MessagePart.ToolCall>()
        val result = message.parts.filterIsInstance<MessagePart.ToolResult>().firstOrNull()
        if (message.role == MessageRole.TOOL && result != null) {
            put("tool_call_id", result.callId)
            put("content", result.output)
        } else {
            put("content", message.textContent())
            if (calls.isNotEmpty()) {
                put("tool_calls", buildJsonArray {
                    calls.forEach { call ->
                        add(buildJsonObject {
                            put("id", call.id)
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", call.name)
                                put("arguments", call.arguments)
                            })
                        })
                    }
                })
            }
        }
    }

    private fun toolJson(tool: ToolDescriptor): JsonObject = buildJsonObject {
        put("type", "function")
        put("function", buildJsonObject {
            put("name", tool.name)
            put("description", tool.description)
            put("parameters", tool.inputSchema)
        })
    }

    private fun request(path: String): Request.Builder {
        val builder = Request.Builder()
            .url(configuration.baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer " + apiKey())
            .header("Accept", "application/json")
        configuration.headers.forEach { (name, value) -> builder.header(name, value) }
        return builder
    }

    private fun error(code: Int, body: String): ProviderException {
        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: "Provider request failed with HTTP " + code + "."
        return ProviderException(
            message = message,
            statusCode = code,
            retryable = code == 429 || code >= 500,
        )
    }
}
