package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ScanViewModel
import com.example.ui.Screen
import com.example.ui.screens.CameraScanMode
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.CropScreen
import com.example.ui.screens.FiltersScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.OcrResultScreen
import com.example.ui.screens.PageEditorScreen
import com.example.ui.screens.PageGridScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ScanFlowTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ScanFlowTheme {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(statusMessage) {
                    statusMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearStatusMessage()
                    }
                }

                // Hardware back button navigation
                BackHandler(enabled = currentScreen !is Screen.Library) {
                    viewModel.navigateBack()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    when (val screen = currentScreen) {
                        is Screen.Library -> {
                            LibraryScreen(viewModel = viewModel)
                        }
                        is Screen.Camera -> {
                            CameraScreen(
                                viewModel = viewModel,
                                targetDocumentId = screen.targetDocumentId,
                                retakePageIndex = screen.retakePageIndex
                            )
                        }
                        is Screen.Crop -> {
                            CropScreen(
                                viewModel = viewModel,
                                pageId = screen.pageId,
                                originalBitmap = screen.originalBitmap,
                                documentId = screen.documentId
                            )
                        }
                        is Screen.Filters -> {
                            FiltersScreen(
                                viewModel = viewModel,
                                pageId = screen.pageId,
                                croppedBitmap = screen.croppedBitmap,
                                documentId = screen.documentId
                            )
                        }
                        is Screen.PageGrid -> {
                            PageGridScreen(
                                viewModel = viewModel,
                                documentId = screen.documentId
                            )
                        }
                        is Screen.PageEditor -> {
                            PageEditorScreen(
                                viewModel = viewModel,
                                pageId = screen.pageId,
                                documentId = screen.documentId
                            )
                        }
                        is Screen.OcrResultView -> {
                            OcrResultScreen(
                                viewModel = viewModel,
                                pageId = screen.pageId,
                                documentId = screen.documentId
                            )
                        }
                        is Screen.Search -> {
                            SearchScreen(viewModel = viewModel)
                        }
                        is Screen.Settings -> {
                            SettingsScreen(viewModel = viewModel)
                        }
                        is Screen.PdfToolsView -> {
                            LibraryScreen(viewModel = viewModel)
                        }
                        is Screen.IdCardScan -> {
                            CameraScreen(
                                viewModel = viewModel,
                                targetDocumentId = screen.targetDocumentId,
                                initialScanMode = CameraScanMode.ID_CARD
                            )
                        }
                    }
                }
            }
        }
    }
}
