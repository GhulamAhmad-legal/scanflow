package com.example.ocr

import com.example.data.model.OcrResult
import com.example.data.model.OcrStatus
import com.example.data.model.Page
import com.example.data.repository.FileManager
import com.example.data.repository.ScanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

data class OcrQueueItem(
    val pageId: Long,
    val documentId: Long,
    val imagePath: String,
    val script: String = "Latin"
)

data class OcrQueueState(
    val currentProcessingPageId: Long? = null,
    val queuedCount: Int = 0,
    val completedCount: Int = 0
)

class OcrQueue(
    private val repository: ScanRepository,
    private val fileManager: FileManager,
    private val ocrEngine: OcrEngine = OcrEngine(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val queue = ConcurrentLinkedQueue<OcrQueueItem>()
    private var workerJob: Job? = null

    private val _queueState = MutableStateFlow(OcrQueueState())
    val queueState: StateFlow<OcrQueueState> = _queueState.asStateFlow()

    fun enqueue(page: Page, script: String = "Latin") {
        enqueue(
            OcrQueueItem(
                pageId = page.id,
                documentId = page.documentId,
                imagePath = page.processedPath.ifBlank { page.croppedPath },
                script = script
            )
        )
    }

    fun enqueue(item: OcrQueueItem) {
        // Avoid duplicate queuing
        if (queue.any { it.pageId == item.pageId } || _queueState.value.currentProcessingPageId == item.pageId) {
            return
        }

        queue.add(item)
        scope.launch {
            // Mark as pending in database
            val existing = repository.getOcrResultDirect(item.pageId)
            if (existing != null) {
                repository.updateOcrResult(existing.copy(status = OcrStatus.PENDING.name, error = null))
            } else {
                repository.saveOcrResult(
                    OcrResult(
                        pageId = item.pageId,
                        status = OcrStatus.PENDING.name,
                        script = item.script
                    )
                )
            }
            updateState()
            startWorkerIfNeeded()
        }
    }

    fun cancel(pageId: Long) {
        queue.removeIf { it.pageId == pageId }
        updateState()
    }

    fun retry(page: Page, script: String = "Latin") {
        cancel(page.id)
        enqueue(page, script)
    }

    private fun startWorkerIfNeeded() {
        if (workerJob?.isActive == true) return

        workerJob = scope.launch {
            while (queue.isNotEmpty()) {
                val item = queue.poll() ?: break
                _queueState.value = _queueState.value.copy(
                    currentProcessingPageId = item.pageId,
                    queuedCount = queue.size
                )

                try {
                    // Update DB to RUNNING
                    val existing = repository.getOcrResultDirect(item.pageId)
                    if (existing != null) {
                        repository.updateOcrResult(existing.copy(status = OcrStatus.RUNNING.name))
                    }

                    val bitmap = fileManager.loadBitmap(item.imagePath)
                    if (bitmap != null) {
                        val result = ocrEngine.recognizeText(bitmap, item.script)
                        val updatedResult = OcrResult(
                            id = existing?.id ?: 0,
                            pageId = item.pageId,
                            text = result.fullText,
                            editedText = existing?.editedText ?: "",
                            blocksJson = result.toJson(),
                            script = item.script,
                            status = OcrStatus.DONE.name,
                            error = null,
                            updatedAt = System.currentTimeMillis()
                        )
                        repository.saveOcrResult(updatedResult)
                    } else {
                        repository.saveOcrResult(
                            OcrResult(
                                id = existing?.id ?: 0,
                                pageId = item.pageId,
                                status = OcrStatus.FAILED.name,
                                error = "Could not load image",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (e: Exception) {
                    val existing = repository.getOcrResultDirect(item.pageId)
                    repository.saveOcrResult(
                        OcrResult(
                            id = existing?.id ?: 0,
                            pageId = item.pageId,
                            status = OcrStatus.FAILED.name,
                            error = e.localizedMessage ?: "OCR processing failed",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } finally {
                    _queueState.value = _queueState.value.copy(
                        currentProcessingPageId = null,
                        completedCount = _queueState.value.completedCount + 1,
                        queuedCount = queue.size
                    )
                }
            }
        }
    }

    private fun updateState() {
        _queueState.value = _queueState.value.copy(queuedCount = queue.size)
    }
}
