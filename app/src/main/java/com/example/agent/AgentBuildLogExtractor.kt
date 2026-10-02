package com.example.agent

import com.example.api.ToolArguments
import java.util.Locale

/**
 * AgentBuildLogExtractor
 * Precision diagnostic extractor for Build Tab GitHub Actions & compiler output.
 * Specifically isolates real compiler errors, Kotlin/Java syntax bugs, Gradle failure blocks,
 * and Vite/NPM/Flutter diagnostics while strictly filtering out GitHub Actions runner setup noise.
 */
object AgentBuildLogExtractor {

    private val COMPILER_DIAGNOSTIC_REGEX = Regex(
        """(?:e:\s+)?(?:file:///)?([a-zA-Z0-9_\-./\\]+\.(?:kt|java|dart|js|ts|tsx|jsx|xml|gradle\.kts|gradle)):(?:\((\d+),\s*(\d+)\)|(\d+):(\d+))(?::\s*error:|\s*:\s*|\s+)(.+)""",
        RegexOption.IGNORE_CASE
    )

    private val AAPT_RESOURCE_REGEX = Regex(
        """(?:AAPT:\s*error:\s*|error:\s*)(.+?)(?:\s*in\s*([a-zA-Z0-9_\-./\\]+\.xml):(\d+))?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Checks if a line is part of GitHub Actions runner setup, toolchains, cache, or action configuration dump.
     */
    fun isRunnerSetupOrNoiseLine(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT).trim()
        return lower.startsWith("##[group]") ||
                lower.startsWith("##[endgroup]") ||
                lower.startsWith("##[add-matcher]") ||
                lower.startsWith("##[remove-matcher]") ||
                lower.contains("toolchains.xml") ||
                lower.contains("merged default jdk locations") ||
                lower.contains("restore gradle state from cache") ||
                lower.contains("restore-cache") ||
                lower.contains("save-cache") ||
                lower.contains("post job cleanup") ||
                lower.contains("complete job") ||
                lower.contains("cleaning up orphan processes") ||
                lower.contains("add-job-summary") ||
                lower.contains("dependency-graph") ||
                lower.contains("continue-on-failure") ||
                lower.contains("if-no-files-found") ||
                lower.contains("workflow-run-conclusion") ||
                lower.contains("actions/setup-java") ||
                lower.contains("actions/setup-gradle") ||
                lower.contains("actions/cache") ||
                lower.contains("actions/checkout") ||
                (lower.startsWith("job-status:") || lower.startsWith("on-failure:") || lower.startsWith("fail-on-error:")) ||
                Regex("""^\s*[\w.-]+:\s*(never|always|true|false|disabled|enabled|null|\d+)\s*$""").matches(lower)
    }

    /**
     * Clean raw log lines by removing ANSI escape sequences and GitHub runner timestamps.
     */
    fun cleanLogLine(raw: String): String {
        return raw.replace(Regex("""\u001B\[[;\d]*m"""), "")
            .replace(Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z\s*"""), "")
            .trimEnd()
    }

    /**
     * Extracts pure compiler errors, Gradle failure blocks, and actionable diagnostics from build logs.
     */
    fun extractBuildDiagnostics(
        buildLogs: String,
        buildStatus: String,
        args: ToolArguments?
    ): String {
        if (buildLogs.isBlank()) {
            val status = if (buildStatus.isNotBlank()) " (Current status: $buildStatus)" else ""
            return "No build logs available in Build tab yet$status. Trigger a workflow run or push code to inspect build output."
        }

        val maxLines = (args?.maxLines ?: args?.count ?: 60).coerceIn(10, 150)
        val query = args?.query?.trim()?.lowercase(Locale.ROOT)
            ?: args?.search?.trim()?.lowercase(Locale.ROOT)

        val rawLines = buildLogs.lines()
        val cleanedLines = rawLines.map { cleanLogLine(it) }

        val compilerErrors = mutableListOf<String>()
        val gradleFailureBlocks = mutableListOf<String>()
        val webAndFlutterErrors = mutableListOf<String>()

        // 1. Primary Pass: Extract explicit compiler errors (e.g. Kotlin, Java, XML AAPT)
        for (line in cleanedLines) {
            if (isRunnerSetupOrNoiseLine(line)) continue
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // Kotlin / Java compiler diagnostic line
            val compilerMatch = COMPILER_DIAGNOSTIC_REGEX.find(trimmed)
            if (compilerMatch != null && !trimmed.startsWith("at ") && !trimmed.contains("Process completed with exit code")) {
                val filePath = compilerMatch.groupValues[1]
                val lineNum = compilerMatch.groupValues[2].ifEmpty { compilerMatch.groupValues[4] }
                val colNum = compilerMatch.groupValues[3].ifEmpty { compilerMatch.groupValues[5] }
                val errorMsg = compilerMatch.groupValues[6].trim()

                val loc = if (lineNum.isNotEmpty() && colNum.isNotEmpty()) " (Line $lineNum:$colNum)"
                else if (lineNum.isNotEmpty()) " (Line $lineNum)" else ""

                val entry = "📍 File: $filePath$loc\n   Error: $errorMsg\n   Snippet: $trimmed"
                if (!compilerErrors.contains(entry)) {
                    if (query.isNullOrBlank() || entry.lowercase(Locale.ROOT).contains(query)) {
                        compilerErrors.add(entry)
                    }
                }
                continue
            }

            // Android AAPT / Resource errors
            if (trimmed.contains("AAPT: error:", ignoreCase = true) || (trimmed.startsWith("error:") && trimmed.contains(".xml"))) {
                val aaptMatch = AAPT_RESOURCE_REGEX.find(trimmed)
                val msg = aaptMatch?.groupValues?.getOrNull(1)?.trim() ?: trimmed
                val file = aaptMatch?.groupValues?.getOrNull(2)
                val lineNo = aaptMatch?.groupValues?.getOrNull(3)

                val loc = if (!file.isNullOrBlank() && !lineNo.isNullOrBlank()) " in $file:$lineNo"
                else if (!file.isNullOrBlank()) " in $file" else ""

                val entry = "📍 Android Resource Error$loc:\n   $msg"
                if (!compilerErrors.contains(entry)) {
                    if (query.isNullOrBlank() || entry.lowercase(Locale.ROOT).contains(query)) {
                        compilerErrors.add(entry)
                    }
                }
                continue
            }

            // Web / Vite / NPM / Flutter errors
            val isWebOrFlutter = trimmed.contains("npm ERR!") ||
                    trimmed.contains("[vite]") && trimmed.contains("error", ignoreCase = true) ||
                    trimmed.contains("Rollup failed", ignoreCase = true) ||
                    trimmed.contains("SyntaxError:", ignoreCase = true) ||
                    trimmed.contains("TypeError:", ignoreCase = true) ||
                    trimmed.contains("Failed to compile", ignoreCase = true) ||
                    trimmed.contains("Target debug_android_application failed", ignoreCase = true)

            if (isWebOrFlutter) {
                if (query.isNullOrBlank() || trimmed.lowercase(Locale.ROOT).contains(query)) {
                    if (!webAndFlutterErrors.contains(trimmed)) {
                        webAndFlutterErrors.add(trimmed)
                    }
                }
            }
        }

        // 2. Secondary Pass: Extract Gradle task failure & "* What went wrong:" block
        val whatWentWrongIdx = cleanedLines.indexOfLast { it.contains("* What went wrong:", ignoreCase = true) }
        val taskFailedIdx = cleanedLines.indexOfLast { it.contains("Execution failed for task", ignoreCase = true) || it.contains("> Task") && it.contains("FAILED") }

        if (whatWentWrongIdx != -1) {
            val endIdx = cleanedLines.subList(whatWentWrongIdx, cleanedLines.size).indexOfFirst {
                it.contains("* Try:", ignoreCase = true) || it.contains("BUILD FAILED", ignoreCase = true)
            }.let { if (it != -1) whatWentWrongIdx + it + 1 else (whatWentWrongIdx + 12).coerceAtMost(cleanedLines.size) }

            val block = cleanedLines.subList(whatWentWrongIdx, endIdx.coerceAtMost(cleanedLines.size))
                .filter { !isRunnerSetupOrNoiseLine(it) }
                .joinToString("\n")
                .trim()

            if (block.isNotBlank()) gradleFailureBlocks.add(block)
        } else if (taskFailedIdx != -1) {
            val start = (taskFailedIdx - 1).coerceAtLeast(0)
            val end = (taskFailedIdx + 8).coerceAtMost(cleanedLines.size)
            val block = cleanedLines.subList(start, end)
                .filter { !isRunnerSetupOrNoiseLine(it) }
                .joinToString("\n")
                .trim()

            if (block.isNotBlank()) gradleFailureBlocks.add(block)
        }

        // Build structured, actionable diagnostic response for the AI Agent
        val sb = StringBuilder()
        sb.append("🚨 BUILD TAB ERRORS")
        if (buildStatus.isNotBlank()) sb.append(" [Status: $buildStatus]")
        sb.append(":\n\n")

        var hasActionableErrors = false

        if (compilerErrors.isNotEmpty()) {
            hasActionableErrors = true
            sb.append("=== COMPILER & SYNTAX DIAGNOSTICS (${compilerErrors.size} errors found) ===\n")
            compilerErrors.take(10).forEach { err ->
                sb.append(err).append("\n---\n")
            }
            sb.append("\n")
        }

        if (webAndFlutterErrors.isNotEmpty()) {
            hasActionableErrors = true
            sb.append("=== RUNTIME & MODULE RESOLUTION ERRORS ===\n")
            webAndFlutterErrors.take(10).forEach { err ->
                sb.append("❌ $err\n")
            }
            sb.append("\n")
        }

        if (gradleFailureBlocks.isNotEmpty()) {
            hasActionableErrors = true
            sb.append("=== GRADLE & BUILD TASK FAILURE DETAILS ===\n")
            gradleFailureBlocks.forEach { block ->
                sb.append(block).append("\n\n")
            }
        }

        // If no structured error regex matched, pull the actual build step tail (ignoring runner setup)
        if (!hasActionableErrors) {
            val nonNoiseLines = cleanedLines.filter { !isRunnerSetupOrNoiseLine(it) && it.isNotBlank() }
            val tail = nonNoiseLines.takeLast(maxLines.coerceAtMost(40))
            if (tail.isNotEmpty()) {
                sb.append("No explicit compiler syntax error matched. Showing latest build step output tail:\n\n")
                tail.forEach { sb.append("   $it\n") }
            } else {
                sb.append("No build errors detected in logs. The build output does not contain fatal compiler failures.")
            }
        }

        return sb.toString().trimEnd()
    }
}
