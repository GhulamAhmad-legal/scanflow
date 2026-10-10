package com.example.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.example.annotate.AnnotationItem
import com.example.annotate.AnnotationType
import com.example.signature.PlacedSignature
import com.example.watermark.WatermarkConfig
import com.example.watermark.WatermarkLayout
import com.example.watermark.WatermarkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class PageRenderer(private val context: Context) {

    suspend fun renderCompositeBitmap(
        baseBitmap: Bitmap,
        annotations: List<AnnotationItem> = emptyList(),
        placedSignatures: List<PlacedSignature> = emptyList(),
        watermark: WatermarkConfig = WatermarkConfig(),
        targetWidth: Int = baseBitmap.width,
        targetHeight: Int = baseBitmap.height
    ): Bitmap = withContext(Dispatchers.Default) {
        val composite = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composite)
        renderOnCanvas(canvas, baseBitmap, annotations, placedSignatures, watermark, targetWidth.toFloat(), targetHeight.toFloat())
        composite
    }

    fun renderOnCanvas(
        canvas: Canvas,
        baseBitmap: Bitmap,
        annotations: List<AnnotationItem>,
        placedSignatures: List<PlacedSignature>,
        watermark: WatermarkConfig,
        canvasWidth: Float,
        canvasHeight: Float
    ) {
        // 1. Draw Base Scanned Image
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val destRect = RectF(0f, 0f, canvasWidth, canvasHeight)
        canvas.drawBitmap(baseBitmap, null, destRect, basePaint)

        // 2. Draw Annotations
        for (item in annotations) {
            when (item) {
                is AnnotationItem.FreehandStroke -> drawFreehandStroke(canvas, item, canvasWidth, canvasHeight)
                is AnnotationItem.ShapeItem -> drawShape(canvas, item, canvasWidth, canvasHeight)
                is AnnotationItem.TextItem -> drawTextItem(canvas, item, canvasWidth, canvasHeight)
            }
        }

        // 3. Draw Placed Signatures
        for (sig in placedSignatures) {
            drawPlacedSignature(canvas, sig, canvasWidth, canvasHeight)
        }

        // 4. Draw Watermark
        if (watermark.enabled) {
            drawWatermark(canvas, watermark, canvasWidth, canvasHeight)
        }
    }

    private fun drawFreehandStroke(canvas: Canvas, stroke: AnnotationItem.FreehandStroke, w: Float, h: Float) {
        if (stroke.points.size < 2) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parseColorSafely(stroke.colorHex)
            strokeWidth = stroke.strokeWidth * (w / 800f).coerceAtLeast(1f)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (stroke.isHighlighter) {
                alpha = 110 // ~40% opacity for highlighter overlay
            }
        }

        val path = Path()
        val p0 = stroke.points[0]
        path.moveTo(p0.x * w, p0.y * h)

        for (i in 1 until stroke.points.size) {
            val prev = stroke.points[i - 1]
            val curr = stroke.points[i]
            val midX = (prev.x + curr.x) / 2f * w
            val midY = (prev.y + curr.y) / 2f * h
            path.quadTo(prev.x * w, prev.y * h, midX, midY)
        }
        val last = stroke.points.last()
        path.lineTo(last.x * w, last.y * h)

        canvas.drawPath(path, paint)
    }

    private fun drawShape(canvas: Canvas, shape: AnnotationItem.ShapeItem, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parseColorSafely(shape.colorHex)
            strokeWidth = shape.strokeWidth * (w / 800f).coerceAtLeast(1f)
            style = if (shape.isFilled) Paint.Style.FILL else Paint.Style.STROKE
        }

        val sx = shape.start.x * w
        val sy = shape.start.y * h
        val ex = shape.end.x * w
        val ey = shape.end.y * h

        when (shape.type) {
            AnnotationType.RECTANGLE -> {
                val rect = RectF(minOf(sx, ex), minOf(sy, ey), maxOf(sx, ex), maxOf(sy, ey))
                canvas.drawRect(rect, paint)
            }
            AnnotationType.ELLIPSE -> {
                val rect = RectF(minOf(sx, ex), minOf(sy, ey), maxOf(sx, ex), maxOf(sy, ey))
                canvas.drawOval(rect, paint)
            }
            AnnotationType.LINE -> {
                canvas.drawLine(sx, sy, ex, ey, paint)
            }
            AnnotationType.ARROW -> {
                canvas.drawLine(sx, sy, ex, ey, paint)
                // Draw arrowhead
                val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                val arrowHeadLen = 22f * (w / 800f).coerceAtLeast(1f)
                val arrowAngle = Math.PI / 6

                val x1 = ex - arrowHeadLen * cos(angle - arrowAngle).toFloat()
                val y1 = ey - arrowHeadLen * sin(angle - arrowAngle).toFloat()
                val x2 = ex - arrowHeadLen * cos(angle + arrowAngle).toFloat()
                val y2 = ey - arrowHeadLen * sin(angle + arrowAngle).toFloat()

                val headPath = Path().apply {
                    moveTo(ex, ey)
                    lineTo(x1, y1)
                    lineTo(x2, y2)
                    close()
                }
                val fillPaint = Paint(paint).apply { style = Paint.Style.FILL }
                canvas.drawPath(headPath, fillPaint)
            }
            else -> {}
        }
    }

    private fun drawTextItem(canvas: Canvas, textItem: AnnotationItem.TextItem, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parseColorSafely(textItem.colorHex)
            textSize = textItem.fontSize * (w / 500f).coerceAtLeast(1f)
            typeface = if (textItem.isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val x = textItem.position.x * w
        val y = textItem.position.y * h
        canvas.drawText(textItem.text, x, y, paint)
    }

    private fun drawPlacedSignature(canvas: Canvas, sig: PlacedSignature, w: Float, h: Float) {
        val file = File(sig.path)
        if (!file.exists()) return

        val sigBitmap = BitmapFactory.decodeFile(sig.path) ?: return
        val sigW = sig.normalizedWidth * w
        val sigH = sig.normalizedHeight * h
        val sigLeft = sig.normalizedX * w - sigW / 2f
        val sigTop = sig.normalizedY * h - sigH / 2f

        canvas.save()
        if (sig.rotationDegrees != 0f) {
            canvas.rotate(sig.rotationDegrees, sigLeft + sigW / 2f, sigTop + sigH / 2f)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val destRect = RectF(sigLeft, sigTop, sigLeft + sigW, sigTop + sigH)
        canvas.drawBitmap(sigBitmap, null, destRect, paint)

        // Draw stamp date / text if present
        if (sig.stampDate != null || sig.stampText != null) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = (sigH * 0.16f).coerceAtLeast(14f)
                typeface = Typeface.DEFAULT_BOLD
            }
            val stampStr = listOfNotNull(sig.stampText, sig.stampDate).joinToString(" • ")
            canvas.drawText(stampStr, sigLeft, sigTop + sigH + textPaint.textSize + 4f, textPaint)
        }

        canvas.restore()
    }

    private fun drawWatermark(canvas: Canvas, config: WatermarkConfig, w: Float, h: Float) {
        val alphaInt = (config.opacity.coerceIn(0.05f, 1f) * 255).toInt()

        if (config.type == WatermarkType.TEXT) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = parseColorSafely(config.colorHex)
                alpha = alphaInt
                textSize = config.fontSizeSp * (w / 400f).coerceAtLeast(1f)
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            if (config.layout == WatermarkLayout.SINGLE_CENTER) {
                canvas.save()
                canvas.rotate(config.rotationDegrees, w / 2f, h / 2f)
                canvas.drawText(config.text, w / 2f, h / 2f, paint)
                canvas.restore()
            } else {
                // Tiled diagonal pattern
                val stepX = w / 2.5f
                val stepY = h / 4.0f
                var x = -stepX
                while (x < w + stepX) {
                    var y = -stepY
                    while (y < h + stepY) {
                        canvas.save()
                        canvas.rotate(config.rotationDegrees, x, y)
                        canvas.drawText(config.text, x, y, paint)
                        canvas.restore()
                        y += stepY
                    }
                    x += stepX
                }
            }
        } else if (config.type == WatermarkType.IMAGE && config.imagePath != null) {
            val file = File(config.imagePath)
            if (file.exists()) {
                val logo = BitmapFactory.decodeFile(config.imagePath) ?: return
                val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = alphaInt
                }
                val logoW = w * 0.35f
                val logoH = logoW * (logo.height.toFloat() / logo.width.toFloat())
                val rect = RectF((w - logoW) / 2f, (h - logoH) / 2f, (w + logoW) / 2f, (h + logoH) / 2f)

                canvas.save()
                canvas.rotate(config.rotationDegrees, w / 2f, h / 2f)
                canvas.drawBitmap(logo, null, rect, logoPaint)
                canvas.restore()
            }
        }
    }

    private fun parseColorSafely(hex: String): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            Color.BLACK
        }
    }
}
