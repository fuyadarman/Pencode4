package com.example.context

import com.example.api.Content
import com.example.api.Part

/**
 * Handles Smart Output Pruning, File Chunking, and Tool Response Truncation
 * to prevent massive token usage from large file reads or verbose command logs.
 */
object CodeChunkerAndPruner {

    private const val MAX_FILE_READ_CHARS = 5000
    private const val MAX_COMMAND_OUTPUT_CHARS = 3000
    private const val MAX_MODEL_OUTPUT_CHARS = 2500

    /**
     * Optimizes tool output parts and content messages in conversation history.
     */
    fun pruneAndChunkContents(history: List<Content>): List<Content> {
        val seenReadFilePaths = mutableSetOf<String>()
        val optimized = history.map { content -> content.copy() }.toMutableList()

        for (i in optimized.indices.reversed()) {
            val content = optimized[i]

            if (content.role == "user") {
                val updatedParts = content.parts.map { part ->
                    val text = part.text ?: return@map part

                    when {
                        text.contains("System/Tool Output for") -> {
                            pruneToolOutputText(text, seenReadFilePaths)
                        }
                        text.contains("[IMAGE_BASE64:") -> {
                            val cleanText = text.replace("""\[IMAGE_BASE64: data:.*?;base64,.*?\]""".toRegex(), "[Attached Image]")
                            part.copy(text = cleanText)
                        }
                        else -> part
                    }
                }
                optimized[i] = content.copy(parts = updatedParts)
            } else if (content.role == "model") {
                val updatedParts = content.parts.map { part ->
                    val text = part.text ?: return@map part
                    if (text.length > MAX_MODEL_OUTPUT_CHARS) {
                        val head = text.take(1000)
                        val tail = text.takeLast(1000)
                        part.copy(text = "$head\n\n[... Omitted middle thought/response content (${text.length - 2000} chars) to keep context slim ...]\n\n$tail")
                    } else {
                        part
                    }
                }
                optimized[i] = content.copy(parts = updatedParts)
            }
        }

        return optimized
    }

    private fun pruneToolOutputText(text: String, seenReadFilePaths: MutableSet<String>): Part {
        val isReadFile = text.contains("System/Tool Output for 'read_file'") || text.contains("System/Tool Output for 'read_file_range'")

        if (isReadFile) {
            val fileLine = text.lineSequence().firstOrNull { it.contains("--- File:") }
            if (fileLine != null) {
                val filePath = fileLine.substringAfter("--- File:")
                    .substringBefore(" (Lines")
                    .substringBefore(" (lines")
                    .substringBefore("---")
                    .trim()

                if (filePath.isNotEmpty()) {
                    if (seenReadFilePaths.contains(filePath)) {
                        val isRange = text.contains("read_file_range")
                        val toolName = if (isRange) "read_file_range" else "read_file"
                        return Part(text = "System/Tool Output for '$toolName' (File: $filePath):\n[Older duplicate content omitted to optimize context tokens. See latest read below for current content.]")
                    } else {
                        seenReadFilePaths.add(filePath)
                        if (text.length > MAX_FILE_READ_CHARS) {
                            val chunkedText = chunkLargeFileRead(text)
                            return Part(text = chunkedText)
                        }
                    }
                }
            }
        } else {
            if (text.length > MAX_COMMAND_OUTPUT_CHARS) {
                val chunkedText = chunkVerboseCommandOutput(text)
                return Part(text = chunkedText)
            }
        }

        return Part(text = text)
    }

    /**
     * Smart File Chunking: Keeps header, relevant key lines, and footer of long source files.
     */
    private fun chunkLargeFileRead(text: String): String {
        val head = text.take(2200)
        val tail = text.takeLast(2200)
        val omittedLength = text.length - 4400
        return "$head\n\n[... Smart Chunking: Omitted $omittedLength characters of middle code. Use read_file with startLine/endLine for specific sections ...]\n\n$tail"
    }

    /**
     * Smart Command Truncation: Preserves error messages, stack traces, and exit codes while stripping log spam.
     */
    private fun chunkVerboseCommandOutput(text: String): String {
        val lines = text.lines()
        val errorLines = lines.filter { line ->
            line.contains("error:", ignoreCase = true) ||
            line.contains("exception", ignoreCase = true) ||
            line.contains("FAILED", ignoreCase = true) ||
            line.contains("e:", ignoreCase = true) ||
            line.startsWith("Caused by:")
        }

        val head = text.take(1200)
        val tail = text.takeLast(1200)

        val errorSummary = if (errorLines.isNotEmpty()) {
            "\n\n--- Key Error/Exception Highlights ---\n" + errorLines.take(10).joinToString("\n") + "\n-------------------------------------\n\n"
        } else ""

        val omittedLength = text.length - (head.length + tail.length)
        return "$head$errorSummary[... Truncated $omittedLength characters of output log to optimize context tokens ...]\n\n$tail"
    }
}
