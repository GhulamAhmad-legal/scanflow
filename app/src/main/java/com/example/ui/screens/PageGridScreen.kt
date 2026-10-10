package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.OcrStatus
import com.example.data.model.Page
import com.example.ui.Screen
import com.example.ui.ScanViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageGridScreen(
    viewModel: ScanViewModel,
    documentId: Long
) {
    val coroutineScope = rememberCoroutineScope()
    val docFlow = remember(documentId) { viewModel.repository.getDocumentFlow(documentId) }
    val document by docFlow.collectAsStateWithLifecycle(null)

    val pagesFlow = remember(documentId) { viewModel.repository.getPagesForDocument(documentId) }
    val pages by pagesFlow.collectAsStateWithLifecycle(emptyList())

    val queueState by viewModel.ocrQueue.queueState.collectAsStateWithLifecycle()
    var showExportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            document?.title ?: "Document",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${pages.size} page(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.Library) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Library")
                    }
                },
                actions = {
                    // Full text view button
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val fullText = viewModel.pdfTools.exportDocumentText(pages)
                                viewModel.pdfTools.shareText(fullText, "Export ${document?.title ?: "Document"} Text")
                            }
                        }
                    ) {
                        Icon(Icons.Default.TextFields, contentDescription = "Export All Text")
                    }

                    // Export PDF Button
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.navigateTo(Screen.Camera(targetDocumentId = documentId)) },
                icon = { Icon(Icons.Default.AddAPhoto, contentDescription = null) },
                text = { Text("Add Page") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_page_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // OCR Background Queue Banner if active
            if (queueState.queuedCount > 0 || queueState.currentProcessingPageId != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Recognizing text in background (${queueState.queuedCount} queued)...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (pages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No pages in this document. Tap 'Add Page' to scan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                        PageItemCard(
                            page = page,
                            pageNumber = index + 1,
                            isFirst = index == 0,
                            isLast = index == pages.size - 1,
                            viewModel = viewModel,
                            onMoveUp = {
                                coroutineScope.launch {
                                    val prev = pages[index - 1]
                                    viewModel.repository.updatePage(page.copy(orderIndex = index - 1))
                                    viewModel.repository.updatePage(prev.copy(orderIndex = index))
                                }
                            },
                            onMoveDown = {
                                coroutineScope.launch {
                                    val next = pages[index + 1]
                                    viewModel.repository.updatePage(page.copy(orderIndex = index + 1))
                                    viewModel.repository.updatePage(next.copy(orderIndex = index))
                                }
                            },
                            onReCrop = {
                                viewModel.navigateTo(Screen.Crop(page.id, null, documentId))
                            },
                            onReFilter = {
                                viewModel.navigateTo(Screen.Filters(page.id, null, documentId))
                            },
                            onViewOcr = {
                                viewModel.navigateTo(Screen.OcrResultView(page.id, documentId))
                            },
                            onMarkup = {
                                viewModel.navigateTo(Screen.PageEditor(page.id, documentId))
                            },
                            onRetake = {
                                viewModel.navigateTo(Screen.Camera(targetDocumentId = documentId, retakePageIndex = index))
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    viewModel.repository.deletePage(page)
                                    viewModel.showMessage("Page deleted")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showExportDialog && document != null) {
        PdfExportModal(
            document = document!!,
            viewModel = viewModel,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
fun PageItemCard(
    page: Page,
    pageNumber: Int,
    isFirst: Boolean,
    isLast: Boolean,
    viewModel: ScanViewModel,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onReCrop: () -> Unit,
    onReFilter: () -> Unit,
    onViewOcr: () -> Unit,
    onMarkup: () -> Unit,
    onRetake: () -> Unit,
    onDelete: () -> Unit
) {
    val ocrFlow = remember(page.id) { viewModel.repository.getOcrResult(page.id) }
    val ocrResult by ocrFlow.collectAsStateWithLifecycle(null)
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onViewOcr() },
                contentAlignment = Alignment.Center
            ) {
                val imagePath = page.processedPath.ifBlank { page.croppedPath }
                if (File(imagePath).exists()) {
                    AsyncImage(
                        model = File(imagePath),
                        contentDescription = "Page $pageNumber",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Page number badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .size(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$pageNumber", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                // OCR Status Chip
                val ocrStatus = ocrResult?.status ?: OcrStatus.PENDING.name
                val badgeColor = when (ocrStatus) {
                    OcrStatus.DONE.name -> Color(0xFF10B981)
                    OcrStatus.RUNNING.name -> Color(0xFF0284C7)
                    OcrStatus.FAILED.name -> Color(0xFFF43F5E)
                    else -> Color(0xFFF59E0B)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(badgeColor.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        when (ocrStatus) {
                            OcrStatus.DONE.name -> "OCR Text"
                            OcrStatus.RUNNING.name -> "Processing"
                            OcrStatus.FAILED.name -> "OCR Failed"
                            else -> "OCR Queued"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Page Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    if (!isFirst) {
                        IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Move Left", modifier = Modifier.size(16.dp))
                        }
                    }
                    if (!isLast) {
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Move Right", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Page Actions", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Markup & Sign") },
                            leadingIcon = { Icon(Icons.Default.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { menuOpen = false; onMarkup() }
                        )
                        DropdownMenuItem(
                            text = { Text("View & Edit OCR") },
                            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                            onClick = { menuOpen = false; onViewOcr() }
                        )
                        DropdownMenuItem(
                            text = { Text("Re-Crop") },
                            leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null) },
                            onClick = { menuOpen = false; onReCrop() }
                        )
                        DropdownMenuItem(
                            text = { Text("Re-Filter") },
                            leadingIcon = { Icon(Icons.Default.ColorLens, contentDescription = null) },
                            onClick = { menuOpen = false; onReFilter() }
                        )
                        DropdownMenuItem(
                            text = { Text("Retake Page") },
                            leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                            onClick = { menuOpen = false; onRetake() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Page", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}
