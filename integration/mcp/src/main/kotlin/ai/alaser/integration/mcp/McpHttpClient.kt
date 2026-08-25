package ai.alaser.integration.mcp

import ai.alaser.core.model.McpServerConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicLong

data class McpToolDescription(val name: String, val description: String, val inputSchema: JsonObject)

class McpHttpClient(
    private val server: McpServerConfiguration,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val nextRequestId = AtomicLong(1)
    private var sessionId: String? = null

    suspend fun initialize(): JsonObject = call(
        "initialize",
        buildJsonObject {
            put("protocolVersion", "2025-03-26")
            put("capabilities", buildJsonObject {})
            put("clientInfo", buildJsonObject {
                put("name", "Alaser AI")
                put("version", "0.1.0")
            })
        },
    )

    suspend fun listTools(): List<McpToolDescription> {
        check(server.enabled) { "The MCP server is disabled." }
        val result = call("tools/list", buildJsonObject {})
        return result["tools"]?.jsonArray.orEmpty().map { item ->
            val tool = item.jsonObject
            McpToolDescription(
                name = tool["name"]?.jsonPrimitive?.contentOrNull ?: error("An MCP tool has no name."),
                description = tool["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                inputSchema = tool["inputSchema"]?.jsonObject ?: buildJsonObject { put("type", "object") },
            )
        }
    }

    suspend fun callTool(name: String, arguments: JsonObject): JsonObject {
        check(server.enabled && server.trusted) {
            "MCP tool execution requires an enabled server that the user explicitly trusts."
        }
        return call(
            "tools/call",
            buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            },
        )
    }

    private suspend fun call(method: String, parameters: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        require(server.endpoint.startsWith("https://") || server.endpoint.startsWith("http://127.0.0.1:")) {
            "MCP endpoints must use HTTPS or an explicit loopback address."
        }
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", nextRequestId.getAndIncrement())
            put("method", method)
            put("params", parameters)
        }
        val request = Request.Builder()
            .url(server.endpoint)
            .header("Accept", "application/json, text/event-stream")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .apply { sessionId?.let { header("Mcp-Session-Id", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "MCP request failed with HTTP " + response.code + "." }
            response.header("Mcp-Session-Id")?.let { sessionId = it }
            val body = response.body?.string() ?: error("The MCP server returned an empty response.")
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.let { problem -> error("MCP server error: " + problem) }
            root["result"]?.jsonObject ?: error("The MCP response did not include a result.")
        }
    }
}
