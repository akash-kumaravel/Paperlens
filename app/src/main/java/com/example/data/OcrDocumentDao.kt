package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrDocumentDao {
    @Query("SELECT * FROM ocr_documents ORDER BY date DESC")
    fun getAllDocuments(): Flow<List<OcrDocument>>

    @Query("SELECT * FROM ocr_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): OcrDocument?

    @Query("SELECT * FROM ocr_documents WHERE name LIKE :query OR ocrText LIKE :query ORDER BY date DESC")
    fun searchDocuments(query: String): Flow<List<OcrDocument>>

    @Query("SELECT * FROM ocr_documents WHERE sourceType = :type ORDER BY date DESC")
    fun getDocumentsBySourceType(type: String): Flow<List<OcrDocument>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: OcrDocument): Long

    @Update
    suspend fun updateDocument(document: OcrDocument)

    @Delete
    suspend fun deleteDocument(document: OcrDocument)

    @Query("DELETE FROM ocr_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)
}
