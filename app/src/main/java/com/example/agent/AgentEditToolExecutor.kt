package com.example.agent

import com.example.data.ProjectFileEntity
import com.example.data.VibeRepository
import com.example.ui.EditRecord

/**
 * Modular executor for single-file surgical edits and patches.
 * Keeps VibeViewModel method bytecode compact and handles auto-recovery gracefully.
 */
object AgentEditToolExecutor {

    sealed class EditExecutionResult {
        data class Success(
            val message: String,
            val filePath: String,
            val rangeDesc: String,
            val editRecord: EditRecord
        ) : EditExecutionResult()

        data class Failure(
            val errorMessage: String,
            val markFileAsRead: Boolean = false,
            val filePath: String? = null
        ) : EditExecutionResult()
    }

    suspend fun executeEdit(
        tool: String,
        filePath: String,
        searchStr: String,
        replaceStr: String,
        targetFile: ProjectFileEntity?,
        fileHasBeenRead: Boolean,
        projectName: String,
        repository: VibeRepository
    ): EditExecutionResult {
        if (repository.isBinaryExtension(filePath)) {
            return EditExecutionResult.Failure(
                "Error: Reading, editing, patching, or appending to binary image or 3D files directly as text is NOT allowed. You can only view their existence via 'list_directory' or perform operations like rename, delete, move, resize, or format change."
            )
        }
        if (targetFile == null) {
            return EditExecutionResult.Failure("Error: File '$filePath' not found.")
        }

        if (!fileHasBeenRead) {
            val unreadDecision = AgentModelExecutionSafeguard.handleUnreadFileEditAttempt(
                filePath = filePath,
                targetFile = targetFile,
                searchStr = searchStr,
                replaceStr = replaceStr
            )
            return when (unreadDecision) {
                is AgentModelExecutionSafeguard.UnreadFileEditDecision.ApplyDirectly -> {
                    try {
                        repository.saveFile(projectName, filePath, unreadDecision.updatedContent)
                        val toolType = if (tool.contains("patch")) "patch" else "edit"
                        val record = EditRecord(
                            tool = toolType,
                            path = filePath,
                            lines = unreadDecision.rangeDesc
                        )
                        EditExecutionResult.Success(
                            message = "Successfully modified file '$filePath'. Changes are saved. DO NOT re-read this file. If all requested changes are done, call 'complete'.",
                            filePath = filePath,
                            rangeDesc = unreadDecision.rangeDesc,
                            editRecord = record
                        )
                    } catch (e: Exception) {
                        EditExecutionResult.Failure(
                            errorMessage = "Error writing modified file: ${e.localizedMessage}",
                            markFileAsRead = true,
                            filePath = filePath
                        )
                    }
                }
                is AgentModelExecutionSafeguard.UnreadFileEditDecision.ProvideContentForEdit -> {
                    EditExecutionResult.Failure(
                        errorMessage = unreadDecision.guidanceMessage,
                        markFileAsRead = true,
                        filePath = filePath
                    )
                }
            }
        }

        val originalContent = targetFile.content
        if (searchStr.isEmpty()) {
            return EditExecutionResult.Failure("Error: 'search' block cannot be empty. You must specify the exact, unique block of code to search and replace. Do not use write_file/create to overwrite an existing file for small edits.")
        }
        if (!originalContent.contains(searchStr)) {
            return EditExecutionResult.Failure("Error: Could not find exact search block in $filePath. Please double-check characters, indentation, and spaces.")
        }

        val occurrences = originalContent.split(searchStr).size - 1
        if (occurrences > 1) {
            return EditExecutionResult.Failure("Error: The search block is not unique. It occurs $occurrences times in the file. Please provide a larger unique block of context code.")
        }

        val startIndex = originalContent.indexOf(searchStr)
        val linesBefore = originalContent.substring(0, startIndex).count { it == '\n' } + 1
        val linesInSearch = searchStr.count { it == '\n' }
        val endLine = linesBefore + linesInSearch
        val foundRange = if (linesBefore == endLine) "line $linesBefore" else "lines $linesBefore-$endLine"

        val updatedContent = originalContent.replace(searchStr, replaceStr)
        return try {
            repository.saveFile(projectName, filePath, updatedContent)
            val toolType = if (tool.contains("patch")) "patch" else "edit"
            val record = EditRecord(
                tool = toolType,
                path = filePath,
                lines = foundRange
            )
            EditExecutionResult.Success(
                message = "Successfully modified file '$filePath'. Changes are saved. DO NOT re-read this file. If all requested changes are done, call 'complete'.",
                filePath = filePath,
                rangeDesc = foundRange,
                editRecord = record
            )
        } catch (e: Exception) {
            EditExecutionResult.Failure("Error writing modified file: ${e.localizedMessage}")
        }
    }
}
