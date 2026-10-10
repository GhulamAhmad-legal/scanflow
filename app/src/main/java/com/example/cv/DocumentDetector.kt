package com.example.cv

import android.graphics.Bitmap
import android.graphics.PointF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class DocumentDetector {

    private var previousQuad: Quad? = null
    private var stableFrameCount = 0
    private var lastStableTimestamp = 0L

    fun reset() {
        previousQuad = null
        stableFrameCount = 0
        lastStableTimestamp = 0L
    }

    suspend fun detectDocument(
        bitmap: Bitmap,
        targetWidth: Int = 480
    ): DetectionResult = withContext(Dispatchers.Default) {
        val origW = bitmap.width.toFloat()
        val origH = bitmap.height.toFloat()

        // Downscale for fast real-time analysis
        val scale = targetWidth.toFloat() / origW
        val targetHeight = (origH * scale).roundToInt()
        val smallBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

        val w = smallBitmap.width
        val h = smallBitmap.height
        val pixels = IntArray(w * h)
        smallBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 1. Grayscale & fast 5x5 Gaussian blur approximation
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        }

        // Fast horizontal + vertical box blur passes for speed & noise suppression
        val blurred = boxBlur(gray, w, h, radius = 2)

        // 2. Sobel Edge Magnitude & Gradient direction
        val edges = ByteArray(w * h)
        val thresholdLow = 28
        val thresholdHigh = 75

        for (y in 1 until h - 1) {
            val yOffset = y * w
            for (x in 1 until w - 1) {
                val gx = (-blurred[yOffset - w + x - 1] + blurred[yOffset - w + x + 1]
                        - 2 * blurred[yOffset + x - 1] + 2 * blurred[yOffset + x + 1]
                        - blurred[yOffset + w + x - 1] + blurred[yOffset + w + x + 1])
                val gy = (blurred[yOffset - w + x - 1] + 2 * blurred[yOffset - w + x] + blurred[yOffset - w + x + 1]
                        - blurred[yOffset + w + x - 1] - 2 * blurred[yOffset + w + x] - blurred[yOffset + w + x + 1])
                val mag = abs(gx) + abs(gy)
                if (mag > thresholdHigh) {
                    edges[yOffset + x] = 2 // Strong edge
                } else if (mag > thresholdLow) {
                    edges[yOffset + x] = 1 // Weak edge
                }
            }
        }

        // 3. Find rectangular document boundary via radial ray-casting from image center
        // This is robust against background clutter and fast (sub-10ms)
        val cx = w / 2f
        val cy = h / 2f
        val rayCount = 36
        val edgePoints = mutableListOf<PointF>()

        for (i in 0 until rayCount) {
            val angle = (i * 2.0 * Math.PI / rayCount).toFloat()
            val cosA = kotlin.math.cos(angle.toDouble()).toFloat()
            val sinA = kotlin.math.sin(angle.toDouble()).toFloat()
            val maxDist = min(w / 2f, h / 2f) * 0.95f

            var foundDist = -1f
            // Search outwards from center towards periphery
            var d = 25f
            while (d < maxDist) {
                val px = (cx + d * cosA).roundToInt()
                val py = (cy + d * sinA).roundToInt()
                if (px in 1 until w - 1 && py in 1 until h - 1) {
                    if (edges[py * w + px].toInt() > 0) {
                        foundDist = d
                    }
                }
                d += 3f
            }

            if (foundDist > 30f) {
                edgePoints.add(PointF(cx + foundDist * cosA, cy + foundDist * sinA))
            }
        }

        val detectedQuad: Quad
        val isFallback: Boolean

        if (edgePoints.size >= 8) {
            // Find 4 extreme corners along 4 quadrants (TL: x<cx, y<cy; TR: x>cx, y<cy; BR: x>cx, y>cy; BL: x<cx, y>cy)
            val tlCandidates = edgePoints.filter { it.x <= cx && it.y <= cy }
            val trCandidates = edgePoints.filter { it.x >= cx && it.y <= cy }
            val brCandidates = edgePoints.filter { it.x >= cx && it.y >= cy }
            val blCandidates = edgePoints.filter { it.x <= cx && it.y >= cy }

            if (tlCandidates.isNotEmpty() && trCandidates.isNotEmpty() && brCandidates.isNotEmpty() && blCandidates.isNotEmpty()) {
                val tl = tlCandidates.minByOrNull { it.x + it.y } ?: PointF(w * 0.1f, h * 0.1f)
                val tr = trCandidates.maxByOrNull { it.x - it.y } ?: PointF(w * 0.9f, h * 0.1f)
                val br = brCandidates.maxByOrNull { it.x + it.y } ?: PointF(w * 0.9f, h * 0.9f)
                val bl = blCandidates.minByOrNull { it.x - it.y } ?: PointF(w * 0.1f, h * 0.9f)

                // Scale detected points back to original image coordinates
                val invScale = 1f / scale
                val rawQuad = Quad(
                    topLeft = PointF(tl.x * invScale, tl.y * invScale),
                    topRight = PointF(tr.x * invScale, tr.y * invScale),
                    bottomRight = PointF(br.x * invScale, br.y * invScale),
                    bottomLeft = PointF(bl.x * invScale, bl.y * invScale)
                )

                // Sanity check area: must cover at least 15% of frame
                val quadArea = computeQuadArea(rawQuad)
                val frameArea = origW * origH
                if (quadArea > frameArea * 0.15f) {
                    detectedQuad = rawQuad
                    isFallback = false
                } else {
                    detectedQuad = Quad.defaultFullFrame(origW, origH)
                    isFallback = true
                }
            } else {
                detectedQuad = Quad.defaultFullFrame(origW, origH)
                isFallback = true
            }
        } else {
            detectedQuad = Quad.defaultFullFrame(origW, origH)
            isFallback = true
        }

        // Temporal smoothing
        val prev = previousQuad
        val smoothedQuad = if (prev != null) {
            prev.lerp(detectedQuad, 0.45f)
        } else {
            detectedQuad
        }
        previousQuad = smoothedQuad

        // Stability tracking for auto-capture
        val isStable = if (prev != null && !isFallback) {
            val distDiff = prev.isCloseTo(smoothedQuad, threshold = origW * 0.025f)
            if (distDiff) {
                stableFrameCount++
            } else {
                stableFrameCount = 0
            }
            // ~5-8 fps -> 6 stable frames = ~1 second
            stableFrameCount >= 5
        } else {
            stableFrameCount = 0
            false
        }

        DetectionResult(
            quad = smoothedQuad,
            isDocumentDetected = !isFallback,
            isStable = isStable
        )
    }

    private fun boxBlur(input: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val output = IntArray(w * h)
        val temp = IntArray(w * h)

        // Horizontal blur
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

        // Vertical blur
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

    private fun computeQuadArea(q: Quad): Float {
        // Shoelace formula
        return 0.5f * abs(
            (q.topLeft.x * q.topRight.y - q.topRight.x * q.topLeft.y) +
            (q.topRight.x * q.bottomRight.y - q.bottomRight.x * q.topRight.y) +
            (q.bottomRight.x * q.bottomLeft.y - q.bottomLeft.x * q.bottomRight.y) +
            (q.bottomLeft.x * q.topLeft.y - q.topLeft.x * q.bottomLeft.y)
        )
    }
}

data class DetectionResult(
    val quad: Quad,
    val isDocumentDetected: Boolean,
    val isStable: Boolean
)
