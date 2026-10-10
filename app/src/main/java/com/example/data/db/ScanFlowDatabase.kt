package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AnnotationRecord
import com.example.data.model.Document
import com.example.data.model.Folder
import com.example.data.model.OcrResult
import com.example.data.model.Page
import com.example.data.model.SignatureItem
import com.example.data.model.WatermarkSetting

@Database(
    entities = [
        Folder::class,
        Document::class,
        Page::class,
        OcrResult::class,
        SignatureItem::class,
        AnnotationRecord::class,
        WatermarkSetting::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ScanFlowDatabase : RoomDatabase() {
    abstract fun scanFlowDao(): ScanFlowDao

    companion object {
        @Volatile
        private var INSTANCE: ScanFlowDatabase? = null

        fun getDatabase(context: Context): ScanFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScanFlowDatabase::class.java,
                    "scanflow.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
