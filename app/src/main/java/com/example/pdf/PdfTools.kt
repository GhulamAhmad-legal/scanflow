package com.example.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.example.data.model.Document
import com.example.data.model.Page
import com.example.data.repository.FileManager
import com.example.data.repository.ScanRepository
import com.example.ocr.OcrQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfTools(
    private val context: Context,
    private val repository: ScanRepository,
    private val fileManager: FileManager,
    private val ocrQueue: OcrQueue
) {

    suspend fun importPdf(uri: Uri, documentTitle: String? = null): Long = withContext(Dispatchers.IO) {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Could not open PDF file descriptor")

        val renderer = PdfRenderer(pfd)
        val title = documentTitle ?: "Imported PDF (${System.currentTimeMillis() % 10000})"
        val docId = repository.createDocument(title)

        try {
            val pageCount = renderer.pageCount
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                // 180 DPI rendering: standard PDF 72pt -> 2.5x scale
                val scale = 2.5f
                val bmpW = (page.width * scale).toInt()
                val bmpH = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val savedPath = fileManager.saveBitmap(bitmap, quality = 90)

                val newPage = Page(
                    documentId = docId,
                    orderIndex = i,
                    originalPath = savedPath,
                    croppedPath = savedPath,
                    processedPath = savedPath
                )
                val pageId = repository.addPage(newPage)
                ocrQueue.enqueue(newPage.copy(id = pageId))
            }
        } finally {
            renderer.close()
            pfd.close()
        }

        docId
    }

    suspend fun mergeDocuments(documentIds: List<Long>, newTitle: String): Long = withContext(Dispatchers.IO) {
        val newDocId = repository.createDocument(newTitle)
        var currentIndex = 0

        for (docId in documentIds) {
            val pages = repository.getPagesListForDocument(docId)
            for (p in pages) {
                val newPage = p.copy(
                    id = 0,
                    documentId = newDocId,
                    orderIndex = currentIndex++
                )
                val addedPageId = repository.addPage(newPage)

                // Copy OCR result if available
                val ocr = repository.getOcrResultDirect(p.id)
                if (ocr != null) {
                    repository.saveOcrResult(ocr.copy(id = 0, pageId = addedPageId))
                } else {
                    ocrQueue.enqueue(newPage.copy(id = addedPageId))
                }
            }
        }
        newDocId
    }

    suspend fun splitDocument(
        sourceDocId: Long,
        selectedPageIds: List<Long>,
        newTitle: String,
        shouldMove: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val newDocId = repository.createDocument(newTitle)
        val sourcePages = repository.getPagesListForDocument(sourceDocId)
        val pagesToSplit = sourcePages.filter { it.id in selectedPageIds }

        for ((index, p) in pagesToSplit.withIndex()) {
            val newPage = p.copy(
                id = 0,
                documentId = newDocId,
                orderIndex = index
            )
            val addedPageId = repository.addPage(newPage)

            val ocr = repository.getOcrResultDirect(p.id)
            if (ocr != null) {
                repository.saveOcrResult(ocr.copy(id = 0, pageId = addedPageId))
            }

            if (shouldMove) {
                repository.deletePage(p)
            }
        }

        // Re-index remaining pages in source doc if moved
        if (shouldMove) {
            val remaining = repository.getPagesListForDocument(sourceDocId)
            remaining.forEachIndexed { i, page ->
                if (page.orderIndex != i) {
                    repository.updatePage(page.copy(orderIndex = i))
                }
            }
        }

        newDocId
    }

    suspend fun exportDocumentText(pages: List<Page>): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        for ((index, page) in pages.withIndex()) {
            sb.append("--- PAGE ${index + 1} ---\n\n")
            val ocr = repository.getOcrResultDirect(page.id)
            val text = ocr?.effectiveText ?: "[No text recognized]"
            sb.append(text)
            sb.append("\n\n")
        }
        sb.toString()
    }

    fun shareFile(file: File, mimeType: String = "application/pdf", title: String = "Share Document") {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareText(text: String, title: String = "Share Scanned Text") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
