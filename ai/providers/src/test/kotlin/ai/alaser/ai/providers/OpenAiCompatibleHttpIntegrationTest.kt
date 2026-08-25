package ai.alaser.ai.providers

import ai.alaser.core.model.ChatMessage
import ai.alaser.core.model.MessagePart
import ai.alaser.core.model.MessageRole
import ai.alaser.core.model.ProviderConfiguration
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleHttpIntegrationTest {
    private lateinit var server: HttpServer

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun fetchesModelsWithBearerAuthenticationAndCustomHeaders() = runBlocking {
        server.createContext("/v1/models") { exchange ->
            val authenticated = exchange.requestHeaders.getFirst("Authorization") == "Bearer real-test-key" &&
                exchange.requestHeaders.getFirst("X-Alaser-Test") == "enabled"
            val response = if (authenticated) {
                """{"data":[{"id":"z-model"},{"id":"a-model"}]}"""
            } else {
                """{"error":{"message":"Invalid API credential"}}"""
            }
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(if (authenticated) 200 else 401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        registerSuccessfulGeneration()

        val connection = provider().testConnection()

        assertTrue(connection.success)
        assertEquals(200, connection.statusCode)
        assertEquals(listOf("a-model", "z-model"), connection.models.map { it.id })
        assertTrue(connection.detail.orEmpty().contains("real streaming generation"))
    }

    @Test
    fun streamsTextToolCallsAndUsageFromRealHttpServer() = runBlocking {
        var body = ""
        server.createContext("/v1/chat/completions") { exchange ->
            body = exchange.requestBody.bufferedReader().use { it.readText() }
            val chunks = listOf(
                "data: {\"choices\":[{\"delta\":{\"content\":\"مرحبا\"}}]}\n\n",
                "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"tool-1\",\"function\":{\"name\":\"write_file\",\"arguments\":\"{}\"}}]}}]}\n\n",
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":3}}\n\n",
                "data: [DONE]\n\n",
            )
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { output ->
                chunks.forEach {
                    output.write(it.toByteArray())
                    output.flush()
                }
            }
        }

        val events = provider().streamChat(
            ModelRequest(
                modelId = "custom-company-model-v7",
                messages = listOf(
                    ChatMessage(
                        id = "message",
                        sessionId = "session",
                        role = MessageRole.USER,
                        parts = listOf(MessagePart.Text("اكتب ملفًا")),
                        createdAt = 0,
                    ),
                ),
            ),
        ).toList()

        assertTrue(body.contains("custom-company-model-v7"))
        assertTrue(body.contains("اكتب ملفًا"))
        assertTrue(events.contains(ModelStreamEvent.TextDelta("مرحبا")))
        assertTrue(events.contains(ModelStreamEvent.ToolCallDelta(0, "tool-1", "write_file", "{}")))
        assertTrue(events.contains(ModelStreamEvent.Usage(7, 3)))
        assertEquals(ModelStreamEvent.Completed, events.last())
    }

    @Test
    fun returnsClearAuthenticationFailureForInvalidCredential() = runBlocking {
        server.createContext("/v1/models") { exchange ->
            val bytes = """{"error":{"message":"Incorrect API key provided"}}""".toByteArray()
            exchange.sendResponseHeaders(401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val result = provider().testConnection()

        assertEquals(false, result.success)
        assertEquals(401, result.statusCode)
        assertTrue(result.detail.orEmpty().contains("Incorrect API key"))
    }

    private fun provider(): OpenAiCompatibleProvider = OpenAiCompatibleProvider(
        ProviderConfiguration(
            id = "integration-provider",
            name = "HTTP integration test",
            baseUrl = "http://127.0.0.1:${server.address.port}/v1",
            defaultModel = "custom-company-model-v7",
            secretId = "encrypted-reference",
            headers = mapOf("X-Alaser-Test" to "enabled"),
        ),
        apiKey = { "real-test-key" },
    )

    private fun registerSuccessfulGeneration() {
        server.createContext("/v1/chat/completions") { exchange ->
            val authenticated = exchange.requestHeaders.getFirst("Authorization") == "Bearer real-test-key"
            val bytes = if (authenticated) {
                "data: {\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}\n\ndata: [DONE]\n\n".toByteArray()
            } else {
                "{\"error\":{\"message\":\"Invalid API credential\"}}".toByteArray()
            }
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(if (authenticated) 200 else 401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
