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
        return AgentBuildLogExtractor.extractBuildDiagnostics(
            buildLogs = buildLogs,
            buildStatus = buildStatus,
            args = args
        )
    }
}
