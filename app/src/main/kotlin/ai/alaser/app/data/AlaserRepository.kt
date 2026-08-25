package ai.alaser.app.data

import android.content.Context
import ai.alaser.ai.providers.OpenAiCompatibleProvider
import ai.alaser.ai.providers.ProviderConnectionResult
import ai.alaser.core.database.AlaserDatabase
import ai.alaser.core.filesystem.WorkspaceFileEntry
import ai.alaser.core.filesystem.WorkspaceFileSystem
import ai.alaser.core.model.AgentMode
import ai.alaser.core.model.AgentSession
import ai.alaser.core.model.AgentState
import ai.alaser.core.model.ChatMessage
import ai.alaser.core.model.ProviderConfiguration
import ai.alaser.core.model.Workspace
import ai.alaser.core.security.AndroidSecretStore
import ai.alaser.core.terminal.ProcessTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.UUID

class AlaserRepository(context: Context) {
    private val database = AlaserDatabase(context)
    private val secrets = AndroidSecretStore(context)
    private val workspacesDirectory = File(context.filesDir, "workspaces").apply { mkdirs() }

    val terminal = ProcessTerminal()

    private val workspacesValue = MutableStateFlow(database.listWorkspaces())
    private val providersValue = MutableStateFlow(database.listProviders())

    val workspaces: StateFlow<List<Workspace>> = workspacesValue
    val providers: StateFlow<List<ProviderConfiguration>> = providersValue

    suspend fun createWorkspace(name: String): Workspace = withContext(Dispatchers.IO) {
        val safeName = name.trim()
        require(safeName.isNotBlank()) { "A project name is required." }
        require(safeName.length <= 120) { "Project names cannot exceed 120 characters." }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val directory = File(workspacesDirectory, id)
        check(directory.mkdirs()) { "The workspace directory could not be created." }
        Workspace(id, safeName, directory.absolutePath, now, now).also {
            database.saveWorkspace(it)
            workspacesValue.value = database.listWorkspaces()
        }
    }

    suspend fun saveProvider(
        name: String,
        baseUrl: String,
        model: String,
        apiKey: String,
    ): ProviderConfiguration = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "A provider name is required." }
        require(model.isNotBlank()) { "A model identifier is required." }
        require(apiKey.isNotBlank()) { "An API key is required." }
        validateProviderUrl(baseUrl)
        val id = UUID.randomUUID().toString()
        val secretId = "provider." + id
        secrets.put(secretId, apiKey.trim())
        ProviderConfiguration(id, name.trim(), baseUrl.trim().trimEnd('/'), model.trim(), secretId).also {
            database.saveProvider(it)
            providersValue.value = database.listProviders()
        }
    }

    fun provider(configuration: ProviderConfiguration): OpenAiCompatibleProvider =
        OpenAiCompatibleProvider(configuration) {
            secrets.get(configuration.secretId) ?: error("The encrypted provider credential is unavailable.")
        }

    suspend fun testProvider(configuration: ProviderConfiguration): ProviderConnectionResult =
        provider(configuration).testConnection()

    suspend fun createSession(
        workspace: Workspace,
        provider: ProviderConfiguration,
        title: String,
        mode: AgentMode,
    ): AgentSession = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        AgentSession(
            id = UUID.randomUUID().toString(),
            workspaceId = workspace.id,
            title = title.take(100),
            modelId = provider.defaultModel,
            providerId = provider.id,
            mode = mode,
            state = AgentState.IDLE,
            createdAt = now,
            updatedAt = now,
        ).also(database::saveSession)
    }

    suspend fun saveSession(session: AgentSession) = withContext(Dispatchers.IO) {
        database.saveSession(session)
    }

    suspend fun sessions(workspaceId: String): List<AgentSession> = withContext(Dispatchers.IO) {
        database.listSessions(workspaceId)
    }

    suspend fun saveMessage(message: ChatMessage) = withContext(Dispatchers.IO) {
        database.saveMessage(message)
    }

    suspend fun messages(sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        database.listMessages(sessionId)
    }

    fun filesystem(workspace: Workspace): WorkspaceFileSystem =
        WorkspaceFileSystem(File(workspace.rootPath))

    suspend fun files(workspace: Workspace, path: String = "."): List<WorkspaceFileEntry> =
        filesystem(workspace).list(path)

    companion object {
        fun validateProviderUrl(value: String) {
            val uri = runCatching { URI(value.trim()) }.getOrElse {
                throw IllegalArgumentException("The provider URL is invalid.")
            }
            require(uri.host != null) { "The provider URL must include a host." }
            val secure = uri.scheme == "https"
            val local = uri.scheme == "http" && uri.host in setOf("127.0.0.1", "localhost", "::1")
            require(secure || local) { "Providers must use HTTPS, except explicit local endpoints." }
            require(uri.userInfo == null) { "Credentials are not allowed inside provider URLs." }
        }
    }
}
