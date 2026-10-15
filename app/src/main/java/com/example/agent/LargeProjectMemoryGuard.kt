package com.example.agent

import com.example.ui.AiActionLog
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.lang.reflect.Type

/**
 * LargeProjectMemoryGuard
 *
 * Dedicated guard system preventing app slowdowns, ANRs, CursorWindow SQLite overflows,
 * and OutOfMemoryError crashes when AI agents process large projects with massive tool inputs/outputs.
 */
object LargeProjectMemoryGuard {

    private const val MAX_LOG_DETAILS_CHARS = 8000
    private const val MAX_HISTORY_TOOL_OUTPUT_CHARS = 40000
    private const val MIN_INDEXING_INTERVAL_MS = 25000L

    private var lastIndexTimestamp: Long = 0L
    private var lastIndexedProjectPath: String? = null

    private val cachedMoshi: Moshi by lazy {
        Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    }

    private val logsListAdapter: JsonAdapter<List<AiActionLog>> by lazy {
        val listType: Type = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
        cachedMoshi.adapter(listType)
    }

    /**
     * Sanitizes and caps log details string to protect Jetpack Compose UI rendering
     * and SQLite CursorWindow (2MB row limit).
     */
    fun sanitizeLogDetails(details: String?): String? {
        if (details == null) return null
        if (details.length <= MAX_LOG_DETAILS_CHARS) return details

        val head = details.take(4500)
        val tail = details.takeLast(2500)
        val omitted = details.length - (head.length + tail.length)
        return "$head\n\n... [Omitted $omitted characters in preview for UI performance] ...\n\n$tail"
    }

    /**
     * Bounds tool output text added to the conversation history to prevent
     * memory ballooning and excessive token consumption across multiple turns.
     */
    fun sanitizeToolOutput(tool: String, output: String): String {
        if (output.length <= MAX_HISTORY_TOOL_OUTPUT_CHARS) return output

        val isRead = tool.contains("read", ignoreCase = true) || tool.contains("source", ignoreCase = true)
        val headChars = if (isRead) 25000 else 18000
        val tailChars = if (isRead) 10000 else 8000

        val head = output.take(headChars)
        val tail = output.takeLast(tailChars)
        val omitted = output.length - (head.length + tail.length)

        return "$head\n\n[... Large output truncated ($omitted characters omitted to prevent memory overflow). Use specific range/selectors for additional segments ...] \n\n$tail"
    }

    /**
     * Determines whether project directory symbol re-indexing is actually needed,
     * avoiding expensive recursive disk I/O on every single AI step.
     */
    @Synchronized
    fun isIndexingNeeded(projectDir: File): Boolean {
        if (!projectDir.exists()) return false
        val now = System.currentTimeMillis()
        val currentPath = projectDir.absolutePath

        if (currentPath != lastIndexedProjectPath || (now - lastIndexTimestamp) > MIN_INDEXING_INTERVAL_MS) {
            lastIndexedProjectPath = currentPath
            lastIndexTimestamp = now
            return true
        }
        return false
    }

    /**
     * Safely serializes action logs into JSON while ensuring total payload size
     * stays well within Android SQLite CursorWindow safety bounds (< 600 KB).
     */
    fun safeSerializeLogs(logs: List<AiActionLog>, moshi: Moshi? = null): String? {
        return try {
            val safeLogs = logs.map { log ->
                val safeDetails = sanitizeLogDetails(log.details)
                if (safeDetails != log.details) log.copy(details = safeDetails) else log
            }
            logsListAdapter.toJson(safeLogs)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * High-speed, cached deserialization of action logs from JSON without
     * creating Moshi instances on the UI thread.
     */
    fun parseActionLogsJson(json: String?): List<AiActionLog> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            logsListAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
