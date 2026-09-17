package com.example.agent

import com.example.api.ToolArguments
import com.example.data.VibeRepository

object AgentReadToolHandler {

    private fun normalizePath(path: String?): String = AgentLoopProtectionEngine.normalizePath(path)

    data class ReadResult(
        val output: String,
        val isSuccess: Boolean,
        val logTitle: String,
        val logDetails: String,
        val lineRange: String? = null,
        val pathsRead: List<String> = emptyList()
    )

    suspend fun handleReadTool(
        tool: String,
        args: ToolArguments?,
        projectName: String,
        repository: VibeRepository
    ): ReadResult {
        return when (tool) {
            "list_directory" -> {
                val targetPath = args?.path ?: "."
                val files = repository.getFilesForProject(projectName)
                val fileDetails = files.sortedBy { it.path }.map { file ->
                    val lineCount = file.content.lines().size
                    val sizeInBytes = file.content.toByteArray(Charsets.UTF_8).size
                    val sizeStr = if (sizeInBytes >= 1024 * 1024) {
                        String.format("%.2f MB", sizeInBytes.toDouble() / (1024 * 1024))
                    } else {
                        String.format("%.2f KB", sizeInBytes.toDouble() / 1024)
                    }
                    "  - ${file.path} ($lineCount lines, $sizeStr)"
                }.joinToString("\n")
                val result = if (fileDetails.isEmpty()) "Directory is empty." else "Files found:\n$fileDetails"
                ReadResult(
                    output = result,
                    isSuccess = true,
                    logTitle = "Explored directory",
                    logDetails = result,
                    lineRange = null
                )
            }
            "scan_dir" -> {
                val rawPath = args?.path ?: args?.query ?: args?.targetFile ?: ""
                val scanResult = AgentDirectoryScanResolver.scanDirectory(rawPath, projectName, repository)
                ReadResult(
                    output = scanResult.output,
                    isSuccess = scanResult.isSuccess,
                    logTitle = "Scanned directory (scan_dir)",
                    logDetails = if (scanResult.isSuccess) "Found ${scanResult.fileCount} files in $rawPath" else scanResult.output,
                    lineRange = null,
                    pathsRead = scanResult.matchedFiles
                )
            }
            "read_file" -> {
                val filePath = normalizePath(args?.path ?: "")
                val loopCheck = AgentReadLoopPolicy.evaluateRead(tool, filePath, "all")
                if (loopCheck is AgentReadLoopPolicy.LoopCheckResult.Intercept) {
                    return ReadResult(
                        output = loopCheck.responseMessage,
                        isSuccess = true,
                        logTitle = "Read loop suppressed ($filePath)",
                        logDetails = loopCheck.logDetails,
                        lineRange = "all",
                        pathsRead = listOf(filePath)
                    )
                }
                val warningSuffix = (loopCheck as? AgentReadLoopPolicy.LoopCheckResult.Warn)?.directive ?: ""

                if (repository.isBinaryExtension(filePath)) {
                    val err = "Error: Reading, editing, patching, or appending to binary image or 3D files directly as text is NOT allowed. You can only view their existence via 'list_directory' or perform operations like rename, delete, move, resize, or format change."
                    ReadResult(
                        output = err,
                        isSuccess = false,
                        logTitle = "Read file",
                        logDetails = "Error: Cannot read binary files as text",
                        lineRange = "all"
                    )
                } else {
                    val files = repository.getFilesForProject(projectName)
                    val targetFile = files.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }
                    if (targetFile != null) {
                        val paths = listOf(filePath, normalizePath(filePath), targetFile.path, normalizePath(targetFile.path))
                        ReadResult(
                            output = "--- File: $filePath ---\n${targetFile.content}$warningSuffix",
                            isSuccess = true,
                            logTitle = "Read file",
                            logDetails = "Read ${targetFile.content.lines().size} lines from $filePath",
                            lineRange = "all",
                            pathsRead = paths
                        )
                    } else {
                        val diskResolved = AgentDirectoryScanResolver.resolveFile(args?.path ?: filePath, projectName, repository)
                        if (diskResolved != null) {
                            val paths = listOf(filePath, diskResolved.path)
                            ReadResult(
                                output = "--- File: ${diskResolved.path} ---\n${diskResolved.content}$warningSuffix",
                                isSuccess = true,
                                logTitle = "Read file",
                                logDetails = "Read ${diskResolved.linesCount} lines from ${diskResolved.path}",
                                lineRange = "all",
                                pathsRead = paths
                            )
                        } else {
                            val err = "Error: File '$filePath' not found."
                            ReadResult(
                                output = err,
                                isSuccess = false,
                                logTitle = "Read file",
                                logDetails = err,
                                lineRange = "all"
                            )
                        }
                    }
                }
            }
            "read_file_range" -> {
                val filePath = normalizePath(args?.path ?: "")
                var rawStartLine = args?.startLine
                var rawEndLine = args?.endLine

                val lr = args?.lineRange
                if (!lr.isNullOrBlank()) {
                    val match = """(\d+)\s*[-:to\.]+\s*(\d+)""".toRegex().find(lr)
                    if (match != null) {
                        rawStartLine = rawStartLine ?: match.groupValues[1].toIntOrNull()
                        rawEndLine = rawEndLine ?: match.groupValues[2].toIntOrNull()
                    } else {
                        val singleMatch = """(\d+)""".toRegex().find(lr)
                        if (singleMatch != null) {
                            rawStartLine = rawStartLine ?: singleMatch.groupValues[1].toIntOrNull()
                        }
                    }
                }

                val startLine = rawStartLine ?: 1
                var endLineInput = rawEndLine ?: (startLine + 99)
                if (endLineInput < startLine) {
                    endLineInput = startLine + 99
                }
                val endLine = endLineInput
                val lineRangeDesc = "Line $startLine-$endLine"

                val loopCheck = AgentReadLoopPolicy.evaluateRead(tool, filePath, lineRangeDesc)
                if (loopCheck is AgentReadLoopPolicy.LoopCheckResult.Intercept) {
                    return ReadResult(
                        output = loopCheck.responseMessage,
                        isSuccess = true,
                        logTitle = "Read loop suppressed ($filePath)",
                        logDetails = loopCheck.logDetails,
                        lineRange = lineRangeDesc,
                        pathsRead = listOf(filePath)
                    )
                }
                val warningSuffix = (loopCheck as? AgentReadLoopPolicy.LoopCheckResult.Warn)?.directive ?: ""

                if (repository.isBinaryExtension(filePath)) {
                    val err = "Error: Reading, editing, patching, or appending to binary image or 3D files directly as text is NOT allowed."
                    ReadResult(
                        output = err,
                        isSuccess = false,
                        logTitle = "read :$filePath",
                        logDetails = "Error: Cannot read binary files as text",
                        lineRange = lineRangeDesc
                    )
                } else {
                    val files = repository.getFilesForProject(projectName)
                    val targetFile = files.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }
                    if (targetFile != null) {
                        val paths = listOf(filePath, normalizePath(filePath), targetFile.path, normalizePath(targetFile.path))
                        val lines = targetFile.content.lines()
                        val startIdx = (startLine - 1).coerceAtLeast(0).coerceAtMost(lines.size)
                        val endIdx = endLine.coerceAtLeast(startIdx).coerceAtMost(lines.size)
                        val selectedLines = lines.subList(startIdx, endIdx).joinToString("\n")
                        ReadResult(
                            output = "--- File: $filePath (Lines ${startIdx + 1}-$endIdx) ---\n$selectedLines$warningSuffix",
                            isSuccess = true,
                            logTitle = "read :$filePath",
                            logDetails = "Read lines $startLine-$endLine from $filePath",
                            lineRange = lineRangeDesc,
                            pathsRead = paths
                        )
                    } else {
                        val diskResolved = AgentDirectoryScanResolver.resolveFile(args?.path ?: filePath, projectName, repository)
                        if (diskResolved != null) {
                            val paths = listOf(filePath, diskResolved.path)
                            val lines = diskResolved.content.lines()
                            val startIdx = (startLine - 1).coerceAtLeast(0).coerceAtMost(lines.size)
                            val endIdx = endLine.coerceAtLeast(startIdx).coerceAtMost(lines.size)
                            val selectedLines = lines.subList(startIdx, endIdx).joinToString("\n")
                            ReadResult(
                                output = "--- File: ${diskResolved.path} (Lines ${startIdx + 1}-$endIdx) ---\n$selectedLines$warningSuffix",
                                isSuccess = true,
                                logTitle = "read :${diskResolved.path}",
                                logDetails = "Read lines $startLine-$endLine from ${diskResolved.path}",
                                lineRange = lineRangeDesc,
                                pathsRead = paths
                            )
                        } else {
                            val err = "Error: File '$filePath' not found."
                            ReadResult(
                                output = err,
                                isSuccess = false,
                                logTitle = "read :$filePath",
                                logDetails = err,
                                lineRange = lineRangeDesc
                            )
                        }
                    }
                }
            }
            "multi_read_file", "multi_read" -> {
                val filePath = normalizePath(args?.path ?: args?.targetFile ?: "")
                val rangePairs = mutableListOf<Pair<Int, Int>>()

                val rawRanges = args?.ranges
                val rawRangeList = args?.rangeList
                val rawLineRange = args?.lineRange ?: args?.query ?: args?.search ?: ""

                if (!rawRanges.isNullOrEmpty()) {
                    for (item in rawRanges) {
                        val s = item.startLine
                        val e = item.endLine ?: item.startLine
                        if (s != null) {
                            rangePairs.add(Pair(s, e ?: s))
                        } else if (!item.range.isNullOrBlank()) {
                            val match = """(\d+)\s*[-:to\.]+\s*(\d+)""".toRegex().find(item.range)
                            if (match != null) {
                                val st = match.groupValues[1].toIntOrNull()
                                val en = match.groupValues[2].toIntOrNull()
                                if (st != null) rangePairs.add(Pair(st, en ?: st))
                            } else {
                                val single = """(\d+)""".toRegex().find(item.range)?.groupValues?.get(1)?.toIntOrNull()
                                if (single != null) rangePairs.add(Pair(single, single))
                            }
                        }
                    }
                }

                if (rangePairs.isEmpty() && !rawRangeList.isNullOrEmpty()) {
                    for (str in rawRangeList) {
                        val match = """(\d+)\s*[-:to\.]+\s*(\d+)""".toRegex().find(str)
                        if (match != null) {
                            val st = match.groupValues[1].toIntOrNull()
                            val en = match.groupValues[2].toIntOrNull()
                            if (st != null) rangePairs.add(Pair(st, en ?: st))
                        } else {
                            val single = """(\d+)""".toRegex().find(str)?.groupValues?.get(1)?.toIntOrNull()
                            if (single != null) rangePairs.add(Pair(single, single))
                        }
                    }
                }

                if (rangePairs.isEmpty() && rawLineRange.isNotBlank()) {
                    val matches = """(\d+)\s*[-:to\.]+\s*(\d+)""".toRegex().findAll(rawLineRange)
                    for (m in matches) {
                        val st = m.groupValues[1].toIntOrNull()
                        val en = m.groupValues[2].toIntOrNull()
                        if (st != null) rangePairs.add(Pair(st, en ?: st))
                    }
                    if (rangePairs.isEmpty()) {
                        val singleMatches = """(\d+)""".toRegex().findAll(rawLineRange)
                        for (sm in singleMatches) {
                            val num = sm.groupValues[1].toIntOrNull()
                            if (num != null) rangePairs.add(Pair(num, num))
                        }
                    }
                }

                if (rangePairs.isEmpty()) {
                    rangePairs.add(Pair(1, 30))
                }

                val formattedRanges = rangePairs.joinToString(", ") { (st, en) ->
                    if (st == en) "$st" else "$st-$en"
                }

                if (repository.isBinaryExtension(filePath)) {
                    ReadResult(
                        output = "Error: Cannot read binary files as text.",
                        isSuccess = false,
                        logTitle = "read :$filePath",
                        logDetails = "Error: Cannot read binary files as text.",
                        lineRange = "Line $formattedRanges"
                    )
                } else {
                    val files = repository.getFilesForProject(projectName)
                    val targetFile = files.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }
                    if (targetFile != null) {
                        val paths = listOf(filePath, normalizePath(filePath), targetFile.path, normalizePath(targetFile.path))
                        val lines = targetFile.content.lines()
                        val blocks = mutableListOf<String>()
                        for ((st, en) in rangePairs) {
                            val startIdx = (st - 1).coerceAtLeast(0).coerceAtMost(lines.size)
                            val endIdx = en.coerceAtLeast(startIdx).coerceAtMost(lines.size)
                            val selectedLines = lines.subList(startIdx, endIdx).joinToString("\n")
                            blocks.add("--- File: $filePath (Lines ${startIdx + 1}-$endIdx) ---\n$selectedLines")
                        }
                        val result = blocks.joinToString("\n\n")
                        ReadResult(
                            output = result,
                            isSuccess = true,
                            logTitle = "read :$filePath",
                            logDetails = "Read lines $formattedRanges from $filePath",
                            lineRange = "Line $formattedRanges",
                            pathsRead = paths
                        )
                    } else {
                        val diskResolved = AgentDirectoryScanResolver.resolveFile(args?.path ?: args?.targetFile ?: filePath, projectName, repository)
                        if (diskResolved != null) {
                            val paths = listOf(filePath, diskResolved.path)
                            val lines = diskResolved.content.lines()
                            val blocks = mutableListOf<String>()
                            for ((st, en) in rangePairs) {
                                val startIdx = (st - 1).coerceAtLeast(0).coerceAtMost(lines.size)
                                val endIdx = en.coerceAtLeast(startIdx).coerceAtMost(lines.size)
                                val selectedLines = lines.subList(startIdx, endIdx).joinToString("\n")
                                blocks.add("--- File: ${diskResolved.path} (Lines ${startIdx + 1}-$endIdx) ---\n$selectedLines")
                            }
                            val result = blocks.joinToString("\n\n")
                            ReadResult(
                                output = result,
                                isSuccess = true,
                                logTitle = "read :${diskResolved.path}",
                                logDetails = "Read lines $formattedRanges from ${diskResolved.path}",
                                lineRange = "Line $formattedRanges",
                                pathsRead = paths
                            )
                        } else {
                            val err = "Error: File '$filePath' not found."
                            ReadResult(
                                output = err,
                                isSuccess = false,
                                logTitle = "read :$filePath",
                                logDetails = err,
                                lineRange = "Line $formattedRanges"
                            )
                        }
                    }
                }
            }
            else -> ReadResult(
                output = "Error: Unknown read tool '$tool'",
                isSuccess = false,
                logTitle = tool,
                logDetails = "Unknown read tool"
            )
        }
    }
}
