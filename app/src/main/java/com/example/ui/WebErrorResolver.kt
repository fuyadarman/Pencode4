package com.example.ui

import com.example.data.ProjectFileEntity
import java.io.File
import java.util.Locale

data class ResolvedWebError(
    val id: String = java.util.UUID.randomUUID().toString(),
    val rawMessage: String,
    val rawSourceId: String,
    val cleanFilePath: String,
    val lineNumber: Int,
    val codeSnippet: String,
    val isSelected: Boolean = true
)

object WebErrorResolver {

    fun cleanSourceId(rawSourceId: String, files: List<ProjectFileEntity>): String {
        var clean = rawSourceId.trim()

        // Strip query params and fragment identifiers (?v=123, #hash)
        val queryIdx = clean.indexOf('?')
        if (queryIdx != -1) clean = clean.substring(0, queryIdx)
        val hashIdx = clean.indexOf('#')
        if (hashIdx != -1) clean = clean.substring(0, hashIdx)

        // Strip protocols and origins
        clean = clean.removePrefix("https://virtual-app/")
            .removePrefix("http://virtual-app/")
            .removePrefix("http://localhost:8080/")
            .removePrefix("http://127.0.0.1:8080/")
            .removePrefix("file:///")

        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            val urlParts = clean.split("/")
            clean = if (urlParts.size > 3) urlParts.drop(3).joinToString("/") else urlParts.last()
        }

        while (clean.startsWith("/")) {
            clean = clean.substring(1)
        }

        if (clean.startsWith("./")) {
            clean = clean.substring(2)
        }

        // Check if clean matches any file in workspace
        val match = findMatchingFile(clean, files)
        if (match != null) {
            return match.path
        }

        // Handle generic/empty/blob/data source IDs
        if (clean.isBlank() || clean == "about:blank" || clean.startsWith("data:") || clean.startsWith("blob:")) {
            val indexFile = files.find { it.path.equals("index.html", ignoreCase = true) || it.path.endsWith("/index.html", ignoreCase = true) }
            if (indexFile != null) return indexFile.path

            val htmlFile = files.find { it.path.endsWith(".html", ignoreCase = true) }
            if (htmlFile != null) return htmlFile.path

            val firstFile = files.firstOrNull()
            if (firstFile != null) return firstFile.path
        }

        return clean.ifBlank { "index.html" }
    }

    fun findMatchingFile(filePath: String, files: List<ProjectFileEntity>): ProjectFileEntity? {
        if (files.isEmpty()) return null
        val normalizedTarget = filePath.replace("\\", "/").lowercase(Locale.US)
        val targetName = File(normalizedTarget).name

        // Exact relative path match
        files.find { it.path.replace("\\", "/").lowercase(Locale.US) == normalizedTarget }?.let { return it }

        // Ends with target path (e.g. /app.js matches app.js)
        files.find {
            val p = it.path.replace("\\", "/").lowercase(Locale.US)
            p.endsWith("/$normalizedTarget") || normalizedTarget.endsWith("/$p")
        }?.let { return it }

        // Match filename strictly
        if (targetName.isNotBlank()) {
            files.find {
                File(it.path.replace("\\", "/")).name.equals(targetName, ignoreCase = true)
            }?.let { return it }
        }

        return null
    }

    fun extractCodeSnippet(content: String, lineNumber: Int, contextLines: Int = 8): String {
        if (content.isBlank()) return ""
        val lines = content.split("\n")
        if (lines.isEmpty()) return ""

        val targetIdx = if (lineNumber > 0) lineNumber - 1 else 0
        val clampedTarget = targetIdx.coerceIn(0, lines.size - 1)

        val start = maxOf(0, clampedTarget - contextLines)
        val end = minOf(lines.size - 1, clampedTarget + contextLines)

        return buildString {
            append("\n[Source Snippet around Line ${clampedTarget + 1}]:\n")
            append("--------------------------------------------------\n")
            for (i in start..end) {
                val marker = if (i == clampedTarget) "->" else "  "
                append(String.format(Locale.US, "%s %4d: %s\n", marker, i + 1, lines[i]))
            }
            append("--------------------------------------------------")
        }
    }

    fun resolveError(
        rawMessage: String,
        rawSourceId: String,
        lineNumber: Int,
        files: List<ProjectFileEntity>
    ): ResolvedWebError {
        val cleanPath = cleanSourceId(rawSourceId, files)
        var matchedFile = findMatchingFile(cleanPath, files)

        if (matchedFile == null && rawMessage.isNotBlank()) {
            val symbols = extractSymbolsFromMessage(rawMessage)
            for (sym in symbols) {
                val found = files.find { it.content.contains(sym) }
                if (found != null) {
                    matchedFile = found
                    break
                }
            }
        }

        val fileContent = matchedFile?.content ?: ""
        val resolvedPath = matchedFile?.path ?: cleanPath
        val snippet = extractCodeSnippet(fileContent, lineNumber)

        return ResolvedWebError(
            rawMessage = rawMessage,
            rawSourceId = rawSourceId,
            cleanFilePath = resolvedPath,
            lineNumber = lineNumber,
            codeSnippet = snippet
        )
    }

    private fun extractSymbolsFromMessage(message: String): List<String> {
        val result = mutableListOf<String>()
        val notDefinedRegex = Regex("""([a-zA-Z0-9_\$]+)\s+is not defined""")
        notDefinedRegex.find(message)?.groupValues?.get(1)?.let { result.add(it) }

        val propertyRegex = Regex("""property '([a-zA-Z0-9_\$]+)'""")
        propertyRegex.find(message)?.groupValues?.get(1)?.let { result.add(it) }

        return result
    }

    fun buildPromptReport(
        errors: List<ResolvedWebError>,
        allProjectFiles: List<ProjectFileEntity>
    ): String {
        val sb = StringBuilder()
        sb.append("CRITICAL PREVIEW ERROR DETECTED:\n")
        sb.append("The web application failed with the following console error(s) during live rendering.\n\n")

        errors.forEachIndexed { index, err ->
            sb.append("--- Error #${index + 1} ---\n")
            sb.append("• Message: \"${err.rawMessage}\"\n")
            sb.append("• Location: File \"${err.cleanFilePath}\" at Line ${err.lineNumber}\n")
            if (err.rawSourceId != err.cleanFilePath && err.rawSourceId.isNotBlank()) {
                sb.append("• Original Source ID: ${err.rawSourceId}\n")
            }
            if (err.codeSnippet.isNotBlank()) {
                sb.append(err.codeSnippet).append("\n")
            } else {
                sb.append("[No line snippet directly matched for file \"${err.cleanFilePath}\"]\n")
            }
            sb.append("\n")
        }

        if (allProjectFiles.isNotEmpty()) {
            sb.append("Project Structure & Available Files:\n")
            allProjectFiles.forEach { f ->
                sb.append("  - ${f.path} (${f.content.lines().size} lines)\n")
            }
        }

        sb.append("\nInstructions for AI:\n")
        sb.append("1. Locate the exact file and line number indicated in the error snippet.\n")
        sb.append("2. Identify the root cause (e.g., missing variable, syntax error, undefined reference, or broken import).\n")
        sb.append("3. Fix the bug by editing the file cleanly and completely.\n")

        return sb.toString()
    }
}
