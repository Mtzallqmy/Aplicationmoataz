package ai.alaser.integration.telegram

import ai.alaser.core.model.TelegramBotConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class TelegramMessage(
    val updateId: Long,
    val messageId: Long,
    val chatId: Long,
    val userId: Long,
    val text: String,
)

data class TelegramBotIdentity(val id: Long, val username: String)

class TelegramClient(
    private val token: () -> String,
    baseClient: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = baseClient.newBuilder().readTimeout(40, TimeUnit.SECONDS).build()

    suspend fun testConnection(): TelegramBotIdentity = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val result = request("getMe")
        TelegramBotIdentity(
            id = result["id"]?.jsonPrimitive?.long ?: error("Telegram did not return a bot identifier."),
            username = result["username"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    fun poll(configuration: TelegramBotConfiguration, initialOffset: Long = 0): Flow<TelegramMessage> = flow {
        require(configuration.allowedUserIds.isNotEmpty()) {
            "Telegram polling requires at least one explicitly allowed user."
        }
        var offset = initialOffset
        while (true) {
            currentCoroutineContext().ensureActive()
            val response = request(
                "getUpdates",
                buildJsonObject {
                    put("offset", offset)
                    put("timeout", 25)
                },
            )
            val updates = response["updates"]?.jsonArray
                ?: error("Telegram returned an unexpected updates response.")
            updates.forEach { item ->
                val update = item.jsonObject
                val updateId = update["update_id"]?.jsonPrimitive?.long ?: return@forEach
                offset = maxOf(offset, updateId + 1)
                val message = update["message"]?.jsonObject ?: return@forEach
                val sender = message["from"]?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull ?: return@forEach
                val chat = message["chat"]?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull ?: return@forEach
                val text = message["text"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                if (configuration.accepts(sender, chat) && text.length <= MAX_MESSAGE_SIZE) {
                    emit(
                        TelegramMessage(
                            updateId,
                            message["message_id"]?.jsonPrimitive?.longOrNull ?: 0,
                            chat,
                            sender,
                            text,
                        ),
                    )
                }
            }
            if (updates.isEmpty()) delay(250)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun sendMessage(chatId: Long, text: String) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        require(text.length in 1..MAX_MESSAGE_SIZE) { "Telegram messages must be between 1 and 4096 characters." }
        request(
            "sendMessage",
            buildJsonObject {
                put("chat_id", chatId)
                put("text", text)
            },
        )
        Unit
    }

    private fun request(method: String, payload: kotlinx.serialization.json.JsonObject? = null): kotlinx.serialization.json.JsonObject {
        val builder = Request.Builder().url("https://api.telegram.org/bot" + token() + "/" + method)
        if (payload != null) {
            builder.post(payload.toString().toRequestBody("application/json".toMediaType()))
        }
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val root = json.parseToJsonElement(raw).jsonObject
            check(response.isSuccessful && root["ok"]?.jsonPrimitive?.contentOrNull == "true") {
                "Telegram API request failed with HTTP " + response.code + "."
            }
            val result = root["result"] ?: error("Telegram returned no result.")
            return if (result is kotlinx.serialization.json.JsonArray) {
                buildJsonObject { put("updates", result) }
            } else {
                result.jsonObject
            }
        }
    }

    companion object {
        const val MAX_MESSAGE_SIZE = 4096
    }
}
