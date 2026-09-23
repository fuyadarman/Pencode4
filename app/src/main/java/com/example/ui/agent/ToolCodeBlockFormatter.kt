package com.example.ui.agent

object ToolCodeBlockFormatter {

    /**
     * Formats a code block read by read_file_range with line numbers.
     */
    fun formatReadRange(filePath: String, startLine: Int, endLine: Int, rawCode: String): String {
        val lines = rawCode.lines()
        val sb = StringBuilder()
        sb.append("File: ").append(filePath).append(" (Lines ").append(startLine).append("-").append(endLine).append(")\n\n")

        val maxLineNum = startLine + lines.size - 1
        val padWidth = maxLineNum.toString().length.coerceAtLeast(2)

        lines.forEachIndexed { index, line ->
            val currentLineNum = startLine + index
            val lineNumStr = currentLineNum.toString().padStart(padWidth, ' ')
            sb.append(lineNumStr).append(" | ").append(line).append("\n")
        }
        return sb.toString().trimEnd()
    }

    /**
     * Formats surgical edit_file target and replacement code blocks.
     */
    fun formatEditFileChunks(filePath: String, searchStr: String, replaceStr: String, lineRange: String? = null): String {
        val sb = StringBuilder()
        sb.append("File: ").append(filePath)
        if (!lineRange.isNullOrBlank()) {
            sb.append(" (").append(lineRange).append(")")
        }
        sb.append("\n\n")

        sb.append("<<<<<<< Target Block (Search):\n")
        sb.append(searchStr.trimEnd()).append("\n")
        sb.append("=======\n")
        sb.append(">>>>>>> Replacement Block:\n")
        sb.append(replaceStr.trimEnd()).append("\n")

        return sb.toString()
    }

    /**
     * Formats multi_edit_file replacement chunks.
     */
    fun formatMultiEditChunks(filePath: String, chunks: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.append("File: ").append(filePath).append(" (").append(chunks.size).append(" chunks)\n\n")

        chunks.forEachIndexed { index, (search, replace) ->
            sb.append("--- Chunk ").append(index + 1).append(" ---\n")
            sb.append("<<<<<<< Target Block:\n")
            sb.append(search.trimEnd()).append("\n")
            sb.append("=======\n")
            sb.append(">>>>>>> Replacement Block:\n")
            sb.append(replace.trimEnd()).append("\n\n")
        }
        return sb.toString().trimEnd()
    }

    /**
     * Formats directory scanning result with clean tree structure.
     */
    fun formatScanDir(path: String, fileCount: Int, fileListing: String): String {
        val sb = StringBuilder()
        sb.append("Scanned Directory: ").append(if (path.isBlank()) "workspace root" else path)
        sb.append(" (Found ").append(fileCount).append(" files)\n\n")
        sb.append(fileListing)
        return sb.toString()
    }
}
