package com.example.agent

import com.example.data.ProjectEntity
import com.example.data.VibeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance engine for copy_file, duplicate_file, and advanced batch file manipulations.
 */
object ExtendedFileOperationsEngine {

    suspend fun copyFile(
        projectName: String,
        sourcePath: String,
        destinationPath: String,
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): String = withContext(Dispatchers.IO) {
        val cleanSource = normalizePath(sourcePath)
        val cleanDest = normalizePath(destinationPath)

        if (cleanSource.isBlank() || cleanDest.isBlank()) {
            return@withContext "Error: 'path' (source) and 'destinationPath' (destination) are required for copy_file."
        }

        val allFiles = repository.getFilesForProject(projectName)
        val srcFile = allFiles.find { it.path == cleanSource || normalizePath(it.path) == cleanSource }
            ?: return@withContext "Error: Source file '$cleanSource' not found in project."

        try {
            repository.saveFile(projectName, cleanDest, srcFile.content)
            "Successfully copied file from '$cleanSource' to '$cleanDest' (${srcFile.content.length} characters)."
        } catch (e: Exception) {
            "Error copying file: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    suspend fun duplicateFile(
        projectName: String,
        sourcePath: String,
        targetPaths: List<String>?,
        count: Int?,
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): String = withContext(Dispatchers.IO) {
        val cleanSource = normalizePath(sourcePath)
        if (cleanSource.isBlank()) {
            return@withContext "Error: 'path' (source file) is required for duplicate_file."
        }

        val allFiles = repository.getFilesForProject(projectName)
        val srcFile = allFiles.find { it.path == cleanSource || normalizePath(it.path) == cleanSource }
            ?: return@withContext "Error: Source file '$cleanSource' not found to duplicate."

        val destinations = mutableListOf<String>()
        if (!targetPaths.isNullOrEmpty()) {
            destinations.addAll(targetPaths.map { normalizePath(it) })
        } else {
            val numCopies = count ?: 1
            val ext = if (cleanSource.contains(".")) ".${cleanSource.substringAfterLast(".")}" else ""
            val baseName = cleanSource.removeSuffix(ext)
            for (i in 1..numCopies) {
                destinations.add("${baseName}_copy$i$ext")
            }
        }

        val createdList = mutableListOf<String>()
        try {
            for (dest in destinations) {
                repository.saveFile(projectName, dest, srcFile.content)
                createdList.add(dest)
            }
            "Successfully created ${createdList.size} duplicate copies of '$cleanSource':\n${createdList.joinToString("\n") { "• $it" }}"
        } catch (e: Exception) {
            "Error creating duplicates: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }
}
