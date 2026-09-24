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
        val errorBlocks = mutableListOf<String>()

        allLines.forEachIndexed { index, line ->
            val lower = line.lowercase(Locale.ROOT)
            val isErrorLine = lower.contains("error:") || lower.contains("failed:") ||
                    lower.contains("failure") || lower.contains("fatal:") ||
                    lower.contains("exception") || lower.contains("compilation error") ||
                    lower.contains("process completed with exit code") ||
                    lower.contains("build failed") || lower.contains("npm err!") ||
                    lower.contains("gradle error") || lower.contains("task failed")

            if (isErrorLine) {
                if (query.isNullOrBlank() || lower.contains(query)) {
                    val start = (index - 2).coerceAtLeast(0)
                    val end = (index + 2).coerceAtMost(allLines.size - 1)
                    val context = (start..end).map { i ->
                        val marker = if (i == index) ">> " else "   "
                        "$marker${allLines[i]}"
                    }.joinToString("\n")
                    errorBlocks.add(context)
                }
            }
        }

        val sb = StringBuilder()
        sb.append("🚨 BUILD TAB ERRORS")
        if (buildStatus.isNotBlank()) sb.append(" [Status: $buildStatus]")
        sb.append(":\n")

        if (errorBlocks.isEmpty()) {
            val tail = allLines.takeLast(maxLines.coerceAtMost(40))
            sb.append("No explicit error keywords detected in build logs. Showing tail of the log:\n")
            tail.forEach { sb.append("   $it\n") }
        } else {
            val displayBlocks = if (errorBlocks.size > maxLines / 3) errorBlocks.takeLast(maxLines / 3) else errorBlocks
            sb.append("Found ${errorBlocks.size} error occurrences. Showing relevant error snippets:\n\n")
            displayBlocks.forEach { block ->
                sb.append(block).append("\n---\n")
            }
        }

        return sb.toString().trimEnd()
    }
}
