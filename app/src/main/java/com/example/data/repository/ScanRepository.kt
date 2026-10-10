package com.example.data.repository

import com.example.data.db.RawSearchResultRow
import com.example.data.db.ScanFlowDao
import com.example.data.db.SearchResultItem
import com.example.data.model.AnnotationRecord
import com.example.data.model.Document
import com.example.data.model.Folder
import com.example.data.model.OcrResult
import com.example.data.model.Page
import com.example.data.model.SignatureItem
import com.example.data.model.WatermarkSetting
import com.example.security.CryptoManager
import kotlinx.coroutines.flow.Flow

class ScanRepository(
    private val dao: ScanFlowDao,
    val fileManager: FileManager,
    val cryptoManager: CryptoManager
) {
    // Folders
    val allFolders: Flow<List<Folder>> = dao.getAllFolders()
    fun getFoldersByParent(parentId: Long?): Flow<List<Folder>> = dao.getFoldersByParent(parentId)
    suspend fun createFolder(name: String, parentId: Long? = null): Long =
        dao.insertFolder(Folder(name = name, parentId = parentId))
    suspend fun updateFolder(folder: Folder) = dao.updateFolder(folder)
    suspend fun deleteFolder(folder: Folder) = dao.deleteFolder(folder)

    // Documents
    val allDocuments: Flow<List<Document>> = dao.getAllDocuments()
    fun getDocumentsByFolder(folderId: Long): Flow<List<Document>> = dao.getDocumentsByFolder(folderId)
    val rootDocuments: Flow<List<Document>> = dao.getRootDocuments()
    fun getDocumentFlow(id: Long): Flow<Document?> = dao.getDocumentFlow(id)
    suspend fun getDocumentById(id: Long): Document? = dao.getDocumentById(id)
    suspend fun createDocument(title: String, folderId: Long? = null, docType: String = "standard"): Long =
        dao.insertDocument(Document(title = title, folderId = folderId, docType = docType))
    suspend fun updateDocument(document: Document) = dao.updateDocument(document)
    suspend fun deleteDocument(id: Long) {
        val pages = dao.getPagesListForDocument(id)
        pages.forEach { page ->
            fileManager.deleteFile(page.originalPath)
            fileManager.deleteFile(page.croppedPath)
            fileManager.deleteFile(page.processedPath)
            dao.deleteOcrResultForPage(page.id)
        }
        dao.deletePagesForDocument(id)
        dao.deleteDocument(id)
    }

    // Pages
    fun getPagesForDocument(documentId: Long): Flow<List<Page>> = dao.getPagesForDocument(documentId)
    suspend fun getPagesListForDocument(documentId: Long): List<Page> = dao.getPagesListForDocument(documentId)
    suspend fun getPageById(id: Long): Page? = dao.getPageById(id)
    suspend fun addPage(page: Page): Long {
        val pageId = dao.insertPage(page)
        val doc = dao.getDocumentById(page.documentId)
        if (doc != null) {
            dao.updateDocument(doc.copy(updatedAt = System.currentTimeMillis()))
        }
        return pageId
    }
    suspend fun updatePage(page: Page) {
        dao.updatePage(page)
        val doc = dao.getDocumentById(page.documentId)
        if (doc != null) {
            dao.updateDocument(doc.copy(updatedAt = System.currentTimeMillis()))
        }
    }
    suspend fun deletePage(page: Page) {
        fileManager.deleteFile(page.originalPath)
        fileManager.deleteFile(page.croppedPath)
        fileManager.deleteFile(page.processedPath)
        dao.deleteOcrResultForPage(page.id)
        dao.deletePage(page.id)
    }

    // OCR
    fun getOcrResult(pageId: Long): Flow<OcrResult?> = dao.getOcrResultForPage(pageId)
    suspend fun getOcrResultDirect(pageId: Long): OcrResult? = dao.getOcrResultDirect(pageId)
    val allOcrResults: Flow<List<OcrResult>> = dao.getAllOcrResults()
    suspend fun saveOcrResult(ocrResult: OcrResult): Long = dao.insertOcrResult(ocrResult)
    suspend fun updateOcrResult(ocrResult: OcrResult) = dao.updateOcrResult(ocrResult)

    // Signatures (Phase 3)
    val allSignatures: Flow<List<SignatureItem>> = dao.getAllSignatures()
    suspend fun saveSignature(signature: SignatureItem): Long = dao.insertSignature(signature)
    suspend fun updateSignature(signature: SignatureItem) = dao.updateSignature(signature)
    suspend fun deleteSignature(signature: SignatureItem) {
        fileManager.deleteFile(signature.path)
        dao.deleteSignature(signature)
    }

    // Annotations (Phase 3)
    fun getAnnotationFlow(pageId: Long): Flow<AnnotationRecord?> = dao.getAnnotationForPage(pageId)
    suspend fun getAnnotationDirect(pageId: Long): AnnotationRecord? = dao.getAnnotationDirect(pageId)
    suspend fun saveAnnotation(record: AnnotationRecord): Long = dao.insertAnnotation(record)
    suspend fun updateAnnotation(record: AnnotationRecord) = dao.updateAnnotation(record)

    // Watermark Settings (Phase 3)
    fun getWatermarkSettingFlow(documentId: Long): Flow<WatermarkSetting?> = dao.getWatermarkSettingForDoc(documentId)
    suspend fun getWatermarkSettingDirect(documentId: Long): WatermarkSetting? = dao.getWatermarkSettingDirect(documentId)
    suspend fun saveWatermarkSetting(setting: WatermarkSetting): Long = dao.insertWatermarkSetting(setting)

    // Locking & Encryption (Phase 3)
    suspend fun toggleLockDocument(documentId: Long, lockMode: String = "biometric", password: String? = null): Boolean {
        val doc = dao.getDocumentById(documentId) ?: return false
        val newLockedState = !doc.isLocked
        val pages = dao.getPagesListForDocument(documentId)

        if (newLockedState) {
            val salt = if (password != null) cryptoManager.generateSalt() else null
            // Encrypt all page images
            for (p in pages) {
                if (!p.isEncrypted) {
                    val encOrig = cryptoManager.encryptFile(p.originalPath, password, salt)
                    val encCrop = cryptoManager.encryptFile(p.croppedPath, password, salt)
                    val encProc = cryptoManager.encryptFile(p.processedPath, password, salt)
                    dao.updatePage(
                        p.copy(
                            originalPath = encOrig,
                            croppedPath = encCrop,
                            processedPath = encProc,
                            isEncrypted = true
                        )
                    )
                }
            }
            dao.updateDocument(
                doc.copy(
                    isLocked = true,
                    lockMode = lockMode,
                    kdfSalt = salt?.joinToString(",") { it.toString() } ?: "",
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            val salt = if (doc.kdfSalt.isNotBlank()) {
                doc.kdfSalt.split(",").map { it.toByte() }.toByteArray()
            } else null

            // Decrypt all page images
            for (p in pages) {
                if (p.isEncrypted) {
                    val decOrig = cryptoManager.decryptFileToDisk(p.originalPath, password, salt)
                    val decCrop = cryptoManager.decryptFileToDisk(p.croppedPath, password, salt)
                    val decProc = cryptoManager.decryptFileToDisk(p.processedPath, password, salt)
                    dao.updatePage(
                        p.copy(
                            originalPath = decOrig,
                            croppedPath = decCrop,
                            processedPath = decProc,
                            isEncrypted = false
                        )
                    )
                }
            }
            dao.updateDocument(
                doc.copy(
                    isLocked = false,
                    lockMode = "none",
                    kdfSalt = "",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return newLockedState
    }

    // Full-Text Search (Filters out locked documents!)
    suspend fun search(query: String): List<SearchResultItem> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()

        val rawRows: List<RawSearchResultRow> = dao.rawSearch(cleanQuery)
        val results = mutableListOf<SearchResultItem>()

        for (row in rawRows) {
            val titleMatches = row.docTitle.contains(cleanQuery, ignoreCase = true)
            val ocrMatches = row.ocrContent.contains(cleanQuery, ignoreCase = true)

            var snippet = ""
            if (ocrMatches) {
                val index = row.ocrContent.indexOf(cleanQuery, ignoreCase = true)
                val start = maxOf(0, index - 25)
                val end = minOf(row.ocrContent.length, index + cleanQuery.length + 35)
                snippet = (if (start > 0) "…" else "") +
                        row.ocrContent.substring(start, end).replace("\n", " ") +
                        (if (end < row.ocrContent.length) "…" else "")
            } else if (titleMatches) {
                snippet = "Matched title: ${row.docTitle}"
            }

            results.add(
                SearchResultItem(
                    documentId = row.docId,
                    documentTitle = row.docTitle,
                    pageId = row.pId,
                    pageIndex = row.pIndex,
                    matchedSnippet = snippet,
                    isTitleMatch = titleMatches
                )
            )
        }
        return results
    }
}
