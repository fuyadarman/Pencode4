package com.example.document

import android.content.Context
import java.io.File

object UniversalDocumentManager {

    fun generateDocument(
        context: Context,
        type: DocumentType,
        title: String,
        content: String,
        style: DocumentStyleConfig,
        destinationDir: File,
        customFileName: String? = null
    ): GeneratedDocumentResult {
        destinationDir.mkdirs()

        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").trim('_')
        val baseName = if (!customFileName.isNullOrBlank()) {
            if (customFileName.endsWith(".${type.extension}")) customFileName else "$customFileName.${type.extension}"
        } else {
            "${sanitizedTitle.ifBlank { "document" }}_${System.currentTimeMillis()}.${type.extension}"
        }

        val targetFile = File(destinationDir, baseName)

        return when (type) {
            DocumentType.PDF -> {
                NativePdfGenerator.generatePdf(
                    context = context,
                    title = title,
                    content = content,
                    style = style,
                    outputFile = targetFile
                )
            }
            DocumentType.HTML -> {
                WebHtmlDocumentGenerator.generateHtml(
                    title = title,
                    content = content,
                    style = style,
                    outputFile = targetFile
                )
            }
            DocumentType.MARKDOWN, DocumentType.PLAIN_TEXT, DocumentType.CSV, DocumentType.JSON -> {
                targetFile.writeText(content, Charsets.UTF_8)
                GeneratedDocumentResult(
                    filePath = targetFile.absolutePath,
                    fileName = targetFile.name,
                    type = type,
                    sizeBytes = targetFile.length(),
                    message = "${type.displayName} created successfully"
                )
            }
        }
    }

    fun modifyDocumentContent(
        targetFile: File,
        search: String,
        replace: String
    ): Boolean {
        if (!targetFile.exists()) return false
        val current = targetFile.readText(Charsets.UTF_8)
        if (!current.contains(search)) return false
        val modified = current.replace(search, replace)
        targetFile.writeText(modified, Charsets.UTF_8)
        return true
    }

    fun appendToDocument(
        targetFile: File,
        contentToAppend: String
    ): Boolean {
        if (!targetFile.exists()) return false
        targetFile.appendText("\n$contentToAppend", Charsets.UTF_8)
        return true
    }
}
