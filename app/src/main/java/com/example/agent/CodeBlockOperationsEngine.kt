package com.example.agent

import com.example.data.ProjectEntity
import com.example.data.VibeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CodeBlockOperationsEngine
 * Comprehensive, production-grade engine for:
 * 1. Moving code blocks/chunks from a source file into a target file (with atomic cut & paste).
 * 2. Copying code blocks/chunks from a source file into a target file.
 * 3. Deleting code blocks/chunks from any file.
 *
 * Supports both string-based chunk matching (exact, trimmed, normalized newlines)
 * and line-range extraction (startLine to endLine).
 * Supports flexible target anchor positioning ('start', 'end', 'before', 'after', 'replace').
 */
object CodeBlockOperationsEngine {

    sealed class OperationResult {
        data class Success(val message: String, val affectedLines: Int, val isMove: Boolean = false) : OperationResult()
        data class Failure(val errorMessage: String) : OperationResult()
    }

    /**
     * Transfers (copies or moves) a code block from sourcePath to targetPath.
     */
    suspend fun transferBlock(
        sourcePath: String,
        targetPath: String,
        codeChunk: String? = null,
        startLine: Int? = null,
        endLine: Int? = null,
        targetAnchor: String? = null,
        insertAt: String? = null, // "start", "prepend", "end", "append", "before", "after", "replace"
        isMove: Boolean = false,
        createTargetIfMissing: Boolean = true,
        project: ProjectEntity,
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): OperationResult = withContext(Dispatchers.IO) {
        val normSource = normalizePath(sourcePath.trim())
        val normTarget = normalizePath(targetPath.trim())

        if (normSource.isBlank()) {
            return@withContext OperationResult.Failure("Error: 'sourcePath' (or 'sourceFile'/'fromPath') is required.")
        }
        if (normTarget.isBlank()) {
            return@withContext OperationResult.Failure("Error: 'targetPath' (or 'targetFile'/'toPath') is required.")
        }

        val allFiles = repository.getFilesForProject(project.name)
        val srcFile = allFiles.find {
            it.path == normSource || normalizePath(it.path) == normSource || it.path.endsWith(normSource)
        } ?: return@withContext OperationResult.Failure("Error: Source file '$normSource' not found in workspace.")

        var dstFile = allFiles.find {
            it.path == normTarget || normalizePath(it.path) == normTarget || it.path.endsWith(normTarget)
        }

        if (dstFile == null) {
            if (createTargetIfMissing) {
                // Auto-create destination file if it doesn't exist
                try {
                    repository.saveFile(project.name, normTarget, "")
                    dstFile = repository.getFilesForProject(project.name).find {
                        it.path == normTarget || normalizePath(it.path) == normTarget || it.path.endsWith(normTarget)
                    }
                } catch (e: Exception) {
                    return@withContext OperationResult.Failure("Error creating target file '$normTarget': ${e.localizedMessage ?: e.javaClass.simpleName}")
                }
            }
            if (dstFile == null) {
                return@withContext OperationResult.Failure("Error: Target file '$normTarget' not found in workspace.")
            }
        }

        var srcContent = srcFile.content
        var dstContent = dstFile.content

        // 1. Extract the code chunk from source
        val effectiveChunk: String
        val chunkLinesCount: Int

        if (startLine != null && startLine > 0) {
            val lines = srcContent.lines()
            val totalLines = lines.size
            val effectiveStart = startLine.coerceIn(1, totalLines)
            val effectiveEnd = (endLine ?: startLine).coerceIn(effectiveStart, totalLines)

            val extractedLines = lines.subList(effectiveStart - 1, effectiveEnd)
            effectiveChunk = extractedLines.joinToString("\n")
            chunkLinesCount = extractedLines.size

            if (isMove) {
                val remainingLines = mutableListOf<String>()
                if (effectiveStart > 1) {
                    remainingLines.addAll(lines.subList(0, effectiveStart - 1))
                }
                if (effectiveEnd < totalLines) {
                    remainingLines.addAll(lines.subList(effectiveEnd, totalLines))
                }
                srcContent = cleanExcessiveBlankLines(remainingLines.joinToString("\n"))
            }
        } else {
            val chunkCandidate = codeChunk?.trim() ?: ""
            if (chunkCandidate.isBlank()) {
                return@withContext OperationResult.Failure(
                    "Error: Either 'codeChunk' (or 'codeBlock'/'code'/'search') or line numbers ('startLine' and 'endLine') must be provided."
                )
            }

            // Find match in source content
            var matchedSubstring = ""
            if (srcContent.contains(codeChunk!!)) {
                matchedSubstring = codeChunk
            } else {
                val normSrc = srcContent.replace("\r\n", "\n")
                val normChunk = codeChunk.replace("\r\n", "\n")
                if (normSrc.contains(normChunk)) {
                    srcContent = normSrc
                    matchedSubstring = normChunk
                } else if (normSrc.contains(chunkCandidate)) {
                    srcContent = normSrc
                    matchedSubstring = chunkCandidate
                } else {
                    // Try whitespace-relaxed line matching
                    val chunkLines = normChunk.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (chunkLines.isNotEmpty()) {
                        val srcLines = normSrc.lines()
                        var matchStart = -1
                        var matchEnd = -1
                        for (i in 0..srcLines.size - chunkLines.size) {
                            var allMatch = true
                            for (j in chunkLines.indices) {
                                if (srcLines[i + j].trim() != chunkLines[j]) {
                                    allMatch = false
                                    break
                                }
                            }
                            if (allMatch) {
                                matchStart = i
                                matchEnd = i + chunkLines.size
                                break
                            }
                        }
                        if (matchStart != -1) {
                            matchedSubstring = srcLines.subList(matchStart, matchEnd).joinToString("\n")
                        }
                    }
                }
            }

            if (matchedSubstring.isEmpty()) {
                return@withContext OperationResult.Failure(
                    "Error: Could not locate the specified code block in '$normSource'. Please verify exact indentation and syntax, or specify 'startLine' and 'endLine'."
                )
            }

            effectiveChunk = matchedSubstring
            chunkLinesCount = effectiveChunk.lines().size

            if (isMove) {
                srcContent = cleanExcessiveBlankLines(srcContent.replaceFirst(matchedSubstring, ""))
            }
        }

        // 2. Insert into destination file
        val placement = (insertAt ?: if (!targetAnchor.isNullOrBlank()) "after" else "append").lowercase().trim()
        val anchorText = targetAnchor?.trim() ?: ""

        val newDstContent = when (placement) {
            "start", "prepend", "top" -> {
                if (dstContent.isBlank()) effectiveChunk else effectiveChunk + "\n\n" + dstContent.trimStart()
            }
            "end", "append", "bottom" -> {
                if (dstContent.isBlank()) effectiveChunk else dstContent.trimEnd() + "\n\n" + effectiveChunk + "\n"
            }
            "before" -> {
                if (anchorText.isBlank()) {
                    if (dstContent.isBlank()) effectiveChunk else effectiveChunk + "\n\n" + dstContent.trimStart()
                } else {
                    val res = insertRelativeToAnchor(dstContent, anchorText, effectiveChunk, insertBefore = true)
                    if (res != null) res else dstContent.trimEnd() + "\n\n" + effectiveChunk + "\n"
                }
            }
            "replace" -> {
                if (anchorText.isBlank()) {
                    effectiveChunk
                } else {
                    val res = replaceAnchor(dstContent, anchorText, effectiveChunk)
                    if (res != null) res else dstContent.trimEnd() + "\n\n" + effectiveChunk + "\n"
                }
            }
            else -> { // "after"
                if (anchorText.isNotBlank()) {
                    val res = insertRelativeToAnchor(dstContent, anchorText, effectiveChunk, insertBefore = false)
                    if (res != null) res else dstContent.trimEnd() + "\n\n" + effectiveChunk + "\n"
                } else {
                    if (dstContent.isBlank()) effectiveChunk else dstContent.trimEnd() + "\n\n" + effectiveChunk + "\n"
                }
            }
        }

        // 3. Save destination file
        try {
            repository.saveFile(project.name, dstFile.path, newDstContent)
        } catch (e: Exception) {
            return@withContext OperationResult.Failure("Error saving target file '${dstFile.path}': ${e.localizedMessage ?: e.javaClass.simpleName}")
        }

        // 4. Save source file if move was requested
        if (isMove) {
            try {
                repository.saveFile(project.name, srcFile.path, srcContent)
            } catch (e: Exception) {
                return@withContext OperationResult.Failure("Error updating source file '${srcFile.path}' during move: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }

        val actionWord = if (isMove) "Moved" else "Copied"
        OperationResult.Success(
            message = "Successfully $actionWord $chunkLinesCount lines from '$normSource' to '$normTarget' ($placement).",
            affectedLines = chunkLinesCount,
            isMove = isMove
        )
    }

    /**
     * Deletes a code block or line range from a file.
     */
    suspend fun deleteBlock(
        filePath: String,
        codeChunk: String? = null,
        startLine: Int? = null,
        endLine: Int? = null,
        deleteAllOccurrences: Boolean = false,
        project: ProjectEntity,
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): OperationResult = withContext(Dispatchers.IO) {
        val normPath = normalizePath(filePath.trim())
        if (normPath.isBlank()) {
            return@withContext OperationResult.Failure("Error: 'path' (or 'filePath'/'targetFile') is required.")
        }

        val allFiles = repository.getFilesForProject(project.name)
        val fileEntity = allFiles.find {
            it.path == normPath || normalizePath(it.path) == normPath || it.path.endsWith(normPath)
        } ?: return@withContext OperationResult.Failure("Error: File '$normPath' not found in workspace.")

        var content = fileEntity.content
        val deletedLinesCount: Int
        val newContent: String

        if (startLine != null && startLine > 0) {
            val lines = content.lines()
            val totalLines = lines.size
            val effectiveStart = startLine.coerceIn(1, totalLines)
            val effectiveEnd = (endLine ?: startLine).coerceIn(effectiveStart, totalLines)

            deletedLinesCount = (effectiveEnd - effectiveStart) + 1
            val remaining = mutableListOf<String>()
            if (effectiveStart > 1) {
                remaining.addAll(lines.subList(0, effectiveStart - 1))
            }
            if (effectiveEnd < totalLines) {
                remaining.addAll(lines.subList(effectiveEnd, totalLines))
            }
            newContent = cleanExcessiveBlankLines(remaining.joinToString("\n"))
        } else {
            val chunk = codeChunk ?: ""
            if (chunk.isBlank()) {
                return@withContext OperationResult.Failure(
                    "Error: Either 'codeChunk' (or 'codeBlock'/'code'/'search') or line numbers ('startLine' and 'endLine') must be provided to delete."
                )
            }

            var targetSubstring = ""
            if (content.contains(chunk)) {
                targetSubstring = chunk
            } else {
                val normContent = content.replace("\r\n", "\n")
                val normChunk = chunk.replace("\r\n", "\n")
                if (normContent.contains(normChunk)) {
                    content = normContent
                    targetSubstring = normChunk
                } else if (normContent.contains(chunk.trim())) {
                    content = normContent
                    targetSubstring = chunk.trim()
                } else {
                    // Try line matching
                    val chunkLines = normChunk.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (chunkLines.isNotEmpty()) {
                        val srcLines = normContent.lines()
                        var matchStart = -1
                        var matchEnd = -1
                        for (i in 0..srcLines.size - chunkLines.size) {
                            var allMatch = true
                            for (j in chunkLines.indices) {
                                if (srcLines[i + j].trim() != chunkLines[j]) {
                                    allMatch = false
                                    break
                                }
                            }
                            if (allMatch) {
                                matchStart = i
                                matchEnd = i + chunkLines.size
                                break
                            }
                        }
                        if (matchStart != -1) {
                            targetSubstring = srcLines.subList(matchStart, matchEnd).joinToString("\n")
                        }
                    }
                }
            }

            if (targetSubstring.isEmpty()) {
                return@withContext OperationResult.Failure(
                    "Error: Could not find the specified code block in '$normPath'. Please verify exact code or specify 'startLine' and 'endLine'."
                )
            }

            val occurrences = content.split(targetSubstring).size - 1
            if (occurrences > 1 && !deleteAllOccurrences) {
                return@withContext OperationResult.Failure(
                    "Error: Found $occurrences matching occurrences in '$normPath'. Provide more surrounding context for uniqueness or pass deleteAllOccurrences: true."
                )
            }

            deletedLinesCount = targetSubstring.lines().size
            val afterDeletion = if (deleteAllOccurrences) {
                content.replace(targetSubstring, "")
            } else {
                val idx = content.indexOf(targetSubstring)
                if (idx != -1) {
                    content.substring(0, idx) + content.substring(idx + targetSubstring.length)
                } else {
                    content.replaceFirst(targetSubstring, "")
                }
            }
            newContent = cleanExcessiveBlankLines(afterDeletion)
        }

        try {
            repository.saveFile(project.name, fileEntity.path, newContent)
            OperationResult.Success(
                message = "Successfully deleted $deletedLinesCount lines from '${fileEntity.path}'.",
                affectedLines = deletedLinesCount
            )
        } catch (e: Exception) {
            OperationResult.Failure("Error saving file after deletion: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    private fun insertRelativeToAnchor(content: String, anchor: String, chunk: String, insertBefore: Boolean): String? {
        val normContent = content.replace("\r\n", "\n")
        val normAnchor = anchor.replace("\r\n", "\n")

        val target = if (normContent.contains(normAnchor)) normAnchor else if (normContent.contains(normAnchor.trim())) normAnchor.trim() else null
        if (target != null) {
            return if (insertBefore) {
                normContent.replaceFirst(target, chunk + "\n\n" + target)
            } else {
                normContent.replaceFirst(target, target + "\n\n" + chunk)
            }
        }
        return null
    }

    private fun replaceAnchor(content: String, anchor: String, chunk: String): String? {
        val normContent = content.replace("\r\n", "\n")
        val normAnchor = anchor.replace("\r\n", "\n")

        val target = if (normContent.contains(normAnchor)) normAnchor else if (normContent.contains(normAnchor.trim())) normAnchor.trim() else null
        if (target != null) {
            return normContent.replaceFirst(target, chunk)
        }
        return null
    }

    private fun cleanExcessiveBlankLines(text: String): String {
        return text.replace(Regex("\n{3,}"), "\n\n").trimEnd() + "\n"
    }
}
