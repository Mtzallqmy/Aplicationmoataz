package ai.alaser.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import ai.alaser.app.ui.AppUiState

@Composable
fun LinuxEnvironmentsScreen(
    state: AppUiState,
    onInstall: (String, String, String) -> Unit,
    onInstallBundled: (String) -> Unit,
    onSelect: (String?) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var checksum by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Ubuntu 24.04 Developer, Alpine Linux, PRoot, Python, Git, Node.js, npm, Java, Go, " +
                    "Rust, Cargo, GCC, and development tools are included directly in this app.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            Button(
                onClick = { onInstallBundled("ubuntu") },
                enabled = "ubuntu" !in state.installedEnvironments,
            ) { Text(if ("ubuntu" in state.installedEnvironments) "Ubuntu Developer installed" else "Install Ubuntu with all development tools") }
        }
        item {
            Button(
                onClick = { onInstallBundled("alpine") },
                enabled = "alpine" !in state.installedEnvironments,
            ) { Text(if ("alpine" in state.installedEnvironments) "Alpine Linux installed" else "Install bundled Alpine Linux") }
        }
        item {
            Text(
                "Current project environment: " + (state.activeEnvironment ?: "Native Android shell"),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (state.activeEnvironment != null) {
            item {
                TextButton(onClick = { onSelect(null) }) { Text("Use native Android shell") }
            }
        }
        item { Text("Add another verified Linux distribution", style = MaterialTheme.typography.titleMedium) }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Environment name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("HTTPS rootfs archive URL") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = checksum,
                onValueChange = { checksum = it },
                label = { Text("Expected SHA-256 checksum") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            Button(
                onClick = { onInstall(name, url, checksum) },
                enabled = name.isNotBlank() && url.startsWith("https://") && checksum.length == 64,
            ) { Text("Download and verify rootfs") }
        }
        state.environmentStatus?.let { status ->
            item { Text(status, color = MaterialTheme.colorScheme.primary) }
        }
        items(state.installedEnvironments, key = { it }) { environment ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(environment, style = MaterialTheme.typography.titleMedium)
                    Text(if (state.activeEnvironment == environment) "Active for this project" else "Verified local root filesystem")
                    androidx.compose.foundation.layout.Row {
                        TextButton(
                            onClick = { onSelect(environment) },
                            enabled = state.activeWorkspace != null && state.activeEnvironment != environment,
                        ) { Text("Use in terminal and agent") }
                        TextButton(onClick = { onDelete(environment) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}
