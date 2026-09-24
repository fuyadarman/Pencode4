package com.example.agent

import com.example.data.ProjectEntity
import com.example.data.ProjectFileEntity
import com.example.data.VibeRepository
import java.io.File

/**
 * AgentFileCreationResolver
 * Resolves file creation requests intelligently when a target file already exists.
 * If the existing file has 30 or fewer lines (e.g. empty stub, starter file), updates it safely.
 * If the file exceeds 30 lines, guides the AI with the file's current content for surgical editing.
 */
object AgentFileCreationResolver {

    sealed class FileCreationDecision {
        data class Saved(val message: String, val rangeDesc: String) : FileCreationDecision()
        data class RejectionWithContent(val message: String) : FileCreationDecision()
        data class Error(val message: String) : FileCreationDecision()
    }

    suspend fun resolveExistingFileCreation(
        filePath: String,
        newContent: String,
        targetFile: ProjectFileEntity?,
        fileOnDisk: File?,
        project: ProjectEntity,
        repository: VibeRepository,
        isOverwriteAllowed: Boolean = false
    ): FileCreationDecision {
        val existingContent = targetFile?.content ?: if (fileOnDisk != null && fileOnDisk.exists()) fileOnDisk.readText() else ""
        val lineCount = if (existingContent.isBlank()) 0 else existingContent.lines().size
        val snippet = existingContent.take(2500)

        // 1. By default, create_file MUST error if TargetFile already exists unless overwrite is explicitly true
        if (!isOverwriteAllowed) {
            val guidance = "Error: TargetFile '$filePath' already exists ($lineCount lines).\n" +
                    "By default, 'create_file' fails if the file already exists. To modify an existing file, use 'edit_file' or 'multi_edit_file'.\n" +
                    "To overwrite an existing file (only permitted if 30 or fewer lines), set 'overwrite: true'.\n\n" +
                    "Current content of '$filePath' for context:\n```\n$snippet\n```\n" +
                    "Please use 'edit_file' or 'multi_edit_file' to modify this file."
            return FileCreationDecision.RejectionWithContent(guidance)
        }

        // 2. If overwrite is true, enforce Rule 1 (files with > 30 lines cannot be overwritten or replaced)
        if (lineCount > 30) {
            val guidance = "Error: File '$filePath' already exists and contains $lineCount lines (more than 30 lines).\n" +
                    "Under Rule 1, you are STRICTLY FORBIDDEN from recreating, overwriting, or replacing files with more than 30 lines.\n" +
                    "You MUST use 'edit_file' or 'multi_edit_file' with unique search and replace blocks.\n\n" +
                    "Current content of '$filePath' for context:\n```\n$snippet\n```\n" +
                    "Please call 'edit_file' with exact search and replace blocks."
            return FileCreationDecision.RejectionWithContent(guidance)
        }

        // 3. If overwrite is true and line count <= 30 lines, overwriting is permitted
        return try {
            repository.saveFile(project.name, filePath, newContent)
            val linesInNew = newContent.lines().size
            val range = if (linesInNew <= 1) "line 1" else "lines 1-$linesInNew"
            FileCreationDecision.Saved(
                message = "Successfully overwrote '$filePath' ($lineCount lines previously). Content is saved. DO NOT re-read this file to verify. If all requested changes are done, call 'complete'.",
                rangeDesc = range
            )
        } catch (e: Exception) {
            FileCreationDecision.Error("Error overwriting existing file '$filePath': ${e.localizedMessage}")
        }
    }
}
