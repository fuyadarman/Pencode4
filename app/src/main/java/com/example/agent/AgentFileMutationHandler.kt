package com.example.agent

import com.example.api.Content
import com.example.api.ToolArguments
import com.example.data.ProjectEntity
import com.example.data.ProjectFileEntity
import com.example.data.VibeRepository
import java.io.File

/**
 * AgentFileMutationHandler
 *
 * Dedicated modular processor for file mutations:
 * - create_file
 * - write_file
 * - append
 * - edit_file / patch_file
 * - multi_edit_file
 */
object AgentFileMutationHandler {

    data class MutationResult(
        val resultText: String,
        val isSuccess: Boolean,
        val filePath: String,
        val lineRange: String,
        val logTitle: String,
        val recordTool: String? = null
    )

    suspend fun handleMutation(
        tool: String,
        args: ToolArguments?,
        project: ProjectEntity,
        repository: VibeRepository,
        projectFiles: List<ProjectFileEntity>,
        readFilesThisSession: MutableSet<String>,
        history: List<Content>,
        normalizePath: (String) -> String
    ): MutationResult {
        return when (tool) {
            "create_file" -> handleCreateFile(
                args = args,
                project = project,
                repository = repository,
                projectFiles = projectFiles,
                normalizePath = normalizePath
            )
            "write_file", "write" -> handleWriteFile(
                args = args,
                project = project,
                repository = repository,
                projectFiles = projectFiles,
                readFilesThisSession = readFilesThisSession,
                history = history,
                normalizePath = normalizePath
            )
            "append" -> handleAppendFile(
                args = args,
                project = project,
                repository = repository,
                projectFiles = projectFiles,
                readFilesThisSession = readFilesThisSession,
                history = history,
                normalizePath = normalizePath
            )
            "edit", "patch", "patch_file", "edit_file" -> handleEditFile(
                tool = tool,
                args = args,
                project = project,
                repository = repository,
                projectFiles = projectFiles,
                readFilesThisSession = readFilesThisSession,
                history = history,
                normalizePath = normalizePath
            )
            "multi_edit_file", "multi_edit", "multi_patch" -> handleMultiEditFile(
                args = args,
                project = project,
                repository = repository,
                projectFiles = projectFiles,
                readFilesThisSession = readFilesThisSession,
                history = history,
                normalizePath = normalizePath
            )
            else -> MutationResult("Unknown mutation tool: $tool", false, "", "", "File error")
        }
    }

    private suspend fun handleCreateFile(
        args: ToolArguments?,
        project: ProjectEntity,
        repository: VibeRepository,
        projectFiles: List<ProjectFileEntity>,
        normalizePath: (String) -> String
    ): MutationResult {
        val filePath = normalizePath(AgentArgumentNormalizer.resolvePath(args))
        val fileContent = AgentArgumentNormalizer.resolveContent(args)
        val cleanNormalizedPath = normalizePath(filePath)
        val projectDir = repository.getProjectDir(project.name)

        val existingFileEntity = projectFiles.find {
            val exNorm = normalizePath(it.path)
            exNorm == cleanNormalizedPath || it.path == filePath || it.path == cleanNormalizedPath || exNorm.trimStart('/') == cleanNormalizedPath.trimStart('/')
        }
        val fileOnDisk = File(projectDir, cleanNormalizedPath.trimStart('/'))
        val fileAlreadyExists = existingFileEntity != null || fileOnDisk.exists()

        var saved = false
        var rangeDesc = args?.lineRange ?: "all"
        val result = if (fileAlreadyExists) {
            when (val decision = AgentFileCreationResolver.resolveExistingFileCreation(
                filePath = filePath,
                newContent = fileContent,
                targetFile = existingFileEntity,
                fileOnDisk = fileOnDisk,
                project = project,
                repository = repository
            )) {
                is AgentFileCreationResolver.FileCreationDecision.Saved -> {
                    saved = true
                    rangeDesc = decision.rangeDesc
                    decision.message
                }
                is AgentFileCreationResolver.FileCreationDecision.RejectionWithContent -> {
                    decision.message
                }
                is AgentFileCreationResolver.FileCreationDecision.Error -> {
                    decision.message
                }
            }
        } else {
            try {
                repository.saveFile(project.name, filePath, fileContent)
                saved = true
                "Successfully created new file '$filePath'. Content is saved. DO NOT re-read this file to verify. If all requested changes are done, call 'complete'."
            } catch (e: Exception) {
                "Error creating file: ${e.localizedMessage}"
            }
        }

        val isSuccess = !result.startsWith("Error") && saved
        return MutationResult(
            resultText = result,
            isSuccess = isSuccess,
            filePath = filePath,
            lineRange = rangeDesc,
            logTitle = if (fileAlreadyExists) "Updated existing file" else "Created new file",
            recordTool = if (isSuccess) "create_file" else null
        )
    }

    private suspend fun handleWriteFile(
        args: ToolArguments?,
        project: ProjectEntity,
        repository: VibeRepository,
        projectFiles: List<ProjectFileEntity>,
        readFilesThisSession: MutableSet<String>,
        history: List<Content>,
        normalizePath: (String) -> String
    ): MutationResult {
        val filePath = normalizePath(args?.path ?: "")
        val fileContent = args?.content ?: ""
        val targetFile = projectFiles.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }
        val linesCount = targetFile?.content?.lines()?.size ?: 0

        val fileHasBeenRead = if (targetFile != null) {
            readFilesThisSession.contains(filePath) ||
            readFilesThisSession.contains(normalizePath(filePath)) ||
            readFilesThisSession.contains(targetFile.path) ||
            readFilesThisSession.contains(normalizePath(targetFile.path)) ||
            history.any { content ->
                content.parts.any { part ->
                    val t = part.text ?: ""
                    (t.contains("System/Tool Output for 'read_file'") || t.contains("System/Tool Output for 'read_file_range'")) &&
                    (t.contains(filePath) || t.contains(normalizePath(filePath)) || t.contains(targetFile.path) || t.contains(normalizePath(targetFile.path)))
                }
            }
        } else true

        var saved = false
        val result = if (targetFile != null && linesCount > 30) {
            "Error: SYSTEM REJECTION - File '$filePath' has $linesCount lines (more than 30 lines). Overwriting or recreating existing files larger than 30 lines with 'write_file' is STRICTLY FORBIDDEN to prevent code destruction. You MUST use 'edit_file' or 'patch_file' to make precise surgical edits."
        } else if (targetFile != null && !fileHasBeenRead) {
            "Error: SYSTEM REJECTION - Overwriting/recreating existing file '$filePath' without reading it first is STRICTLY FORBIDDEN! You MUST call 'read_file' or 'read_file_range' on '$filePath' before attempting to modify or overwrite it. Furthermore, 'write_file' is a RESTRICTED tool—prefer using 'edit_file' or 'patch_file' for surgical code edits instead of overwriting full files."
        } else {
            try {
                repository.saveFile(project.name, filePath, fileContent)
                saved = true
                if (targetFile != null) {
                    "Successfully overwrote existing file '$filePath'. Content is saved. DO NOT re-read this file to verify. If all requested changes are done, call 'complete'."
                } else {
                    "Successfully created new file '$filePath'. Content is saved. DO NOT re-read this file to verify. If all requested changes are done, call 'complete'."
                }
            } catch (e: Exception) {
                "Error writing file: ${e.localizedMessage}"
            }
        }

        val isSuccess = !result.startsWith("Error") && saved
        return MutationResult(
            resultText = result,
            isSuccess = isSuccess,
            filePath = filePath,
            lineRange = args?.lineRange ?: "all",
            logTitle = "Writing file",
            recordTool = if (isSuccess) "write_file" else null
        )
    }

    private suspend fun handleAppendFile(
        args: ToolArguments?,
        project: ProjectEntity,
        repository: VibeRepository,
        projectFiles: List<ProjectFileEntity>,
        readFilesThisSession: MutableSet<String>,
        history: List<Content>,
        normalizePath: (String) -> String
    ): MutationResult {
        val filePath = normalizePath(args?.path ?: "")
        val fileContent = args?.content ?: ""
        val targetFile = projectFiles.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }

        val fileHasBeenRead = if (targetFile != null) {
            readFilesThisSession.contains(filePath) ||
            readFilesThisSession.contains(normalizePath(filePath)) ||
            readFilesThisSession.contains(targetFile.path) ||
            readFilesThisSession.contains(normalizePath(targetFile.path)) ||
            history.any { content ->
                content.parts.any { part ->
                    val t = part.text ?: ""
                    (t.contains("System/Tool Output for 'read_file'") || t.contains("System/Tool Output for 'read_file_range'") || t.contains("System/Tool Output for 'view_file'")) &&
                    (t.contains(filePath) || t.contains(normalizePath(filePath)) || t.contains(targetFile.path) || t.contains(normalizePath(targetFile.path)))
                }
            }
        } else true

        var calculatedRange = args?.lineRange ?: ""
        var saved = false
        val result = if (repository.isBinaryExtension(filePath)) {
            "Error: Reading, editing, patching, or appending to binary image or 3D files directly as text is NOT allowed."
        } else if (targetFile != null && !fileHasBeenRead) {
            "Error: SYSTEM REJECTION - You cannot append to existing file '$filePath' without reading it first! Please read the file first."
        } else if (targetFile != null) {
            try {
                val newContent = targetFile.content + "\n" + fileContent
                repository.saveFile(project.name, filePath, newContent)
                val startLine = targetFile.content.lines().size + 1
                val addedLines = fileContent.lines().size
                val endLine = startLine + addedLines - 1
                calculatedRange = if (startLine >= endLine) "line $startLine" else "lines $startLine-$endLine"
                saved = true
                "Successfully appended to '$filePath'. Content is saved. DO NOT re-read this file to verify. If all requested changes are done, call 'complete'."
            } catch (e: Exception) {
                "Error appending to file: ${e.localizedMessage}"
            }
        } else {
            "Error: File '$filePath' not found. Cannot append."
        }

        val isSuccess = !result.startsWith("Error") && saved
        return MutationResult(
            resultText = result,
            isSuccess = isSuccess,
            filePath = filePath,
            lineRange = calculatedRange,
            logTitle = "Append to file",
            recordTool = if (isSuccess) "append" else null
        )
    }

    private suspend fun handleEditFile(
        tool: String,
        args: ToolArguments?,
        project: ProjectEntity,
        repository: VibeRepository,
        projectFiles: List<ProjectFileEntity>,
        readFilesThisSession: MutableSet<String>,
        history: List<Content>,
        normalizePath: (String) -> String
    ): MutationResult {
        val filePath = normalizePath(AgentArgumentNormalizer.resolvePath(args))
        val searchStr = AgentArgumentNormalizer.resolveSearch(args)
        val replaceStr = AgentArgumentNormalizer.resolveReplace(args)
        val targetFile = projectFiles.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }

        val fileHasBeenRead = if (targetFile != null) {
            readFilesThisSession.contains(filePath) ||
            readFilesThisSession.contains(normalizePath(filePath)) ||
            readFilesThisSession.contains(targetFile.path) ||
            readFilesThisSession.contains(normalizePath(targetFile.path)) ||
            history.any { content ->
                content.parts.any { part ->
                    val t = part.text ?: ""
                    (t.contains("System/Tool Output for 'read_file'") || t.contains("System/Tool Output for 'read_file_range'") || t.contains("System/Tool Output for 'view_file'")) &&
                    (t.contains(filePath) || t.contains(normalizePath(filePath)) || t.contains(targetFile.path) || t.contains(normalizePath(targetFile.path)))
                }
            }
        } else true

        val execResult = AgentEditToolExecutor.executeEdit(
            tool = tool,
            filePath = filePath,
            searchStr = searchStr,
            replaceStr = replaceStr,
            targetFile = targetFile,
            fileHasBeenRead = fileHasBeenRead,
            projectName = project.name,
            repository = repository
        )

        return when (execResult) {
            is AgentEditToolExecutor.EditExecutionResult.Success -> {
                MutationResult(
                    resultText = execResult.message,
                    isSuccess = true,
                    filePath = filePath,
                    lineRange = execResult.rangeDesc,
                    logTitle = "Modified file",
                    recordTool = execResult.editRecord.tool
                )
            }
            is AgentEditToolExecutor.EditExecutionResult.Failure -> {
                if (execResult.markFileAsRead && execResult.filePath != null) {
                    readFilesThisSession.add(execResult.filePath)
                }
                MutationResult(
                    resultText = execResult.errorMessage,
                    isSuccess = false,
                    filePath = filePath,
                    lineRange = args?.lineRange ?: "",
                    logTitle = "Modified file"
                )
            }
        }
    }

    private suspend fun handleMultiEditFile(
        args: ToolArguments?,
        project: ProjectEntity,
        repository: VibeRepository,
        projectFiles: List<ProjectFileEntity>,
        readFilesThisSession: MutableSet<String>,
        history: List<Content>,
        normalizePath: (String) -> String
    ): MutationResult {
        val filePath = normalizePath(args?.path ?: args?.targetFile ?: args?.destinationPath ?: "")
        val chunks = MultiEditChunkParser.resolveChunks(args, args?.content)
        val targetFile = projectFiles.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }

        val fileHasBeenRead = if (targetFile != null) {
            readFilesThisSession.contains(filePath) ||
            readFilesThisSession.contains(normalizePath(filePath)) ||
            readFilesThisSession.contains(targetFile.path) ||
            readFilesThisSession.contains(normalizePath(targetFile.path)) ||
            history.any { content ->
                content.parts.any { part ->
                    val t = part.text ?: ""
                    (t.contains("System/Tool Output for 'read_file'") || t.contains("System/Tool Output for 'read_file_range'") || t.contains("System/Tool Output for 'view_file'")) &&
                    (t.contains(filePath) || t.contains(normalizePath(filePath)) || t.contains(targetFile.path) || t.contains(normalizePath(targetFile.path)))
                }
            }
        } else true

        var foundRange = ""
        var saved = false
        val result = if (repository.isBinaryExtension(filePath)) {
            "Error: Reading or editing binary files directly as text is NOT allowed."
        } else if (targetFile != null && !fileHasBeenRead) {
            "Error: SYSTEM REJECTION - You cannot multi-edit file '$filePath' without reading it first! Please read the file first."
        } else if (targetFile != null) {
            var currentContent = targetFile.content
            if (chunks.isEmpty()) {
                "Error: No edit chunks provided for multi_edit_file. Provide 'chunks' or 'replacementChunks' list with search and replace blocks."
            } else if (chunks.size == 1 && MultiEditChunkParser.getEffectiveSearch(chunks[0]).isEmpty()) {
                val singleReplace = MultiEditChunkParser.getEffectiveReplace(chunks[0])
                val autoHeal = AgentSearchBlockAutoHealer.attemptAutoHeal(
                    projectName = project.name,
                    filePath = filePath,
                    originalContent = currentContent,
                    replaceStr = singleReplace,
                    tool = "multi_edit_file",
                    repository = repository
                )
                if (autoHeal is AgentEditToolExecutor.EditExecutionResult.Success) {
                    saved = true
                    foundRange = autoHeal.rangeDesc
                    autoHeal.message
                } else {
                    AgentSearchBlockAutoHealer.buildActionableEmptySearchMessage(filePath, currentContent.lines().size, currentContent.take(2000))
                }
            } else {
                var chunkError: String? = null
                val lineRanges = mutableListOf<String>()

                for ((index, chunk) in chunks.withIndex()) {
                    var searchStr = MultiEditChunkParser.getEffectiveSearch(chunk)
                    val replaceStr = MultiEditChunkParser.getEffectiveReplace(chunk)

                    if (searchStr.isEmpty()) {
                        chunkError = "Error in chunk #${index + 1}: 'search' block cannot be empty."
                        break
                    }
                    if (!currentContent.contains(searchStr)) {
                        val normContent = currentContent.replace("\r\n", "\n")
                        val normSearch = searchStr.replace("\r\n", "\n")
                        if (normContent.contains(normSearch)) {
                            currentContent = normContent
                            searchStr = normSearch
                        } else {
                            chunkError = "Error in chunk #${index + 1}: Could not find exact search block in $filePath. Please double-check characters, indentation, and spaces."
                            break
                        }
                    }
                    val occurrences = currentContent.split(searchStr).size - 1
                    if (occurrences > 1) {
                        chunkError = "Error in chunk #${index + 1}: The search block is not unique. It occurs $occurrences times in the file."
                        break
                    }

                    val startIndex = currentContent.indexOf(searchStr)
                    val linesBefore = currentContent.substring(0, startIndex).count { it == '\n' } + 1
                    val linesInSearch = searchStr.count { it == '\n' }
                    val endLine = linesBefore + linesInSearch
                    val chunkRangeStr = if (linesBefore == endLine) "$linesBefore" else "$linesBefore-$endLine"
                    lineRanges.add(chunkRangeStr)

                    currentContent = currentContent.replace(searchStr, replaceStr)
                }

                if (chunkError != null) {
                    chunkError
                } else {
                    foundRange = if (lineRanges.isNotEmpty()) lineRanges.joinToString(", ") else ""
                    try {
                        repository.saveFile(project.name, filePath, currentContent)
                        saved = true
                        "Successfully multi-edited file '$filePath' ($foundRange). Changes are saved. DO NOT re-read this file. If all requested changes are done, call 'complete'."
                    } catch (e: Exception) {
                        "Error writing modified file: ${e.localizedMessage}"
                    }
                }
            }
        } else {
            "Error: File '$filePath' not found."
        }

        val isSuccess = result.startsWith("Successfully") && saved
        return MutationResult(
            resultText = result,
            isSuccess = isSuccess,
            filePath = filePath,
            lineRange = if (foundRange.isNotEmpty()) foundRange else args?.lineRange ?: "",
            logTitle = "Multi-edited file",
            recordTool = if (isSuccess) "multi_edit" else null
        )
    }
}
