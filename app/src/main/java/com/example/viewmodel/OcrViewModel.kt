package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.OcrDocument
import com.example.data.OcrRepository
import com.example.export.DocxExporter
import com.example.export.PdfExporter
import com.example.ocr.MlKitOcrEngine
import com.example.ocr.OcrEngine
import com.example.ocr.PaddleOcrEngine
import com.example.pdf.PdfHelper
import com.example.pdf.PdfMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class OcrViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: OcrRepository
    
    // UI state flows
    val documents: StateFlow<List<OcrDocument>>
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedDocTypeFilter = MutableStateFlow<String?>(null) // "IMAGE", "PDF"
    val selectedDocTypeFilter = _selectedDocTypeFilter.asStateFlow()

    // Config preferences
    val appTheme = MutableStateFlow("System Default") // "Light", "Dark", "System Default"
    val ocrEngineType = MutableStateFlow("Local Standard Engine") // "Local Standard Engine", "PaddleOCR 3.x Mobile"
    val ocrLanguage = MutableStateFlow("Latin / Multilingual")
    val recognitionQuality = MutableStateFlow("High Quality")

    // Active screen processing context
    val activeBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap = MutableStateFlow<Bitmap?>(null) // For crop/reset actions
    val processingStatus = MutableStateFlow("")
    val ocrProgressPercent = MutableStateFlow(0f)
    val activeScanSourceType = MutableStateFlow("CAMERA") // "CAMERA", "IMAGE", "PDF"
    val activePdfMetadata = MutableStateFlow<PdfMetadata?>(null)
    val activePdfUri = MutableStateFlow<Uri?>(null)
    val activePdfPagesToOcr = MutableStateFlow<List<Int>>(emptyList())
    
    // Result editing states (Undo/Redo buffers)
    val ocrResultText = MutableStateFlow("")
    val documentTitle = MutableStateFlow("New Scanned Document")
    private val undoStack = java.util.Stack<String>()
    private val redoStack = java.util.Stack<String>()

    // Selected active document for view/edit detail
    val activeDocument = MutableStateFlow<OcrDocument?>(null)

    init {
        val database = AppDatabase.getDatabase(application)
        repository = OcrRepository(database.ocrDocumentDao())
        
        // Search & filter combined flow for History and Home
        documents = combine(
            repository.allDocuments,
            _searchQuery,
            _selectedDocTypeFilter
        ) { rawDocs, query, typeFilter ->
            var items = rawDocs
            if (query.isNotBlank()) {
                items = items.filter { 
                    it.name.contains(query, ignoreCase = true) || 
                    it.ocrText.contains(query, ignoreCase = true) 
                }
            }
            if (typeFilter != null) {
                items = items.filter { it.sourceType == typeFilter }
            }
            items
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    // Filter operations
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateDocTypeFilter(filter: String?) {
        _selectedDocTypeFilter.value = filter
    }

    fun updateTheme(themeName: String) {
        appTheme.value = themeName
    }

    fun updateEngine(engineName: String) {
        ocrEngineType.value = engineName
    }

    // Interactive edit actions
    fun setOcrResultText(newText: String) {
        if (ocrResultText.value != newText) {
            undoStack.push(ocrResultText.value)
            redoStack.clear()
            ocrResultText.value = newText
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.push(ocrResultText.value)
            ocrResultText.value = undoStack.pop()
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.push(ocrResultText.value)
            ocrResultText.value = redoStack.pop()
        }
    }

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    // Core OCR triggers
    fun startImageOcr(bitmap: Bitmap, sourceType: String, onComplete: (Long) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            processingStatus.value = "Preparing Document Canvas..."
            ocrProgressPercent.value = 10f
            originatingSourceType = sourceType
            activeBitmap.value = bitmap
            
            val engine: OcrEngine = if (ocrEngineType.value == "Local Standard Engine") {
                MlKitOcrEngine(getApplication())
            } else {
                PaddleOcrEngine(getApplication())
            }

            try {
                ocrProgressPercent.value = 30f
                val text = withContext(Dispatchers.Default) {
                    engine.recognizeText(bitmap) { step ->
                        viewModelScope.launch(Dispatchers.Main) {
                            processingStatus.value = step
                            if (ocrProgressPercent.value < 90f) {
                                ocrProgressPercent.value += 12f
                            }
                        }
                    }
                }
                
                ocrProgressPercent.value = 100f
                ocrResultText.value = text
                undoStack.clear()
                redoStack.clear()
                val timestamp = System.currentTimeMillis()
                documentTitle.value = "Doc_$timestamp"
                
                // Write local JPEG thumbnail of the document
                val thumbnailPath = saveThumbnailLocally(bitmap)
                
                // Save document base to local database
                val newDoc = OcrDocument(
                    name = documentTitle.value,
                    ocrText = text,
                    date = timestamp,
                    sourceType = sourceType,
                    thumbnailUri = thumbnailPath
                )
                
                val docId = repository.insertDocument(newDoc)
                activeDocument.value = newDoc.copy(id = docId)
                
                onComplete(docId)
            } catch (e: Exception) {
                Log.e("OcrViewModel", "OCR execution crash", e)
                onError("Failed during image text extraction: ${e.message}")
            }
        }
    }

    private var originatingSourceType = "CAMERA"

    // PDF processing steps
    fun selectPdf(uri: Uri): Boolean {
        val metadata = PdfHelper.getMetadata(getApplication(), uri) ?: return false
        activePdfMetadata.value = metadata
        activePdfUri.value = uri
        activePdfPagesToOcr.value = (0 until metadata.pageCount).toList()
        return true
    }

    fun startPdfOcr(onComplete: (Long) -> Unit, onError: (String) -> Unit) {
        val uri = activePdfUri.value ?: return onError("No PDF File loaded")
        val pages = activePdfPagesToOcr.value
        if (pages.isEmpty()) return onError("Please select page limits to process.")
        
        viewModelScope.launch {
            val stringBuilder = java.lang.StringBuilder()
            processingStatus.value = "Initializing PDF Reader..."
            ocrProgressPercent.value = 10f
            
            val engine: OcrEngine = if (ocrEngineType.value == "Local Standard Engine") {
                MlKitOcrEngine(getApplication())
            } else {
                PaddleOcrEngine(getApplication())
            }

            try {
                var firstBitmap: Bitmap? = null
                val total = pages.size.toFloat()
                
                for ((index, pageIndex) in pages.withIndex()) {
                    processingStatus.value = "Rendering page ${pageIndex + 1} of ${pages.size}..."
                    ocrProgressPercent.value = 10f + (index / total) * 30f
                    
                    val bitmap = withContext(Dispatchers.IO) {
                        PdfHelper.renderPageToBitmap(getApplication(), uri, pageIndex)
                    }
                    
                    if (bitmap != null) {
                        if (firstBitmap == null) firstBitmap = bitmap
                        
                        processingStatus.value = "Scanning page ${pageIndex + 1} for text..."
                        val pageText = withContext(Dispatchers.Default) {
                            engine.recognizeText(bitmap) { step ->
                                viewModelScope.launch(Dispatchers.Main) {
                                    processingStatus.value = "Page ${pageIndex + 1}: $step"
                                }
                            }
                        }
                        
                        stringBuilder.append("--- Page ${pageIndex + 1} ---\n")
                        stringBuilder.append(pageText).append("\n\n")
                    }
                }
                
                ocrProgressPercent.value = 90f
                processingStatus.value = "Formatting and saving document..."
                
                val finalOcrText = stringBuilder.toString()
                ocrResultText.value = finalOcrText
                undoStack.clear()
                redoStack.clear()
                
                val timestamp = System.currentTimeMillis()
                documentTitle.value = activePdfMetadata.value?.fileName?.substringBeforeLast(".") ?: "Doc_$timestamp"
                
                val thumbnailPath = firstBitmap?.let { saveThumbnailLocally(it) }
                
                val newDoc = OcrDocument(
                    name = documentTitle.value,
                    ocrText = finalOcrText,
                    date = timestamp,
                    sourceType = "PDF",
                    thumbnailUri = thumbnailPath
                )
                
                val docId = repository.insertDocument(newDoc)
                activeDocument.value = newDoc.copy(id = docId)
                
                ocrProgressPercent.value = 100f
                onComplete(docId)
            } catch (e: Exception) {
                Log.e("OcrViewModel", "PDF OCR error", e)
                onError("PDF Processing Error: ${e.message}")
            }
        }
    }

    // Exporters
    fun exportResultDocx(context: Context, docName: String, text: String, onComplete: (File) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                DocxExporter.exportToDocx(context, docName, text)
            }
            if (file != null) {
                // Update file path references inside cached database documents to enable sharing/re-export
                activeDocument.value?.let { currentDoc ->
                    val updated = currentDoc.copy(exportPathDocx = file.absolutePath)
                    repository.updateDocument(updated)
                    activeDocument.value = updated
                }
                onComplete(file)
            } else {
                onError("Failed to compile local Word Docx file. Check storage limits.")
            }
        }
    }

    fun exportResultPdf(context: Context, docName: String, text: String, onComplete: (File) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                PdfExporter.exportToPdf(context, docName, text)
            }
            if (file != null) {
                activeDocument.value?.let { currentDoc ->
                    val updated = currentDoc.copy(exportPathPdf = file.absolutePath)
                    repository.updateDocument(updated)
                    activeDocument.value = updated
                }
                onComplete(file)
            } else {
                onError("Failed to compile local searchable PDF. Please verify memory space.")
            }
        }
    }

    // Document Database updates
    fun updateActiveDocumentTitle(newName: String) {
        viewModelScope.launch {
            val current = activeDocument.value ?: return@launch
            val updated = current.copy(name = newName)
            repository.updateDocument(updated)
            activeDocument.value = updated
            documentTitle.value = newName
        }
    }

    fun deleteDocument(document: OcrDocument) {
        viewModelScope.launch {
            repository.deleteDocument(document)
            // Delete accompanying thumbnail locally to save space
            document.thumbnailUri?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
        }
    }

    fun selectDocument(document: OcrDocument) {
        activeDocument.value = document
        documentTitle.value = document.name
        ocrResultText.value = document.ocrText
        undoStack.clear()
        redoStack.clear()
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export Document via:"))
        } catch (e: Exception) {
            Log.e("OcrViewModel", "Sharing file failed", e)
        }
    }

    private fun saveThumbnailLocally(bitmap: Bitmap): String? {
        return try {
            val cacheFile = File(getApplication<Application>().cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
            val out = FileOutputStream(cacheFile)
            // Resize thumbnail for fast performance
            val resized = Bitmap.createScaledBitmap(bitmap, 180, 240, true)
            resized.compress(Bitmap.CompressFormat.JPEG, 75, out)
            out.flush()
            out.close()
            cacheFile.absolutePath
        } catch (e: java.lang.Exception) {
            null
        }
    }
}
