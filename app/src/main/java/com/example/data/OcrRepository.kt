package com.example.data

import kotlinx.coroutines.flow.Flow

class OcrRepository(private val dao: OcrDocumentDao) {
    val allDocuments: Flow<List<OcrDocument>> = dao.getAllDocuments()

    fun searchDocuments(query: String): Flow<List<OcrDocument>> {
        return dao.searchDocuments("%$query%")
    }

    suspend fun getDocumentById(id: Long): OcrDocument? {
        return dao.getDocumentById(id)
    }

    suspend fun insertDocument(document: OcrDocument): Long {
        return dao.insertDocument(document)
    }

    suspend fun updateDocument(document: OcrDocument) {
        dao.updateDocument(document)
    }

    suspend fun deleteDocument(document: OcrDocument) {
        dao.deleteDocument(document)
    }

    suspend fun deleteDocumentById(id: Long) {
        dao.deleteDocumentById(id)
    }
}
