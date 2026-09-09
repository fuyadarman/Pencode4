package com.example.document

import android.content.Context
import com.example.api.ToolArguments
import com.example.data.ProjectEntity
import com.example.data.VibeRepository
import com.example.ui.AiActionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DocumentToolHandler {

    suspend fun handleDocumentTool(
        tool: String,
        args: ToolArguments?,
        project: ProjectEntity,
        repository: VibeRepository,
        context: Context,
        createLog: (title: String, status: String, details: String, lineRange: String?) -> AiActionLog,
        addLog: (AiActionLog) -> Unit,
        updateLog: (id: String, status: String, details: String) -> Unit,
        setAgentStatus: (String) -> Unit,
        normalizePath: (String) -> String
    ): String {
        return when (tool) {
            "generate_pdf" -> {
                val title = args?.title ?: args?.query ?: "Document"
                val content = args?.content ?: args?.message ?: args?.prompt ?: ""
                val themeId = (args?.theme ?: "modern").lowercase()
                val customPath = args?.path

                val selectedTheme = when (themeId) {
                    "elegant" -> DocumentTheme.ELEGANT
                    "minimal" -> DocumentTheme.MINIMAL
                    "cyberpunk" -> DocumentTheme.CYBERPUNK
                    "dark" -> DocumentTheme.DARK_PROFESSIONAL
                    else -> DocumentTheme.MODERN
                }

                val docLog = createLog(
                    "Generate Styled PDF",
                    "thinking",
                    "Generating PDF '$title' with theme ${selectedTheme.displayName}",
                    "document"
                )
                addLog(docLog)
                setAgentStatus("Generating styled PDF document: $title...")

                try {
                    val result = withContext(Dispatchers.IO) {
                        val baseDir = File(context.filesDir, "projects/${project.name}")
                        val style = DocumentStyleConfig(
                            theme = selectedTheme,
                            fontSize = 14,
                            titleFontSize = 24,
                            showHeader = true,
                            showFooter = true,
                            showPageNumbers = true,
                            author = "PenCode AI"
                        )
                        UniversalDocumentManager.generateDocument(
                            context = context,
                            type = DocumentType.PDF,
                            title = title,
                            content = content,
                            style = style,
                            destinationDir = baseDir,
                            customFileName = customPath
                        )
                    }

                    updateLog(
                        docLog.id,
                        "success",
                        "PDF generated successfully: ${result.fileName} (${result.sizeBytes} bytes, ${result.pageCount} pages)"
                    )
                    "Success: PDF generated at '${result.fileName}'. Path: ${result.filePath}. Pages: ${result.pageCount}. Total size: ${result.sizeBytes} bytes."
                } catch (e: Exception) {
                    val errorMsg = "Error generating PDF: ${e.localizedMessage}"
                    updateLog(docLog.id, "failed", errorMsg)
                    errorMsg
                }
            }

            "generate_document" -> {
                val rawType = (args?.type ?: "pdf").lowercase()
                val docType = when (rawType) {
                    "html", "web" -> DocumentType.HTML
                    "md", "markdown" -> DocumentType.MARKDOWN
                    "txt", "text" -> DocumentType.PLAIN_TEXT
                    "csv" -> DocumentType.CSV
                    "json" -> DocumentType.JSON
                    else -> DocumentType.PDF
                }
                val title = args?.title ?: args?.query ?: "Document"
                val content = args?.content ?: args?.message ?: args?.prompt ?: ""
                val themeId = (args?.theme ?: "modern").lowercase()
                val customPath = args?.path

                val selectedTheme = when (themeId) {
                    "elegant" -> DocumentTheme.ELEGANT
                    "minimal" -> DocumentTheme.MINIMAL
                    "cyberpunk" -> DocumentTheme.CYBERPUNK
                    "dark" -> DocumentTheme.DARK_PROFESSIONAL
                    else -> DocumentTheme.MODERN
                }

                val docLog = createLog(
                    "Generate Document (${docType.extension.uppercase()})",
                    "thinking",
                    "Generating ${docType.displayName}: '$title'",
                    "document"
                )
                addLog(docLog)
                setAgentStatus("Generating ${docType.displayName}...")

                try {
                    val result = withContext(Dispatchers.IO) {
                        val baseDir = File(context.filesDir, "projects/${project.name}")
                        val style = DocumentStyleConfig(
                            theme = selectedTheme,
                            author = "PenCode AI"
                        )
                        UniversalDocumentManager.generateDocument(
                            context = context,
                            type = docType,
                            title = title,
                            content = content,
                            style = style,
                            destinationDir = baseDir,
                            customFileName = customPath
                        )
                    }

                    updateLog(
                        docLog.id,
                        "success",
                        "${docType.displayName} generated: ${result.fileName} (${result.sizeBytes} bytes)"
                    )
                    "Success: ${docType.displayName} generated at '${result.fileName}'. File path: ${result.filePath}. Total size: ${result.sizeBytes} bytes."
                } catch (e: Exception) {
                    val errorMsg = "Error generating ${docType.displayName}: ${e.localizedMessage}"
                    updateLog(docLog.id, "failed", errorMsg)
                    errorMsg
                }
            }

            else -> "Error: Unknown document tool '$tool'"
        }
    }
}
