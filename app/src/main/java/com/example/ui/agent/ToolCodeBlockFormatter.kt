package com.example.ui.agent

object ToolCodeBlockFormatter {

    /**
     * Formats a code block read by read_file_range with line numbers.
     */
    fun formatReadRange(filePath: String, startLine: Int, endLine: Int, rawCode: String): String {
        val lines = rawCode.lines()
        val sb = StringBuilder()
        sb.append("File: ").append(filePath).append(" (Lines ").append(startLine).append("-").append(endLine).append(")\n\n")

        val maxDisplayLines = 120
        val displayLines = lines.take(maxDisplayLines)
        val maxLineNum = startLine + displayLines.size - 1
        val padWidth = maxLineNum.toString().length.coerceAtLeast(2)

        displayLines.forEachIndexed { index, line ->
            val currentLineNum = startLine + index
            val lineNumStr = currentLineNum.toString().padStart(padWidth, ' ')
            sb.append(lineNumStr).append(" | ").append(line).append("\n")
        }
        if (lines.size > maxDisplayLines) {
            sb.append("\n... [Omitted remaining ${lines.size - maxDisplayLines} lines in preview to protect UI performance]\n")
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

        val safeSearch = if (searchStr.length > 3000) searchStr.take(3000) + "\n... [Search block truncated in log preview]" else searchStr
        val safeReplace = if (replaceStr.length > 3000) replaceStr.take(3000) + "\n... [Replacement block truncated in log preview]" else replaceStr

        sb.append("<<<<<<< Target Block (Search):\n")
        sb.append(safeSearch.trimEnd()).append("\n")
        sb.append("=======\n")
        sb.append(">>>>>>> Replacement Block:\n")
        sb.append(safeReplace.trimEnd()).append("\n")

        return sb.toString()
    }

    /**
     * Formats multi_edit_file replacement chunks.
     */
    fun formatMultiEditChunks(filePath: String, chunks: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.append("File: ").append(filePath).append(" (").append(chunks.size).append(" chunks)\n\n")

        val displayChunks = chunks.take(8)
        displayChunks.forEachIndexed { index, (search, replace) ->
            val safeSearch = if (search.length > 1500) search.take(1500) + "..." else search
            val safeReplace = if (replace.length > 1500) replace.take(1500) + "..." else replace
            sb.append("--- Chunk ").append(index + 1).append(" ---\n")
            sb.append("<<<<<<< Target Block:\n")
            sb.append(safeSearch.trimEnd()).append("\n")
            sb.append("=======\n")
            sb.append(">>>>>>> Replacement Block:\n")
            sb.append(safeReplace.trimEnd()).append("\n\n")
        }
        if (chunks.size > 8) {
            sb.append("... [Omitted ${chunks.size - 8} additional chunks in preview]\n")
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
