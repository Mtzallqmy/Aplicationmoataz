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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.alaser.app.ui.AppUiState
import ai.alaser.core.model.McpServerConfiguration

@Composable
fun McpScreen(
    state: AppUiState,
    onSave: (String, String) -> Unit,
    onInspect: (McpServerConfiguration) -> Unit,
    onToggleTrust: (McpServerConfiguration) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("https://") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Inspect tools before trusting a server. Only HTTPS and explicit loopback endpoints are accepted.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("MCP server name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text("JSON-RPC HTTP endpoint") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            Button(
                onClick = {
                    onSave(name, endpoint)
                    name = ""
                    endpoint = "https://"
                },
                enabled = name.isNotBlank() && endpoint.length > 8,
            ) { Text("Add MCP server") }
        }
        items(state.mcpServers, key = { it.id }) { server ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium)
                    Text(server.endpoint, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (server.trusted) "Trusted for tool execution" else "Not trusted for execution",
                        color = if (server.trusted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    )
                    Row {
                        TextButton(onClick = { onInspect(server) }) { Text("Inspect tools") }
                        TextButton(onClick = { onToggleTrust(server) }) {
                            Text(if (server.trusted) "Revoke trust" else "Trust server")
                        }
                    }
                }
            }
        }
        items(state.mcpTools, key = { it.name }) { tool ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(tool.name, style = MaterialTheme.typography.titleMedium)
                    Text(tool.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        state.integrationStatus?.let { status ->
            item { Text(status, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
