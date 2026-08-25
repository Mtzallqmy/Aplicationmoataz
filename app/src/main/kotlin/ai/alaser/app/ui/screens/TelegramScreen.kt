package ai.alaser.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import ai.alaser.app.ui.i18n.AlaserText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ai.alaser.app.ui.AppUiState
import ai.alaser.core.model.TelegramBotConfiguration

@Composable
fun TelegramScreen(
    state: AppUiState,
    onSave: (String, String, String, String) -> Unit,
    onTest: (TelegramBotConfiguration) -> Unit,
    onToggle: (TelegramBotConfiguration) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var allowedUsers by remember { mutableStateOf("") }
    var allowedChats by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Tokens are encrypted. Unknown users are rejected. Local polling works only while the app is running.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Bot name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Bot token") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = allowedUsers,
                onValueChange = { allowedUsers = it },
                label = { Text("Allowed Telegram user IDs") },
                supportingText = { Text("Required; separate multiple IDs with commas.") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = allowedChats,
                onValueChange = { allowedChats = it },
                label = { Text("Allowed chat IDs (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = {
                    onSave(name, token, allowedUsers, allowedChats)
                    name = ""
                    token = ""
                    allowedUsers = ""
                    allowedChats = ""
                },
                enabled = state.activeWorkspace != null &&
                    name.isNotBlank() &&
                    token.isNotBlank() &&
                    allowedUsers.isNotBlank(),
            ) {
                Text("Save encrypted bot")
            }
        }
        items(state.telegramBots, key = { it.id }) { bot ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(bot.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        bot.allowedUserIds.size.toString() + " allowed users · " +
                            if (bot.enabled) "polling enabled" else "stopped",
                    )
                    Row {
                        TextButton(onClick = { onTest(bot) }) { Text("Test connection") }
                        TextButton(onClick = { onToggle(bot) }) {
                            Text(if (bot.enabled) "Stop polling" else "Start polling")
                        }
                    }
                }
            }
        }
        state.integrationStatus?.let { status ->
            item { Text(status, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
