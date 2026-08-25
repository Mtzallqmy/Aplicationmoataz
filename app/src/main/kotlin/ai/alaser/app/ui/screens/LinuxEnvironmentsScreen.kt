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
import androidx.compose.material3.Text
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
                "Install a trusted ZIP or tar.gz root filesystem. A verified SHA-256 checksum is mandatory. " +
                    "A separately supplied PRoot executable is still required to run the environment.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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
                    Text("Verified local root filesystem")
                }
            }
        }
    }
}
