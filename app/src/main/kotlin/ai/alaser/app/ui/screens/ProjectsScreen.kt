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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.alaser.app.ui.AppUiState
import ai.alaser.core.model.Workspace

@Composable
fun ProjectsScreen(
    state: AppUiState,
    onCreate: (String) -> Unit,
    onSelect: (Workspace) -> Unit,
    onOpenFiles: () -> Unit,
) {
    var projectName by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Local workspaces stay on this device. Every agent task is restricted to its selected project.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                label = { Text("Project name") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(
                onClick = {
                    onCreate(projectName)
                    projectName = ""
                },
                enabled = projectName.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Create") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.workspaces, key = { it.id }) { workspace ->
                Card(
                    onClick = { onSelect(workspace) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (workspace.id == state.activeWorkspace?.id) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(workspace.name, fontWeight = FontWeight.SemiBold)
                        Text("Local workspace", style = MaterialTheme.typography.bodyMedium)
                        if (workspace.id == state.activeWorkspace?.id) {
                            TextButton(onClick = onOpenFiles) { Text("Browse files") }
                        }
                    }
                }
            }
        }
    }
}
