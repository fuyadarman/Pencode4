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

        var actualSearchStr = searchStr
        val originalContent = targetFile.content
        val lineCount = if (originalContent.isBlank()) 0 else originalContent.lines().size

        if (actualSearchStr.isEmpty()) {
            if (lineCount <= 30 && replaceStr.isNotBlank()) {
                // Auto-healing: file has 30 or fewer lines, replacing is permitted under Rule 1
                return try {
                    repository.saveFile(projectName, filePath, replaceStr)
                    val newLines = replaceStr.lines().size
                    val range = if (newLines <= 1) "all" else "lines 1-$newLines"
                    EditExecutionResult.Success(
                        message = "Successfully updated '$filePath' ($lineCount lines). Content is saved. DO NOT re-read this file. If all requested changes are done, call 'complete'.",
                        filePath = filePath,
                        rangeDesc = range,
                        editRecord = EditRecord(tool = "edit", path = filePath, lines = range)
                    )
                } catch (e: Exception) {
                    EditExecutionResult.Failure("Error writing updated file: ${e.localizedMessage}")
                }
            }
            val snippet = originalContent.take(2000)
            return EditExecutionResult.Failure(
                "Error: 'search' block cannot be empty. You must specify the exact, unique block of code to search and replace.\n" +
                "File '$filePath' has $lineCount lines. Current content snippet:\n```\n$snippet\n```\n" +
                "Please provide the unique code block to replace in 'search', and new code in 'replace'."
            )
        }

        var matchContent = originalContent
        if (!matchContent.contains(actualSearchStr)) {
            val normContent = matchContent.replace("\r\n", "\n")
            val normSearch = actualSearchStr.replace("\r\n", "\n")
            if (normContent.contains(normSearch)) {
                matchContent = normContent
                actualSearchStr = normSearch
            } else {
                val snippet = originalContent.take(2000)
                return EditExecutionResult.Failure(
                    "Error: Could not find exact search block in $filePath. Please double-check characters, indentation, and spaces.\nCurrent file content:\n```\n$snippet\n```"
                )
            }
        }

        val occurrences = matchContent.split(actualSearchStr).size - 1
        if (occurrences > 1) {
            return EditExecutionResult.Failure("Error: The search block is not unique. It occurs $occurrences times in the file. Please provide a larger unique block of context code.")
        }

        val startIndex = matchContent.indexOf(actualSearchStr)
        val linesBefore = matchContent.substring(0, startIndex).count { it == '\n' } + 1
        val linesInSearch = actualSearchStr.count { it == '\n' }
        val endLine = linesBefore + linesInSearch
        val foundRange = if (linesBefore == endLine) "line $linesBefore" else "lines $linesBefore-$endLine"

        val updatedContent = matchContent.replace(actualSearchStr, replaceStr)
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
