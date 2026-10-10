package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.SignatureItem
import com.example.signature.SignatureManager
import com.example.ui.ScanViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SignaturePickerModal(
    viewModel: ScanViewModel,
    onDismiss: () -> Unit,
    onSelectSignature: (SignatureItem, String?, String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val allSignatures by viewModel.repository.allSignatures.collectAsStateWithLifecycle(emptyList())

    var showPadDialog by remember { mutableStateOf(false) }
    var showScanDialog by remember { mutableStateOf(false) }

    var stampDateChecked by remember { mutableStateOf(false) }
    var stampTextChecked by remember { mutableStateOf(false) }
    var stampCustomText by remember { mutableStateOf("APPROVED") }

    val currentDateStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signature Library") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // Actions: Draw New, Scan Paper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showPadDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Draw")
                    }

                    OutlinedButton(
                        onClick = { showScanDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan Paper")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Stamp Options
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = stampDateChecked,
                        onCheckedChange = { stampDateChecked = it }
                    )
                    Text("Stamp Date ($currentDateStr)", style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = stampTextChecked,
                        onCheckedChange = { stampTextChecked = it }
                    )
                    OutlinedTextField(
                        value = stampCustomText,
                        onValueChange = { stampCustomText = it },
                        label = { Text("Stamp Label") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = stampTextChecked
                    )
                }

                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))

                Text("Saved Signatures", style = MaterialTheme.typography.labelLarge)

                if (allSignatures.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No saved signatures yet. Tap 'Draw' or 'Scan Paper'.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(allSignatures) { sig ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onSelectSignature(
                                            sig,
                                            if (stampDateChecked) currentDateStr else null,
                                            if (stampTextChecked) stampCustomText else null
                                        )
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp, 36.dp)
                                                .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (File(sig.path).exists()) {
                                                AsyncImage(
                                                    model = File(sig.path),
                                                    contentDescription = sig.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(sig.name, fontWeight = FontWeight.Medium)
                                    }

                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                viewModel.repository.deleteSignature(sig)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    if (showPadDialog) {
        SignaturePadDialog(
            viewModel = viewModel,
            onDismiss = { showPadDialog = false },
            onSaved = { showPadDialog = false }
        )
    }

    if (showScanDialog) {
        SignatureScanDialog(
            viewModel = viewModel,
            onDismiss = { showScanDialog = false },
            onSaved = { showScanDialog = false }
        )
    }
}

@Composable
fun SignaturePadDialog(
    viewModel: ScanViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sigManager = remember { SignatureManager(context) }

    var strokeList by remember { mutableStateOf<List<List<PointF>>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<PointF>>(emptyList()) }
    var selectedColor by remember { mutableStateOf("#000000") }
    var penWidth by remember { mutableFloatStateOf(6f) }
    var sigName by remember { mutableStateOf("My Signature") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw Signature") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = sigName,
                    onValueChange = { sigName = it },
                    label = { Text("Signature Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Interactive Drawing Canvas with Bézier smoothing
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .border(1.dp, androidx.compose.ui.graphics.Color.LightGray, RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(PointF(offset.x, offset.y))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + PointF(change.position.x, change.position.y)
                                },
                                onDragEnd = {
                                    if (currentStroke.size > 1) {
                                        strokeList = strokeList + listOf(currentStroke)
                                    }
                                    currentStroke = emptyList()
                                },
                                onDragCancel = { currentStroke = emptyList() }
                            )
                        }
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val drawStroke: (List<PointF>) -> Unit = { pts ->
                            if (pts.size > 1) {
                                val p = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(pts[0].x, pts[0].y)
                                    for (i in 1 until pts.size) {
                                        val prev = pts[i - 1]
                                        val curr = pts[i]
                                        val midX = (prev.x + curr.x) / 2f
                                        val midY = (prev.y + curr.y) / 2f
                                        quadraticTo(prev.x, prev.y, midX, midY)
                                    }
                                    lineTo(pts.last().x, pts.last().y)
                                }
                                drawPath(
                                    path = p,
                                    color = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(selectedColor)),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = penWidth,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        strokeList.forEach { drawStroke(it) }
                        if (currentStroke.size > 1) drawStroke(currentStroke)
                    }

                    // Watermark hint inside pad
                    if (strokeList.isEmpty() && currentStroke.isEmpty()) {
                        Text(
                            "Sign here with your finger",
                            color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Pen Controls: Color & Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#000000", "#1E40AF", "#DC2626").forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)))
                                    .border(
                                        width = if (selectedColor == hex) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = hex }
                            )
                        }
                    }

                    Row {
                        TextButton(
                            onClick = {
                                if (strokeList.isNotEmpty()) strokeList = strokeList.dropLast(1)
                            },
                            enabled = strokeList.isNotEmpty()
                        ) { Text("Undo") }

                        TextButton(onClick = { strokeList = emptyList() }) { Text("Clear") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (strokeList.isNotEmpty()) {
                        coroutineScope.launch {
                            val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.parseColor(selectedColor)
                                strokeWidth = penWidth * 1.5f
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                            }

                            for (stroke in strokeList) {
                                if (stroke.size > 1) {
                                    val path = Path().apply {
                                        moveTo(stroke[0].x * (800f / 400f), stroke[0].y * (400f / 200f))
                                        for (i in 1 until stroke.size) {
                                            val prev = stroke[i - 1]
                                            val curr = stroke[i]
                                            val midX = ((prev.x + curr.x) / 2f) * (800f / 400f)
                                            val midY = ((prev.y + curr.y) / 2f) * (400f / 200f)
                                            quadTo(prev.x * 2f, prev.y * 2f, midX, midY)
                                        }
                                        lineTo(stroke.last().x * 2f, stroke.last().y * 2f)
                                    }
                                    canvas.drawPath(path, paint)
                                }
                            }

                            val path = sigManager.saveSignatureBitmap(bitmap, sigName)
                            viewModel.repository.saveSignature(
                                SignatureItem(name = sigName, path = path, type = "drawn")
                            )
                            onSaved()
                        }
                    }
                },
                enabled = strokeList.isNotEmpty()
            ) { Text("Save Signature") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SignatureScanDialog(
    viewModel: ScanViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sigManager = remember { SignatureManager(context) }

    var paperBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var thresholdSensitivity by remember { mutableFloatStateOf(0.68f) }
    var sigName by remember { mutableStateOf("Scanned Signature") }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val path = viewModel.fileManager.copyUriToInternal(uri)
                val bmp = viewModel.fileManager.loadBitmap(path, 1200)
                paperBitmap = bmp
            }
        }
    }

    LaunchedEffect(paperBitmap, thresholdSensitivity) {
        paperBitmap?.let { bmp ->
            extractedBitmap = sigManager.extractSignatureFromPaper(bmp, thresholdSensitivity)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Signature from Paper") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = sigName,
                    onValueChange = { sigName = it },
                    label = { Text("Signature Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                if (paperBitmap == null) {
                    Button(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Select Signature Photo")
                    }
                } else {
                    Text("Threshold Sensitivity", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = thresholdSensitivity,
                        onValueChange = { thresholdSensitivity = it },
                        valueRange = 0.35f..0.90f
                    )

                    Spacer(Modifier.height(6.dp))

                    Text("Transparent Preview:", style = MaterialTheme.typography.labelSmall)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFFE2E8F0)), // Checkerboard / light gray background
                        contentAlignment = Alignment.Center
                    ) {
                        extractedBitmap?.let { ext ->
                            Image(
                                bitmap = ext.asImageBitmap(),
                                contentDescription = "Extracted Signature",
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    extractedBitmap?.let { bmp ->
                        coroutineScope.launch {
                            val path = sigManager.saveSignatureBitmap(bmp, sigName)
                            viewModel.repository.saveSignature(
                                SignatureItem(name = sigName, path = path, type = "scanned")
                            )
                            onSaved()
                        }
                    }
                },
                enabled = extractedBitmap != null
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
