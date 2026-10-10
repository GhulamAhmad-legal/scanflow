package com.example.cv

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

data class Quad(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) {
    // 4 Midpoints for intuitive crop handles
    val topMid: PointF
        get() = PointF((topLeft.x + topRight.x) / 2f, (topLeft.y + topRight.y) / 2f)
    val rightMid: PointF
        get() = PointF((topRight.x + bottomRight.x) / 2f, (topRight.y + bottomRight.y) / 2f)
    val bottomMid: PointF
        get() = PointF((bottomLeft.x + bottomRight.x) / 2f, (bottomLeft.y + bottomRight.y) / 2f)
    val leftMid: PointF
        get() = PointF((topLeft.x + bottomLeft.x) / 2f, (topLeft.y + bottomLeft.y) / 2f)

    fun toList(): List<PointF> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun getWidth(): Float {
        val topWidth = hypot((topRight.x - topLeft.x).toDouble(), (topRight.y - topLeft.y).toDouble()).toFloat()
        val bottomWidth = hypot((bottomRight.x - bottomLeft.x).toDouble(), (bottomRight.y - bottomLeft.y).toDouble()).toFloat()
        return maxOf(topWidth, bottomWidth)
    }

    fun getHeight(): Float {
        val leftHeight = hypot((bottomLeft.x - topLeft.x).toDouble(), (bottomLeft.y - topLeft.y).toDouble()).toFloat()
        val rightHeight = hypot((bottomRight.x - topRight.x).toDouble(), (bottomRight.y - topRight.y).toDouble()).toFloat()
        return maxOf(leftHeight, rightHeight)
    }

    fun lerp(target: Quad, alpha: Float): Quad {
        return Quad(
            topLeft = PointF(lerp(topLeft.x, target.topLeft.x, alpha), lerp(topLeft.y, target.topLeft.y, alpha)),
            topRight = PointF(lerp(topRight.x, target.topRight.x, alpha), lerp(topRight.y, target.topRight.y, alpha)),
            bottomRight = PointF(lerp(bottomRight.x, target.bottomRight.x, alpha), lerp(bottomRight.y, target.bottomRight.y, alpha)),
            bottomLeft = PointF(lerp(bottomLeft.x, target.bottomLeft.x, alpha), lerp(bottomLeft.y, target.bottomLeft.y, alpha))
        )
    }

    fun isCloseTo(other: Quad, threshold: Float = 15f): Boolean {
        return dist(topLeft, other.topLeft) < threshold &&
                dist(topRight, other.topRight) < threshold &&
                dist(bottomRight, other.bottomRight) < threshold &&
                dist(bottomLeft, other.bottomLeft) < threshold
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)
    private fun dist(p1: PointF, p2: PointF): Float = hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()

    companion object {
        fun defaultFullFrame(width: Float, height: Float, marginPercent: Float = 0.08f): Quad {
            val mx = width * marginPercent
            val my = height * marginPercent
            return Quad(
                topLeft = PointF(mx, my),
                topRight = PointF(width - mx, my),
                bottomRight = PointF(width - mx, height - my),
                bottomLeft = PointF(mx, height - my)
            )
        }

        fun orderCorners(points: List<PointF>): Quad {
            if (points.size != 4) {
                return defaultFullFrame(100f, 100f)
            }
            // Sort by sum of (x + y):
            // Top-left has smallest sum (x + y)
            // Bottom-right has largest sum (x + y)
            // Top-right has smallest diff (x - y) -> wait, largest (x - y)
            // Bottom-left has smallest (x - y)
            val sortedBySum = points.sortedBy { it.x + it.y }
            val tl = sortedBySum.first()
            val br = sortedBySum.last()

            val remaining = points.filter { it != tl && it != br }
            val sortedByDiff = remaining.sortedBy { it.x - it.y }
            val bl = sortedByDiff.first()
            val tr = sortedByDiff.last()

            return Quad(tl, tr, br, bl)
        }
    }
}
