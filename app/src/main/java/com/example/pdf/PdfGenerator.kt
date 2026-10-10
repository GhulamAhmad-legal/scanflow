package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.annotate.AnnotationItem
import com.example.data.model.Page
import com.example.data.repository.FileManager
import com.example.data.repository.ScanRepository
import com.example.ocr.OcrDocumentResult
import com.example.render.PageRenderer
import com.example.signature.PlacedSignature
import com.example.watermark.WatermarkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PdfPageSize(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4 (210 × 297 mm)", 595, 842),
    LETTER("US Letter (8.5 × 11 in)", 612, 792),
    FIT_TO_IMAGE("Fit to Image", 0, 0)
}

enum class PdfCompression(val label: String, val jpegQuality: Int, val maxDimension: Int) {
    HIGH("High Quality (minimal compression)", 90, 2400),
    MEDIUM("Medium (recommended)", 75, 1800),
    LOW("Low (maximum compression)", 48, 1200)
}

data class PdfExportOptions(
    val title: String,
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val compression: PdfCompression = PdfCompression.MEDIUM,
    val marginPt: Int = 18,
    val makeSearchable: Boolean = true,
    val targetMaxSizeBytes: Long? = null,
    val password: String? = null
)

class PdfGenerator(
    private val context: Context,
    private val fileManager: FileManager,
    private val repository: ScanRepository,
    private val pageRenderer: PageRenderer = PageRenderer(context)
) {

    suspend fun generatePdf(
        pages: List<Page>,
        options: PdfExportOptions
    ): File = withContext(Dispatchers.IO) {
        val outputFile = fileManager.getNewPdfFile(options.title)
        var currentQuality = options.compression.jpegQuality
        var currentMaxDim = options.compression.maxDimension

        var attempts = 0
        while (attempts < 3) {
            val pdfDoc = PdfDocument()

            for ((index, page) in pages.withIndex()) {
                val imagePath = page.processedPath.ifBlank { page.croppedPath }
                val rawBitmap = fileManager.loadBitmap(imagePath, currentMaxDim) ?: continue

                // Retrieve page annotations and placed signatures
                val annotationRecord = repository.getAnnotationDirect(page.id)
                val annotations = AnnotationItem.listFromJson(annotationRecord?.json ?: "")
                val signatures = PlacedSignature.listFromJson(annotationRecord?.placedSignaturesJson ?: "")

                // Retrieve watermark settings for this document
                val watermarkSetting = repository.getWatermarkSettingDirect(page.documentId)
                val watermark = WatermarkConfig.fromJson(watermarkSetting?.configJson ?: "")

                // Render composite image with annotations, signatures, and watermark
                val compositeBitmap = pageRenderer.renderCompositeBitmap(
                    baseBitmap = rawBitmap,
                    annotations = annotations,
                    placedSignatures = signatures,
                    watermark = watermark
                )

                // Compress composite bitmap with target quality
                val compressedStream = ByteArrayOutputStream()
                compositeBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, compressedStream)
                val compressedBytes = compressedStream.toByteArray()
                val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                val (pageWidth, pageHeight) = when (options.pageSize) {
                    PdfPageSize.A4 -> PdfPageSize.A4.widthPt to PdfPageSize.A4.heightPt
                    PdfPageSize.LETTER -> PdfPageSize.LETTER.widthPt to PdfPageSize.LETTER.heightPt
                    PdfPageSize.FIT_TO_IMAGE -> {
                        val aspect = compressedBitmap.height.toFloat() / compressedBitmap.width.toFloat()
                        595 to (595 * aspect).roundToInt()
                    }
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val pdfPage = pdfDoc.startPage(pageInfo)
                val canvas = pdfPage.canvas

                canvas.drawColor(Color.WHITE)

                val m = options.marginPt.toFloat()
                val contentW = pageWidth - 2 * m
                val contentH = pageHeight - 2 * m

                val imgW = compressedBitmap.width.toFloat()
                val imgH = compressedBitmap.height.toFloat()
                val scale = min(contentW / imgW, contentH / imgH)
                val destW = imgW * scale
                val destH = imgH * scale
                val destLeft = m + (contentW - destW) / 2f
                val destTop = m + (contentH - destH) / 2f
                val destRect = RectF(destLeft, destTop, destLeft + destW, destTop + destH)

                // 1. Draw composite scanned page layer
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(compressedBitmap, null, destRect, paint)

                // 2. Draw invisible searchable text layer if enabled
                if (options.makeSearchable) {
                    val ocrResult = repository.getOcrResultDirect(page.id)
                    if (ocrResult != null && ocrResult.blocksJson.isNotBlank()) {
                        val docResult = OcrDocumentResult.fromJson(ocrResult.blocksJson)
                        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.BLACK
                            alpha = 0 // Transparent text for native PDF search / highlight
                        }

                        for (block in docResult.blocks) {
                            for (line in block.lines) {
                                for (word in line.words) {
                                    val wordX = destLeft + word.left * destW
                                    val wordY = destTop + word.top * destH
                                    val wordWidth = (word.right - word.left) * destW
                                    val wordHeight = (word.bottom - word.top) * destH

                                    textPaint.textSize = max(6f, wordHeight * 0.9f)
                                    canvas.drawText(word.text, wordX, wordY + wordHeight * 0.82f, textPaint)
                                }
                            }
                        }
                    }
                }

                pdfDoc.finishPage(pdfPage)
            }

            FileOutputStream(outputFile).use { out ->
                pdfDoc.writeTo(out)
            }
            pdfDoc.close()

            val target = options.targetMaxSizeBytes
            if (target == null || outputFile.length() <= target || attempts >= 2) {
                break
            }

            currentQuality = max(25, (currentQuality * 0.7f).roundToInt())
            currentMaxDim = max(800, (currentMaxDim * 0.75f).roundToInt())
            attempts++
        }

        outputFile
    }
}
