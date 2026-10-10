package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class FileManager(private val context: Context) {

    private val imagesDir: File by lazy {
        File(context.filesDir, "scans").apply {
            if (!exists()) mkdirs()
        }
    }

    private val pdfDir: File by lazy {
        File(context.filesDir, "pdfs").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun saveBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 90): String = withContext(Dispatchers.IO) {
        val fileName = "scan_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
        val file = File(imagesDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(format, quality, out)
        }
        file.absolutePath
    }

    suspend fun copyUriToInternal(uri: Uri): String = withContext(Dispatchers.IO) {
        val fileName = "import_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
        val destFile = File(imagesDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        destFile.absolutePath
    }

    suspend fun loadBitmap(path: String, maxDimension: Int = 4000): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null

        // Safe decode with bounds checking to avoid OOM
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)

        var sampleSize = 1
        val maxSide = maxOf(options.outWidth, options.outHeight)
        while (maxSide / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(path, decodeOptions)
    }

    fun getNewPdfFile(name: String): File {
        val safeName = name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(pdfDir, "${safeName}_${System.currentTimeMillis()}.pdf")
    }

    fun deleteFile(path: String) {
        try {
            File(path).delete()
        } catch (_: Exception) {}
    }
}
