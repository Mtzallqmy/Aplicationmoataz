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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import ai.alaser.app.ui.i18n.AlaserText as Text
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
    onCreateDirectory: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
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
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingRename by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf("") }

    pendingDelete?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete file or empty folder?") },
            text = { Text(path) },
            confirmButton = {
                TextButton(onClick = { onDelete(path); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
    pendingRename?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename or move") },
            text = {
                OutlinedTextField(
                    value = renameTarget,
                    onValueChange = { renameTarget = it },
                    label = { Text("New workspace-relative path") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onRename(source, renameTarget); pendingRename = null },
                    enabled = renameTarget.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pendingRename = null }) { Text("Cancel") } },
        )
    }
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
        TextButton(
            enabled = newPath.isNotBlank(),
            onClick = {
                val prefix = if (state.currentDirectory == ".") "" else state.currentDirectory + "/"
                onCreateDirectory(prefix + newPath)
                newPath = ""
            },
        ) { Text("Create folder instead") }
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
                        Column(Modifier.weight(1f)) {
                            Text(entry.name)
                            if (!entry.directory) {
                                Text(
                                    entry.size.toString() + " bytes",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        TextButton(onClick = {
                            pendingRename = entry.path
                            renameTarget = entry.path
                        }) { Text("Rename") }
                        TextButton(onClick = { pendingDelete = entry.path }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
