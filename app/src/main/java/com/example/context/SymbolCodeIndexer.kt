package com.example.context

import java.io.File

data class CodeSymbol(
    val name: String,
    val kind: String, // "class", "function", "composable", "state"
    val filePath: String,
    val lineNum: Int,
    val snippet: String
)

/**
 * Lightweight AST & Code Symbol Indexer (Local RAG & Code Search Engine).
 * Scans project files to index declarations, functions, and composables for fast context retrieval.
 */
class SymbolCodeIndexer {

    private val index = mutableListOf<CodeSymbol>()

    /**
     * Index code content from a specific file path.
     */
    fun indexFileContent(filePath: String, fileContent: String) {
        val lines = fileContent.lines()
        lines.forEachIndexed { indexZero, line ->
            val trimmed = line.trim()
            val lineNum = indexZero + 1

            when {
                trimmed.startsWith("class ") || trimmed.startsWith("data class ") || trimmed.startsWith("object ") || trimmed.startsWith("interface ") -> {
                    val name = trimmed.substringAfter("class ").substringAfter("object ").substringAfter("interface ").substringBefore(" ").substringBefore("(")
                    if (name.isNotBlank()) {
                        index.add(CodeSymbol(name, "class", filePath, lineNum, trimmed))
                    }
                }
                trimmed.startsWith("fun ") || trimmed.contains(" fun ") -> {
                    val name = trimmed.substringAfter("fun ").substringBefore("(").substringBefore("<").trim()
                    if (name.isNotBlank()) {
                        val kind = if (trimmed.contains("@Composable")) "composable" else "function"
                        index.add(CodeSymbol(name, kind, filePath, lineNum, trimmed))
                    }
                }
                trimmed.startsWith("val ") || trimmed.startsWith("var ") -> {
                    if (trimmed.contains("MutableStateFlow") || trimmed.contains("mutableStateOf") || trimmed.contains("by remember")) {
                        val name = trimmed.substringAfter("val ").substringAfter("var ").substringBefore(":").substringBefore("=").trim()
                        if (name.isNotBlank()) {
                            index.add(CodeSymbol(name, "state", filePath, lineNum, trimmed))
                        }
                    }
                }
            }
        }
    }

    /**
     * Performs a lightweight symbol search for relevant symbols matching a user query or error keyword.
     */
    fun findRelevantSymbols(query: String, maxResults: Int = 5): List<CodeSymbol> {
        val keywords = query.lowercase().split(Regex("""\W+""")).filter { it.length > 2 }
        if (keywords.isEmpty()) return emptyList()

        return index.map { symbol ->
            var score = 0
            val symbolName = symbol.name.lowercase()
            val snippet = symbol.snippet.lowercase()

            for (kw in keywords) {
                if (symbolName == kw) score += 10
                else if (symbolName.contains(kw)) score += 5
                if (snippet.contains(kw)) score += 2
            }
            symbol to score
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(maxResults)
        .map { it.first }
    }

    /**
     * Generates a compact context block summarizing relevant code symbols for the prompt.
     */
    fun buildSymbolContextBlock(query: String): String {
        val symbols = findRelevantSymbols(query)
        if (symbols.isEmpty()) return ""

        return StringBuilder().apply {
            append("\n[Indexed Code Symbols Context (RAG Relevance)]\n")
            symbols.forEach { sym ->
                append("• ${sym.kind.uppercase()} `${sym.name}` -> ${sym.filePath}:${sym.lineNum}\n  `${sym.snippet}`\n")
            }
            append("------------------------------------------------\n")
        }.toString()
    }

    fun indexProjectDirectory(projectDir: File) {
        if (!projectDir.exists()) return
        clearIndex()
        projectDir.walkTopDown().filter { file ->
            file.isFile && (file.extension == "kt" || file.extension == "java" || file.extension == "js" || file.extension == "ts" || file.extension == "py")
        }.forEach { file ->
            val relPath = file.relativeToOrNull(projectDir)?.path ?: file.name
            try {
                indexFileContent(relPath, file.readText())
            } catch (_: Exception) {}
        }
    }

    fun clearIndex() {
        index.clear()
    }
}
