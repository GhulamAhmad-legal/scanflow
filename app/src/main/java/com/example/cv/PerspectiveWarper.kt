package com.example.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PerspectiveWarper {

    suspend fun warp(source: Bitmap, quad: Quad): Bitmap = withContext(Dispatchers.Default) {
        val tl = quad.topLeft
        val tr = quad.topRight
        val br = quad.bottomRight
        val bl = quad.bottomLeft

        // Calculate target dimensions from corner distances
        val topWidth = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble()).toFloat()
        val bottomWidth = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble()).toFloat()
        val leftHeight = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble()).toFloat()
        val rightHeight = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble()).toFloat()

        var targetWidth = max(topWidth, bottomWidth).roundToInt()
        var targetHeight = max(leftHeight, rightHeight).roundToInt()

        // Sanity constraints to prevent OOM
        targetWidth = max(100, min(targetWidth, 3200))
        targetHeight = max(100, min(targetHeight, 4200))

        val srcPoints = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        // Compute forward perspective matrix from source quad to target rectangle
        val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        if (success) {
            val canvas = Canvas(resultBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            canvas.drawColor(Color.WHITE)

            // Draw transformed bitmap
            canvas.drawBitmap(source, matrix, paint)
        } else {
            // Fallback: simple crop bounding box
            val minX = max(0, min(min(tl.x, tr.x), min(bl.x, br.x)).roundToInt())
            val minY = max(0, min(min(tl.y, tr.y), min(bl.y, br.y)).roundToInt())
            val maxX = min(source.width, max(max(tl.x, tr.x), max(bl.x, br.x)).roundToInt())
            val maxY = min(source.height, max(max(tl.y, tr.y), max(bl.y, br.y)).roundToInt())
            val cropW = max(10, maxX - minX)
            val cropH = max(10, maxY - minY)
            val cropped = Bitmap.createBitmap(source, minX, minY, cropW, cropH)
            return@withContext Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        }

        resultBitmap
    }
}
