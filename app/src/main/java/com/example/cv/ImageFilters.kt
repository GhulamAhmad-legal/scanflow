package com.example.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.example.data.model.FilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class ImageFilters {

    suspend fun applyFilter(
        source: Bitmap,
        filterType: FilterType,
        brightness: Float = 0f,
        contrast: Float = 1f,
        sharpness: Float = 0f,
        rotation: Int = 0
    ): Bitmap = withContext(Dispatchers.Default) {
        var result = when (filterType) {
            FilterType.ORIGINAL -> source.copy(Bitmap.Config.ARGB_8888, true)
            FilterType.MAGIC_COLOR -> applyMagicColor(source)
            FilterType.BLACK_WHITE -> applyAdaptiveThreshold(source)
            FilterType.GRAYSCALE -> applyGrayscale(source)
            FilterType.LIGHTEN -> applyLighten(source)
        }

        // Apply slider adjustments if any
        if (brightness != 0f || contrast != 1f) {
            result = applyBrightnessContrast(result, brightness, contrast)
        }

        if (sharpness > 0.05f) {
            result = applySharpness(result, sharpness)
        }

        if (rotation != 0) {
            result = rotateBitmap(result, rotation)
        }

        result
    }

    private fun applyMagicColor(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // Downscale to estimate background illumination rapidly
        val downscaleFactor = max(1, max(w, h) / 120)
        val smallW = max(1, w / downscaleFactor)
        val smallH = max(1, h / downscaleFactor)
        val smallBitmap = Bitmap.createScaledBitmap(src, smallW, smallH, true)

        val smallPixels = IntArray(smallW * smallH)
        smallBitmap.getPixels(smallPixels, 0, smallW, 0, 0, smallW, smallH)

        // Large blur on downscaled image estimates smooth background illumination
        val bgR = IntArray(smallW * smallH)
        val bgG = IntArray(smallW * smallH)
        val bgB = IntArray(smallW * smallH)

        for (i in smallPixels.indices) {
            val c = smallPixels[i]
            bgR[i] = (c shr 16) and 0xFF
            bgG[i] = (c shr 8) and 0xFF
            bgB[i] = c and 0xFF
        }

        val blurredR = fastBoxBlur(bgR, smallW, smallH, radius = 5)
        val blurredG = fastBoxBlur(bgG, smallW, smallH, radius = 5)
        val blurredB = fastBoxBlur(bgB, smallW, smallH, radius = 5)

        // Rescale background back to full resolution
        val bgBitmap = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
        val bgPixelsFull = IntArray(smallW * smallH)
        for (i in smallPixels.indices) {
            bgPixelsFull[i] = (0xFF shl 24) or (blurredR[i] shl 16) or (blurredG[i] shl 8) or blurredB[i]
        }
        bgBitmap.setPixels(bgPixelsFull, 0, smallW, 0, 0, smallW, smallH)
        val bgScaled = Bitmap.createScaledBitmap(bgBitmap, w, h, true)
        val bgFull = IntArray(w * h)
        bgScaled.getPixels(bgFull, 0, w, 0, 0, w, h)

        // Divide source by background to remove shadows, then stretch contrast
        val outPixels = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            val bgC = bgFull[i]
            val br = max(1, (bgC shr 16) and 0xFF)
            val bg = max(1, (bgC shr 8) and 0xFF)
            val bb = max(1, bgC and 0xFF)

            // Division normalization
            var nr = (r * 255) / br
            var ng = (g * 255) / bg
            var nb = (b * 255) / bb

            // Contrast stretch
            nr = ((nr - 15) * 255 / 235).coerceIn(0, 255)
            ng = ((ng - 15) * 255 / 235).coerceIn(0, 255)
            nb = ((nb - 15) * 255 / 235).coerceIn(0, 255)

            // Slight saturation boost for ink/stamps
            val avg = (nr + ng + nb) / 3
            val satBoost = 1.25f
            val finalR = (avg + (nr - avg) * satBoost).roundToInt().coerceIn(0, 255)
            val finalG = (avg + (ng - avg) * satBoost).roundToInt().coerceIn(0, 255)
            val finalB = (avg + (nb - avg) * satBoost).roundToInt().coerceIn(0, 255)

            outPixels[i] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun applyAdaptiveThreshold(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        }

        // Integral image for fast O(1) local mean adaptive threshold
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            val yOffset = y * w
            val intRowOffset = (y + 1) * (w + 1)
            val intPrevRowOffset = y * (w + 1)
            for (x in 0 until w) {
                rowSum += gray[yOffset + x]
                integral[intRowOffset + x + 1] = integral[intPrevRowOffset + x + 1] + rowSum
            }
        }

        val outPixels = IntArray(w * h)
        val s = max(5, min(w, h) / 16) // block size
        val cOffset = 8 // threshold offset

        for (y in 0 until h) {
            val y1 = max(0, y - s)
            val y2 = min(h - 1, y + s)
            val yOffset = y * w

            for (x in 0 until w) {
                val x1 = max(0, x - s)
                val x2 = min(w - 1, x + s)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                val sum = integral[(y2 + 1) * (w + 1) + (x2 + 1)] -
                        integral[y1 * (w + 1) + (x2 + 1)] -
                        integral[(y2 + 1) * (w + 1) + x1] +
                        integral[y1 * (w + 1) + x1]

                val threshold = (sum / count) - cOffset
                val isText = gray[yOffset + x] < threshold
                val color = if (isText) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                outPixels[yOffset + x] = color
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun applyGrayscale(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)

        // Stretch contrast slightly for crisp reading
        return applyBrightnessContrast(result, brightness = 5f, contrast = 1.2f)
    }

    private fun applyLighten(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val gamma = 0.72f // Gamma boost to lift midtones/shadows
        val invGamma = 1.0f / gamma
        val lut = IntArray(256) { i ->
            val norm = i / 255.0f
            (norm.pow(invGamma) * 255.0f).roundToInt().coerceIn(0, 255)
        }

        val outPixels = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = lut[(c shr 16) and 0xFF]
            val g = lut[(c shr 8) and 0xFF]
            val b = lut[c and 0xFF]
            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun applyBrightnessContrast(src: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun applySharpness(src: Bitmap, sharpness: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val outPixels = IntArray(w * h)
        val weight = sharpness * 0.7f

        // 3x3 Laplacian unsharp mask
        for (y in 1 until h - 1) {
            val yOffset = y * w
            for (x in 1 until w - 1) {
                val center = pixels[yOffset + x]
                val cr = (center shr 16) and 0xFF
                val cg = (center shr 8) and 0xFF
                val cb = center and 0xFF

                val top = pixels[yOffset - w + x]
                val bottom = pixels[yOffset + w + x]
                val left = pixels[yOffset + x - 1]
                val right = pixels[yOffset + x + 1]

                val tr = (top shr 16) and 0xFF
                val br = (bottom shr 16) and 0xFF
                val lr = (left shr 16) and 0xFF
                val rr = (right shr 16) and 0xFF

                val tg = (top shr 8) and 0xFF
                val bg = (bottom shr 8) and 0xFF
                val lg = (left shr 8) and 0xFF
                val rg = (right shr 8) and 0xFF

                val tb = top and 0xFF
                val bb = bottom and 0xFF
                val lb = left and 0xFF
                val rb = right and 0xFF

                val sharpR = (cr + weight * (4 * cr - tr - br - lr - rr)).roundToInt().coerceIn(0, 255)
                val sharpG = (cg + weight * (4 * cg - tg - bg - lg - rg)).roundToInt().coerceIn(0, 255)
                val sharpB = (cb + weight * (4 * cb - tb - bb - lb - rb)).roundToInt().coerceIn(0, 255)

                outPixels[yOffset + x] = (0xFF shl 24) or (sharpR shl 16) or (sharpG shl 8) or sharpB
            }
        }

        // Keep borders
        for (x in 0 until w) {
            outPixels[x] = pixels[x]
            outPixels[(h - 1) * w + x] = pixels[(h - 1) * w + x]
        }
        for (y in 0 until h) {
            outPixels[y * w] = pixels[y * w]
            outPixels[y * w + (w - 1)] = pixels[y * w + (w - 1)]
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun fastBoxBlur(input: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val output = IntArray(w * h)
        val temp = IntArray(w * h)

        for (y in 0 until h) {
            val yOffset = y * w
            for (x in 0 until w) {
                var sum = 0
                var count = 0
                for (kx in -radius..radius) {
                    val px = x + kx
                    if (px in 0 until w) {
                        sum += input[yOffset + px]
                        count++
                    }
                }
                temp[yOffset + x] = sum / count
            }
        }

        for (y in 0 until h) {
            val yOffset = y * w
            for (x in 0 until w) {
                var sum = 0
                var count = 0
                for (ky in -radius..radius) {
                    val py = y + ky
                    if (py in 0 until h) {
                        sum += temp[py * w + x]
                        count++
                    }
                }
                output[yOffset + x] = sum / count
            }
        }

        return output
    }
}
