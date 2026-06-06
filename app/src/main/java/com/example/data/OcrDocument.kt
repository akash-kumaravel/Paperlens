package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "ocr_documents")
data class OcrDocument(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val ocrText: String,
    val date: Long = System.currentTimeMillis(),
    val sourceType: String, // "IMAGE", "PDF", "CAMERA"
    val exportPathDocx: String? = null,
    val exportPathPdf: String? = null,
    val thumbnailUri: String? = null // local file path to thumbnail image of scanned page
) : Serializable
