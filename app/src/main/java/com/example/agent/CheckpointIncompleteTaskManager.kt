package com.example.agent

import android.util.Log
import com.example.data.VibeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CheckpointIncompleteTaskManager
 *
 * Saves incomplete and in-progress tasks along with their user prompts into `Checkpoint.txt`.
 * Automatically truncates content when exceeding the 20,000 token limit to maintain safety.
 */
object CheckpointIncompleteTaskManager {

    const val CHECKPOINT_FILE_NAME = "Checkpoint.txt"
    const val CHECKPOINT_FILE_NAME_LOWER = "checkpoint.txt"
    const val MAX_TOKENS = 20_000

    private const val TAG = "CheckpointTaskManager"

    /**
     * Estimates tokens using standard heuristic: ~4 chars per ASCII token, ~2 chars per non-ASCII.
     */
    fun estimateTokens(text: String): Int {
        var nonAsciiCount = 0
        for (ch in text) {
            if (ch.code > 127) nonAsciiCount++
        }
        val asciiChars = text.length - nonAsciiCount
        return (asciiChars / 4) + (nonAsciiCount / 2) + 1
    }

    /**
     * Truncates content if exceeding maxTokens (20k tokens), pruning older entries from top.
     */
    fun truncateTo20kTokens(content: String, maxTokens: Int = MAX_TOKENS): String {
        if (estimateTokens(content) <= maxTokens) return content

        val lines = content.lines()
        var low = 0
        var high = lines.size - 1
        var bestIndex = 0
        while (low <= high) {
            val mid = (low + high) / 2
            val candidate = lines.subList(mid, lines.size).joinToString("\n")
            if (estimateTokens(candidate) <= maxTokens) {
                bestIndex = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        val truncated = lines.subList(bestIndex, lines.size).joinToString("\n")
        return "[... Older checkpoint records truncated to enforce 20k token limit ...]\n$truncated"
    }

    /**
     * Records an incomplete task and its original prompt into Checkpoint.txt.
     */
    fun recordIncompleteTask(
        projectName: String,
        projectDir: File,
        repository: VibeRepository,
        prompt: String,
        incompleteObjectives: List<String> = emptyList(),
        latestContextSummary: String? = null
    ) {
        try {
            val existing = loadCheckpoint(projectDir) ?: ""
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())

            val entry = buildString {
                append("\n================================================================================\n")
                append("[INCOMPLETE TASK CHECKPOINT]\n")
                append("TIMESTAMP: $timestamp\n")
                append("PROJECT: $projectName\n")
                append("USER PROMPT:\n${prompt.trim()}\n\n")
                append("TASK STATUS: UNFINISHED / IN-PROGRESS\n")

                if (incompleteObjectives.isNotEmpty()) {
                    append("PENDING & INCOMPLETE WORK:\n")
                    incompleteObjectives.forEach { obj ->
                        append("• ${obj.trim()}\n")
                    }
                    append("\n")
                }

                if (!latestContextSummary.isNullOrBlank()) {
                    append("LATEST ACTIONS & INTERRUPT CONTEXT:\n")
                    append(latestContextSummary.trim())
                    append("\n")
                }
                append("================================================================================\n")
            }

            val combined = if (existing.isNotBlank()) {
                existing + "\n" + entry
            } else {
                entry
            }

            val sanitized = truncateTo20kTokens(combined)
            persistCheckpoint(projectName, projectDir, repository, sanitized)
            Log.d(TAG, "Saved incomplete task to $CHECKPOINT_FILE_NAME (Tokens: ~${estimateTokens(sanitized)})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record incomplete task in $CHECKPOINT_FILE_NAME", e)
        }
    }

    /**
     * Loads current Checkpoint.txt content from disk if present.
     */
    fun loadCheckpoint(projectDir: File): String? {
        val fileUpper = File(projectDir, CHECKPOINT_FILE_NAME)
        val fileLower = File(projectDir, CHECKPOINT_FILE_NAME_LOWER)
        val target = if (fileUpper.exists()) fileUpper else if (fileLower.exists()) fileLower else null
        return try {
            target?.readText()?.trim()?.ifBlank { null }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading checkpoint file", e)
            null
        }
    }

    /**
     * Persists content to both Checkpoint.txt and checkpoint.txt and synchronizes to Database.
     */
    private fun persistCheckpoint(
        projectName: String,
        projectDir: File,
        repository: VibeRepository,
        content: String
    ) {
        try {
            projectDir.mkdirs()
            val fileUpper = File(projectDir, CHECKPOINT_FILE_NAME)
            val fileLower = File(projectDir, CHECKPOINT_FILE_NAME_LOWER)
            fileUpper.writeText(content)
            fileLower.writeText(content)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.saveFile(projectName, CHECKPOINT_FILE_NAME, content)
                    repository.saveFile(projectName, CHECKPOINT_FILE_NAME_LOWER, content)
                } catch (e: Exception) {
                    Log.w(TAG, "Error syncing checkpoint to DB", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing checkpoint to disk", e)
        }
    }
}
