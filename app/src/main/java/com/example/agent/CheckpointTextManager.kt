package com.example.agent

import android.util.Log
import com.example.data.VibeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * CheckpointTextManager
 *
 * Manages persistent text checkpoints in `checkpoint.txt`.
 * - Records user prompts, thoughts, tool inputs, and tool outputs.
 * - When AI halts, stops mid-way, or does not invoke 'complete', all actions remain saved.
 * - On the next prompt, new actions append to prior records and triggers automatically in background.
 * - Enforces strict 20k token limit, pruning oldest entries from top if needed.
 * - Completely clears checkpoint.txt when 'complete' is explicitly called.
 */
object CheckpointTextManager {

    const val CHECKPOINT_FILE_NAME = "checkpoint.txt"
    const val MAX_TOKENS = 20_000

    fun estimateTokens(text: String): Int {
        var nonAsciiCount = 0
        for (ch in text) {
            if (ch.code > 127) nonAsciiCount++
        }
        val asciiChars = text.length - nonAsciiCount
        return (asciiChars / 4) + (nonAsciiCount / 2) + 1
    }

    fun trimToMaxTokens(content: String, maxTokens: Int = MAX_TOKENS): String {
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
        val trimmed = lines.subList(bestIndex, lines.size).joinToString("\n")
        return "[... older checkpoint records trimmed to enforce 20k token limit ...]\n$trimmed"
    }

    fun startPromptSession(
        projectName: String,
        projectDir: File,
        repository: VibeRepository,
        prompt: String
    ) {
        val existing = loadCheckpointText(projectDir)
        val sessionHeader = if (!existing.isNullOrBlank()) {
            """
            
            ================================================================================
            [NEW PROMPT SESSION]
            PROMPT: $prompt
            ================================================================================
            """.trimIndent()
        } else {
            """
            ================================================================================
            [PROMPT SESSION]
            PROMPT: $prompt
            ================================================================================
            """.trimIndent()
        }

        val combined = if (!existing.isNullOrBlank()) {
            existing + "\n\n" + sessionHeader
        } else {
            sessionHeader
        }

        val sanitized = trimToMaxTokens(combined)
        persist(projectName, projectDir, repository, sanitized)
    }

    fun recordAction(
        projectName: String,
        projectDir: File,
        repository: VibeRepository,
        thought: String?,
        tool: String,
        inputArgs: String?,
        outputResult: String?
    ) {
        val existing = loadCheckpointText(projectDir) ?: ""
        val actionEntry = buildString {
            append("\n--------------------------------------------------------------------------------\n")
            append("ACTION: $tool\n")
            if (!thought.isNullOrBlank()) {
                append("[THOUGHT]\n")
                append(thought.trim())
                append("\n\n")
            }
            append("[INPUT]\n")
            append(inputArgs?.trim()?.ifBlank { "{}" } ?: "{}")
            append("\n\n")
            append("[OUTPUT]\n")
            val sanitizedOutput = outputResult?.trim()?.ifBlank { "Success" } ?: "Success"
            val cappedOutput = if (sanitizedOutput.length > 25000) {
                sanitizedOutput.take(25000) + "\n...[output truncated for checkpoint]"
            } else {
                sanitizedOutput
            }
            append(cappedOutput)
            append("\n--------------------------------------------------------------------------------\n")
        }

        val combined = existing + actionEntry
        val sanitized = trimToMaxTokens(combined)
        persist(projectName, projectDir, repository, sanitized)
    }

    fun recordThought(
        projectName: String,
        projectDir: File,
        repository: VibeRepository,
        thought: String
    ) {
        if (thought.isBlank()) return
        val existing = loadCheckpointText(projectDir) ?: ""
        val thoughtEntry = "\n[THOUGHT]\n${thought.trim()}\n"
        val combined = existing + thoughtEntry
        val sanitized = trimToMaxTokens(combined)
        persist(projectName, projectDir, repository, sanitized)
    }

    fun clearCheckpoint(
        projectName: String,
        projectDir: File,
        repository: VibeRepository
    ) {
        try {
            val file = File(projectDir, CHECKPOINT_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.deleteFile(projectName, CHECKPOINT_FILE_NAME)
                } catch (e: Exception) {
                    Log.w("CheckpointTextManager", "Error clearing $CHECKPOINT_FILE_NAME from repository", e)
                }
            }
        } catch (e: Exception) {
            Log.e("CheckpointTextManager", "Error clearing $CHECKPOINT_FILE_NAME", e)
        }
    }

    fun loadCheckpointText(projectDir: File): String? {
        val file = File(projectDir, CHECKPOINT_FILE_NAME)
        if (!file.exists() || !file.isFile) return null
        return try {
            val content = file.readText().trim()
            if (content.isNotBlank()) content else null
        } catch (e: Exception) {
            Log.e("CheckpointTextManager", "Error reading $CHECKPOINT_FILE_NAME", e)
            null
        }
    }

    fun getBackgroundTriggerContext(projectDir: File): String? {
        val content = loadCheckpointText(projectDir) ?: return null
        return """
            [BACKGROUND CHECKPOINT CONTEXT FROM checkpoint.txt]
            Previous task execution stopped before calling 'complete'.
            The recorded prompt, thoughts, and action inputs/outputs from checkpoint.txt (max 20k tokens):
            
            $content
            
            [END BACKGROUND CHECKPOINT CONTEXT]
        """.trimIndent()
    }

    private fun persist(
        projectName: String,
        projectDir: File,
        repository: VibeRepository,
        content: String
    ) {
        try {
            val file = File(projectDir, CHECKPOINT_FILE_NAME)
            file.parentFile?.mkdirs()
            file.writeText(content)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.saveFile(projectName, CHECKPOINT_FILE_NAME, content)
                } catch (e: Exception) {
                    Log.w("CheckpointTextManager", "Error saving $CHECKPOINT_FILE_NAME to repository", e)
                }
            }
        } catch (e: Exception) {
            Log.e("CheckpointTextManager", "Error persisting $CHECKPOINT_FILE_NAME", e)
        }
    }

    fun formatToolArguments(args: Any?, moshi: com.squareup.moshi.Moshi? = null): String {
        if (args == null) return "{}"
        return try {
            if (moshi != null) {
                moshi.adapter(args.javaClass).toJson(args)
            } else {
                args.toString()
            }
        } catch (_: Exception) {
            args.toString()
        }
    }

    fun createCheckpointAwareHistory(
        initialHistory: List<com.example.api.Content>,
        projectName: String,
        projectDir: File,
        repository: VibeRepository,
        getThought: () -> String?,
        getCurrentTool: () -> String,
        getCurrentArgs: () -> String
    ): MutableList<com.example.api.Content> {
        return object : ArrayList<com.example.api.Content>(initialHistory) {
            override fun add(element: com.example.api.Content): Boolean {
                val res = super.add(element)
                if (element.role == "user") {
                    val text = element.parts.firstOrNull()?.text
                    if (text != null && text.startsWith("System/Tool Output")) {
                        val headerEnd = text.indexOf('\n')
                        val headerLine = if (headerEnd != -1) text.substring(0, headerEnd) else text
                        val toolNameMatch = Regex("System/Tool Output for ['\"]?([^':\"]+)['\"]?").find(headerLine)
                        val resolvedTool = toolNameMatch?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null }
                            ?: getCurrentTool().ifBlank { "action" }
                        val outputText = if (headerEnd != -1) text.substring(headerEnd + 1).trim() else text.substringAfter(":", "").trim()

                        recordAction(
                            projectName = projectName,
                            projectDir = projectDir,
                            repository = repository,
                            thought = getThought(),
                            tool = resolvedTool,
                            inputArgs = getCurrentArgs(),
                            outputResult = outputText
                        )
                    }
                }
                return res
            }
        }
    }
}
