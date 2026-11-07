package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ExportProgressModal
import com.example.ui.dialogs.ExportDialog
import com.example.ui.screens.*
import com.example.ui.theme.AnimeStudioTheme
import com.example.viewmodel.StudioScreen
import com.example.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnimeStudioTheme {
                AnimeStudioApp()
            }
        }
    }
}

@Composable
fun AnimeStudioApp(
    viewModel: StudioViewModel = viewModel()
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val allProjects by viewModel.allProjects.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val isExportDialogOpen by viewModel.isExportDialogOpen.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Back handling according to Guidelines
    BackHandler(enabled = currentScreen != StudioScreen.HOME) {
        when (currentScreen) {
            StudioScreen.EDITOR, StudioScreen.TIMELINE -> {
                viewModel.navigateTo(StudioScreen.PREVIEW)
            }
            StudioScreen.PREVIEW, StudioScreen.ASSETS, StudioScreen.SETTINGS -> {
                viewModel.navigateTo(StudioScreen.HOME)
            }
            StudioScreen.HOME -> {
                // Exit app
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        when (currentScreen) {
            StudioScreen.HOME -> {
                HomeScreen(
                    viewModel = viewModel,
                    projects = allProjects,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            StudioScreen.EDITOR -> {
                XmlEditorScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            StudioScreen.PREVIEW -> {
                PreviewScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            StudioScreen.TIMELINE -> {
                TimelineScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            StudioScreen.ASSETS -> {
                AssetManagerScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            StudioScreen.SETTINGS -> {
                SettingsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        // Export Configuration Dialog
        if (isExportDialogOpen) {
            ExportDialog(
                initialTitle = currentProject?.title ?: "anime_export",
                onDismiss = { viewModel.closeExportDialog() },
                onStartExport = { config ->
                    viewModel.startExport(config)
                }
            )
        }

        // Export Rendering & Progress Modal
        ExportProgressModal(
            progress = exportProgress,
            onCancel = { viewModel.cancelExport() },
            onDismiss = { viewModel.cancelExport() }
        )
    }
}
