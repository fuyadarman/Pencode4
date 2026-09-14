package com.example.document

import android.util.Base64
import com.example.data.ProjectEntity
import com.example.data.VibeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * DocumentExplorerSyncHelper
 * Ensures generated documents (PDF, Markdown, HTML, etc.) are immediately synced
 * into Room database and visible in the File Explorer and editor workspace.
 */
object DocumentExplorerSyncHelper {

    suspend fun syncGeneratedDocument(
        projectName: String,
        repository: VibeRepository,
        result: GeneratedDocumentResult
    ) = withContext(Dispatchers.IO) {
        try {
            val generatedFile = File(result.filePath)
            if (generatedFile.exists()) {
                val relPath = generatedFile.name
                if (result.type == DocumentType.PDF) {
                    val bytes = generatedFile.readBytes()
                    val base64 = "data:application/pdf;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                    repository.saveFile(projectName, relPath, base64)
                } else {
                    val content = generatedFile.readText(Charsets.UTF_8)
                    repository.saveFile(projectName, relPath, content)
                }
            }
            // Sync filesystem changes into database
            repository.syncStorageToDatabase(projectName)
        } catch (e: Exception) {
            // Non-fatal error during sync
        }
    }
}
