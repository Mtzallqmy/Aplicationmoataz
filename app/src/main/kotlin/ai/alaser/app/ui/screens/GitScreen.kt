package ai.alaser.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import ai.alaser.app.ui.theme.AlaserCodeTextStyle

@Composable
fun GitScreen(state: AppUiState, onAction: (String, String) -> Unit) {
    var message by remember { mutableStateOf("") }
    var confirmPush by remember { mutableStateOf(false) }
    if (confirmPush) {
        AlertDialog(
            onDismissRequest = { confirmPush = false },
            title = { Text("Push commits to the remote repository?") },
            text = { Text("This publishes your committed changes to the configured Git remote.") },
            confirmButton = {
                TextButton(onClick = { onAction("push", ""); confirmPush = false }) { Text("Push") }
            },
            dismissButton = { TextButton(onClick = { confirmPush = false }) { Text("Cancel") } },
        )
    }
    Column(
        Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            state.activeWorkspace?.let { "Project: " + it.name } ?: "Select a project first.",
            color = MaterialTheme.colorScheme.primary,
        )
        Text("Git commands run in your project's selected Android or Linux environment.")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onAction("status", "") }) { Text("Status") }
            TextButton(onClick = { onAction("diff", "") }) { Text("Diff") }
            TextButton(onClick = { onAction("log", "") }) { Text("Log") }
            TextButton(onClick = { onAction("branch", "") }) { Text("Branches") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onAction("init", "") }) { Text("Initialize") }
            TextButton(onClick = { onAction("stage", "") }) { Text("Stage all") }
            TextButton(onClick = { onAction("pull", "") }) { Text("Pull") }
            TextButton(onClick = { confirmPush = true }) { Text("Push") }
        }
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Commit message") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onAction("commit", message); message = "" },
            enabled = message.isNotBlank() && state.activeWorkspace != null,
        ) { Text("Create commit") }
        if (state.gitOutput.isNotBlank()) {
            Text(state.gitOutput, style = AlaserCodeTextStyle, modifier = Modifier.fillMaxWidth())
        }
    }
}
