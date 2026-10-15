package com.example.ui.preview

import com.example.ui.VibeViewModel.WebConsoleLog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * WebConsoleSyncManager
 *
 * Resolves race conditions between AI code modifications, preview reload delays,
 * and console log capture:
 * 1. Tracks exact timestamps of code edits vs preview reloads.
 * 2. Invalidates stale pre-fix errors so the AI never gets confused by errors it already fixed.
 * 3. Gracefully waits for preview reload to emit fresh logs if the AI queries logs immediately after an edit.
 */
object WebConsoleSyncManager {

    private val _lastCodeEditTimestamp = AtomicLong(0L)
    val lastCodeEditTimestamp: Long get() = _lastCodeEditTimestamp.get()

    private val _lastReloadRequestedTimestamp = AtomicLong(0L)
    val lastReloadRequestedTimestamp: Long get() = _lastReloadRequestedTimestamp.get()

    @Volatile
    private var _lastEditedFilePath: String = ""
    val lastEditedFilePath: String get() = _lastEditedFilePath

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun notifyCodeEdited(filePath: String) {
        val now = System.currentTimeMillis()
        _lastCodeEditTimestamp.set(now)
        _lastEditedFilePath = filePath
    }

    fun notifyReloadTriggered() {
        _lastReloadRequestedTimestamp.set(System.currentTimeMillis())
    }

    fun hasRecentCodeEdit(withinMs: Long = 4000L): Boolean {
        val editTime = _lastCodeEditTimestamp.get()
        if (editTime == 0L) return false
        return (System.currentTimeMillis() - editTime) < withinMs
    }

    fun isStaleLog(logTimestamp: Long): Boolean {
        val editTime = _lastCodeEditTimestamp.get()
        if (editTime == 0L) return false
        // If the log was emitted BEFORE the code was edited, it is stale
        return logTimestamp < editTime
    }

    suspend fun waitForFreshLogsIfNeeded(
        logsProvider: () -> List<WebConsoleLog>,
        maxWaitMs: Long = 1800L
    ): List<WebConsoleLog> {
        val editTime = _lastCodeEditTimestamp.get()
        if (editTime == 0L) return logsProvider()

        val elapsedSinceEdit = System.currentTimeMillis() - editTime
        if (elapsedSinceEdit < 3500L) {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < maxWaitMs) {
                val currentLogs = logsProvider()
                val hasPostEditLogs = currentLogs.any { it.timestamp >= editTime }
                if (hasPostEditLogs) {
                    return currentLogs
                }
                delay(200)
            }
        }
        return logsProvider()
    }

    fun generateDiagnosticConsoleOutput(
        logs: List<WebConsoleLog>,
        filterLevel: String?,
        query: String?,
        maxLines: Int
    ): String {
        val editTime = _lastCodeEditTimestamp.get()
        val hasEdit = editTime > 0L

        // Split into fresh (post-edit) and pre-edit logs
        val freshLogs = if (hasEdit) logs.filter { it.timestamp >= editTime } else logs
        val staleLogs = if (hasEdit) logs.filter { it.timestamp < editTime } else emptyList()

        val sb = StringBuilder()
        val editTimeStr = if (hasEdit) {
            try { timeFormat.format(Date(editTime)) } catch (_: Exception) { "" }
        } else ""

        if (hasEdit) {
            sb.append("=== Web Preview Console Sync Status ===\n")
            sb.append("• Last Code Edit: $editTimeStr")
            if (_lastEditedFilePath.isNotBlank()) {
                sb.append(" on '$_lastEditedFilePath'")
            }
            sb.append("\n")

            val freshErrors = freshLogs.filter { it.level.lowercase(Locale.ROOT).contains("error") }
            val staleErrors = staleLogs.filter { it.level.lowercase(Locale.ROOT).contains("error") }

            if (freshErrors.isEmpty()) {
                if (staleErrors.isNotEmpty()) {
                    sb.append("• Previous pre-fix error(s) (${staleErrors.size}) have been RESOLVED by your latest changes.\n")
                    sb.append("• Current Status: Clean! No new runtime errors have been emitted after your fix.\n\n")
                } else {
                    sb.append("• Current Status: No runtime errors emitted after your latest edit.\n\n")
                }
            } else {
                sb.append("• ATTENTION: ${freshErrors.size} runtime error(s) were emitted AFTER your latest edit:\n\n")
            }
        }

        // Apply filters
        var targetSequence = freshLogs.asSequence()
        if (!filterLevel.isNullOrBlank() && filterLevel != "all") {
            targetSequence = targetSequence.filter { log ->
                val lvl = log.level.lowercase(Locale.ROOT)
                when (filterLevel) {
                    "error", "errors", "err" -> lvl.contains("error") || lvl.contains("err")
                    "warn", "warning", "warnings" -> lvl.contains("warn")
                    "info" -> lvl.contains("info")
                    "log" -> lvl == "log" || lvl == "info"
                    else -> lvl.contains(filterLevel)
                }
            }
        }

        if (!query.isNullOrBlank()) {
            val q = query.lowercase(Locale.ROOT)
            targetSequence = targetSequence.filter { log ->
                log.message.lowercase(Locale.ROOT).contains(q) ||
                        log.sourceId.lowercase(Locale.ROOT).contains(q)
            }
        }

        val filteredLogs = targetSequence.toList()
        if (filteredLogs.isEmpty()) {
            if (hasEdit && freshLogs.isEmpty()) {
                sb.append("Preview is loading/executing fresh code. No new console output emitted yet after edit at $editTimeStr.")
                return sb.toString().trimEnd()
            }
            sb.append("No console logs matched the criteria.")
            return sb.toString().trimEnd()
        }

        val displayLogs = if (filteredLogs.size > maxLines) filteredLogs.takeLast(maxLines) else filteredLogs
        sb.append("=== Preview Console Logs (${displayLogs.size} logs) ===\n")
        displayLogs.forEach { log ->
            val time = try { timeFormat.format(Date(log.timestamp)) } catch (_: Exception) { "" }
            val src = if (log.sourceId.isNotBlank()) " [${log.sourceId}:${log.lineNumber}]" else ""
            val timeStr = if (time.isNotBlank()) "[$time] " else ""
            sb.append("$timeStr[${log.level.uppercase(Locale.ROOT)}]$src: ${log.message}\n")
        }

        return sb.toString().trimEnd()
    }
}
