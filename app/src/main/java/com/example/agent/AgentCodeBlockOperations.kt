package com.example.agent

import com.example.api.ToolArguments
import com.example.data.ProjectEntity
import com.example.data.VibeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AgentCodeBlockOperations
 * Handles delete_code, move_code, and copy_code chunk operations.
 */
object AgentCodeBlockOperations {

    suspend fun handleDeleteCode(
        args: ToolArguments?,
        project: ProjectEntity,
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): String = withContext(Dispatchers.IO) {
        val filePath = normalizePath(AgentArgumentNormalizer.resolvePath(args))
        val searchStr = AgentArgumentNormalizer.resolveSearch(args)

        val files = repository.getFilesForProject(project.name)
        val targetFile = files.find { it.path == filePath }
        if (targetFile == null) {
            return@withContext "Error: File '$filePath' not found."
        }
        val originalContent = targetFile.content
        if (searchStr.isEmpty()) {
            return@withContext "Error: 'search' block cannot be empty for code deletion. You must specify the exact, unique block of code you want to delete in the 'search' argument."
        }
        if (!originalContent.contains(searchStr)) {
            return@withContext "Error: Could not find exact code block in $filePath to delete."
        }
        val occurrences = originalContent.split(searchStr).size - 1
        if (occurrences > 1) {
            return@withContext "Error: The code block to delete is not unique ($occurrences matches). Provide more context."
        }
        val updatedContent = originalContent.replace(searchStr, "")
        try {
            repository.saveFile(project.name, filePath, updatedContent)
            "Successfully deleted the specified code block from '$filePath'"
        } catch (e: Exception) {
            "Error writing file: ${e.localizedMessage}"
        }
    }

    suspend fun handleMoveCode(
        args: ToolArguments?,
        project: ProjectEntity,
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): String = withContext(Dispatchers.IO) {
        val sourcePath = normalizePath(args?.path ?: "")
        val destPath = normalizePath(args?.destinationPath ?: "")
        val searchStr = args?.search ?: ""
        val destSearchStr = args?.destinationSearch ?: ""

        val files = repository.getFilesForProject(project.name)
        val sourceFile = files.find { it.path == sourcePath }
        val destFile = files.find { it.path == destPath }

        if (sourceFile == null) return@withContext "Error: Source file '$sourcePath' not found."
        if (destFile == null) return@withContext "Error: Destination file '$destPath' not found."
        if (searchStr.isEmpty()) return@withContext "Error: 'search' code block to move cannot be empty."
        if (!sourceFile.content.contains(searchStr)) return@withContext "Error: Could not find code block in source file '$sourcePath'."

        val sourceOccurrences = sourceFile.content.split(searchStr).size - 1
        if (sourceOccurrences > 1) {
            return@withContext "Error: The code block to move is not unique in source file ($sourceOccurrences matches)."
        }

        val destContent = destFile.content
        val newDestContent = if (destSearchStr.isNotEmpty()) {
            if (!destContent.contains(destSearchStr)) {
                destContent + "\n" + searchStr
            } else {
                val destOccurrences = destContent.split(destSearchStr).size - 1
                if (destOccurrences > 1) {
                    return@withContext "Error: destinationSearch block is not unique in '$destPath'."
                } else {
                    destContent.replace(destSearchStr, destSearchStr + "\n" + searchStr)
                }
            }
        } else {
            destContent + "\n" + searchStr
        }

        try {
            val newSourceContent = sourceFile.content.replace(searchStr, "")
            repository.saveFile(project.name, sourcePath, newSourceContent)
            repository.saveFile(project.name, destPath, newDestContent)
            "Successfully moved code block from '$sourcePath' to '$destPath'"
        } catch (e: Exception) {
            "Error executing move_code: ${e.localizedMessage}"
        }
    }

    suspend fun handleCopyCode(
        args: ToolArguments?,
        project: ProjectEntity,
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): String = withContext(Dispatchers.IO) {
        val sourcePath = normalizePath(args?.path ?: "")
        val destPath = normalizePath(args?.destinationPath ?: "")
        val searchStr = args?.search ?: ""
        val destSearchStr = args?.destinationSearch ?: ""

        val files = repository.getFilesForProject(project.name)
        val sourceFile = files.find { it.path == sourcePath }
        val destFile = files.find { it.path == destPath }

        if (sourceFile == null) return@withContext "Error: Source file '$sourcePath' not found."
        if (destFile == null) return@withContext "Error: Destination file '$destPath' not found."
        if (searchStr.isEmpty()) return@withContext "Error: 'search' code block to copy cannot be empty."
        if (!sourceFile.content.contains(searchStr)) return@withContext "Error: Could not find code block in source file '$sourcePath'."

        val sourceOccurrences = sourceFile.content.split(searchStr).size - 1
        if (sourceOccurrences > 1) {
            return@withContext "Error: The code block to copy is not unique in source file ($sourceOccurrences matches)."
        }

        val destContent = destFile.content
        val newDestContent = if (destSearchStr.isNotEmpty()) {
            if (!destContent.contains(destSearchStr)) {
                destContent + "\n" + searchStr
            } else {
                val destOccurrences = destContent.split(destSearchStr).size - 1
                if (destOccurrences > 1) {
                    return@withContext "Error: destinationSearch block is not unique in '$destPath'."
                } else {
                    destContent.replace(destSearchStr, destSearchStr + "\n" + searchStr)
                }
            }
        } else {
            destContent + "\n" + searchStr
        }

        try {
            repository.saveFile(project.name, destPath, newDestContent)
            "Successfully copied code block from '$sourcePath' to '$destPath'"
        } catch (e: Exception) {
            "Error executing copy_code: ${e.localizedMessage}"
        }
    }
}
