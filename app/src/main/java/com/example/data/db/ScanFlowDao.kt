package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AnnotationRecord
import com.example.data.model.Document
import com.example.data.model.Folder
import com.example.data.model.OcrResult
import com.example.data.model.Page
import com.example.data.model.SignatureItem
import com.example.data.model.WatermarkSetting
import kotlinx.coroutines.flow.Flow

data class SearchResultItem(
    val documentId: Long,
    val documentTitle: String,
    val pageId: Long?,
    val pageIndex: Int?,
    val matchedSnippet: String,
    val isTitleMatch: Boolean
)

@Dao
interface ScanFlowDao {
    // Folders
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentId = :parentId ORDER BY name ASC")
    fun getFoldersByParent(parentId: Long?): Flow<List<Folder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Update
    suspend fun updateFolder(folder: Folder)

    @Delete
    suspend fun deleteFolder(folder: Folder)

    // Documents
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun getDocumentsByFolder(folderId: Long): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE folderId IS NULL ORDER BY updatedAt DESC")
    fun getRootDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): Document?

    @Query("SELECT * FROM documents WHERE id = :id")
    fun getDocumentFlow(id: Long): Flow<Document?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document): Long

    @Update
    suspend fun updateDocument(document: Document)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    // Pages
    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY orderIndex ASC")
    fun getPagesForDocument(documentId: Long): Flow<List<Page>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY orderIndex ASC")
    suspend fun getPagesListForDocument(documentId: Long): List<Page>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageById(pageId: Long): Page?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: Page): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<Page>): List<Long>

    @Update
    suspend fun updatePage(page: Page)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePage(pageId: Long)

    @Query("DELETE FROM pages WHERE documentId = :documentId")
    suspend fun deletePagesForDocument(documentId: Long)

    // OCR Results
    @Query("SELECT * FROM ocr_results WHERE pageId = :pageId")
    fun getOcrResultForPage(pageId: Long): Flow<OcrResult?>

    @Query("SELECT * FROM ocr_results WHERE pageId = :pageId")
    suspend fun getOcrResultDirect(pageId: Long): OcrResult?

    @Query("SELECT * FROM ocr_results")
    fun getAllOcrResults(): Flow<List<OcrResult>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrResult(ocrResult: OcrResult): Long

    @Update
    suspend fun updateOcrResult(ocrResult: OcrResult)

    @Query("DELETE FROM ocr_results WHERE pageId = :pageId")
    suspend fun deleteOcrResultForPage(pageId: Long)

    // Signatures (Phase 3)
    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    fun getAllSignatures(): Flow<List<SignatureItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignature(signature: SignatureItem): Long

    @Update
    suspend fun updateSignature(signature: SignatureItem)

    @Delete
    suspend fun deleteSignature(signature: SignatureItem)

    // Annotations (Phase 3)
    @Query("SELECT * FROM annotations WHERE pageId = :pageId")
    fun getAnnotationForPage(pageId: Long): Flow<AnnotationRecord?>

    @Query("SELECT * FROM annotations WHERE pageId = :pageId")
    suspend fun getAnnotationDirect(pageId: Long): AnnotationRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(record: AnnotationRecord): Long

    @Update
    suspend fun updateAnnotation(record: AnnotationRecord)

    // Watermark Settings (Phase 3)
    @Query("SELECT * FROM watermark_settings WHERE documentId = :documentId")
    fun getWatermarkSettingForDoc(documentId: Long): Flow<WatermarkSetting?>

    @Query("SELECT * FROM watermark_settings WHERE documentId = :documentId")
    suspend fun getWatermarkSettingDirect(documentId: Long): WatermarkSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatermarkSetting(setting: WatermarkSetting): Long

    @Update
    suspend fun updateWatermarkSetting(setting: WatermarkSetting)

    // Full-Text Search: Exclude locked documents from global search results!
    @Query("""
        SELECT d.id AS docId, d.title AS docTitle, p.id AS pId, p.orderIndex AS pIndex,
               COALESCE(NULLIF(o.editedText, ''), o.text, '') AS ocrContent
        FROM documents d
        LEFT JOIN pages p ON p.documentId = d.id
        LEFT JOIN ocr_results o ON o.pageId = p.id
        WHERE d.isLocked = 0
          AND (d.title LIKE '%' || :query || '%'
               OR o.text LIKE '%' || :query || '%'
               OR o.editedText LIKE '%' || :query || '%')
        ORDER BY d.updatedAt DESC
    """)
    suspend fun rawSearch(query: String): List<RawSearchResultRow>
}

data class RawSearchResultRow(
    val docId: Long,
    val docTitle: String,
    val pId: Long?,
    val pIndex: Int?,
    val ocrContent: String
)
