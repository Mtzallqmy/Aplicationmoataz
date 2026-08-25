package ai.alaser.ai.providers

import ai.alaser.core.model.ProviderConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleProviderTest {
    private val provider = OpenAiCompatibleProvider(
        ProviderConfiguration("provider", "Test", "https://example.com/v1", "test-model", "secret"),
        apiKey = { "test-only-key" },
    )

    @Test
    fun parsesTextChunks() {
        val events = provider.parseServerSentEvent(
            """{"choices":[{"delta":{"content":"hello"}}]}""",
        )
        assertEquals(ModelStreamEvent.TextDelta("hello"), events.single())
    }

    @Test
    fun parsesIncrementalToolArguments() {
        val events = provider.parseServerSentEvent(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"write_file","arguments":"{\"path\":"}}]}}]}""",
        )
        val call = events.single() as ModelStreamEvent.ToolCallDelta
        assertEquals("call_1", call.id)
        assertEquals("write_file", call.name)
        assertTrue(call.arguments.startsWith("{"))
    }
}
