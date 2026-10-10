package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.annotate.AnnotationItem
import com.example.annotate.AnnotationType
import com.example.data.model.AnnotationRecord
import com.example.data.model.SignatureItem
import com.example.render.PageRenderer
import com.example.signature.PlacedSignature
import com.example.ui.ScanViewModel
import com.example.ui.components.SignaturePadDialog
import com.example.ui.components.SignaturePickerModal
import com.example.ui.components.WatermarkModal
import com.example.watermark.WatermarkConfig
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EditorTool {
    SELECT,
    PEN,
    HIGHLIGHTER,
    RECTANGLE,
    ELLIPSE,
    LINE,
    ARROW,
    TEXT,
    SIGNATURE,
    WATERMARK,
    ERASER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    viewModel: ScanViewModel,
    pageId: Long,
    documentId: Long
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pageRenderer = remember { PageRenderer(context) }

    val pageFlow = remember(pageId) { kotlinx.coroutines.flow.flow { emit(viewModel.repository.getPageById(pageId)) } }
    val page by pageFlow.collectAsStateWithLifecycle(null)

    val annotationFlow = remember(pageId) { viewModel.repository.getAnnotationFlow(pageId) }
    val annotationRecord by annotationFlow.collectAsStateWithLifecycle(null)

    val watermarkFlow = remember(documentId) { viewModel.repository.getWatermarkSettingFlow(documentId) }
    val watermarkSetting by watermarkFlow.collectAsStateWithLifecycle(null)

    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page) {
        page?.let { p ->
            val path = p.processedPath.ifBlank { p.croppedPath }
            baseBitmap = viewModel.fileManager.loadBitmap(path)
        }
    }

    // Annotation elements list & Undo/Redo stack
    var annotations by remember { mutableStateOf<List<AnnotationItem>>(emptyList()) }
    var placedSignatures by remember { mutableStateOf<List<PlacedSignature>>(emptyList()) }
    var watermarkConfig by remember { mutableStateOf(WatermarkConfig()) }

    var undoStack by remember { mutableStateOf<List<List<AnnotationItem>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<AnnotationItem>>>(emptyList()) }

    var isInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(annotationRecord) {
        if (!isInitialized && annotationRecord != null) {
            annotations = AnnotationItem.listFromJson(annotationRecord!!.json)
            placedSignatures = PlacedSignature.listFromJson(annotationRecord!!.placedSignaturesJson)
            isInitialized = true
        }
    }

    LaunchedEffect(watermarkSetting) {
        watermarkSetting?.let { ws ->
            watermarkConfig = WatermarkConfig.fromJson(ws.configJson)
        }
    }

    var activeTool by remember { mutableStateOf(EditorTool.PEN) }
    var activeColorHex by remember { mutableStateOf("#000000") }
    var strokeThickness by remember { mutableFloatStateOf(4f) }

    // Active drawing stroke / shape
    var currentPoints by remember { mutableStateOf<List<PointF>>(emptyList()) }
    var shapeStart by remember { mutableStateOf<PointF?>(null) }
    var shapeEnd by remember { mutableStateOf<PointF?>(null) }

    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // Dialogs
    var showSignaturePicker by remember { mutableStateOf(false) }
    var showWatermarkModal by remember { mutableStateOf(false) }
    var showTextPrompt by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var textPosition by remember { mutableStateOf(PointF(0.5f, 0.5f)) }

    var showLayersDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val commitState: () -> Unit = {
        undoStack = undoStack + listOf(annotations)
        redoStack = emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Markup & Sign", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Undo
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                redoStack = redoStack + listOf(annotations)
                                annotations = undoStack.last()
                                undoStack = undoStack.dropLast(1)
                            }
                        },
                        enabled = undoStack.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }

                    // Redo
                    IconButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                undoStack = undoStack + listOf(annotations)
                                annotations = redoStack.last()
                                redoStack = redoStack.dropLast(1)
                            }
                        },
                        enabled = redoStack.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }

                    // Layers list
                    IconButton(onClick = { showLayersDialog = true }) {
                        Icon(Icons.Default.Layers, contentDescription = "Layers")
                    }

                    // Save Done
                    Button(
                        onClick = {
                            isSaving = true
                            coroutineScope.launch {
                                val current = annotationRecord ?: AnnotationRecord(pageId = pageId)
                                viewModel.repository.saveAnnotation(
                                    current.copy(
                                        json = AnnotationItem.listToJson(annotations),
                                        placedSignaturesJson = PlacedSignature.listToJson(placedSignatures),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                isSaving = false
                                viewModel.showMessage("Annotations saved")
                                viewModel.navigateBack()
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Done")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(8.dp)
                ) {
                    // Tool Palette Selector
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = activeTool == EditorTool.PEN,
                                onClick = { activeTool = EditorTool.PEN; activeColorHex = "#000000"; strokeThickness = 4f },
                                label = { Text("Pen") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeTool == EditorTool.HIGHLIGHTER,
                                onClick = { activeTool = EditorTool.HIGHLIGHTER; activeColorHex = "#FEF08A"; strokeThickness = 20f },
                                label = { Text("Highlight") },
                                leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeTool == EditorTool.SIGNATURE,
                                onClick = { showSignaturePicker = true },
                                label = { Text("Signature") },
                                leadingIcon = { Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeTool == EditorTool.TEXT,
                                onClick = { activeTool = EditorTool.TEXT; textPosition = PointF(0.3f, 0.4f); showTextPrompt = true },
                                label = { Text("Text") },
                                leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeTool == EditorTool.RECTANGLE,
                                onClick = { activeTool = EditorTool.RECTANGLE },
                                label = { Text("Rect") },
                                leadingIcon = { Icon(Icons.Default.CropSquare, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeTool == EditorTool.ARROW,
                                onClick = { activeTool = EditorTool.ARROW },
                                label = { Text("Arrow") },
                                leadingIcon = { Icon(Icons.Default.TrendingFlat, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeTool == EditorTool.WATERMARK,
                                onClick = { showWatermarkModal = true },
                                label = { Text("Watermark") },
                                leadingIcon = { Icon(Icons.Default.BrandingWatermark, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeTool == EditorTool.ERASER,
                                onClick = { activeTool = EditorTool.ERASER },
                                label = { Text("Eraser") },
                                leadingIcon = { Icon(Icons.Default.AutoFixNormal, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }

                    // Color swatches (for Pen, Shapes, Text)
                    if (activeTool in listOf(EditorTool.PEN, EditorTool.RECTANGLE, EditorTool.ARROW, EditorTool.TEXT)) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            val colors = listOf("#000000", "#1E40AF", "#DC2626", "#059669", "#7C3AED")
                            colors.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(hex)))
                                        .border(
                                            width = if (activeColorHex == hex) 3.dp else 1.dp,
                                            color = if (activeColorHex == hex) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { activeColorHex = hex }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = baseBitmap
            if (bmp != null) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .onGloballyPositioned { coordinates ->
                            canvasSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                        }
                        .pointerInput(activeTool, canvasSize) {
                            when (activeTool) {
                                EditorTool.PEN, EditorTool.HIGHLIGHTER -> {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            if (canvasSize.width <= 0) return@detectDragGestures
                                            currentPoints = listOf(PointF(offset.x / canvasSize.width, offset.y / canvasSize.height))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            if (canvasSize.width <= 0) return@detectDragGestures
                                            val normPt = PointF(change.position.x / canvasSize.width, change.position.y / canvasSize.height)
                                            currentPoints = currentPoints + normPt
                                        },
                                        onDragEnd = {
                                            if (currentPoints.size > 1) {
                                                commitState()
                                                val newStroke = AnnotationItem.FreehandStroke(
                                                    type = if (activeTool == EditorTool.HIGHLIGHTER) AnnotationType.HIGHLIGHTER else AnnotationType.PEN,
                                                    points = currentPoints,
                                                    colorHex = activeColorHex,
                                                    strokeWidth = strokeThickness,
                                                    isHighlighter = (activeTool == EditorTool.HIGHLIGHTER)
                                                )
                                                annotations = annotations + newStroke
                                            }
                                            currentPoints = emptyList()
                                        },
                                        onDragCancel = { currentPoints = emptyList() }
                                    )
                                }
                                EditorTool.RECTANGLE, EditorTool.ELLIPSE, EditorTool.LINE, EditorTool.ARROW -> {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            shapeStart = PointF(offset.x / canvasSize.width, offset.y / canvasSize.height)
                                            shapeEnd = shapeStart
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            shapeEnd = PointF(change.position.x / canvasSize.width, change.position.y / canvasSize.height)
                                        },
                                        onDragEnd = {
                                            val start = shapeStart
                                            val end = shapeEnd
                                            if (start != null && end != null) {
                                                commitState()
                                                val shapeType = when (activeTool) {
                                                    EditorTool.RECTANGLE -> AnnotationType.RECTANGLE
                                                    EditorTool.ELLIPSE -> AnnotationType.ELLIPSE
                                                    EditorTool.LINE -> AnnotationType.LINE
                                                    else -> AnnotationType.ARROW
                                                }
                                                val newShape = AnnotationItem.ShapeItem(
                                                    type = shapeType,
                                                    start = start,
                                                    end = end,
                                                    colorHex = activeColorHex,
                                                    strokeWidth = strokeThickness
                                                )
                                                annotations = annotations + newShape
                                            }
                                            shapeStart = null
                                            shapeEnd = null
                                        },
                                        onDragCancel = { shapeStart = null; shapeEnd = null }
                                    )
                                }
                                EditorTool.ERASER -> {
                                    detectTapGestures { offset ->
                                        if (annotations.isNotEmpty()) {
                                            commitState()
                                            annotations = annotations.dropLast(1)
                                        } else if (placedSignatures.isNotEmpty()) {
                                            placedSignatures = placedSignatures.dropLast(1)
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                ) {
                    if (size.width <= 0 || size.height <= 0) return@Canvas

                    // Render Base + Annotations + Signatures + Watermark onto native Canvas
                    drawContext.canvas.nativeCanvas.let { native ->
                        pageRenderer.renderOnCanvas(
                            canvas = native,
                            baseBitmap = bmp,
                            annotations = annotations,
                            placedSignatures = placedSignatures,
                            watermark = watermarkConfig,
                            canvasWidth = size.width,
                            canvasHeight = size.height
                        )
                    }

                    // Render in-progress drawing stroke
                    if (currentPoints.size > 1) {
                        val strokeItem = AnnotationItem.FreehandStroke(
                            points = currentPoints,
                            colorHex = activeColorHex,
                            strokeWidth = strokeThickness,
                            isHighlighter = (activeTool == EditorTool.HIGHLIGHTER)
                        )
                        drawContext.canvas.nativeCanvas.let { native ->
                            pageRenderer.renderOnCanvas(native, bmp, listOf(strokeItem), emptyList(), WatermarkConfig(), size.width, size.height)
                        }
                    }

                    // Render in-progress shape
                    if (shapeStart != null && shapeEnd != null) {
                        val shapeType = when (activeTool) {
                            EditorTool.RECTANGLE -> AnnotationType.RECTANGLE
                            EditorTool.ELLIPSE -> AnnotationType.ELLIPSE
                            EditorTool.LINE -> AnnotationType.LINE
                            else -> AnnotationType.ARROW
                        }
                        val inProgressShape = AnnotationItem.ShapeItem(
                            type = shapeType,
                            start = shapeStart!!,
                            end = shapeEnd!!,
                            colorHex = activeColorHex,
                            strokeWidth = strokeThickness
                        )
                        drawContext.canvas.nativeCanvas.let { native ->
                            pageRenderer.renderOnCanvas(native, bmp, listOf(inProgressShape), emptyList(), WatermarkConfig(), size.width, size.height)
                        }
                    }
                }
            } else {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    // Modal: Signature Picker & Placer
    if (showSignaturePicker) {
        SignaturePickerModal(
            viewModel = viewModel,
            onDismiss = { showSignaturePicker = false },
            onSelectSignature = { sigItem, stampDate, stampText ->
                showSignaturePicker = false
                val newPlaced = PlacedSignature(
                    signatureId = sigItem.id,
                    path = sigItem.path,
                    normalizedX = 0.5f,
                    normalizedY = 0.7f,
                    normalizedWidth = 0.38f,
                    normalizedHeight = 0.16f,
                    stampDate = stampDate,
                    stampText = stampText
                )
                placedSignatures = placedSignatures + newPlaced
                viewModel.showMessage("Signature placed on page")
            }
        )
    }

    // Modal: Watermark Settings
    if (showWatermarkModal) {
        WatermarkModal(
            initialConfig = watermarkConfig,
            onDismiss = { showWatermarkModal = false },
            onSave = { updated ->
                watermarkConfig = updated
                showWatermarkModal = false
                coroutineScope.launch {
                    val current = watermarkSetting ?: com.example.data.model.WatermarkSetting(documentId = documentId)
                    viewModel.repository.saveWatermarkSetting(
                        current.copy(configJson = updated.toJson())
                    )
                    viewModel.showMessage("Watermark updated")
                }
            }
        )
    }

    // Dialog: Text Input
    if (showTextPrompt) {
        AlertDialog(
            onDismissRequest = { showTextPrompt = false },
            title = { Text("Add Text Note") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Annotation Text") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            commitState()
                            val textItem = AnnotationItem.TextItem(
                                text = textInput.trim(),
                                position = textPosition,
                                colorHex = activeColorHex,
                                fontSize = 18f,
                                isBold = true
                            )
                            annotations = annotations + textItem
                            textInput = ""
                            showTextPrompt = false
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showTextPrompt = false }) { Text("Cancel") }
            }
        )
    }

    // Layers Drawer / Dialog
    if (showLayersDialog) {
        AlertDialog(
            onDismissRequest = { showLayersDialog = false },
            title = { Text("Annotation Layers (${annotations.size + placedSignatures.size})") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    if (annotations.isEmpty() && placedSignatures.isEmpty()) {
                        Text("No annotation layers on this page.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        placedSignatures.forEachIndexed { i, sig ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Signature #${i + 1}")
                                }
                                IconButton(onClick = {
                                    placedSignatures = placedSignatures.filter { it.id != sig.id }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        annotations.forEachIndexed { i, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        when (item.type) {
                                            AnnotationType.TEXT -> Icons.Default.TextFields
                                            AnnotationType.HIGHLIGHTER -> Icons.Default.Brush
                                            else -> Icons.Default.Edit
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("${item.type.name} #${i + 1}")
                                }
                                IconButton(onClick = {
                                    commitState()
                                    annotations = annotations.filter { it.id != item.id }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showLayersDialog = false }) { Text("Close") }
            }
        )
    }
}
