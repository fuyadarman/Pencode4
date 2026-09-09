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
                val rawPath = args?.path ?: args?.query ?: ""
                var targetPath = normalizePath(rawPath)
                if (targetPath == "." || targetPath == "./" || targetPath == "/") {
                    targetPath = ""
                }
                if (targetPath.isEmpty()) {
                    val errorMsg = "Error: Scanning the entire project root with 'scan_dir' is strictly forbidden to protect the context limit. You MUST specify a specific target subdirectory path (e.g., 'app', 'app/src', 'app/src/main/java/com/example') to scan its contents. This is a mandatory safety rule."
                    ReadResult(
                        output = errorMsg,
                        isSuccess = false,
                        logTitle = "Scanned directory (scan_dir)",
                        logDetails = errorMsg,
                        lineRange = null
                    )
                } else {
                    val files = repository.getFilesForProject(projectName)
                    val filteredFiles = files.filter { it.path.startsWith(targetPath) }
                    val fileDetails = filteredFiles.sortedBy { it.path }.map { file ->
                        val lineCount = file.content.lines().size
                        val sizeInBytes = file.content.toByteArray(Charsets.UTF_8).size
                        val sizeStr = if (sizeInBytes >= 1024 * 1024) {
                            String.format("%.2f MB", sizeInBytes.toDouble() / (1024 * 1024))
                        } else {
                            String.format("%.2f KB", sizeInBytes.toDouble() / 1024)
                        }
                        "  - ${file.path} ($lineCount lines, $sizeStr)"
                    }.joinToString("\n")

                    val result = if (fileDetails.isEmpty()) {
                        "No files or subdirectories found under '$targetPath'."
                    } else {
                        "Recursive scan of directory '$targetPath' succeeded. Found ${filteredFiles.size} files:\n$fileDetails"
                    }
                    ReadResult(
                        output = result,
                        isSuccess = true,
                        logTitle = "Scanned directory (scan_dir)",
                        logDetails = result,
                        lineRange = null
                    )
                }
            }
            "read_file" -> {
                val filePath = normalizePath(args?.path ?: "")
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
                            output = "--- File: $filePath ---\n${targetFile.content}",
                            isSuccess = true,
                            logTitle = "Read file",
                            logDetails = "Read ${targetFile.content.lines().size} lines from $filePath",
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

                if (repository.isBinaryExtension(filePath)) {
                    val err = "Error: Reading, editing, patching, or appending to binary image or 3D files directly as text is NOT allowed."
                    ReadResult(
                        output = err,
                        isSuccess = false,
                        logTitle = "read :$filePath",
                        logDetails = "Error: Cannot read binary files as text",
                        lineRange = "Line $startLine-$endLine"
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
                            output = "--- File: $filePath (Lines ${startIdx + 1}-$endIdx) ---\n$selectedLines",
                            isSuccess = true,
                            logTitle = "read :$filePath",
                            logDetails = "Read lines $startLine-$endLine from $filePath",
                            lineRange = "Line $startLine-$endLine",
                            pathsRead = paths
                        )
                    } else {
                        val err = "Error: File '$filePath' not found."
                        ReadResult(
                            output = err,
                            isSuccess = false,
                            logTitle = "read :$filePath",
                            logDetails = err,
                            lineRange = "Line $startLine-$endLine"
                        )
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
            else -> ReadResult(
                output = "Error: Unknown read tool '$tool'",
                isSuccess = false,
                logTitle = tool,
                logDetails = "Unknown read tool"
            )
        }
    }
}
