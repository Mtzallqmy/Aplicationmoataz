package ai.alaser.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ai.alaser.app.ui.AppUiState
import ai.alaser.app.ui.theme.AlaserCodeTextStyle

@Composable
fun FilesScreen(
    state: AppUiState,
    onOpenDirectory: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onCreateFile: (String) -> Unit,
    onEditorChanged: (String) -> Unit,
    onSaveEditor: () -> Unit,
    onCloseEditor: () -> Unit,
) {
    state.editorPath?.let { path ->
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(path, style = AlaserCodeTextStyle, modifier = Modifier.weight(1f))
                TextButton(onClick = onCloseEditor) { Text("Close") }
                Button(onClick = onSaveEditor) { Text("Save") }
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = state.editorContent,
                    onValueChange = onEditorChanged,
                    modifier = Modifier.fillMaxSize(),
                    textStyle = AlaserCodeTextStyle,
                )
            }
        }
        return
    }

    var newPath by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.activeWorkspace == null) {
            Text("Create a project before browsing files.")
            return
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.currentDirectory, style = AlaserCodeTextStyle, modifier = Modifier.weight(1f))
            if (state.currentDirectory != ".") {
                TextButton(
                    onClick = {
                        val parent = state.currentDirectory.substringBeforeLast('/', "")
                        onOpenDirectory(parent.ifBlank { "." })
                    },
                ) { Text("Parent") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newPath,
                onValueChange = { newPath = it },
                label = { Text("New file path") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(
                onClick = {
                    val prefix = if (state.currentDirectory == ".") "" else state.currentDirectory + "/"
                    onCreateFile(prefix + newPath)
                    newPath = ""
                },
                enabled = newPath.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Add") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.files, key = { it.path }) { entry ->
                Card(
                    onClick = {
                        if (entry.directory) onOpenDirectory(entry.path) else onOpenFile(entry.path)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            if (entry.directory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
                            contentDescription = if (entry.directory) "Folder" else "File",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text(entry.name)
                            if (!entry.directory) {
                                Text(
                                    entry.size.toString() + " bytes",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
