package com.example.idcard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class IdCardType(val label: String, val widthMm: Float, val heightMm: Float, val isDualSided: Boolean) {
    ID_CARD("National ID Card", 85.6f, 53.98f, true),
    DRIVING_LICENSE("Driving License", 85.6f, 53.98f, true),
    PASSPORT("Passport Data Page", 125.0f, 88.0f, false),
    CUSTOM("Custom Card", 85.6f, 53.98f, true)
}

enum class IdCompositeLayout {
    STACKED_VERTICAL,
    SIDE_BY_SIDE
}

class IdCardComposer {

    suspend fun createA4Composite(
        frontBitmap: Bitmap,
        backBitmap: Bitmap?,
        cardType: IdCardType,
        layout: IdCompositeLayout = IdCompositeLayout.STACKED_VERTICAL,
        dpi: Int = 300
    ): Bitmap = withContext(Dispatchers.Default) {
        // Standard A4 dimensions in mm: 210 x 297 mm
        val a4WidthMm = 210.0f
        val a4HeightMm = 297.0f

        val a4WidthPx = ((a4WidthMm / 25.4f) * dpi).toInt() // 2480 px
        val a4HeightPx = ((a4HeightMm / 25.4f) * dpi).toInt() // 3508 px

        val cardWidthPx = ((cardType.widthMm / 25.4f) * dpi).toInt()
        val cardHeightPx = ((cardType.heightMm / 25.4f) * dpi).toInt()

        val canvasBitmap = Bitmap.createBitmap(a4WidthPx, a4HeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)

        // Pure white A4 sheet background
        canvas.drawColor(Color.WHITE)

        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCBD5E1.toInt() // Slate 300 cut guide
            strokeWidth = 3f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
        }

        val cornerRadius = 24f

        if (cardType.isDualSided && backBitmap != null) {
            when (layout) {
                IdCompositeLayout.STACKED_VERTICAL -> {
                    val gap = 120f
                    val totalCardsHeight = cardHeightPx * 2 + gap
                    val startY = (a4HeightPx - totalCardsHeight) / 2f
                    val startX = (a4WidthPx - cardWidthPx) / 2f

                    // Draw Front Card
                    val frontRect = RectF(startX, startY, startX + cardWidthPx, startY + cardHeightPx)
                    canvas.drawBitmap(frontBitmap, null, frontRect, imagePaint)
                    canvas.drawRoundRect(frontRect, cornerRadius, cornerRadius, borderPaint)

                    // Draw Back Card
                    val backStartY = startY + cardHeightPx + gap
                    val backRect = RectF(startX, backStartY, startX + cardWidthPx, backStartY + cardHeightPx)
                    canvas.drawBitmap(backBitmap, null, backRect, imagePaint)
                    canvas.drawRoundRect(backRect, cornerRadius, cornerRadius, borderPaint)
                }
                IdCompositeLayout.SIDE_BY_SIDE -> {
                    val gap = 100f
                    val totalCardsWidth = cardWidthPx * 2 + gap
                    val startX = (a4WidthPx - totalCardsWidth) / 2f
                    val startY = (a4HeightPx - cardHeightPx) / 2f

                    val frontRect = RectF(startX, startY, startX + cardWidthPx, startY + cardHeightPx)
                    canvas.drawBitmap(frontBitmap, null, frontRect, imagePaint)
                    canvas.drawRoundRect(frontRect, cornerRadius, cornerRadius, borderPaint)

                    val backStartX = startX + cardWidthPx + gap
                    val backRect = RectF(backStartX, startY, backStartX + cardWidthPx, startY + cardHeightPx)
                    canvas.drawBitmap(backBitmap, null, backRect, imagePaint)
                    canvas.drawRoundRect(backRect, cornerRadius, cornerRadius, borderPaint)
                }
            }
        } else {
            // Single Sided (Passport or single card)
            val startX = (a4WidthPx - cardWidthPx) / 2f
            val startY = (a4HeightPx - cardHeightPx) / 2f

            val rect = RectF(startX, startY, startX + cardWidthPx, startY + cardHeightPx)
            canvas.drawBitmap(frontBitmap, null, rect, imagePaint)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
        }

        canvasBitmap
    }
}
