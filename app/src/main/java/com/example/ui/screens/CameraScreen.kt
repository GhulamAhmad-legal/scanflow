package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cv.Quad
import com.example.idcard.IdCardType
import com.example.ui.Screen
import com.example.ui.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

enum class CameraScanMode {
    SINGLE,
    BATCH,
    ID_CARD
}

@Composable
fun CameraScreen(
    viewModel: ScanViewModel,
    targetDocumentId: Long? = null,
    retakePageIndex: Int? = null,
    initialScanMode: CameraScanMode = CameraScanMode.SINGLE
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedPermanently by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            permissionDeniedPermanently = true
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var scanMode by remember { mutableStateOf(initialScanMode) }
    val autoCaptureEnabled by viewModel.autoCaptureEnabled.collectAsStateWithLifecycle()
    val idCardStep by viewModel.idCardScanStep.collectAsStateWithLifecycle()

    var flashEnabled by remember { mutableStateOf(false) }
    var detectedQuad by remember { mutableStateOf<Quad?>(null) }
    var isDocumentDetected by remember { mutableStateOf(false) }
    var isStable by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importImagesFromUris(uris, targetDocumentId)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Camera Access Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "ScanFlow requires the camera to scan documents with real-time edge detection. No images leave your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (permissionDeniedPermanently) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("grant_camera_button")
                    ) {
                        Text(if (permissionDeniedPermanently) "Open Settings" else "Grant Permission")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.navigateBack() }) {
                        Text("Cancel")
                    }
                }
            }
        }
        return
    }

    val triggerCapture: () -> Unit = {
        if (!isCapturing && imageCapture != null) {
            isCapturing = true
            val capture = imageCapture!!
            val executor = ContextCompat.getMainExecutor(context)

            capture.takePicture(
                executor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val rotationDegrees = image.imageInfo.rotationDegrees
                        val bitmap = imageProxyToBitmap(image, rotationDegrees)
                        image.close()

                        if (scanMode == CameraScanMode.ID_CARD) {
                            viewModel.processIdCardSide(bitmap) { createdDocId ->
                                isCapturing = false
                                viewModel.navigateTo(Screen.PageGrid(createdDocId))
                            }
                            isCapturing = false
                        } else {
                            viewModel.processCapturedImage(bitmap, targetDocumentId) { pageId ->
                                isCapturing = false
                                if (scanMode == CameraScanMode.BATCH) {
                                    viewModel.showMessage("Page added to batch")
                                } else {
                                    viewModel.navigateTo(Screen.Crop(pageId, bitmap, viewModel.activeDocumentId ?: 0))
                                }
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        isCapturing = false
                        viewModel.showMessage("Capture failed: ${exception.message}")
                    }
                }
            )
        }
    }

    // Auto-capture watcher (active only in Single/Batch modes when enabled)
    LaunchedEffect(isStable, autoCaptureEnabled, scanMode) {
        if (isStable && autoCaptureEnabled && scanMode != CameraScanMode.ID_CARD && !isCapturing) {
            triggerCapture()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture

                    var lastAnalyzedTime = 0L
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()

                    val analysisExecutor = Executors.newSingleThreadExecutor()
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        val now = System.currentTimeMillis()
                        if (now - lastAnalyzedTime >= 150) {
                            lastAnalyzedTime = now
                            val frameBitmap = imageProxyToBitmap(imageProxy, imageProxy.imageInfo.rotationDegrees)
                            coroutineScope.launch(Dispatchers.Default) {
                                val result = viewModel.detector.detectDocument(frameBitmap, targetWidth = 480)
                                isDocumentDetected = result.isDocumentDetected
                                isStable = result.isStable
                                detectedQuad = result.quad
                            }
                        }
                        imageProxy.close()
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture,
                            analysis
                        )
                        cameraControl = camera.cameraControl
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Overlay: Document Edge Detection or ID Card Aspect Ratio Guide
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (scanMode == CameraScanMode.ID_CARD) {
                // Draw guided ID Card aspect ratio frame in center of screen
                val guideW = size.width * 0.88f
                // Standard ID Card aspect ratio = 85.6 / 53.98 ≈ 1.585
                val cardRatio = viewModel.selectedIdCardType.widthMm / viewModel.selectedIdCardType.heightMm
                val guideH = guideW / cardRatio
                val left = (size.width - guideW) / 2f
                val top = (size.height - guideH) / 2f - 40.dp.toPx()

                // Dim background outside frame
                drawRect(color = Color.Black.copy(alpha = 0.45f))

                // Cutout clear viewport
                drawRoundRect(
                    color = Color(0xFF00E5FF),
                    topLeft = Offset(left, top),
                    size = Size(guideW, guideH),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )
            } else {
                val quad = detectedQuad
                if (quad != null) {
                    val strokeColor = if (isStable) Color(0xFF10B981) else Color(0xFF00E5FF)
                    val fillAlpha = if (isStable) 0.15f else 0.08f

                    val path = Path().apply {
                        moveTo(quad.topLeft.x, quad.topLeft.y)
                        lineTo(quad.topRight.x, quad.topRight.y)
                        lineTo(quad.bottomRight.x, quad.bottomRight.y)
                        lineTo(quad.bottomLeft.x, quad.bottomLeft.y)
                        close()
                    }

                    drawPath(path, color = strokeColor.copy(alpha = fillAlpha))
                    drawPath(path, color = strokeColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

                    listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft).forEach { pt ->
                        drawCircle(color = strokeColor, radius = 7.dp.toPx(), center = Offset(pt.x, pt.y))
                        drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(pt.x, pt.y))
                    }
                }
            }
        }

        // Top Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    viewModel.resetIdCardScan()
                    viewModel.navigateBack()
                },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (scanMode != CameraScanMode.ID_CARD) {
                    FilterChip(
                        selected = autoCaptureEnabled,
                        onClick = { viewModel.toggleAutoCapture() },
                        label = { Text("Auto", color = Color.White) },
                        leadingIcon = {
                            Icon(
                                if (autoCaptureEnabled) Icons.Default.Bolt else Icons.Default.FlashOff,
                                contentDescription = null,
                                tint = if (autoCaptureEnabled) Color(0xFF00E5FF) else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7).copy(alpha = 0.85f),
                            containerColor = Color.Black.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                }

                IconButton(
                    onClick = {
                        flashEnabled = !flashEnabled
                        cameraControl?.enableTorch(flashEnabled)
                    },
                    modifier = Modifier.background(if (flashEnabled) Color(0xFFF59E0B) else Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash Toggle",
                        tint = Color.White
                    )
                }
            }
        }

        // Status Banner
        if (scanMode == CameraScanMode.ID_CARD) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp)
                    .background(Color(0xFF0284C7).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    if (idCardStep == 0) "Step 1: Fit FRONT side inside frame" else "Step 2: Fit BACK side inside frame",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (isDocumentDetected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp)
                    .background(
                        if (isStable) Color(0xFF10B981).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.65f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    if (isStable) "Document Stable — Auto-Capturing" else "Document Detected — Hold Steady",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.75f))
                .navigationBarsPadding()
                .padding(vertical = 14.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Switcher: Single, Batch, ID Card
            Row(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (scanMode == CameraScanMode.SINGLE) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { scanMode = CameraScanMode.SINGLE }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Single", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (scanMode == CameraScanMode.BATCH) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { scanMode = CameraScanMode.BATCH }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Batch", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (scanMode == CameraScanMode.ID_CARD) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { scanMode = CameraScanMode.ID_CARD }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("ID Card", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Shutter Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Import Gallery", tint = Color.White)
                }

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(
                            width = 4.dp,
                            color = if (isStable || scanMode == CameraScanMode.ID_CARD) Color(0xFF10B981) else Color.White,
                            shape = CircleShape
                        )
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (isCapturing) Color.LightGray else Color.White)
                        .clickable(enabled = !isCapturing) { triggerCapture() }
                        .testTag("capture_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (isStable || scanMode == CameraScanMode.ID_CARD) Color(0xFF10B981) else Color.White)
                        )
                    }
                }

                if (scanMode == CameraScanMode.BATCH && viewModel.activeDocumentId != null) {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.PageGrid(viewModel.activeDocumentId!!)) },
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done Scanning", tint = Color.White)
                    }
                } else {
                    Spacer(Modifier.size(52.dp))
                }
            }
        }
    }
}

private fun imageProxyToBitmap(imageProxy: ImageProxy, rotationDegrees: Int): Bitmap {
    val planes = imageProxy.planes
    val buffer = planes[0].buffer
    val pixelStride = planes[0].pixelStride
    val rowStride = planes[0].rowStride
    val rowPadding = rowStride - pixelStride * imageProxy.width

    val bitmap = Bitmap.createBitmap(
        imageProxy.width + rowPadding / pixelStride,
        imageProxy.height,
        Bitmap.Config.ARGB_8888
    )
    bitmap.copyPixelsFromBuffer(buffer)

    val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, imageProxy.width, imageProxy.height)

    return if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(cleanBitmap, 0, 0, cleanBitmap.width, cleanBitmap.height, matrix, true)
    } else {
        cleanBitmap
    }
}
