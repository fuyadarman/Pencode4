package com.example.agent

import com.example.api.ToolArguments
import com.example.ui.VibeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Autonomous Workspace Logs Reader.
 * Allows PenCode AI to inspect and diagnose:
 * 1. Preview Tab web console logs (console.log, console.error, console.warn, runtime exceptions).
 * 2. Build Tab GitHub Actions build logs & workflow compilation output.
 */
object AgentWorkspaceLogsReader {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * Reads and filters web console logs from the Preview tab.
     */
    fun readConsoleLogs(
        logs: List<VibeViewModel.WebConsoleLog>,
        args: ToolArguments?
    ): String {
        if (logs.isEmpty()) {
            return "No web console logs recorded yet in the Preview tab. (The app preview hasn't emitted console logs or hasn't loaded yet)."
        }

        val filterLevel = args?.filter?.lowercase(Locale.ROOT)?.trim()
            ?: args?.type?.lowercase(Locale.ROOT)?.trim()
        val query = args?.query?.trim()?.lowercase(Locale.ROOT)
            ?: args?.search?.trim()?.lowercase(Locale.ROOT)
        val maxLines = (args?.maxLines ?: args?.count ?: 50).coerceIn(1, 250)

        return com.example.ui.preview.WebConsoleSyncManager.generateDiagnosticConsoleOutput(
            logs = logs,
            filterLevel = filterLevel,
            query = query,
            maxLines = maxLines
        )
    }

    /**
     * Reads, searches, and extracts relevant diagnostic lines from Build Tab GitHub Action logs.
     */
    fun readBuildLogs(
        buildLogs: String,
        buildStatus: String,
        args: ToolArguments?
    ): String {
        if (buildLogs.isBlank()) {
            val statusNote = if (buildStatus.isNotBlank()) " Current build status: $buildStatus" else ""
            return "No GitHub Actions build logs recorded yet in the Build tab.$statusNote Push code or trigger a workflow run from the Build tab to view live build output."
        }

        val query = args?.query?.trim()?.lowercase(Locale.ROOT)
            ?: args?.search?.trim()?.lowercase(Locale.ROOT)
        val filter = args?.filter?.lowercase(Locale.ROOT)?.trim()
            ?: args?.type?.lowercase(Locale.ROOT)?.trim()
        val maxLines = (args?.maxLines ?: args?.count ?: 120).coerceIn(10, 400)

        val allLines = buildLogs.lines()

        val sb = StringBuilder()
        sb.append("=== Build Tab GitHub Action Logs (Total: ${allLines.size} lines) ===\n")
        if (buildStatus.isNotBlank()) {
            sb.append("Status: $buildStatus\n\n")
        }

        if (filter == "error" || filter == "errors") {
            val errorLines = mutableListOf<String>()
            allLines.forEachIndexed { index, line ->
                val lower = line.lowercase(Locale.ROOT)
                if (lower.contains("error:") || lower.contains("failed") || lower.contains("failure") ||
                    lower.contains("exception") || lower.contains("fatal:") || lower.contains("compilation error")
                ) {
                    val start = (index - 1).coerceAtLeast(0)
                    val end = (index + 1).coerceAtMost(allLines.size - 1)
                    for (i in start..end) {
                        val prefix = if (i == index) ">> " else "   "
                        errorLines.add("$prefix${allLines[i]}")
                    }
                }
            }

            if (errorLines.isEmpty()) {
                sb.append("No explicit error markers detected in build logs. Showing tail of build log:\n")
                val tail = allLines.takeLast(maxLines)
                tail.forEach { sb.append(it).append("\n") }
            } else {
                sb.append("Found ${errorLines.size} error-related context lines:\n")
                errorLines.take(maxLines).forEach { sb.append(it).append("\n") }
            }
        } else if (!query.isNullOrBlank()) {
            val matching = allLines.filter { it.lowercase(Locale.ROOT).contains(query) }
            if (matching.isEmpty()) {
                sb.append("No build log lines matched query '$query'. Showing last $maxLines lines:\n")
                allLines.takeLast(maxLines).forEach { sb.append(it).append("\n") }
            } else {
                sb.append("Found ${matching.size} lines matching '$query':\n")
                matching.take(maxLines).forEach { sb.append(it).append("\n") }
            }
        } else {
            // Default: show the latest tail of the build log
            val tail = allLines.takeLast(maxLines)
            tail.forEach { sb.append(it).append("\n") }
        }

        return sb.toString().trimEnd()
    }
}
