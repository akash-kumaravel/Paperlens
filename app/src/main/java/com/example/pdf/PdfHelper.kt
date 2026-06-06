package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class PdfMetadata(
    val fileName: String,
    val fileSize: Long,
    val pageCount: Int
)

object PdfHelper {

    fun getMetadata(context: Context, uri: Uri): PdfMetadata? {
        val resolver = context.contentResolver
        var fileName = "document.pdf"
        var fileSize = 0L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }

        try {
            val fileDescriptor = resolver.openFileDescriptor(uri, "r") ?: return null
            val renderer = PdfRenderer(fileDescriptor)
            val pageCount = renderer.pageCount
            renderer.close()
            fileDescriptor.close()

            return PdfMetadata(fileName, fileSize, pageCount)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun renderPageToBitmap(context: Context, uri: Uri, pageIndex: Int): Bitmap? {
        val resolver = context.contentResolver
        try {
            val fileDescriptor = resolver.openFileDescriptor(uri, "r") ?: return null
            val renderer = PdfRenderer(fileDescriptor)
            
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                renderer.close()
                fileDescriptor.close()
                return null
            }

            val page = renderer.openPage(pageIndex)
            
            // Standard default high-quality rendering density (300 DPI or approx 2.0x base page width for high-fidelity OCR)
            val renderWidth = (page.width * 2.0).toInt()
            val renderHeight = (page.height * 2.0).toInt()
            
            val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE) // Ensure opaque white canvas background
            
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            page.close()
            renderer.close()
            fileDescriptor.close()
            
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Copy uri content to a local temporary file because CameraX or other libraries require direct files occasionally
     */
    fun createTempFileFromUri(context: Context, uri: Uri, suffix: String): File? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("ocr_doc", suffix, context.cacheDir)
            tempFile.deleteOnExit()
            val outputStream = FileOutputStream(tempFile)
            val buffer = ByteArray(4 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
