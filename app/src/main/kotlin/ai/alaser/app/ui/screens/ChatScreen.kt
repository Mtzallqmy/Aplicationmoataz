package ai.alaser.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.alaser.app.ui.AppUiState
import ai.alaser.app.ui.theme.AlaserCodeTextStyle
import ai.alaser.core.model.AgentMode
import ai.alaser.core.model.AgentState
import ai.alaser.core.model.ApprovalDecision
import ai.alaser.core.model.ChatMessage
import ai.alaser.core.model.MessagePart
import ai.alaser.core.model.MessageRole

@Composable
fun ChatScreen(
    state: AppUiState,
    onSend: (String, AgentMode) -> Unit,
    onStop: () -> Unit,
    onApproval: (ApprovalDecision) -> Unit,
    onOpenProviders: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val active = state.agentState !in setOf(
        AgentState.IDLE,
        AgentState.COMPLETED,
        AgentState.FAILED,
        AgentState.CANCELLED,
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.activeProvider?.defaultModel ?: "No model configured",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenFiles, enabled = state.activeWorkspace != null) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = "Open workspace files")
            }
        }

        if (state.messages.isEmpty() && state.streamedText.isEmpty()) {
            EmptyChat(
                state = state,
                onOpenProviders = onOpenProviders,
                onOpenProjects = onOpenProjects,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageCard(message)
                }
                if (state.streamedText.isNotBlank()) {
                    item("stream") {
                        Text(state.streamedText, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                state.approval?.let { approval ->
                    item("approval") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Approval required", fontWeight = FontWeight.SemiBold)
                                Text(approval.invocation.name + " · " + approval.risk)
                                Text(approval.detail.take(2_000), style = AlaserCodeTextStyle)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onApproval(ApprovalDecision.ALLOW_ONCE) }) {
                                        Text("Allow once")
                                    }
                                    TextButton(onClick = { onApproval(ApprovalDecision.DENY) }) {
                                        Text("Deny")
                                    }
                                }
                                if (approval.risk != ai.alaser.core.model.RiskLevel.CRITICAL) {
                                    TextButton(onClick = { onApproval(ApprovalDecision.ALLOW_FOR_SESSION) }) {
                                        Text("Allow this tool for session")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (active) {
            Text(
                state.agentSummary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Describe what you want to build…") },
                minLines = 1,
                maxLines = 6,
                shape = RoundedCornerShape(18.dp),
            )
            IconButton(
                onClick = {
                    if (active) {
                        onStop()
                    } else {
                        onSend(draft, AgentMode.BUILD)
                        draft = ""
                    }
                },
            ) {
                Icon(
                    if (active) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send,
                    contentDescription = if (active) "Stop task" else "Send task",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun EmptyChat(
    state: AppUiState,
    onOpenProviders: () -> Unit,
    onOpenProjects: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Build from your phone", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Alaser reads files, proposes changes, and runs approved commands in your project.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.activeWorkspace == null) {
                Button(onClick = onOpenProjects) { Text("Create a project") }
            }
            if (state.activeProvider == null) {
                Button(onClick = onOpenProviders) { Text("Add an AI provider") }
            }
        }
    }
}

@Composable
private fun MessageCard(message: ChatMessage) {
    val user = message.role == MessageRole.USER
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (user) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (message.role) {
                    MessageRole.USER -> "You"
                    MessageRole.ASSISTANT -> "Alaser"
                    MessageRole.TOOL -> "Tool result"
                    MessageRole.SYSTEM -> "System"
                },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            message.parts.forEach { part ->
                when (part) {
                    is MessagePart.Text -> Text(part.value)
                    is MessagePart.ToolCall -> {
                        Text(part.name.uppercase(), fontWeight = FontWeight.Medium)
                        Text(part.arguments.take(1_000), style = AlaserCodeTextStyle)
                    }
                    is MessagePart.ToolResult -> {
                        Text(part.name, fontWeight = FontWeight.Medium)
                        Text(
                            part.output.take(8_000),
                            style = AlaserCodeTextStyle,
                            color = if (part.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    is MessagePart.Command -> Text(part.command + "\n" + part.output, style = AlaserCodeTextStyle)
                    is MessagePart.Status -> Text(part.summary)
                    is MessagePart.Approval -> Text(part.tool + ": " + part.detail)
                    is MessagePart.Error -> Text(part.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
