package ai.alaser.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ai.alaser.core.model.AgentMode
import ai.alaser.core.model.AgentSession
import ai.alaser.core.model.AgentState
import ai.alaser.core.model.ChatMessage
import ai.alaser.core.model.MessagePart
import ai.alaser.core.model.MessageRole
import ai.alaser.core.model.ProviderConfiguration
import ai.alaser.core.model.TelegramBotConfiguration
import ai.alaser.core.model.McpServerConfiguration
import ai.alaser.core.model.Workspace
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AlaserDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, SCHEMA_VERSION) {
    private val json = Json { ignoreUnknownKeys = true }

    override fun onConfigure(database: SQLiteDatabase) {
        database.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE workspaces (id TEXT PRIMARY KEY, name TEXT NOT NULL, root_path TEXT NOT NULL UNIQUE, " +
                "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, environment_id TEXT, custom_instructions TEXT NOT NULL)",
        )
        database.execSQL(
            "CREATE TABLE providers (id TEXT PRIMARY KEY, name TEXT NOT NULL, base_url TEXT NOT NULL, " +
                "default_model TEXT NOT NULL, secret_id TEXT NOT NULL, headers_json TEXT NOT NULL, " +
                "timeout_seconds INTEGER NOT NULL, context_window INTEGER NOT NULL)",
        )
        database.execSQL(
            "CREATE TABLE sessions (id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, " +
                "title TEXT NOT NULL, model_id TEXT NOT NULL, provider_id TEXT NOT NULL, mode TEXT NOT NULL, " +
                "state TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
        )
        database.execSQL(
            "CREATE TABLE messages (id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, " +
                "role TEXT NOT NULL, parts_json TEXT NOT NULL, created_at INTEGER NOT NULL)",
        )
        database.execSQL("CREATE INDEX idx_sessions_workspace ON sessions(workspace_id, updated_at DESC)")
        database.execSQL("CREATE INDEX idx_messages_session ON messages(session_id, created_at)")
        createIntegrationTables(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var current = oldVersion
        if (current == 1 && newVersion >= 2) {
            createIntegrationTables(database)
            current = 2
        }
        check(current == newVersion) {
            "No migration path exists from schema version " + oldVersion + " to " + newVersion + "."
        }
    }

    private fun createIntegrationTables(database: SQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS telegram_bots (id TEXT PRIMARY KEY, name TEXT NOT NULL, " +
                "token_secret_id TEXT NOT NULL, workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE, " +
                "allowed_user_ids TEXT NOT NULL, allowed_chat_ids TEXT NOT NULL, enabled INTEGER NOT NULL)",
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS mcp_servers (id TEXT PRIMARY KEY, name TEXT NOT NULL, " +
                "endpoint TEXT NOT NULL, enabled INTEGER NOT NULL, trusted INTEGER NOT NULL)",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_telegram_workspace ON telegram_bots(workspace_id)")
    }

    fun saveWorkspace(workspace: Workspace) {
        writableDatabase.insertWithOnConflict("workspaces", null, ContentValues().apply {
            put("id", workspace.id)
            put("name", workspace.name)
            put("root_path", workspace.rootPath)
            put("created_at", workspace.createdAt)
            put("updated_at", workspace.updatedAt)
            put("environment_id", workspace.environmentId)
            put("custom_instructions", workspace.customInstructions)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun listWorkspaces(): List<Workspace> = readableDatabase.query(
        "workspaces", null, null, null, null, null, "updated_at DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    Workspace(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        rootPath = cursor.getString(cursor.getColumnIndexOrThrow("root_path")),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        environmentId = cursor.getString(cursor.getColumnIndexOrThrow("environment_id")),
                        customInstructions = cursor.getString(cursor.getColumnIndexOrThrow("custom_instructions")),
                    ),
                )
            }
        }
    }

    fun saveProvider(provider: ProviderConfiguration) {
        writableDatabase.insertWithOnConflict("providers", null, ContentValues().apply {
            put("id", provider.id)
            put("name", provider.name)
            put("base_url", provider.baseUrl)
            put("default_model", provider.defaultModel)
            put("secret_id", provider.secretId)
            put("headers_json", json.encodeToString(provider.headers))
            put("timeout_seconds", provider.timeoutSeconds)
            put("context_window", provider.contextWindow)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun listProviders(): List<ProviderConfiguration> = readableDatabase.query(
        "providers", null, null, null, null, null, "name COLLATE NOCASE",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    ProviderConfiguration(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        baseUrl = cursor.getString(cursor.getColumnIndexOrThrow("base_url")),
                        defaultModel = cursor.getString(cursor.getColumnIndexOrThrow("default_model")),
                        secretId = cursor.getString(cursor.getColumnIndexOrThrow("secret_id")),
                        headers = json.decodeFromString(cursor.getString(cursor.getColumnIndexOrThrow("headers_json"))),
                        timeoutSeconds = cursor.getLong(cursor.getColumnIndexOrThrow("timeout_seconds")),
                        contextWindow = cursor.getInt(cursor.getColumnIndexOrThrow("context_window")),
                    ),
                )
            }
        }
    }

    fun saveSession(session: AgentSession) {
        writableDatabase.insertWithOnConflict("sessions", null, ContentValues().apply {
            put("id", session.id)
            put("workspace_id", session.workspaceId)
            put("title", session.title)
            put("model_id", session.modelId)
            put("provider_id", session.providerId)
            put("mode", session.mode.name)
            put("state", session.state.name)
            put("created_at", session.createdAt)
            put("updated_at", session.updatedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun listSessions(workspaceId: String): List<AgentSession> = readableDatabase.query(
        "sessions", null, "workspace_id = ?", arrayOf(workspaceId), null, null, "updated_at DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    AgentSession(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        workspaceId = cursor.getString(cursor.getColumnIndexOrThrow("workspace_id")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        modelId = cursor.getString(cursor.getColumnIndexOrThrow("model_id")),
                        providerId = cursor.getString(cursor.getColumnIndexOrThrow("provider_id")),
                        mode = AgentMode.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("mode"))),
                        state = AgentState.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("state"))),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                    ),
                )
            }
        }
    }

    fun saveMessage(message: ChatMessage) {
        writableDatabase.insertWithOnConflict("messages", null, ContentValues().apply {
            put("id", message.id)
            put("session_id", message.sessionId)
            put("role", message.role.name)
            put("parts_json", json.encodeToString(message.parts))
            put("created_at", message.createdAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun listMessages(sessionId: String): List<ChatMessage> = readableDatabase.query(
        "messages", null, "session_id = ?", arrayOf(sessionId), null, null, "created_at ASC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    ChatMessage(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id")),
                        role = MessageRole.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("role"))),
                        parts = json.decodeFromString<List<MessagePart>>(
                            cursor.getString(cursor.getColumnIndexOrThrow("parts_json")),
                        ),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    ),
                )
            }
        }
    }

    fun saveTelegramBots(bots: List<TelegramBotConfiguration>) {
        val database = writableDatabase
        database.beginTransaction()
        try {
            database.delete("telegram_bots", null, null)
            bots.forEach { bot ->
                database.insertOrThrow("telegram_bots", null, ContentValues().apply {
                    put("id", bot.id)
                    put("name", bot.name)
                    put("token_secret_id", bot.tokenSecretId)
                    put("workspace_id", bot.workspaceId)
                    put("allowed_user_ids", json.encodeToString(bot.allowedUserIds))
                    put("allowed_chat_ids", json.encodeToString(bot.allowedChatIds))
                    put("enabled", if (bot.enabled) 1 else 0)
                })
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun listTelegramBots(): List<TelegramBotConfiguration> = readableDatabase.query(
        "telegram_bots", null, null, null, null, null, "name COLLATE NOCASE",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(TelegramBotConfiguration(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    tokenSecretId = cursor.getString(cursor.getColumnIndexOrThrow("token_secret_id")),
                    workspaceId = cursor.getString(cursor.getColumnIndexOrThrow("workspace_id")),
                    allowedUserIds = json.decodeFromString<Set<Long>>(cursor.getString(cursor.getColumnIndexOrThrow("allowed_user_ids"))),
                    allowedChatIds = json.decodeFromString<Set<Long>>(cursor.getString(cursor.getColumnIndexOrThrow("allowed_chat_ids"))),
                    enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) != 0,
                ))
            }
        }
    }

    fun saveMcpServers(servers: List<McpServerConfiguration>) {
        val database = writableDatabase
        database.beginTransaction()
        try {
            database.delete("mcp_servers", null, null)
            servers.forEach { server ->
                database.insertOrThrow("mcp_servers", null, ContentValues().apply {
                    put("id", server.id)
                    put("name", server.name)
                    put("endpoint", server.endpoint)
                    put("enabled", if (server.enabled) 1 else 0)
                    put("trusted", if (server.trusted) 1 else 0)
                })
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun listMcpServers(): List<McpServerConfiguration> = readableDatabase.query(
        "mcp_servers", null, null, null, null, null, "name COLLATE NOCASE",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(McpServerConfiguration(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    endpoint = cursor.getString(cursor.getColumnIndexOrThrow("endpoint")),
                    enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) != 0,
                    trusted = cursor.getInt(cursor.getColumnIndexOrThrow("trusted")) != 0,
                ))
            }
        }
    }

    companion object {
        const val DATABASE_NAME = "alaser.db"
        const val SCHEMA_VERSION = 2
    }
}
