package ai.alaser.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.alaser.app.ui.AppUiState

@Composable
fun SettingsScreen(
    state: AppUiState,
    onOpenProviders: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsCard(
                title = "AI Providers",
                detail = state.providers.size.toString() + " configured · encrypted credentials",
                onClick = onOpenProviders,
            )
        }
        if (state.activeWorkspace != null) {
            item {
                SettingsCard(
                    title = "Workspace Files",
                    detail = state.activeWorkspace.name,
                    onClick = onOpenFiles,
                )
            }
        }
        item {
            InformationCard("Privacy", "Local-first storage · telemetry disabled · provider requests are direct")
        }
        item {
            InformationCard("Sandbox", "Native interactive PTY available. PRoot requires a separately audited executable.")
        }
        item {
            InformationCard("Telegram", "Long-polling client is implemented as an integration module; settings UI is pending.")
        }
        item {
            InformationCard("MCP", "JSON-RPC HTTP transport is implemented; server-management UI is pending.")
        }
        item {
            InformationCard("About", "Alaser AI 0.1.0 · Mobile AI development workspace")
        }
    }
}

@Composable
private fun SettingsCard(title: String, detail: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InformationCard(title: String, detail: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
