package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv.DocumentDetector
import com.example.cv.ImageFilters
import com.example.cv.PerspectiveWarper
import com.example.cv.Quad
import com.example.data.db.ScanFlowDatabase
import com.example.data.db.SearchResultItem
import com.example.data.model.Document
import com.example.data.model.FilterType
import com.example.data.model.Folder
import com.example.data.model.OcrResult
import com.example.data.model.Page
import com.example.data.repository.FileManager
import com.example.data.repository.ScanRepository
import com.example.idcard.IdCardComposer
import com.example.idcard.IdCardType
import com.example.idcard.IdCompositeLayout
import com.example.ocr.OcrDocumentResult
import com.example.ocr.OcrEngine
import com.example.ocr.OcrQueue
import com.example.pdf.PdfCompression
import com.example.pdf.PdfExportOptions
import com.example.pdf.PdfGenerator
import com.example.pdf.PdfPageSize
import com.example.pdf.PdfTools
import com.example.render.PageRenderer
import com.example.security.CryptoManager
import com.example.security.LockTimeoutOption
import com.example.security.SecurityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class Screen {
    data object Library : Screen()
    data class Camera(val targetDocumentId: Long? = null, val retakePageIndex: Int? = null) : Screen()
    data class Crop(val pageId: Long, val originalBitmap: Bitmap? = null, val documentId: Long) : Screen()
    data class Filters(val pageId: Long, val croppedBitmap: Bitmap? = null, val documentId: Long) : Screen()
    data class PageGrid(val documentId: Long) : Screen()
    data class PageEditor(val pageId: Long, val documentId: Long) : Screen()
    data class OcrResultView(val pageId: Long, val documentId: Long) : Screen()
    data object Search : Screen()
    data class PdfToolsView(val initialTab: Int = 0) : Screen()
    data object Settings : Screen()
    data class IdCardScan(val targetDocumentId: Long? = null) : Screen()
}

enum class ViewMode { GRID, LIST }
enum class SortOption { DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC }

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ScanFlowDatabase.getDatabase(application)
    val fileManager = FileManager(application)
    val cryptoManager = CryptoManager(application)
    val securityManager = SecurityManager(application)
    val repository = ScanRepository(db.scanFlowDao(), fileManager, cryptoManager)
    val ocrEngine = OcrEngine()
    val ocrQueue = OcrQueue(repository, fileManager, ocrEngine, viewModelScope)
    val detector = DocumentDetector()
    val warper = PerspectiveWarper()
    val filters = ImageFilters()
    val pageRenderer = PageRenderer(application)
    val pdfGenerator = PdfGenerator(application, fileManager, repository, pageRenderer)
    val pdfTools = PdfTools(application, repository, fileManager, ocrQueue)
    val idCardComposer = IdCardComposer()

    // Navigation & UI State
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Library)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId.asStateFlow()

    // Multi-select in Library
    private val _selectedDocumentIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedDocumentIds: StateFlow<Set<Long>> = _selectedDocumentIds.asStateFlow()

    // Camera batch mode
    private val _isBatchMode = MutableStateFlow(false)
    val isBatchMode: StateFlow<Boolean> = _isBatchMode.asStateFlow()

    private val _autoCaptureEnabled = MutableStateFlow(true)
    val autoCaptureEnabled: StateFlow<Boolean> = _autoCaptureEnabled.asStateFlow()

    private val _autoOcrEnabled = MutableStateFlow(true)
    val autoOcrEnabled: StateFlow<Boolean> = _autoOcrEnabled.asStateFlow()

    private val _defaultOcrScript = MutableStateFlow("Latin")
    val defaultOcrScript: StateFlow<String> = _defaultOcrScript.asStateFlow()

    // Transient image holding during scan -> crop -> filter flow
    var activeCapturedBitmap: Bitmap? = null
    var activeCroppedBitmap: Bitmap? = null
    var activeInitialQuad: Quad? = null
    var activePageId: Long? = null
    var activeDocumentId: Long? = null

    // ID Card scanning state
    var idCardFrontBitmap: Bitmap? = null
    var idCardBackBitmap: Bitmap? = null
    private val _idCardScanStep = MutableStateFlow(0) // 0: Front, 1: Back
    val idCardScanStep: StateFlow<Int> = _idCardScanStep.asStateFlow()
    var selectedIdCardType by mutableStateOf(IdCardType.ID_CARD)

    // Search query & results
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Status message for Snackbars
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Reactive streams from DB
    val allFolders: StateFlow<List<Folder>> = repository.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocuments: StateFlow<List<Document>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOcrResults: StateFlow<List<OcrResult>> = repository.allOcrResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unlockedDocumentIds: StateFlow<Set<Long>> = securityManager.unlockedDocumentIds

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun navigateBack() {
        when (_currentScreen.value) {
            is Screen.Library -> {}
            is Screen.Camera -> _currentScreen.value = Screen.Library
            is Screen.Crop -> {
                val docId = activeDocumentId
                if (docId != null) {
                    _currentScreen.value = Screen.PageGrid(docId)
                } else {
                    _currentScreen.value = Screen.Library
                }
            }
            is Screen.Filters -> {
                val pageId = activePageId
                val docId = activeDocumentId
                if (pageId != null && docId != null) {
                    _currentScreen.value = Screen.Crop(pageId, activeCapturedBitmap, docId)
                } else {
                    _currentScreen.value = Screen.Library
                }
            }
            is Screen.PageGrid -> _currentScreen.value = Screen.Library
            is Screen.PageEditor -> {
                val docId = activeDocumentId
                if (docId != null) {
                    _currentScreen.value = Screen.PageGrid(docId)
                } else {
                    _currentScreen.value = Screen.Library
                }
            }
            is Screen.OcrResultView -> {
                val docId = activeDocumentId
                if (docId != null) {
                    _currentScreen.value = Screen.PageGrid(docId)
                } else {
                    _currentScreen.value = Screen.Library
                }
            }
            is Screen.Search -> _currentScreen.value = Screen.Library
            is Screen.PdfToolsView -> _currentScreen.value = Screen.Library
            is Screen.Settings -> _currentScreen.value = Screen.Library
            is Screen.IdCardScan -> _currentScreen.value = Screen.Library
        }
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun setCurrentFolder(folderId: Long?) {
        _currentFolderId.value = folderId
    }

    fun toggleBatchMode() {
        _isBatchMode.value = !_isBatchMode.value
    }

    fun toggleAutoCapture() {
        _autoCaptureEnabled.value = !_autoCaptureEnabled.value
    }

    fun toggleAutoOcr() {
        _autoOcrEnabled.value = !_autoOcrEnabled.value
    }

    fun setDefaultOcrScript(script: String) {
        _defaultOcrScript.value = script
    }

    fun toggleDocumentSelection(docId: Long) {
        val current = _selectedDocumentIds.value.toMutableSet()
        if (current.contains(docId)) {
            current.remove(docId)
        } else {
            current.add(docId)
        }
        _selectedDocumentIds.value = current
    }

    fun clearSelection() {
        _selectedDocumentIds.value = emptySet()
    }

    fun showMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // Capture & Page Creation
    fun processCapturedImage(bitmap: Bitmap, targetDocId: Long? = null, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            activeCapturedBitmap = bitmap
            val docId = targetDocId ?: repository.createDocument("Scan ${System.currentTimeMillis() % 10000}", _currentFolderId.value)
            activeDocumentId = docId

            val originalPath = fileManager.saveBitmap(bitmap)

            val detection = detector.detectDocument(bitmap)
            activeInitialQuad = detection.quad

            val warped = warper.warp(bitmap, detection.quad)
            activeCroppedBitmap = warped
            val croppedPath = fileManager.saveBitmap(warped)

            val processed = filters.applyFilter(warped, FilterType.MAGIC_COLOR)
            val processedPath = fileManager.saveBitmap(processed)

            val existingPages = repository.getPagesListForDocument(docId)
            val newPage = Page(
                documentId = docId,
                orderIndex = existingPages.size,
                originalPath = originalPath,
                croppedPath = croppedPath,
                processedPath = processedPath,
                filterType = FilterType.MAGIC_COLOR.name
            )

            val pageId = repository.addPage(newPage)
            activePageId = pageId

            if (_autoOcrEnabled.value) {
                ocrQueue.enqueue(newPage.copy(id = pageId), _defaultOcrScript.value)
            }

            onReady(pageId)
        }
    }

    fun saveWarpedCrop(pageId: Long, quad: Quad, onSaved: () -> Unit) {
        viewModelScope.launch {
            val page = repository.getPageById(pageId) ?: return@launch
            val original = activeCapturedBitmap ?: fileManager.loadBitmap(page.originalPath) ?: return@launch

            val warped = warper.warp(original, quad)
            activeCroppedBitmap = warped
            val croppedPath = fileManager.saveBitmap(warped)

            val filterType = try { FilterType.valueOf(page.filterType) } catch (_: Exception) { FilterType.MAGIC_COLOR }
            val processed = filters.applyFilter(
                source = warped,
                filterType = filterType,
                brightness = page.brightness,
                contrast = page.contrast,
                sharpness = page.sharpness,
                rotation = page.rotation
            )
            val processedPath = fileManager.saveBitmap(processed)

            val updated = page.copy(
                croppedPath = croppedPath,
                processedPath = processedPath
            )
            repository.updatePage(updated)

            if (_autoOcrEnabled.value) {
                ocrQueue.enqueue(updated, _defaultOcrScript.value)
            }

            onSaved()
        }
    }

    fun saveFilters(
        pageId: Long,
        filterType: FilterType,
        brightness: Float,
        contrast: Float,
        sharpness: Float,
        rotation: Int,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            val page = repository.getPageById(pageId) ?: return@launch
            val cropped = activeCroppedBitmap ?: fileManager.loadBitmap(page.croppedPath) ?: return@launch

            val processed = filters.applyFilter(
                source = cropped,
                filterType = filterType,
                brightness = brightness,
                contrast = contrast,
                sharpness = sharpness,
                rotation = rotation
            )
            val processedPath = fileManager.saveBitmap(processed)

            val updated = page.copy(
                processedPath = processedPath,
                filterType = filterType.name,
                brightness = brightness,
                contrast = contrast,
                sharpness = sharpness,
                rotation = rotation
            )
            repository.updatePage(updated)

            if (_autoOcrEnabled.value) {
                ocrQueue.enqueue(updated, _defaultOcrScript.value)
            }

            onSaved()
        }
    }

    // ID Card Scan Handling (Phase 3)
    fun processIdCardSide(bitmap: Bitmap, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            if (_idCardScanStep.value == 0) {
                // Front Side captured
                idCardFrontBitmap = bitmap
                if (selectedIdCardType.isDualSided) {
                    _idCardScanStep.value = 1
                    showMessage("Front side captured. Now scan the Back side.")
                } else {
                    // Single sided: compose immediately
                    completeIdCardComposite(onComplete)
                }
            } else {
                // Back Side captured
                idCardBackBitmap = bitmap
                completeIdCardComposite(onComplete)
            }
        }
    }

    private suspend fun completeIdCardComposite(onComplete: (Long) -> Unit) {
        val front = idCardFrontBitmap ?: return
        val back = idCardBackBitmap
        val composite = idCardComposer.createA4Composite(
            frontBitmap = front,
            backBitmap = back,
            cardType = selectedIdCardType,
            layout = IdCompositeLayout.STACKED_VERTICAL
        )

        val docId = repository.createDocument(
            title = "${selectedIdCardType.label} (${SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date())})",
            folderId = _currentFolderId.value,
            docType = if (selectedIdCardType == IdCardType.PASSPORT) "passport" else "id_card"
        )
        activeDocumentId = docId

        val savedPath = fileManager.saveBitmap(composite, quality = 95)
        val newPage = Page(
            documentId = docId,
            orderIndex = 0,
            originalPath = savedPath,
            croppedPath = savedPath,
            processedPath = savedPath
        )
        val pageId = repository.addPage(newPage)
        activePageId = pageId

        // Reset ID card state
        idCardFrontBitmap = null
        idCardBackBitmap = null
        _idCardScanStep.value = 0

        showMessage("ID Card composite created at true physical size (300 DPI)")
        onComplete(docId)
    }

    fun resetIdCardScan() {
        idCardFrontBitmap = null
        idCardBackBitmap = null
        _idCardScanStep.value = 0
    }

    // Document Locking & Encryption
    fun toggleLockDocument(docId: Long, lockMode: String = "biometric", password: String? = null) {
        viewModelScope.launch {
            val isNowLocked = repository.toggleLockDocument(docId, lockMode, password)
            if (isNowLocked) {
                showMessage("Document encrypted with AES-256 and locked")
            } else {
                securityManager.markDocumentUnlocked(docId)
                showMessage("Document unlocked and decrypted")
            }
        }
    }

    fun unlockDocumentSession(docId: Long) {
        securityManager.markDocumentUnlocked(docId)
    }

    fun isDocumentLockedForUser(doc: Document): Boolean {
        return doc.isLocked && !securityManager.isDocumentUnlocked(doc.id)
    }

    // Search logic with 300ms debounce
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            kotlinx.coroutines.delay(300)
            if (_searchQuery.value == query) {
                val results = repository.search(query)
                _searchResults.value = results
                _isSearching.value = false
            }
        }
    }

    // Import from Gallery
    fun importImagesFromUris(uris: List<Uri>, targetDocId: Long? = null) {
        viewModelScope.launch {
            if (uris.isEmpty()) return@launch
            val docId = targetDocId ?: repository.createDocument("Imported Scan ${System.currentTimeMillis() % 10000}", _currentFolderId.value)
            activeDocumentId = docId

            for (uri in uris) {
                val internalPath = fileManager.copyUriToInternal(uri)
                val bitmap = fileManager.loadBitmap(internalPath) ?: continue

                val detection = detector.detectDocument(bitmap)
                val warped = warper.warp(bitmap, detection.quad)
                val croppedPath = fileManager.saveBitmap(warped)
                val processed = filters.applyFilter(warped, FilterType.MAGIC_COLOR)
                val processedPath = fileManager.saveBitmap(processed)

                val existingPages = repository.getPagesListForDocument(docId)
                val page = Page(
                    documentId = docId,
                    orderIndex = existingPages.size,
                    originalPath = internalPath,
                    croppedPath = croppedPath,
                    processedPath = processedPath,
                    filterType = FilterType.MAGIC_COLOR.name
                )
                val pageId = repository.addPage(page)
                if (_autoOcrEnabled.value) {
                    ocrQueue.enqueue(page.copy(id = pageId), _defaultOcrScript.value)
                }
            }

            showMessage("Imported ${uris.size} page(s)")
            _currentScreen.value = Screen.PageGrid(docId)
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            repository.deleteDocument(id)
            clearSelection()
            showMessage("Document deleted")
        }
    }

    fun bulkDeleteSelected() {
        viewModelScope.launch {
            val ids = _selectedDocumentIds.value.toList()
            ids.forEach { repository.deleteDocument(it) }
            clearSelection()
            showMessage("Deleted ${ids.size} document(s)")
        }
    }

    fun bulkMergeSelected(title: String) {
        viewModelScope.launch {
            val ids = _selectedDocumentIds.value.toList()
            if (ids.size < 2) {
                showMessage("Select at least 2 documents to merge")
                return@launch
            }
            val newDocId = pdfTools.mergeDocuments(ids, title)
            clearSelection()
            showMessage("Documents merged successfully")
            _currentScreen.value = Screen.PageGrid(newDocId)
        }
    }

    fun bulkMoveSelected(folderId: Long?) {
        viewModelScope.launch {
            val ids = _selectedDocumentIds.value.toList()
            for (id in ids) {
                val doc = repository.getDocumentById(id) ?: continue
                repository.updateDocument(doc.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
            }
            clearSelection()
            showMessage("Moved ${ids.size} document(s)")
        }
    }
}
