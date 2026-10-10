package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.Document
import com.example.data.model.Folder
import com.example.data.model.OcrStatus
import com.example.data.model.Page
import com.example.pdf.PdfExportOptions
import com.example.ui.Screen
import com.example.ui.ScanViewModel
import com.example.ui.SortOption
import com.example.ui.ViewMode
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: ScanViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val allDocs by viewModel.allDocuments.collectAsStateWithLifecycle()
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val allOcrResults by viewModel.allOcrResults.collectAsStateWithLifecycle()
    val currentFolderId by viewModel.currentFolderId.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val selectedDocIds by viewModel.selectedDocumentIds.collectAsStateWithLifecycle()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderNameInput by remember { mutableStateOf("") }

    var docToRename by remember { mutableStateOf<Document?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var docToMove by remember { mutableStateOf<Document?>(null) }
    var docToExport by remember { mutableStateOf<Document?>(null) }

    // Multi-photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importImagesFromUris(uris)
        }
    }

    // PDF SAF picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val docId = viewModel.pdfTools.importPdf(uri)
                    viewModel.showMessage("PDF imported successfully")
                    viewModel.navigateTo(Screen.PageGrid(docId))
                } catch (e: Exception) {
                    viewModel.showMessage("Failed to import PDF: ${e.localizedMessage}")
                }
            }
        }
    }

    // Filter documents by folder
    val filteredDocs = remember(allDocs, currentFolderId, sortOption) {
        val inFolder = if (currentFolderId == null) {
            allDocs.filter { it.folderId == null }
        } else {
            allDocs.filter { it.folderId == currentFolderId }
        }
        when (sortOption) {
            SortOption.DATE_DESC -> inFolder.sortedByDescending { it.updatedAt }
            SortOption.DATE_ASC -> inFolder.sortedBy { it.updatedAt }
            SortOption.NAME_ASC -> inFolder.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> inFolder.sortedByDescending { it.title.lowercase() }
        }
    }

    Scaffold(
        topBar = {
            if (selectedDocIds.isNotEmpty()) {
                // Multi-select Action Bar
                TopAppBar(
                    title = { Text("${selectedDocIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        if (selectedDocIds.size >= 2) {
                            IconButton(onClick = {
                                viewModel.bulkMergeSelected("Merged Scan (${SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date())})")
                            }) {
                                Icon(Icons.Default.MergeType, contentDescription = "Merge Selected")
                            }
                        }
                        IconButton(onClick = { viewModel.bulkDeleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "ScanFlow",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.navigateTo(Screen.Search) },
                            modifier = Modifier.testTag("search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search Documents")
                        }
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                if (viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle View Mode"
                            )
                        }
                        // Sort menu
                        var sortMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Newest first") },
                                    onClick = { viewModel.setSortOption(SortOption.DATE_DESC); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Oldest first") },
                                    onClick = { viewModel.setSortOption(SortOption.DATE_ASC); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (A-Z)") },
                                    onClick = { viewModel.setSortOption(SortOption.NAME_ASC); sortMenuExpanded = false }
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.navigateTo(Screen.Settings) },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Secondary action: Import
                var fabMenuOpen by remember { mutableStateOf(false) }
                if (fabMenuOpen) {
                    SmallFloatingActionButton(
                        onClick = {
                            fabMenuOpen = false
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Import PDF", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PDF", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    SmallFloatingActionButton(
                        onClick = {
                            fabMenuOpen = false
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Import Photos", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gallery", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { fabMenuOpen = !fabMenuOpen },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            if (fabMenuOpen) Icons.Default.Close else Icons.Default.AddPhotoAlternate,
                            contentDescription = "Import Menu",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    ExtendedFloatingActionButton(
                        onClick = { viewModel.navigateTo(Screen.Camera()) },
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                        text = { Text("Scan", fontWeight = FontWeight.SemiBold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("scan_fab")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Folder Navigation Bar
            FolderBar(
                folders = allFolders,
                currentFolderId = currentFolderId,
                onSelectFolder = { viewModel.setCurrentFolder(it) },
                onCreateFolderClick = {
                    folderNameInput = ""
                    showCreateFolderDialog = true
                }
            )

            if (filteredDocs.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No scans here yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap 'Scan' to capture with edge detection or import from gallery.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                if (viewMode == ViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredDocs, key = { it.id }) { doc ->
                            DocumentCard(
                                document = doc,
                                viewModel = viewModel,
                                isSelected = selectedDocIds.contains(doc.id),
                                onSelect = { viewModel.toggleDocumentSelection(doc.id) },
                                onClick = {
                                    if (selectedDocIds.isNotEmpty()) {
                                        viewModel.toggleDocumentSelection(doc.id)
                                    } else {
                                        val isLocked = viewModel.isDocumentLockedForUser(doc)
                                        if (isLocked) {
                                            if (context is androidx.fragment.app.FragmentActivity) {
                                                viewModel.securityManager.authenticateBiometric(
                                                    activity = context,
                                                    title = "Unlock ${doc.title}",
                                                    onSuccess = {
                                                        viewModel.unlockDocumentSession(doc.id)
                                                        viewModel.navigateTo(Screen.PageGrid(doc.id))
                                                    },
                                                    onError = { viewModel.showMessage(it) }
                                                )
                                            } else {
                                                viewModel.unlockDocumentSession(doc.id)
                                                viewModel.navigateTo(Screen.PageGrid(doc.id))
                                            }
                                        } else {
                                            viewModel.navigateTo(Screen.PageGrid(doc.id))
                                        }
                                    }
                                },
                                onRename = {
                                    docToRename = doc
                                    renameInput = doc.title
                                },
                                onMove = { docToMove = doc },
                                onExport = { docToExport = doc },
                                onDelete = { viewModel.deleteDocument(doc.id) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredDocs, key = { it.id }) { doc ->
                            DocumentListItem(
                                document = doc,
                                viewModel = viewModel,
                                isSelected = selectedDocIds.contains(doc.id),
                                onSelect = { viewModel.toggleDocumentSelection(doc.id) },
                                onClick = {
                                    if (selectedDocIds.isNotEmpty()) {
                                        viewModel.toggleDocumentSelection(doc.id)
                                    } else {
                                        val isLocked = viewModel.isDocumentLockedForUser(doc)
                                        if (isLocked) {
                                            if (context is androidx.fragment.app.FragmentActivity) {
                                                viewModel.securityManager.authenticateBiometric(
                                                    activity = context,
                                                    title = "Unlock ${doc.title}",
                                                    onSuccess = {
                                                        viewModel.unlockDocumentSession(doc.id)
                                                        viewModel.navigateTo(Screen.PageGrid(doc.id))
                                                    },
                                                    onError = { viewModel.showMessage(it) }
                                                )
                                            } else {
                                                viewModel.unlockDocumentSession(doc.id)
                                                viewModel.navigateTo(Screen.PageGrid(doc.id))
                                            }
                                        } else {
                                            viewModel.navigateTo(Screen.PageGrid(doc.id))
                                        }
                                    }
                                },
                                onRename = {
                                    docToRename = doc
                                    renameInput = doc.title
                                },
                                onMove = { docToMove = doc },
                                onExport = { docToExport = doc },
                                onDelete = { viewModel.deleteDocument(doc.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog: Create Folder
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotBlank()) {
                            coroutineScope.launch {
                                viewModel.repository.createFolder(folderNameInput.trim(), currentFolderId)
                                showCreateFolderDialog = false
                            }
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Rename Document
    docToRename?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToRename = null },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            coroutineScope.launch {
                                viewModel.repository.updateDocument(doc.copy(title = renameInput.trim(), updatedAt = System.currentTimeMillis()))
                                docToRename = null
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { docToRename = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Move Document to Folder
    docToMove?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToMove = null },
            title = { Text("Move to Folder") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.repository.updateDocument(doc.copy(folderId = null, updatedAt = System.currentTimeMillis()))
                                docToMove = null
                                viewModel.showMessage("Moved to Home")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Home (Root)", modifier = Modifier.fillMaxWidth())
                    }
                    Divider()
                    allFolders.forEach { f ->
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.repository.updateDocument(doc.copy(folderId = f.id, updatedAt = System.currentTimeMillis()))
                                    docToMove = null
                                    viewModel.showMessage("Moved to ${f.name}")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(f.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { docToMove = null }) { Text("Cancel") }
            }
        )
    }

    // PDF Export Dialog
    docToExport?.let { doc ->
        PdfExportModal(
            document = doc,
            viewModel = viewModel,
            onDismiss = { docToExport = null }
        )
    }
}

@Composable
fun FolderBar(
    folders: List<Folder>,
    currentFolderId: Long?,
    onSelectFolder: (Long?) -> Unit,
    onCreateFolderClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = currentFolderId == null,
            onClick = { onSelectFolder(null) },
            label = { Text("All Scans") },
            leadingIcon = {
                Icon(
                    if (currentFolderId == null) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        )

        Spacer(Modifier.width(8.dp))

        folders.forEach { f ->
            FilterChip(
                selected = currentFolderId == f.id,
                onClick = { onSelectFolder(f.id) },
                label = { Text(f.name) },
                modifier = Modifier.padding(end = 6.dp)
            )
        }

        IconButton(
            onClick = onCreateFolderClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCard(
    document: Document,
    viewModel: ScanViewModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val pagesFlow = remember(document.id) { viewModel.repository.getPagesForDocument(document.id) }
    val pages by pagesFlow.collectAsStateWithLifecycle(emptyList())
    var menuExpanded by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onSelect
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val thumb = pages.firstOrNull()?.let { it.processedPath.ifBlank { it.croppedPath } }
                if (thumb != null && File(thumb).exists()) {
                    AsyncImage(
                        model = File(thumb),
                        contentDescription = document.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Page count badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${pages.size} p.",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Column(Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuExpanded = false; onRename() })
                            DropdownMenuItem(text = { Text("Move to folder") }, onClick = { menuExpanded = false; onMove() })
                            DropdownMenuItem(text = { Text("Export PDF") }, onClick = { menuExpanded = false; onExport() })
                            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menuExpanded = false; onDelete() })
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormat.format(Date(document.updatedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Searchable icon indicator if OCR complete
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Searchable",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentListItem(
    document: Document,
    viewModel: ScanViewModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val pagesFlow = remember(document.id) { viewModel.repository.getPagesForDocument(document.id) }
    val pages by pagesFlow.collectAsStateWithLifecycle(emptyList())
    var menuExpanded by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val thumb = pages.firstOrNull()?.let { it.processedPath.ifBlank { it.croppedPath } }
                if (thumb != null && File(thumb).exists()) {
                    AsyncImage(
                        model = File(thumb),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${pages.size} page(s) • ${dateFormat.format(Date(document.updatedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Searchable",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text("Move to folder") }, onClick = { menuExpanded = false; onMove() })
                    DropdownMenuItem(text = { Text("Export PDF") }, onClick = { menuExpanded = false; onExport() })
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun PdfExportModal(
    document: Document,
    viewModel: ScanViewModel,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var pageSize by remember { mutableStateOf(com.example.pdf.PdfPageSize.A4) }
    var compression by remember { mutableStateOf(com.example.pdf.PdfCompression.MEDIUM) }
    var makeSearchable by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Export PDF")
            }
        },
        text = {
            Column {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))

                // Searchable toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Make PDF Searchable", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Embeds invisible OCR text layer for Ctrl+F search & copy",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = makeSearchable,
                        onCheckedChange = { makeSearchable = it }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Page size selection
                Text("Page Size", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.pdf.PdfPageSize.values().forEach { size ->
                        FilterChip(
                            selected = pageSize == size,
                            onClick = { pageSize = size },
                            label = { Text(if (size == com.example.pdf.PdfPageSize.FIT_TO_IMAGE) "Fit" else size.name) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Compression level
                Text("Quality / Compression", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.pdf.PdfCompression.values().forEach { comp ->
                        FilterChip(
                            selected = compression == comp,
                            onClick = { compression = comp },
                            label = { Text(comp.name) }
                        )
                    }
                }

                if (isExporting) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Generating PDF...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isExporting = true
                    coroutineScope.launch {
                        try {
                            val pages = viewModel.repository.getPagesListForDocument(document.id)
                            val file = viewModel.pdfGenerator.generatePdf(
                                pages = pages,
                                options = PdfExportOptions(
                                    title = document.title,
                                    pageSize = pageSize,
                                    compression = compression,
                                    makeSearchable = makeSearchable
                                )
                            )
                            isExporting = false
                            onDismiss()
                            viewModel.pdfTools.shareFile(file)
                        } catch (e: Exception) {
                            isExporting = false
                            viewModel.showMessage("Export failed: ${e.localizedMessage}")
                        }
                    }
                },
                enabled = !isExporting
            ) {
                Text("Share PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isExporting) {
                Text("Cancel")
            }
        }
    )
}
