package com.example.export

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxExporter {

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private const val RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    fun exportToDocx(context: Context, fileName: String, text: String): File? {
        try {
            // Store exports in public Documents directory or cache directory for easy sharing
            val dDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) 
                ?: context.filesDir
            
            val validName = if (fileName.endsWith(".docx")) fileName else "$fileName.docx"
            val file = File(dDir, validName)
            val fos = FileOutputStream(file)
            val zos = ZipOutputStream(fos)

            // 1. Write [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(CONTENT_TYPES_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Write _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(RELS_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. Generate and Write word/document.xml
            zos.putNextEntry(ZipEntry("word/document.xml"))
            val documentXml = generateDocumentXml(text)
            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.close()
            fos.close()
            
            Log.d("DocxExporter", "Successfully exported DOCX to ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e("DocxExporter", "Failed to export DOCX", e)
            return null
        }
    }

    private fun generateDocumentXml(text: String): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        sb.append("<w:body>")

        // Split text by lines to construct paragraphs properly
        val paragraphs = text.split("\n")
        for (paragraphText in paragraphs) {
            val sanitized = sanitizeXmlString(paragraphText)
            sb.append("<w:p>")
            sb.append("<w:r>")
            sb.append("<w:t>").append(sanitized).append("</w:t>")
            sb.append("</w:r>")
            sb.append("</w:p>")
        }

        sb.append("<w:sectPr>")
        sb.append("<w:pgSz w:w=\"11906\" w:h=\"16838\"/>") // A4 standard size in twentieths of a point (dxa)
        sb.append("<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/>") // Standard 1-inch margins
        sb.append("</w:sectPr>")
        sb.append("</w:body>")
        sb.append("</w:document>")

        return sb.toString()
    }

    private fun sanitizeXmlString(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
