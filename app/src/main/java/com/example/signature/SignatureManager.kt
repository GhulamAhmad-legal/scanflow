package com.example.signature

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class SignatureManager(private val context: Context) {

    private val sigDir: File by lazy {
        File(context.filesDir, "signatures").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun saveSignatureBitmap(bitmap: Bitmap, name: String = "Signature"): String = withContext(Dispatchers.IO) {
        val cropped = cropTightToInk(bitmap)
        val fileName = "sig_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.png"
        val file = File(sigDir, fileName)
        FileOutputStream(file).use { out ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file.absolutePath
    }

    fun cropTightToInk(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        var foundInk = false

        for (y in 0 until h) {
            val yOffset = y * w
            for (x in 0 until w) {
                val pixel = pixels[yOffset + x]
                val alpha = (pixel shr 24) and 0xFF
                if (alpha > 20) { // Non-transparent pixel
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                    foundInk = true
                }
            }
        }

        if (!foundInk) return bitmap

        val pad = 12
        minX = max(0, minX - pad)
        minY = max(0, minY - pad)
        maxX = min(w, maxX + pad)
        maxY = min(h, maxY + pad)

        val cropW = max(1, maxX - minX)
        val cropH = max(1, maxY - minY)

        return Bitmap.createBitmap(bitmap, minX, minY, cropW, cropH)
    }

    suspend fun extractSignatureFromPaper(
        paperBitmap: Bitmap,
        threshold: Float = 0.65f // 0.0 to 1.0 threshold sensitivity
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = paperBitmap.width
        val h = paperBitmap.height
        val pixels = IntArray(w * h)
        paperBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val outPixels = IntArray(w * h)
        val thresholdVal = (threshold * 255).toInt()

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            // Perceived luminance
            val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()

            if (lum > thresholdVal) {
                // Background paper -> pure transparent
                outPixels[i] = 0x00000000
            } else {
                // Ink stroke -> smooth alpha transition & dark ink
                val inkAlpha = ((thresholdVal - lum) * 255 / thresholdVal.coerceAtLeast(1)).coerceIn(160, 255)
                outPixels[i] = (inkAlpha shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        cropTightToInk(result)
    }
}
