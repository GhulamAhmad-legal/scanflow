package com.example.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max

class OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeText(bitmap: Bitmap, script: String = "Latin"): OcrDocumentResult = withContext(Dispatchers.Default) {
        // Downscale safely if image is > 4000px on long side to prevent memory pressure
        val maxSide = max(bitmap.width, bitmap.height)
        val workingBitmap = if (maxSide > 3800) {
            val scale = 3800f / maxSide
            val w = (bitmap.width * scale).toInt()
            val h = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }

        val width = workingBitmap.width.toFloat()
        val height = workingBitmap.height.toFloat()

        val inputImage = InputImage.fromBitmap(workingBitmap, 0)
        val visionText: Text = try {
            recognizer.process(inputImage).await()
        } catch (e: Exception) {
            return@withContext OcrDocumentResult(
                fullText = "",
                blocks = emptyList(),
                script = script
            )
        }

        val blockBoxes = mutableListOf<OcrBlockBox>()

        for (block in visionText.textBlocks) {
            val bBox = block.boundingBox
            val bl = (bBox?.left?.toFloat() ?: 0f) / width
            val bt = (bBox?.top?.toFloat() ?: 0f) / height
            val br = (bBox?.right?.toFloat() ?: width) / width
            val bb = (bBox?.bottom?.toFloat() ?: height) / height

            val lineBoxes = mutableListOf<OcrLineBox>()
            for (line in block.lines) {
                val lBox = line.boundingBox
                val ll = (lBox?.left?.toFloat() ?: 0f) / width
                val lt = (lBox?.top?.toFloat() ?: 0f) / height
                val lr = (lBox?.right?.toFloat() ?: width) / width
                val lb = (lBox?.bottom?.toFloat() ?: height) / height

                val wordBoxes = mutableListOf<OcrWordBox>()
                for (elem in line.elements) {
                    val wBox = elem.boundingBox
                    val wl = (wBox?.left?.toFloat() ?: 0f) / width
                    val wt = (wBox?.top?.toFloat() ?: 0f) / height
                    val wr = (wBox?.right?.toFloat() ?: width) / width
                    val wb = (wBox?.bottom?.toFloat() ?: height) / height

                    wordBoxes.add(
                        OcrWordBox(
                            text = elem.text,
                            left = wl.coerceIn(0f, 1f),
                            top = wt.coerceIn(0f, 1f),
                            right = wr.coerceIn(0f, 1f),
                            bottom = wb.coerceIn(0f, 1f)
                        )
                    )
                }

                lineBoxes.add(
                    OcrLineBox(
                        text = line.text,
                        left = ll.coerceIn(0f, 1f),
                        top = lt.coerceIn(0f, 1f),
                        right = lr.coerceIn(0f, 1f),
                        bottom = lb.coerceIn(0f, 1f),
                        words = wordBoxes
                    )
                )
            }

            blockBoxes.add(
                OcrBlockBox(
                    text = block.text,
                    left = bl.coerceIn(0f, 1f),
                    top = bt.coerceIn(0f, 1f),
                    right = br.coerceIn(0f, 1f),
                    bottom = bb.coerceIn(0f, 1f),
                    lines = lineBoxes
                )
            )
        }

        OcrDocumentResult(
            fullText = visionText.text,
            blocks = blockBoxes,
            script = script
        )
    }
}
