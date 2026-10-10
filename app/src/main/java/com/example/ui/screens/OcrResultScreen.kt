package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.OcrResult
import com.example.data.model.OcrStatus
import com.example.ocr.OcrDocumentResult
import com.example.ocr.OcrWordBox
import com.example.ui.ScanViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrResultScreen(
    viewModel: ScanViewModel,
    pageId: Long,
    documentId: Long
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val ocrFlow = remember(pageId) { viewModel.repository.getOcrResult(pageId) }
    val ocrResult by ocrFlow.collectAsStateWithLifecycle(null)

    val pageFlow = remember(pageId) { kotlinx.coroutines.flow.flow { emit(viewModel.repository.getPageById(pageId)) } }
    val page by pageFlow.collectAsStateWithLifecycle(null)

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Visual Highlights, 1: Editable Text
    var editedTextState by remember { mutableStateOf("") }
    var hasInitializedText by remember { mutableStateOf(false) }

    LaunchedEffect(ocrResult) {
        if (!hasInitializedText && ocrResult != null) {
            editedTextState = ocrResult!!.effectiveText
            hasInitializedText = true
        }
    }

    val parsedDocResult = remember(ocrResult?.blocksJson) {
        OcrDocumentResult.fromJson(ocrResult?.blocksJson ?: "")
    }

    var selectedWord by remember { mutableStateOf<OcrWordBox?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR Text & Highlights", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Re-run OCR action
                    IconButton(
                        onClick = {
                            page?.let { p ->
                                viewModel.ocrQueue.retry(p, viewModel.defaultOcrScript.value)
                                viewModel.showMessage("Re-running OCR...")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-run OCR")
                    }

                    // Share Text
                    IconButton(
                        onClick = {
                            viewModel.pdfTools.shareText(editedTextState, "Share Page Text")
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Text")
                    }
                }
            )
        },
        bottomBar = {
            if (selectedTabIndex == 1) {
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val clip = ClipData.newPlainText("OCR Text", editedTextState)
                                clipboardManager.setPrimaryClip(clip)
                                viewModel.showMessage("Copied page text to clipboard")
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy All")
                        }

                        Button(
                            onClick = {
                                isSaving = true
                                coroutineScope.launch {
                                    val current = ocrResult ?: OcrResult(pageId = pageId)
                                    viewModel.repository.saveOcrResult(
                                        current.copy(
                                            editedText = editedTextState,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                    isSaving = false
                                    viewModel.showMessage("Saved corrected text")
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.testTag("save_ocr_text_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Save Changes")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Visual Highlights") },
                    icon = { Icon(Icons.Default.Highlight, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Editable Text") },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
            }

            if (selectedTabIndex == 0) {
                // Visual Highlight Tab
                VisualHighlightTab(
                    imagePath = page?.let { it.processedPath.ifBlank { it.croppedPath } } ?: "",
                    docResult = parsedDocResult,
                    selectedWord = selectedWord,
                    onSelectWord = { word ->
                        selectedWord = word
                        if (word != null) {
                            val clip = ClipData.newPlainText("Selected Word", word.text)
                            clipboardManager.setPrimaryClip(clip)
                            viewModel.showMessage("Copied \"${word.text}\"")
                        }
                    }
                )
            } else {
                // Editable Text Tab
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (ocrResult?.status == OcrStatus.RUNNING.name) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Recognizing text...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    OutlinedTextField(
                        value = editedTextState,
                        onValueChange = { editedTextState = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        placeholder = { Text("Recognized text will appear here. You can edit and correct any words.") }
                    )
                }
            }
        }
    }
}

@Composable
fun VisualHighlightTab(
    imagePath: String,
    docResult: OcrDocumentResult,
    selectedWord: OcrWordBox?,
    onSelectWord: (OcrWordBox?) -> Unit
) {
    val file = File(imagePath)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    var loadedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(imagePath) {
        if (file.exists()) {
            loadedBitmap = android.graphics.BitmapFactory.decodeFile(imagePath)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = loadedBitmap
        if (bmp != null) {
            val composeBmp = remember(bmp) { bmp.asImageBitmap() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .onGloballyPositioned { canvasSize = it.size }
                    .pointerInput(docResult, canvasSize) {
                        detectTapGestures { offset ->
                            if (canvasSize.width <= 0 || canvasSize.height <= 0) return@detectTapGestures
                            val normX = offset.x / canvasSize.width
                            val normY = offset.y / canvasSize.height

                            // Find word containing point
                            var matched: OcrWordBox? = null
                            for (block in docResult.blocks) {
                                for (line in block.lines) {
                                    for (w in line.words) {
                                        if (normX in w.left..w.right && normY in w.top..w.bottom) {
                                            matched = w
                                            break
                                        }
                                    }
                                    if (matched != null) break
                                }
                                if (matched != null) break
                            }
                            onSelectWord(matched)
                        }
                    }
            ) {
                drawImage(
                    image = composeBmp,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )

                // Draw word highlight bounding boxes
                for (block in docResult.blocks) {
                    for (line in block.lines) {
                        for (w in line.words) {
                            val rect = Rect(
                                left = w.left * size.width,
                                top = w.top * size.height,
                                right = w.right * size.width,
                                bottom = w.bottom * size.height
                            )

                            val isSelected = selectedWord?.text == w.text &&
                                    selectedWord?.left == w.left &&
                                    selectedWord?.top == w.top

                            if (isSelected) {
                                drawRect(
                                    color = Color(0xFF10B981).copy(alpha = 0.45f),
                                    topLeft = Offset(rect.left, rect.top),
                                    size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
                                )
                                drawRect(
                                    color = Color(0xFF10B981),
                                    topLeft = Offset(rect.left, rect.top),
                                    size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            } else {
                                drawRect(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.22f),
                                    topLeft = Offset(rect.left, rect.top),
                                    size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
                                )
                                drawRect(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.6f),
                                    topLeft = Offset(rect.left, rect.top),
                                    size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }

            // Word indicator bar at bottom
            selectedWord?.let { w ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Selected: \"${w.text}\" (Copied)", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}
