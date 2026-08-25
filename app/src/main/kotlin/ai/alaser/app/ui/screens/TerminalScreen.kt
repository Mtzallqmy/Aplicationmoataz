package ai.alaser.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import ai.alaser.app.ui.i18n.AlaserText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ai.alaser.app.ui.AppUiState
import ai.alaser.app.ui.theme.AlaserCodeTextStyle

@Composable
fun TerminalScreen(
    state: AppUiState,
    onRun: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onControl: (Int) -> Unit,
) {
    var command by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            state.activeWorkspace?.let { "Workspace: " + it.name } ?: "Create a project to run shell commands.",
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            if (state.terminalInteractive) {
                "Interactive PTY active · " + (state.activeEnvironment ?: "Android shell") + " · xterm-256color"
            } else {
                "Start a real interactive pseudo-terminal or run individual shell commands."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.terminalInteractive) {
                TextButton(onClick = onStop) { Text("Stop PTY") }
                TextButton(onClick = { onControl(3) }) { Text("CTRL+C") }
                TextButton(onClick = { onControl(9) }) { Text("TAB") }
                TextButton(onClick = { onControl(27) }) { Text("ESC") }
            } else {
                Button(onClick = onStart, enabled = state.activeWorkspace != null) {
                    Text("Start interactive PTY")
                }
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = state.terminalOutput.ifBlank { "$ " },
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                style = AlaserCodeTextStyle,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Shell command") },
                    modifier = Modifier.weight(1f),
                    textStyle = AlaserCodeTextStyle,
                    singleLine = true,
                )
                Button(
                    onClick = {
                        onRun(command)
                        command = ""
                    },
                    enabled = state.activeWorkspace != null && command.isNotBlank(),
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Run") }
            }
        }
    }
}
