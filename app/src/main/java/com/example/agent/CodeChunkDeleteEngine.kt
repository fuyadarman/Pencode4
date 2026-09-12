package com.example.agent

import com.example.data.ProjectEntity
import com.example.data.VibeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Engine for deleting specific code chunks or code blocks from workspace files.
 * Supports exact block matching, whitespace normalization, and safe removal
 * without corrupting surrounding code or leaving excessive blank lines.
 */
object CodeChunkDeleteEngine {

    sealed class DeleteResult {
        data class Success(val message: String, val deletedCharacters: Int, val filePath: String) : DeleteResult()
        data class Failure(val errorMessage: String) : DeleteResult()
    }

    suspend fun deleteCodeChunk(
        filePath: String,
        codeChunk: String,
        deleteAllOccurrences: Boolean = false,
        project: ProjectEntity,
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): DeleteResult = withContext(Dispatchers.IO) {
        val normPath = normalizePath(filePath)

        if (normPath.isBlank()) {
            return@withContext DeleteResult.Failure("Error: 'path' (or 'filePath') cannot be empty.")
        }
        if (codeChunk.isBlank()) {
            return@withContext DeleteResult.Failure("Error: 'codeChunk' (or 'search'/'block') to delete cannot be empty.")
        }

        val allFiles = repository.getFilesForProject(project.name)
        val targetFile = allFiles.find {
            it.path == normPath || normalizePath(it.path) == normPath || it.path.endsWith(normPath)
        } ?: return@withContext DeleteResult.Failure("Error: File '$normPath' not found in project.")

        var content = targetFile.content

        // Try exact match first
        var targetChunk = codeChunk
        if (!content.contains(targetChunk)) {
            val normContent = content.replace("\r\n", "\n")
            val normChunk = targetChunk.replace("\r\n", "\n")
            if (normContent.contains(normChunk)) {
                content = normContent
                targetChunk = normChunk
            } else {
                // Try trimmed match
                val trimmedChunk = normChunk.trim()
                if (trimmedChunk.isNotEmpty() && normContent.contains(trimmedChunk)) {
                    content = normContent
                    targetChunk = trimmedChunk
                } else {
                    return@withContext DeleteResult.Failure(
                        "Error: Could not locate the specified code chunk in '$normPath'. " +
                                "Ensure you provide the exact code block as it currently exists in the file."
                    )
                }
            }
        }

        val occurrences = content.split(targetChunk).size - 1
        if (occurrences > 1 && !deleteAllOccurrences) {
            return@withContext DeleteResult.Failure(
                "Error: Found $occurrences matching occurrences of the code chunk in '$normPath'. " +
                        "Please provide more surrounding lines for a unique match, or specify deleteAllOccurrences: true."
            )
        }

        // Perform clean deletion
        var newContent = if (deleteAllOccurrences) {
            content.replace(targetChunk, "")
        } else {
            val idx = content.indexOf(targetChunk)
            if (idx != -1) {
                content.substring(0, idx) + content.substring(idx + targetChunk.length)
            } else {
                content.replaceFirst(targetChunk, "")
            }
        }

        // Clean up excessive blank lines (more than 2 consecutive newlines)
        newContent = newContent.replace(Regex("\n{3,}"), "\n\n")

        try {
            repository.saveFile(project.name, targetFile.path, newContent)
            DeleteResult.Success(
                message = "Successfully deleted the code chunk from '${targetFile.path}' (${targetChunk.length} characters removed).",
                deletedCharacters = targetChunk.length,
                filePath = targetFile.path
            )
        } catch (e: Exception) {
            DeleteResult.Failure("Error saving file '${targetFile.path}' after code chunk deletion: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }
}
