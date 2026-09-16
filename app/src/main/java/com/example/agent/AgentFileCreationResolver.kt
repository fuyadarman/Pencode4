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
        repository: VibeRepository
    ): FileCreationDecision {
        val existingContent = targetFile?.content ?: if (fileOnDisk != null && fileOnDisk.exists()) fileOnDisk.readText() else ""
        val lineCount = if (existingContent.isBlank()) 0 else existingContent.lines().size

        // If existing file is small (<= 30 lines) or empty, overwriting/recreating is permitted under Rule 1
        if (lineCount <= 30) {
            return try {
                repository.saveFile(project.name, filePath, newContent)
                val linesInNew = newContent.lines().size
                val range = if (linesInNew <= 1) "all" else "lines 1-$linesInNew"
                FileCreationDecision.Saved(
                    message = "Successfully updated '$filePath' ($lineCount lines previously). Content is saved. DO NOT re-read this file to verify. If all requested changes are done, call 'complete'.",
                    rangeDesc = range
                )
            } catch (e: Exception) {
                FileCreationDecision.Error("Error updating existing file '$filePath': ${e.localizedMessage}")
            }
        }

        // For files with > 30 lines, enforce Rule 1 (surgical edits only) and provide content
        val snippet = existingContent.take(2500)
        val guidance = "Error: File '$filePath' already exists and contains $lineCount lines (more than 30 lines).\n" +
                "Under project rules, files larger than 30 lines cannot be overwritten with 'create_file'.\n" +
                "You must use 'edit_file' or 'multi_edit_file' with unique 'search' and 'replace' blocks.\n" +
                "Current content of '$filePath' for context:\n```\n$snippet\n```\n" +
                "Please call 'edit_file' with exact search and replace blocks."
        return FileCreationDecision.RejectionWithContent(guidance)
    }
}
