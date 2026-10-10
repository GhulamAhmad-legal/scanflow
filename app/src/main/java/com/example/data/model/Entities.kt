package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isLocked: Boolean = false,
    val lockMode: String = "none", // none, biometric, password
    val kdfSalt: String = ""
)

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val folderId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isLocked: Boolean = false,
    val lockMode: String = "none", // none, biometric, password
    val kdfSalt: String = "",
    val docType: String = "standard" // standard, id_card, passport
)

enum class FilterType {
    ORIGINAL,
    MAGIC_COLOR,
    BLACK_WHITE,
    GRAYSCALE,
    LIGHTEN
}

@Entity(tableName = "pages")
data class Page(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val orderIndex: Int,
    val originalPath: String,
    val croppedPath: String,
    val processedPath: String,
    val filterType: String = FilterType.MAGIC_COLOR.name,
    val cornersJson: String = "",
    val rotation: Int = 0, // 0, 90, 180, 270
    val brightness: Float = 0f, // -100 to 100
    val contrast: Float = 1f,   // 0.5 to 2.0
    val sharpness: Float = 0f,  // 0 to 2.0
    val isEncrypted: Boolean = false
)

enum class OcrStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED
}

@Entity(tableName = "ocr_results")
data class OcrResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pageId: Long,
    val text: String = "",
    val editedText: String = "",
    val blocksJson: String = "",
    val script: String = "Latin",
    val status: String = OcrStatus.PENDING.name,
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val effectiveText: String
        get() = if (editedText.isNotBlank()) editedText else text
}

// Phase 3 Entities
@Entity(tableName = "signatures")
data class SignatureItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val path: String,
    val type: String = "signature", // signature, initials, scanned
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "annotations")
data class AnnotationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pageId: Long,
    val json: String = "[]",
    val placedSignaturesJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watermark_settings")
data class WatermarkSetting(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val configJson: String = ""
)
