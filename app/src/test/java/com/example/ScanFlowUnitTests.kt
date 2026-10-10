package com.example

import android.graphics.PointF
import com.example.cv.Quad
import com.example.ocr.OcrBlockBox
import com.example.ocr.OcrDocumentResult
import com.example.ocr.OcrLineBox
import com.example.ocr.OcrWordBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanFlowUnitTests {

    @Test
    fun testBoundingBoxToPdfCoordinateConversion() {
        val destLeft = 18f
        val destTop = 18f
        val destW = 559f
        val destH = 806f

        val word = OcrWordBox(
            text = "INVOICE",
            left = 0.1f,
            top = 0.2f,
            right = 0.35f,
            bottom = 0.25f
        )

        val wordX = destLeft + word.left * destW
        val wordY = destTop + word.top * destH
        val wordWidth = (word.right - word.left) * destW
        val wordHeight = (word.bottom - word.top) * destH

        assertEquals(18f + 55.9f, wordX, 0.01f)
        assertEquals(18f + 161.2f, wordY, 0.01f)
        assertEquals(139.75f, wordWidth, 0.01f)
        assertEquals(40.3f, wordHeight, 0.01f)
    }

    @Test
    fun testOcrJsonSerializationAndDeserialization() {
        val word = OcrWordBox("ScanFlow", 0.1f, 0.2f, 0.4f, 0.3f)
        val line = OcrLineBox("ScanFlow OCR", 0.1f, 0.2f, 0.6f, 0.3f, listOf(word))
        val block = OcrBlockBox("ScanFlow OCR Block", 0.1f, 0.2f, 0.6f, 0.3f, listOf(line))
        val original = OcrDocumentResult("ScanFlow OCR Block", listOf(block), "Latin")

        val json = original.toJson()
        val restored = OcrDocumentResult.fromJson(json)

        assertEquals("ScanFlow OCR Block", restored.fullText)
        assertEquals(1, restored.blocks.size)
        assertEquals(1, restored.blocks[0].lines.size)
        assertEquals(1, restored.blocks[0].lines[0].words.size)
        assertEquals("ScanFlow", restored.blocks[0].lines[0].words[0].text)
        assertEquals(0.1f, restored.blocks[0].lines[0].words[0].left, 0.001f)
    }

    @Test
    fun testQuadCornerOrdering() {
        val unordered = listOf(
            PointF(100f, 100f), // BR
            PointF(0f, 0f),     // TL
            PointF(100f, 0f),   // TR
            PointF(0f, 100f)    // BL
        )
        val ordered = Quad.orderCorners(unordered)

        assertEquals(0f, ordered.topLeft.x, 0.01f)
        assertEquals(0f, ordered.topLeft.y, 0.01f)

        assertEquals(100f, ordered.topRight.x, 0.01f)
        assertEquals(0f, ordered.topRight.y, 0.01f)

        assertEquals(100f, ordered.bottomRight.x, 0.01f)
        assertEquals(100f, ordered.bottomRight.y, 0.01f)

        assertEquals(0f, ordered.bottomLeft.x, 0.01f)
        assertEquals(100f, ordered.bottomLeft.y, 0.01f)
    }

    @Test
    fun testQuadDimensionsAndStability() {
        val q1 = Quad(PointF(0f, 0f), PointF(200f, 0f), PointF(200f, 300f), PointF(0f, 300f))
        val q2 = Quad(PointF(2f, 1f), PointF(201f, 1f), PointF(199f, 299f), PointF(1f, 299f))

        assertEquals(200f, q1.getWidth(), 0.01f)
        assertEquals(300f, q1.getHeight(), 0.01f)
        assertTrue(q1.isCloseTo(q2, threshold = 15f))
    }

    @Test
    fun testPlacedSignatureJsonRoundTrip() {
        val original = com.example.signature.PlacedSignature(
            id = "sig-123",
            signatureId = 42L,
            path = "/data/user/0/com.example/files/sig.png",
            normalizedX = 0.65f,
            normalizedY = 0.8f,
            normalizedWidth = 0.3f,
            normalizedHeight = 0.12f,
            rotationDegrees = 15f,
            stampDate = "2026-10-10",
            stampText = "Verified & Approved"
        )

        val jsonString = com.example.signature.PlacedSignature.listToJson(listOf(original))
        val restoredList = com.example.signature.PlacedSignature.listFromJson(jsonString)

        assertEquals(1, restoredList.size)
        val restored = restoredList[0]
        assertEquals("sig-123", restored.id)
        assertEquals(42L, restored.signatureId)
        assertEquals(0.65f, restored.normalizedX, 0.001f)
        assertEquals(0.8f, restored.normalizedY, 0.001f)
        assertEquals("2026-10-10", restored.stampDate)
        assertEquals("Verified & Approved", restored.stampText)
    }

    @Test
    fun testAnnotationItemsJsonRoundTrip() {
        val freehand = com.example.annotate.AnnotationItem.FreehandStroke(
            id = "free-1",
            points = listOf(PointF(0.1f, 0.2f), PointF(0.3f, 0.4f)),
            colorHex = "#FF0000",
            strokeWidth = 4f,
            isHighlighter = false
        )
        val text = com.example.annotate.AnnotationItem.TextItem(
            id = "text-1",
            text = "Signature required below",
            position = PointF(0.2f, 0.7f),
            colorHex = "#0000FF",
            fontSize = 18f,
            isBold = true
        )
        val shape = com.example.annotate.AnnotationItem.ShapeItem(
            id = "shape-1",
            type = com.example.annotate.AnnotationType.RECTANGLE,
            start = PointF(0.1f, 0.1f),
            end = PointF(0.5f, 0.5f),
            colorHex = "#00FF00",
            strokeWidth = 2f,
            isFilled = false
        )

        val jsonStr = com.example.annotate.AnnotationItem.listToJson(listOf(freehand, text, shape))
        val restored = com.example.annotate.AnnotationItem.listFromJson(jsonStr)

        assertEquals(3, restored.size)
        assertTrue(restored[0] is com.example.annotate.AnnotationItem.FreehandStroke)
        assertTrue(restored[1] is com.example.annotate.AnnotationItem.TextItem)
        assertTrue(restored[2] is com.example.annotate.AnnotationItem.ShapeItem)

        val restoredText = restored[1] as com.example.annotate.AnnotationItem.TextItem
        assertEquals("Signature required below", restoredText.text)
        assertTrue(restoredText.isBold)
    }

    @Test
    fun testWatermarkConfigRoundTrip() {
        val config = com.example.watermark.WatermarkConfig(
            enabled = true,
            type = com.example.watermark.WatermarkType.TEXT,
            text = "TOP SECRET",
            opacity = 0.45f,
            rotationDegrees = -30f,
            layout = com.example.watermark.WatermarkLayout.TILED
        )

        val json = config.toJson()
        val restored = com.example.watermark.WatermarkConfig.fromJson(json)

        assertTrue(restored.enabled)
        assertEquals("TOP SECRET", restored.text)
        assertEquals(0.45f, restored.opacity, 0.01f)
        assertEquals(-30f, restored.rotationDegrees, 0.01f)
        assertEquals(com.example.watermark.WatermarkLayout.TILED, restored.layout)
    }

    @Test
    fun testCryptoPasswordKeyDerivationConsistency() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val crypto = com.example.security.CryptoManager(context)
        val salt = crypto.generateSalt()

        val key1 = crypto.deriveKeyFromPassword("SecretPassphrase123!", salt)
        val key2 = crypto.deriveKeyFromPassword("SecretPassphrase123!", salt)
        val keyOther = crypto.deriveKeyFromPassword("DifferentPassphrase999!", salt)

        assertEquals(key1.encoded.toList(), key2.encoded.toList())
        org.junit.Assert.assertNotEquals(key1.encoded.toList(), keyOther.encoded.toList())
    }
}
