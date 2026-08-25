package ai.alaser.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ai.alaser.app.ui.screens.ChatScreen
import ai.alaser.app.ui.screens.FilesScreen
import ai.alaser.app.ui.screens.ProjectsScreen
import ai.alaser.app.ui.screens.ProvidersScreen
import ai.alaser.app.ui.screens.SettingsScreen
import ai.alaser.app.ui.screens.TerminalScreen

private data class NavigationItem(val route: String, val label: String, val icon: ImageVector)

private val items = listOf(
    NavigationItem("chat", "Agent", Icons.AutoMirrored.Outlined.Chat),
    NavigationItem("projects", "Projects", Icons.Outlined.Folder),
    NavigationItem("terminal", "Terminal", Icons.Outlined.Terminal),
    NavigationItem("settings", "Settings", Icons.Outlined.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlaserApp(viewModel: AlaserViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val controller = rememberNavController()
    val backStack by controller.currentBackStackEntryAsState()
    val destination = backStack?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (destination?.route) {
                            "projects" -> "Projects"
                            "terminal" -> "Terminal"
                            "settings" -> "Settings"
                            "providers" -> "AI Providers"
                            "files" -> "Workspace Files"
                            else -> state.activeWorkspace?.name ?: "Alaser AI"
                        },
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = destination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            controller.navigate(item.route) {
                                popUpTo(controller.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        AlaserNavigation(padding, state, viewModel, controller)
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Action could not be completed") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
            },
        )
    }
}

@Composable
private fun AlaserNavigation(
    padding: PaddingValues,
    state: AppUiState,
    viewModel: AlaserViewModel,
    controller: androidx.navigation.NavHostController,
) {
    NavHost(
        navController = controller,
        startDestination = "chat",
        modifier = Modifier.padding(padding),
    ) {
        composable("chat") {
            ChatScreen(
                state = state,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopAgent,
                onApproval = viewModel::approve,
                onOpenProviders = { controller.navigate("providers") },
                onOpenProjects = { controller.navigate("projects") },
                onOpenFiles = { controller.navigate("files") },
            )
        }
        composable("projects") {
            ProjectsScreen(
                state = state,
                onCreate = viewModel::createWorkspace,
                onSelect = {
                    viewModel.selectWorkspace(it)
                    controller.navigate("chat")
                },
                onOpenFiles = { controller.navigate("files") },
            )
        }
        composable("terminal") {
            TerminalScreen(state, viewModel::runTerminalCommand)
        }
        composable("settings") {
            SettingsScreen(
                state = state,
                onOpenProviders = { controller.navigate("providers") },
                onOpenFiles = { controller.navigate("files") },
            )
        }
        composable("providers") {
            ProvidersScreen(
                state = state,
                onSave = viewModel::saveProvider,
                onSelect = viewModel::selectProvider,
                onTest = viewModel::testProvider,
                onDismissTest = viewModel::clearError,
            )
        }
        composable("files") {
            FilesScreen(
                state = state,
                onOpenDirectory = viewModel::loadFiles,
                onOpenFile = viewModel::openFile,
                onCreateFile = viewModel::createFile,
                onEditorChanged = viewModel::updateEditor,
                onSaveEditor = viewModel::saveEditor,
                onCloseEditor = viewModel::closeEditor,
            )
        }
    }
}
