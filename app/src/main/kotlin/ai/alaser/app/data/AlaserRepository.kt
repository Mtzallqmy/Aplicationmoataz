package ai.alaser.app.data

import android.content.Context
import ai.alaser.ai.providers.OpenAiCompatibleProvider
import ai.alaser.ai.providers.ProviderConnectionResult
import ai.alaser.core.database.AlaserDatabase
import ai.alaser.core.filesystem.WorkspaceFileEntry
import ai.alaser.core.filesystem.WorkspaceFileSystem
import ai.alaser.core.filesystem.WorkspaceCheckpointStore
import ai.alaser.core.model.AgentMode
import ai.alaser.core.model.AgentSession
import ai.alaser.core.model.AgentState
import ai.alaser.core.model.ChatMessage
import ai.alaser.core.model.ProviderConfiguration
import ai.alaser.core.model.McpServerConfiguration
import ai.alaser.core.model.TelegramBotConfiguration
import ai.alaser.core.model.Workspace
import ai.alaser.core.sandbox.RootfsInstaller
import ai.alaser.core.sandbox.LinuxEnvironmentDescriptor
import ai.alaser.core.sandbox.ProotBackend
import ai.alaser.core.security.AndroidSecretStore
import ai.alaser.core.terminal.ProcessTerminal
import ai.alaser.integration.mcp.McpHttpClient
import ai.alaser.integration.telegram.TelegramClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.Properties

class AlaserRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val database = AlaserDatabase(context)
    private val secrets = AndroidSecretStore(context)
    private val preferences = context.getSharedPreferences("alaser_integrations", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val workspacesDirectory = File(context.filesDir, "workspaces").apply { mkdirs() }
    private val environmentsDirectory = File(context.filesDir, "linux-environments").apply { mkdirs() }
    private val checkpointsDirectory = File(context.filesDir, "workspace-checkpoints").apply { mkdirs() }

    val terminal = ProcessTerminal()
    val rootfsInstaller = RootfsInstaller(environmentsDirectory)

    private val workspacesValue = MutableStateFlow(database.listWorkspaces())
    private val providersValue = MutableStateFlow(database.listProviders())
    private val telegramBotsValue = MutableStateFlow(readTelegramBots())
    private val mcpServersValue = MutableStateFlow(readMcpServers())

    val workspaces: StateFlow<List<Workspace>> = workspacesValue
    val providers: StateFlow<List<ProviderConfiguration>> = providersValue
    val telegramBots: StateFlow<List<TelegramBotConfiguration>> = telegramBotsValue
    val mcpServers: StateFlow<List<McpServerConfiguration>> = mcpServersValue

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

    suspend fun importWorkspaceArchive(name: String, source: java.io.InputStream): Workspace =
        withContext(Dispatchers.IO) {
            val archive = File.createTempFile("alaser-project-", ".zip", applicationContext.cacheDir)
            try {
                archive.outputStream().buffered().use { output -> source.copyTo(output) }
                val workspace = createWorkspace(name)
                WorkspaceFileSystem(File(workspace.rootPath)).extractZip(archive, ".")
                workspace
            } finally {
                archive.delete()
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
        OpenAiCompatibleProvider(
            configuration = configuration,
            apiKey = {
                secrets.get(configuration.secretId)
                    ?: error("The encrypted provider credential is unavailable.")
            },
        )

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

    fun checkpoints(workspace: Workspace): WorkspaceCheckpointStore =
        WorkspaceCheckpointStore(
            File(workspace.rootPath),
            File(checkpointsDirectory, workspace.id).apply { mkdirs() },
        )

    suspend fun files(workspace: Workspace, path: String = "."): List<WorkspaceFileEntry> =
        filesystem(workspace).list(path)

    suspend fun saveTelegramBot(
        name: String,
        token: String,
        workspace: Workspace,
        allowedUserIds: String,
        allowedChatIds: String,
    ): TelegramBotConfiguration = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "A bot name is required." }
        require(token.matches(Regex("""[0-9]+:[A-Za-z0-9_-]{20,}"""))) {
            "The Telegram bot token has an invalid format."
        }
        val users = parseIdentifiers(allowedUserIds, required = true)
        val chats = parseIdentifiers(allowedChatIds, required = false)
        val id = UUID.randomUUID().toString()
        val secretId = "telegram." + id
        secrets.put(secretId, token.trim())
        val bot = TelegramBotConfiguration(
            id = id,
            name = name.trim(),
            tokenSecretId = secretId,
            workspaceId = workspace.id,
            allowedUserIds = users,
            allowedChatIds = chats,
        )
        persistTelegramBots(telegramBotsValue.value + bot)
        bot
    }

    suspend fun updateTelegramBot(configuration: TelegramBotConfiguration) = withContext(Dispatchers.IO) {
        persistTelegramBots(telegramBotsValue.value.map { if (it.id == configuration.id) configuration else it })
    }

    fun telegramClient(configuration: TelegramBotConfiguration): TelegramClient =
        TelegramClient(
            token = {
                secrets.get(configuration.tokenSecretId)
                    ?: error("The encrypted Telegram bot token is unavailable.")
            },
        )

    suspend fun saveMcpServer(name: String, endpoint: String): McpServerConfiguration =
        withContext(Dispatchers.IO) {
            require(name.isNotBlank()) { "An MCP server name is required." }
            validateProviderUrl(endpoint)
            val server = McpServerConfiguration(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                endpoint = endpoint.trim(),
                enabled = true,
                trusted = false,
            )
            persistMcpServers(mcpServersValue.value + server)
            server
        }

    suspend fun updateMcpServer(configuration: McpServerConfiguration) = withContext(Dispatchers.IO) {
        persistMcpServers(mcpServersValue.value.map { if (it.id == configuration.id) configuration else it })
    }

    fun mcpClient(configuration: McpServerConfiguration): McpHttpClient =
        McpHttpClient(configuration)

    fun linuxEnvironments(): List<File> =
        environmentsDirectory.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }.orEmpty()

    fun bundledLinuxDescriptor(distribution: String = "ubuntu"): LinuxEnvironmentDescriptor {
        require(distribution in setOf("ubuntu", "alpine")) { "Unsupported bundled Linux distribution." }
        val manifest = Properties().apply {
            applicationContext.assets.open("linux/manifest.properties").use { load(it) }
        }
        val suffix = if (distribution == "ubuntu") ".ubuntu" else ""
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull { manifest.containsKey(it + suffix + ".filename") }
            ?: error("This device architecture does not have a bundled Linux environment.")
        val filename = manifest.getProperty(abi + suffix + ".filename")
        val checksum = manifest.getProperty(abi + suffix + ".sha256")
        val version = manifest.getProperty(abi + suffix + ".version")
        return LinuxEnvironmentDescriptor(
            id = distribution,
            displayName = if (distribution == "ubuntu") "Ubuntu " + version + " Developer" else "Alpine Linux " + version,
            architecture = abi,
            archiveUrl = "asset://linux/" + filename,
            sha256 = checksum,
        )
    }

    fun bundledLinuxArchive(descriptor: LinuxEnvironmentDescriptor): java.io.InputStream {
        val filename = descriptor.archiveUrl.removePrefix("asset://")
        require(filename.startsWith("linux/") && !filename.contains("..")) {
            "The bundled Linux archive path is invalid."
        }
        return applicationContext.assets.open(filename)
    }

    fun selectedEnvironment(workspace: Workspace): String? =
        preferences.getString("workspace_environment_" + workspace.id, null)

    fun selectEnvironment(workspace: Workspace, identifier: String?) {
        if (identifier != null) {
            require(linuxEnvironments().any { it.name == identifier }) {
                "The selected Linux environment is not installed."
            }
        }
        preferences.edit().putString("workspace_environment_" + workspace.id, identifier).apply()
    }

    fun sandboxCommand(workspace: Workspace): List<String>? {
        val identifier = selectedEnvironment(workspace) ?: return null
        val rootfs = linuxEnvironments().firstOrNull { it.name == identifier }
            ?: error("The selected Linux environment is no longer installed.")
        val executable = File(applicationContext.applicationInfo.nativeLibraryDir, "libproot_exec.so")
        return ProotBackend(executable, rootfs).commandPrefix(File(workspace.rootPath))
    }

    fun sandboxTemporaryDirectory(): File = applicationContext.cacheDir.apply { mkdirs() }

    fun deleteLinuxEnvironment(identifier: String) {
        val environment = linuxEnvironments().firstOrNull { it.name == identifier }
            ?: error("The selected Linux environment is not installed.")
        require(environment.canonicalFile.parentFile == environmentsDirectory.canonicalFile) {
            "The Linux environment path escaped its managed directory."
        }
        database.listWorkspaces().filter { selectedEnvironment(it) == identifier }.forEach {
            selectEnvironment(it, null)
        }
        check(environment.deleteRecursively()) { "The Linux environment could not be removed safely." }
    }

    private fun readTelegramBots(): List<TelegramBotConfiguration> {
        val stored = database.listTelegramBots()
        if (stored.isNotEmpty()) return stored
        val legacy = preferences.getString("telegram_bots", null)
            ?.let { runCatching { json.decodeFromString<List<TelegramBotConfiguration>>(it) }.getOrDefault(emptyList()) }
            .orEmpty()
        if (legacy.isNotEmpty()) {
            database.saveTelegramBots(legacy)
            preferences.edit().remove("telegram_bots").apply()
        }
        return legacy
    }

    private fun readMcpServers(): List<McpServerConfiguration> {
        val stored = database.listMcpServers()
        if (stored.isNotEmpty()) return stored
        val legacy = preferences.getString("mcp_servers", null)
            ?.let { runCatching { json.decodeFromString<List<McpServerConfiguration>>(it) }.getOrDefault(emptyList()) }
            .orEmpty()
        if (legacy.isNotEmpty()) {
            database.saveMcpServers(legacy)
            preferences.edit().remove("mcp_servers").apply()
        }
        return legacy
    }

    private fun persistTelegramBots(value: List<TelegramBotConfiguration>) {
        database.saveTelegramBots(value)
        telegramBotsValue.value = value
    }

    private fun persistMcpServers(value: List<McpServerConfiguration>) {
        database.saveMcpServers(value)
        mcpServersValue.value = value
    }

    private fun parseIdentifiers(value: String, required: Boolean): Set<Long> {
        val identifiers = value.split(',', ' ', '\n')
            .filter { it.isNotBlank() }
            .map { it.trim().toLongOrNull() ?: error("Telegram user and chat identifiers must be numeric.") }
            .toSet()
        require(!required || identifiers.isNotEmpty()) {
            "At least one explicitly allowed Telegram user ID is required."
        }
        return identifiers
    }

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
