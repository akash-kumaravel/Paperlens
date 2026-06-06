package com.example.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    fun exportToPdf(context: Context, fileName: String, text: String): File? {
        try {
            val document = PdfDocument()
            
            // Standard A4 dimensions in PostScript points: 595 x 842
            val pageWidth = 595
            val pageHeight = 842
            
            // Text formatting configuration
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                isAntiAlias = true
            }

            val titlePaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 10f
                isAntiAlias = true
                isFakeBoldText = true
            }

            val margin = 54f // 0.75-inch margins
            val usableWidth = pageWidth - (margin * 2)
            val lineSpacing = 16f
            var yPosition = margin + 40f // Leave space for headers
            var pageNumber = 1

            // Helper function to create page
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            // Raw line splitter with auto word wrapping
            val linesToDraw = mutableListOf<String>()
            val rawParagraphs = text.split("\n")
            for (paragraph in rawParagraphs) {
                if (paragraph.isBlank()) {
                    linesToDraw.add("")
                    continue
                }
                val words = paragraph.split(" ")
                var currentLine = StringBuilder()
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
                    val measure = paint.measureText(testLine)
                    if (measure <= usableWidth) {
                        currentLine.append(if (currentLine.isEmpty()) word else " $word")
                    } else {
                        linesToDraw.add(currentLine.toString())
                        currentLine = StringBuilder(word)
                    }
                }
                if (currentLine.isNotEmpty()) {
                    linesToDraw.add(currentLine.toString())
                }
            }

            // Draw header on the first page
            drawHeader(canvas, titlePaint, fileName, pageNumber, pageWidth)

            // Loop and draw
            for (line in linesToDraw) {
                // If the next line exceeds the page limit, make a new page
                if (yPosition + lineSpacing > pageHeight - margin) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    yPosition = margin + 40f
                    drawHeader(canvas, titlePaint, fileName, pageNumber, pageWidth)
                }

                if (line.isNotEmpty()) {
                    canvas.drawText(line, margin, yPosition, paint)
                }
                yPosition += lineSpacing
            }

            document.finishPage(page)

            // Output document properties to local file storage
            val docDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) 
                ?: context.filesDir
            val validName = if (fileName.endsWith(".pdf")) fileName else "$fileName.pdf"
            val file = File(docDir, validName)
            
            val fos = FileOutputStream(file)
            document.writeTo(fos)
            
            fos.close()
            document.close()
            
            Log.d("PdfExporter", "Successfully exported PDF to ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e("PdfExporter", "Error generating searchable PDF", e)
            return null
        }
    }

    private fun drawHeader(canvas: Canvas, paint: Paint, title: String, pageNo: Int, pageWidth: Int) {
        // Subtle decorative header divider line and file tag
        paint.color = Color.LTGRAY
        canvas.drawLine(54f, 45f, pageWidth - 54f, 45f, paint)
        
        paint.color = Color.GRAY
        canvas.drawText("Paper Lens - $title", 54f, 38f, paint)
        canvas.drawText("Page $pageNo", pageWidth - 100f, 38f, paint)
    }
}
