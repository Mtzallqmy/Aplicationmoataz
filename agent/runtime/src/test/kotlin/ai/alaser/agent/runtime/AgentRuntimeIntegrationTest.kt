package ai.alaser.agent.runtime

import ai.alaser.ai.providers.AiProvider
import ai.alaser.ai.providers.ModelRequest
import ai.alaser.ai.providers.ModelStreamEvent
import ai.alaser.ai.providers.ProviderConnectionResult
import ai.alaser.ai.providers.ProviderException
import ai.alaser.core.filesystem.WorkspaceFileSystem
import ai.alaser.core.model.AgentMode
import ai.alaser.core.model.ApprovalDecision
import ai.alaser.core.model.ChatMessage
import ai.alaser.core.model.MessagePart
import ai.alaser.core.model.MessageRole
import ai.alaser.core.model.ModelDescriptor
import ai.alaser.core.terminal.ProcessTerminal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgentRuntimeIntegrationTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun completesWriteExecuteObserveVerticalSlice() = runBlocking {
        val root = temporary.newFolder("workspace")
        val filesystem = WorkspaceFileSystem(root)
        val terminal = ProcessTerminal()
        val runtime = AgentRuntime(
            provider = ScriptedProvider(),
            registry = ToolRegistry(filesystem, terminal),
            approvals = ApprovalEngine { ApprovalDecision.ALLOW_ONCE },
        )
        val prompt = ChatMessage(
            id = "message",
            sessionId = "session",
            role = MessageRole.USER,
            parts = listOf(MessagePart.Text("Create hello.txt and print it.")),
            createdAt = 0,
        )

        val conversation = runtime.run("session", "fake-model", AgentMode.BUILD, listOf(prompt))

        assertEquals("hello world", filesystem.readText("hello.txt"))
        assertTrue(
            conversation.flatMap { it.parts }
                .filterIsInstance<MessagePart.ToolResult>()
                .any { it.name == "shell_exec" && it.output.contains("hello world") },
        )
        assertTrue(conversation.last().textContent().contains("verified"))
        terminal.close()
    }

    @Test
    fun retriesRateLimitedModelWithoutReplayingTools() = runBlocking {
        val root = temporary.newFolder("retry-workspace")
        val filesystem = WorkspaceFileSystem(root)
        val terminal = ProcessTerminal()
        val runtime = AgentRuntime(
            provider = ScriptedProvider(initialRateLimits = 1),
            registry = ToolRegistry(filesystem, terminal),
            approvals = ApprovalEngine { ApprovalDecision.ALLOW_ONCE },
        )
        val prompt = ChatMessage(
            id = "retry-message",
            sessionId = "retry-session",
            role = MessageRole.USER,
            parts = listOf(MessagePart.Text("Create hello.txt and print it.")),
            createdAt = 0,
        )

        val conversation = runtime.run("retry-session", "fake-model", AgentMode.BUILD, listOf(prompt))

        assertEquals("hello world", filesystem.readText("hello.txt"))
        assertEquals(
            1,
            conversation.flatMap { it.parts }
                .filterIsInstance<MessagePart.ToolResult>()
                .count { it.name == "write_file" },
        )
        terminal.close()
    }

    private class ScriptedProvider(private var initialRateLimits: Int = 0) : AiProvider {
        override val id = "fake"
        override val displayName = "Test-only scripted provider"
        private var requestNumber = 0

        override suspend fun listModels(): List<ModelDescriptor> =
            listOf(ModelDescriptor("fake-model", providerId = id))

        override suspend fun testConnection(): ProviderConnectionResult =
            ProviderConnectionResult(true, 0, 200)

        override fun streamChat(request: ModelRequest): Flow<ModelStreamEvent> = flow {
            if (initialRateLimits > 0) {
                initialRateLimits -= 1
                throw ProviderException("Rate limited", statusCode = 429, retryable = true)
            }
            when (requestNumber++) {
                0 -> emit(
                    ModelStreamEvent.ToolCallDelta(
                        0,
                        "write",
                        "write_file",
                        """{"path":"hello.txt","content":"hello world"}""",
                    ),
                )
                1 -> emit(
                    ModelStreamEvent.ToolCallDelta(
                        0,
                        "run",
                        "shell_exec",
                        """{"command":"cat hello.txt"}""",
                    ),
                )
                else -> emit(ModelStreamEvent.TextDelta("File created and command output verified."))
            }
            emit(ModelStreamEvent.Completed)
        }
    }
}
