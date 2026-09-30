package com.example.agent

import com.example.api.ToolArguments
import com.example.ui.VibeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Autonomous Error Diagnostics Engine for PenCode AI.
 * Specifically extracts ONLY errors, exceptions, and failure points from:
 * 1. Preview Tab Web Console (Runtime errors, uncaught exceptions, 404/500 network failures).
 * 2. Build Tab GitHub Actions (Compilation failures, syntax errors, build script errors).
 */
object AgentErrorLogsEngine {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * Reads strictly ERRORS from Preview Tab web console logs.
     */
    fun readPreviewErrors(
        logs: List<VibeViewModel.WebConsoleLog>,
        args: ToolArguments?
    ): String {
        if (logs.isEmpty()) {
            return "No web console logs recorded yet in Preview tab. Preview is clean or not yet loaded."
        }

        val maxLines = (args?.maxLines ?: args?.count ?: 50).coerceIn(5, 150)
        val query = args?.query?.trim()?.lowercase(Locale.ROOT)
            ?: args?.search?.trim()?.lowercase(Locale.ROOT)

        val errorLogs = logs.filter { log ->
            val level = log.level.lowercase(Locale.ROOT)
            val msg = log.message.lowercase(Locale.ROOT)
            val isError = level.contains("error") || level.contains("err") ||
                    level.contains("exception") || level.contains("fatal") ||
                    msg.contains("uncaught") || msg.contains("syntaxerror") ||
                    msg.contains("typeerror") || msg.contains("referenceerror") ||
                    msg.contains("failed to load resource") || msg.contains("net::err")

            if (!isError) false
            else if (!query.isNullOrBlank()) msg.contains(query) || log.sourceId.lowercase(Locale.ROOT).contains(query)
            else true
        }

        if (errorLogs.isEmpty()) {
            return "✅ No errors found in Preview Tab! Total console entries: ${logs.size} (all info/warnings, no critical errors)."
        }

        // Separate active fresh logs from stale historical logs before the latest code edit
        val activeErrors = errorLogs.filter { !com.example.ui.preview.WebConsoleSyncManager.isStaleLog(it.timestamp) }
        val staleErrorsCount = errorLogs.size - activeErrors.size

        if (activeErrors.isEmpty()) {
            return "✅ No active errors in Preview Tab! Current preview is running cleanly (all $staleErrorsCount historical errors occurred prior to the latest code update and were resolved)."
        }

        // Group identical duplicate errors to avoid flooding (e.g. 64 repeated classList errors in animation loop)
        data class ErrorGroup(val log: VibeViewModel.WebConsoleLog, var count: Int)
        val groupedList = mutableListOf<ErrorGroup>()
        activeErrors.forEach { log ->
            val existing = groupedList.find { 
                it.log.message == log.message && it.log.sourceId == log.sourceId && it.log.lineNumber == log.lineNumber 
            }
            if (existing != null) {
                existing.count++
            } else {
                groupedList.add(ErrorGroup(log, 1))
            }
        }

        val displayGroups = if (groupedList.size > maxLines) groupedList.takeLast(maxLines) else groupedList
        val sb = StringBuilder()
        sb.append("🚨 PREVIEW TAB ERRORS (Found ${activeErrors.size} active errors across ${groupedList.size} issue points):\n")
        displayGroups.forEach { group ->
            val log = group.log
            val time = try { timeFormat.format(Date(log.timestamp)) } catch (e: Exception) { "" }
            val timePrefix = if (time.isNotBlank()) "[$time] " else ""
            val src = if (log.sourceId.isNotBlank()) " at ${log.sourceId}:${log.lineNumber}" else ""
            val countSuffix = if (group.count > 1) " (occurred ${group.count} times)" else ""
            sb.append("$timePrefix❌ ${log.message}$src$countSuffix\n")
        }
        sb.append("\nTip: Analyze the error message and source line above to locate and fix the bug in your codebase.")
        return sb.toString().trimEnd()
    }

    /**
     * Reads strictly ERRORS and failure points from Build Tab GitHub Actions logs.
     * Accurately filters out setup action configuration dumps (e.g. dependency-graph, continue-on-failure)
     * and extracts real compiler diagnostics, Gradle failures, and Vite/NPM/Flutter exceptions.
     */
    fun readBuildErrors(
        buildLogs: String,
        buildStatus: String,
        args: ToolArguments?
    ): String {
        if (buildLogs.isBlank()) {
            val status = if (buildStatus.isNotBlank()) " (Current status: $buildStatus)" else ""
            return "No build logs available in Build tab yet$status. Trigger a workflow run or push code to inspect build output."
        }

        val maxLines = (args?.maxLines ?: args?.count ?: 80).coerceIn(10, 200)
        val query = args?.query?.trim()?.lowercase(Locale.ROOT)
            ?: args?.search?.trim()?.lowercase(Locale.ROOT)

        val allLines = buildLogs.lines()

        // Helper to identify GitHub action configuration/input dump lines to IGNORE
        fun isConfigDumpOrSetupLine(line: String): Boolean {
            val lower = line.lowercase(Locale.ROOT)
            return lower.contains("add-job-summary") ||
                    lower.contains("dependency-graph") ||
                    lower.contains("continue-on-failure") ||
                    lower.contains("if-no-files-found") ||
                    lower.contains("workflow-run-conclusion") ||
                    lower.contains("setup-gradle") && lower.contains(":") ||
                    lower.contains("setup-java") && lower.contains(":") ||
                    (lower.trim().startsWith("job-status:") || lower.trim().startsWith("on-failure:")) ||
                    Regex("""^\s*[\w.-]+:\s*(never|always|true|false|disabled|enabled|null|\d+)\s*$""").matches(lower.trim())
        }

        val highPriorityErrorIndices = mutableListOf<Int>()
        val generalErrorIndices = mutableListOf<Int>()

        allLines.forEachIndexed { index, line ->
            val clean = line.replace(Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z\s*"""), "").trim()
            if (isConfigDumpOrSetupLine(clean)) return@forEachIndexed

            val lower = clean.lowercase(Locale.ROOT)

            // High-priority: explicit compiler diagnostics or Gradle failure blocks
            val isHighPriority = clean.startsWith("e: ") ||
                    clean.startsWith("w: ") && lower.contains("error") ||
                    clean.contains("* what went wrong:") ||
                    clean.contains("execution failed for task") ||
                    clean.contains("failure: build failed") ||
                    clean.contains("a failure occurred while executing") ||
                    clean.contains("unresolved reference") ||
                    clean.contains("cannot find symbol") ||
                    clean.contains("npm err!") ||
                    clean.contains("[vite]") && lower.contains("error") ||
                    clean.contains("rollup failed") ||
                    clean.contains("syntaxerror:") ||
                    clean.contains("typeerror:") ||
                    clean.contains("flutter failed") ||
                    clean.contains("process completed with exit code") && !clean.contains("exit code 0")

            val isGeneralError = !isHighPriority && (
                    lower.contains("error:") ||
                    lower.contains("compilation error") ||
                    lower.contains("build failed") ||
                    lower.contains("gradle error") ||
                    lower.contains("task failed") ||
                    lower.contains("fatal:") ||
                    (lower.contains("exception:") && !lower.contains("expected exception"))
            )

            if (isHighPriority) {
                if (query.isNullOrBlank() || lower.contains(query)) {
                    highPriorityErrorIndices.add(index)
                }
            } else if (isGeneralError) {
                if (query.isNullOrBlank() || lower.contains(query)) {
                    generalErrorIndices.add(index)
                }
            }
        }

        val targetIndices = if (highPriorityErrorIndices.isNotEmpty()) {
            highPriorityErrorIndices
        } else {
            generalErrorIndices
        }

        val sb = StringBuilder()
        sb.append("🚨 BUILD TAB ERRORS")
        if (buildStatus.isNotBlank()) sb.append(" [Status: $buildStatus]")
        sb.append(":\n")

        if (targetIndices.isEmpty()) {
            val tail = allLines.takeLast(maxLines.coerceAtMost(40))
            sb.append("No explicit compiler or Gradle failure detected in build logs. Showing log tail:\n")
            tail.forEach { sb.append("   $it\n") }
        } else {
            // Cluster adjacent indices to form coherent error blocks
            val visitedIndices = mutableSetOf<Int>()
            val errorBlocks = mutableListOf<String>()

            for (idx in targetIndices) {
                if (visitedIndices.contains(idx)) continue

                val start = (idx - 2).coerceAtLeast(0)
                val end = (idx + 4).coerceAtMost(allLines.size - 1)
                for (v in start..end) visitedIndices.add(v)

                val blockLines = (start..end).map { i ->
                    val marker = if (i == idx) ">> " else "   "
                    "$marker${allLines[i]}"
                }.joinToString("\n")
                errorBlocks.add(blockLines)
            }

            val displayBlocks = if (errorBlocks.size > 8) errorBlocks.takeLast(8) else errorBlocks
            sb.append("Found ${errorBlocks.size} relevant failure points. Showing exact error snippets:\n\n")
            displayBlocks.forEach { block ->
                sb.append(block).append("\n---\n")
            }
        }

        return sb.toString().trimEnd()
    }
}
