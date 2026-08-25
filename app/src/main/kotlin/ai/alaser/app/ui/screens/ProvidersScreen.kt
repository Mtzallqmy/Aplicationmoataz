package ai.alaser.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import ai.alaser.core.model.ProviderConfiguration

@Composable
fun ProvidersScreen(
    state: AppUiState,
    onSave: (String, String, String, String) -> Unit,
    onSelect: (ProviderConfiguration) -> Unit,
    onTest: (ProviderConfiguration) -> Unit,
    onDismissTest: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var model by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item {
            Text(
                "Keys are encrypted using Android Keystore. Requests go directly to your selected provider.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Provider name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model identifier") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            Button(
                onClick = {
                    onSave(name, baseUrl, model, key)
                    name = ""
                    model = ""
                    key = ""
                },
                enabled = name.isNotBlank() && model.isNotBlank() && key.isNotBlank(),
            ) { Text("Save encrypted provider") }
        }
        items(state.providers, key = { it.id }) { provider ->
            Card(
                onClick = { onSelect(provider) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (provider.id == state.activeProvider?.id) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium)
                    Text(provider.defaultModel, color = MaterialTheme.colorScheme.primary)
                    Text(provider.baseUrl, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onTest(provider) }) { Text("Test connection") }
                }
            }
        }
    }

    state.providerTest?.let { result ->
        AlertDialog(
            onDismissRequest = onDismissTest,
            title = { Text(if (result.success) "Provider connected" else "Provider connection failed") },
            text = {
                Text(
                    if (result.success) {
                        "HTTP " + result.statusCode + " · " + result.latencyMilliseconds + " ms · " +
                            result.models.size + " models"
                    } else {
                        result.detail ?: "HTTP " + result.statusCode
                    },
                )
            },
            confirmButton = { TextButton(onClick = onDismissTest) { Text("Close") } },
        )
    }
}
