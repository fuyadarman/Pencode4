package com.example.agent

import com.example.data.ProjectEntity
import com.example.data.VibeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Engine for copying or moving code chunks/blocks between workspace files.
 * Supports exact block matching, anchor positioning (before, after, start, append, replace),
 * and atomic removal from the source file when moving.
 */
object CodeChunkTransferEngine {

    sealed class TransferResult {
        data class Success(val message: String, val isMove: Boolean) : TransferResult()
        data class Failure(val errorMessage: String) : TransferResult()
    }

    suspend fun transferCodeChunk(
        sourcePath: String,
        targetPath: String,
        codeChunk: String,
        targetAnchor: String? = null,
        insertAt: String? = null, // "start", "end", "append", "before", "after", "replace"
        isMove: Boolean = false,
        project: ProjectEntity,
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): TransferResult = withContext(Dispatchers.IO) {
        val normSource = normalizePath(sourcePath)
        val normTarget = normalizePath(targetPath)

        if (normSource.isBlank()) {
            return@withContext TransferResult.Failure("Error: 'sourcePath' is required.")
        }
        if (normTarget.isBlank()) {
            return@withContext TransferResult.Failure("Error: 'targetPath' (or 'destinationPath') is required.")
        }
        if (codeChunk.isBlank()) {
            return@withContext TransferResult.Failure("Error: 'codeChunk' (or 'search'/'sourceBlock') cannot be empty.")
        }

        val allFiles = repository.getFilesForProject(project.name)
        val srcFile = allFiles.find { it.path == normSource || normalizePath(it.path) == normSource || it.path.endsWith(normSource) }
            ?: return@withContext TransferResult.Failure("Error: Source file '$normSource' not found in project.")

        val dstFile = allFiles.find { it.path == normTarget || normalizePath(it.path) == normTarget || it.path.endsWith(normTarget) }
            ?: return@withContext TransferResult.Failure("Error: Target file '$normTarget' not found in project. If target is a new file, create it first or use 'create_file'.")

        var srcContent = srcFile.content
        var dstContent = dstFile.content

        // 1. Locate the codeChunk in source file
        var effectiveChunk = codeChunk
        if (!srcContent.contains(effectiveChunk)) {
            val normSrc = srcContent.replace("\r\n", "\n")
            val normChunk = effectiveChunk.replace("\r\n", "\n")
            if (normSrc.contains(normChunk)) {
                srcContent = normSrc
                effectiveChunk = normChunk
            } else {
                return@withContext TransferResult.Failure("Error: Could not find specified code block in '$normSource'. Please check exact characters, indentation, and spacing.")
            }
        }

        // 2. Determine insertion into target file
        val mode = (insertAt ?: if (!targetAnchor.isNullOrBlank()) "after" else "append").lowercase().trim()
        val newDstContent = when (mode) {
            "start", "prepend" -> {
                effectiveChunk + "\n\n" + dstContent
            }
            "end", "append" -> {
                if (dstContent.isBlank()) effectiveChunk else dstContent.trimEnd() + "\n\n" + effectiveChunk + "\n"
            }
            "before" -> {
                val anchor = targetAnchor ?: ""
                if (anchor.isBlank() || !dstContent.contains(anchor)) {
                    val normDst = dstContent.replace("\r\n", "\n")
                    val normAnchor = anchor.replace("\r\n", "\n")
                    if (anchor.isNotBlank() && normDst.contains(normAnchor)) {
                        normDst.replaceFirst(normAnchor, effectiveChunk + "\n" + normAnchor)
                    } else {
                        return@withContext TransferResult.Failure("Error: Target anchor '$anchor' not found in '$normTarget' for 'before' insertion.")
                    }
                } else {
                    dstContent.replaceFirst(anchor, effectiveChunk + "\n" + anchor)
                }
            }
            "replace" -> {
                val anchor = targetAnchor ?: ""
                if (anchor.isBlank() || !dstContent.contains(anchor)) {
                    val normDst = dstContent.replace("\r\n", "\n")
                    val normAnchor = anchor.replace("\r\n", "\n")
                    if (anchor.isNotBlank() && normDst.contains(normAnchor)) {
                        normDst.replaceFirst(normAnchor, effectiveChunk)
                    } else {
                        return@withContext TransferResult.Failure("Error: Target anchor '$anchor' not found in '$normTarget' to replace.")
                    }
                } else {
                    dstContent.replaceFirst(anchor, effectiveChunk)
                }
            }
            else -> { // Default "after"
                val anchor = targetAnchor ?: ""
                if (anchor.isNotBlank()) {
                    if (!dstContent.contains(anchor)) {
                        val normDst = dstContent.replace("\r\n", "\n")
                        val normAnchor = anchor.replace("\r\n", "\n")
                        if (normDst.contains(normAnchor)) {
                            normDst.replaceFirst(normAnchor, normAnchor + "\n\n" + effectiveChunk)
                        } else {
                            // Fallback to append if anchor not found
                            dstContent.trimEnd() + "\n\n" + effectiveChunk + "\n"
                        }
                    } else {
                        dstContent.replaceFirst(anchor, anchor + "\n\n" + effectiveChunk)
                    }
                } else {
                    if (dstContent.isBlank()) effectiveChunk else dstContent.trimEnd() + "\n\n" + effectiveChunk + "\n"
                }
            }
        }

        // 3. Save target file
        repository.saveFile(project.name, dstFile.path, newDstContent)

        // 4. If move requested, remove codeChunk from source file
        if (isMove) {
            val newSrcContent = srcContent.replaceFirst(effectiveChunk, "").trim()
            repository.saveFile(project.name, srcFile.path, newSrcContent)
        }

        val actionName = if (isMove) "moved" else "copied"
        val linesCount = effectiveChunk.lines().size
        TransferResult.Success(
            message = "Successfully $actionName $linesCount lines from '$normSource' into '$normTarget' ($mode). Both files updated.",
            isMove = isMove
        )
    }
}
