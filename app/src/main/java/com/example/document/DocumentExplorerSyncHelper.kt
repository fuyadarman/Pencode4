package com.example.document

import android.content.Context
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
            // Sync filesystem changes into database
            repository.syncStorageToDatabase(projectName)
        } catch (e: Exception) {
            // Non-fatal error during sync
        }
    }
}
