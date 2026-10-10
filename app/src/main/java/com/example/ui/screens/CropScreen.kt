package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.cv.Quad
import com.example.ui.Screen
import com.example.ui.ScanViewModel
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    viewModel: ScanViewModel,
    pageId: Long,
    originalBitmap: Bitmap?,
    documentId: Long
) {
    val coroutineScope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf(originalBitmap ?: viewModel.activeCapturedBitmap) }

    LaunchedEffect(pageId) {
        if (bitmap == null) {
            val page = viewModel.repository.getPageById(pageId)
            if (page != null) {
                bitmap = viewModel.fileManager.loadBitmap(page.originalPath)
                viewModel.activeCapturedBitmap = bitmap
            }
        }
    }

    if (bitmap == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentBmp = bitmap!!

    // Quad normalized coordinates (0..1)
    var quadNorm by remember {
        val initial = viewModel.activeInitialQuad
        if (initial != null) {
            mutableStateOf(
                Quad(
                    topLeft = PointF(initial.topLeft.x / currentBmp.width, initial.topLeft.y / currentBmp.height),
                    topRight = PointF(initial.topRight.x / currentBmp.width, initial.topRight.y / currentBmp.height),
                    bottomRight = PointF(initial.bottomRight.x / currentBmp.width, initial.bottomRight.y / currentBmp.height),
                    bottomLeft = PointF(initial.bottomLeft.x / currentBmp.width, initial.bottomLeft.y / currentBmp.height)
                )
            )
        } else {
            mutableStateOf(
                Quad(
                    topLeft = PointF(0.08f, 0.08f),
                    topRight = PointF(0.92f, 0.08f),
                    bottomRight = PointF(0.92f, 0.92f),
                    bottomLeft = PointF(0.08f, 0.92f)
                )
            )
        }
    }

    // Canvas size in pixels
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // Active dragged handle index: 0: TL, 1: TR, 2: BR, 3: BL, 4: TopMid, 5: RightMid, 6: BottomMid, 7: LeftMid
    var draggingHandleIndex by remember { mutableStateOf<Int?>(null) }
    var dragTouchOffset by remember { mutableStateOf(Offset.Zero) }
    var isWarping by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop & Straighten", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                val det = viewModel.detector.detectDocument(currentBmp)
                                quadNorm = Quad(
                                    topLeft = PointF(det.quad.topLeft.x / currentBmp.width, det.quad.topLeft.y / currentBmp.height),
                                    topRight = PointF(det.quad.topRight.x / currentBmp.width, det.quad.topRight.y / currentBmp.height),
                                    bottomRight = PointF(det.quad.bottomRight.x / currentBmp.width, det.quad.bottomRight.y / currentBmp.height),
                                    bottomLeft = PointF(det.quad.bottomLeft.x / currentBmp.width, det.quad.bottomLeft.y / currentBmp.height)
                                )
                            }
                        }
                    ) {
                        Text("Auto")
                    }

                    TextButton(
                        onClick = {
                            quadNorm = Quad(
                                topLeft = PointF(0.05f, 0.05f),
                                topRight = PointF(0.95f, 0.05f),
                                bottomRight = PointF(0.95f, 0.95f),
                                bottomLeft = PointF(0.05f, 0.95f)
                            )
                        }
                    ) {
                        Text("Reset")
                    }
                }
            )
        },
        bottomBar = {
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
                    Text(
                        "Drag corners to align document",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            isWarping = true
                            // Convert normalized quad to full bitmap coordinates
                            val absQuad = Quad(
                                topLeft = PointF(quadNorm.topLeft.x * currentBmp.width, quadNorm.topLeft.y * currentBmp.height),
                                topRight = PointF(quadNorm.topRight.x * currentBmp.width, quadNorm.topRight.y * currentBmp.height),
                                bottomRight = PointF(quadNorm.bottomRight.x * currentBmp.width, quadNorm.bottomRight.y * currentBmp.height),
                                bottomLeft = PointF(quadNorm.bottomLeft.x * currentBmp.width, quadNorm.bottomLeft.y * currentBmp.height)
                            )

                            viewModel.saveWarpedCrop(pageId, absQuad) {
                                isWarping = false
                                viewModel.navigateTo(Screen.Filters(pageId, viewModel.activeCroppedBitmap, documentId))
                            }
                        },
                        enabled = !isWarping,
                        modifier = Modifier.testTag("confirm_crop_button")
                    ) {
                        if (isWarping) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Next")
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
            val composeBmp = remember(currentBmp) { currentBmp.asImageBitmap() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .onGloballyPositioned { coordinates ->
                        canvasSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                    }
                    .pointerInput(canvasSize, quadNorm) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragTouchOffset = offset
                                val handles = getHandlePositions(quadNorm, canvasSize)
                                val hitRadius = 38.dp.toPx()

                                val hitIndex = handles.indexOfFirst {
                                    hypot((it.x - offset.x).toDouble(), (it.y - offset.y).toDouble()) < hitRadius
                                }
                                if (hitIndex != -1) {
                                    draggingHandleIndex = hitIndex
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragTouchOffset = change.position
                                val idx = draggingHandleIndex ?: return@detectDragGestures
                                if (canvasSize.width <= 0 || canvasSize.height <= 0) return@detectDragGestures

                                val dxNorm = dragAmount.x / canvasSize.width
                                val dyNorm = dragAmount.y / canvasSize.height

                                quadNorm = updateQuadHandle(quadNorm, idx, dxNorm, dyNorm)
                            },
                            onDragEnd = {
                                draggingHandleIndex = null
                            },
                            onDragCancel = {
                                draggingHandleIndex = null
                            }
                        )
                    }
            ) {
                if (size.width <= 0 || size.height <= 0) return@Canvas

                // 1. Draw source image fitted in canvas
                drawImage(
                    image = composeBmp,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )

                // 2. Dim outside crop polygon
                val handles = getHandlePositions(quadNorm, size)
                val tl = handles[0]
                val tr = handles[1]
                val br = handles[2]
                val bl = handles[3]

                val quadPath = Path().apply {
                    moveTo(tl.x, tl.y)
                    lineTo(tr.x, tr.y)
                    lineTo(br.x, br.y)
                    lineTo(bl.x, bl.y)
                    close()
                }

                // Semi-transparent overlay outside polygon
                drawRect(color = Color.Black.copy(alpha = 0.38f))

                // Cutout inside
                drawPath(
                    path = quadPath,
                    color = Color.Cyan.copy(alpha = 0.12f)
                )

                // 3. Draw quadrilateral boundary lines
                drawPath(
                    path = quadPath,
                    color = Color(0xFF00E5FF),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // 4. Draw 4 Corner Handles + 4 Midpoint Handles
                handles.forEachIndexed { index, pt ->
                    val isCorner = index < 4
                    val isDragged = draggingHandleIndex == index

                    val handleRadius = if (isCorner) 12.dp.toPx() else 8.dp.toPx()
                    val outerColor = if (isDragged) Color(0xFF10B981) else Color(0xFF00E5FF)

                    drawCircle(
                        color = outerColor,
                        radius = handleRadius,
                        center = pt
                    )
                    drawCircle(
                        color = Color.White,
                        radius = handleRadius * 0.6f,
                        center = pt
                    )
                }

                // 5. Magnifier Loupe when dragging
                val activeIdx = draggingHandleIndex
                if (activeIdx != null) {
                    val activePt = handles[activeIdx]
                    drawLoupe(
                        touchPoint = activePt,
                        sourceBitmap = currentBmp,
                        canvasSize = size
                    )
                }
            }
        }
    }
}

// Compute screen handle positions (0..3 corners, 4..7 midpoints)
private fun getHandlePositions(quadNorm: Quad, size: Size): List<Offset> {
    val tl = Offset(quadNorm.topLeft.x * size.width, quadNorm.topLeft.y * size.height)
    val tr = Offset(quadNorm.topRight.x * size.width, quadNorm.topRight.y * size.height)
    val br = Offset(quadNorm.bottomRight.x * size.width, quadNorm.bottomRight.y * size.height)
    val bl = Offset(quadNorm.bottomLeft.x * size.width, quadNorm.bottomLeft.y * size.height)

    val topMid = Offset((tl.x + tr.x) / 2f, (tl.y + tr.y) / 2f)
    val rightMid = Offset((tr.x + br.x) / 2f, (tr.y + br.y) / 2f)
    val bottomMid = Offset((bl.x + br.x) / 2f, (bl.y + br.y) / 2f)
    val leftMid = Offset((tl.x + bl.x) / 2f, (tl.y + bl.y) / 2f)

    return listOf(tl, tr, br, bl, topMid, rightMid, bottomMid, leftMid)
}

// Update Quad coordinates on handle drag
private fun updateQuadHandle(quad: Quad, index: Int, dx: Float, dy: Float): Quad {
    fun clamp(v: Float) = v.coerceIn(0f, 1f)
    return when (index) {
        0 -> quad.copy(topLeft = PointF(clamp(quad.topLeft.x + dx), clamp(quad.topLeft.y + dy)))
        1 -> quad.copy(topRight = PointF(clamp(quad.topRight.x + dx), clamp(quad.topRight.y + dy)))
        2 -> quad.copy(bottomRight = PointF(clamp(quad.bottomRight.x + dx), clamp(quad.bottomRight.y + dy)))
        3 -> quad.copy(bottomLeft = PointF(clamp(quad.bottomLeft.x + dx), clamp(quad.bottomLeft.y + dy)))
        // Midpoint dragging shifts both adjacent corners
        4 -> quad.copy(
            topLeft = PointF(quad.topLeft.x, clamp(quad.topLeft.y + dy)),
            topRight = PointF(quad.topRight.x, clamp(quad.topRight.y + dy))
        )
        5 -> quad.copy(
            topRight = PointF(clamp(quad.topRight.x + dx), quad.topRight.y),
            bottomRight = PointF(clamp(quad.bottomRight.x + dx), quad.bottomRight.y)
        )
        6 -> quad.copy(
            bottomLeft = PointF(quad.bottomLeft.x, clamp(quad.bottomLeft.y + dy)),
            bottomRight = PointF(quad.bottomRight.x, clamp(quad.bottomRight.y + dy))
        )
        7 -> quad.copy(
            topLeft = PointF(clamp(quad.topLeft.x + dx), quad.topLeft.y),
            bottomLeft = PointF(clamp(quad.bottomLeft.x + dx), quad.bottomLeft.y)
        )
        else -> quad
    }
}

// Floating Magnifier Loupe
private fun DrawScope.drawLoupe(
    touchPoint: Offset,
    sourceBitmap: Bitmap,
    canvasSize: Size
) {
    val loupeRadius = 48.dp.toPx()
    // Position loupe 70dp above touch point (or below if too near top)
    val loupeCenterY = if (touchPoint.y > loupeRadius * 2.2f) {
        touchPoint.y - loupeRadius * 1.8f
    } else {
        touchPoint.y + loupeRadius * 1.8f
    }
    val loupeCenter = Offset(touchPoint.x.coerceIn(loupeRadius, canvasSize.width - loupeRadius), loupeCenterY)

    // Loupe outer shadow & white circle background
    drawCircle(color = Color.Black.copy(alpha = 0.5f), radius = loupeRadius + 5.dp.toPx(), center = loupeCenter)
    drawCircle(color = Color.White, radius = loupeRadius + 3.dp.toPx(), center = loupeCenter)
    drawCircle(color = Color(0xFF00E5FF), radius = loupeRadius, style = Stroke(width = 3.dp.toPx()), center = loupeCenter)

    // Crosshairs in center of loupe
    val chLen = 14.dp.toPx()
    drawLine(
        color = Color(0xFF00E5FF),
        start = Offset(loupeCenter.x - chLen, loupeCenter.y),
        end = Offset(loupeCenter.x + chLen, loupeCenter.y),
        strokeWidth = 2.dp.toPx()
    )
    drawLine(
        color = Color(0xFF00E5FF),
        start = Offset(loupeCenter.x, loupeCenter.y - chLen),
        end = Offset(loupeCenter.x, loupeCenter.y + chLen),
        strokeWidth = 2.dp.toPx()
    )
}
