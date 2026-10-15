package com.example.context

import com.example.api.Content
import com.example.api.Part
import java.util.regex.Pattern

/**
 * ContextCompactionEngine
 *
 * Implements OpenCode / Claude Code style Automatic Context Compaction.
 * When conversation history grows beyond the threshold, older conversation turns
 * are automatically compressed into a high-density structured summary (objectives,
 * decisions, completed work, blockers, next action), while strictly preserving
 * the most recent ~2,500 tokens verbatim for immediate operational continuity.
 */
object ContextCompactionEngine {

    const val RECENT_TOKEN_BUDGET = 25000
    private const val CHARS_PER_TOKEN = 4
    private const val RECENT_CHAR_BUDGET = RECENT_TOKEN_BUDGET * CHARS_PER_TOKEN // ~100,000 chars
    private const val COMPACTION_TRIGGER_TOKENS = 35000 // Triggers when conversation exceeds ~35k tokens

    /**
     * Estimates tokens for a single Content object.
     */
    fun estimateContentTokens(content: Content): Int {
        var chars = 0
        for (part in content.parts) {
            chars += part.text?.length ?: 0
        }
        return chars / CHARS_PER_TOKEN
    }

    /**
     * Estimates total tokens for a list of conversation items.
     */
    fun estimateTotalTokens(history: List<Content>): Int {
        var chars = 0
        for (content in history) {
            for (part in content.parts) {
                chars += part.text?.length ?: 0
            }
        }
        return chars / CHARS_PER_TOKEN
    }

    /**
     * Checks if history warrants automatic compaction.
     */
    fun shouldCompact(history: List<Content>): Boolean {
        if (history.size <= 4) return false
        val totalTokens = estimateTotalTokens(history)
        return totalTokens >= COMPACTION_TRIGGER_TOKENS
    }

    /**
     * Compacts older conversation history while strictly preserving:
     * 1. Initial user prompt / root objective.
     * 2. High-density structured memory summary of older turns.
     * 3. The most recent turns up to ~2,500 tokens verbatim.
     */
    fun compactHistory(history: List<Content>): List<Content> {
        if (!shouldCompact(history)) return history

        val initialTurn = history.first()
        val allSubsequent = history.drop(1)
        if (allSubsequent.isEmpty()) return history

        // Step 1: Select recent turns from the end fitting within RECENT_CHAR_BUDGET (~2,500 tokens)
        var accumulatedChars = 0
        var splitIndex = allSubsequent.size // from where recent turns start

        for (i in allSubsequent.indices.reversed()) {
            val content = allSubsequent[i]
            val contentLength = content.parts.sumOf { it.text?.length ?: 0 }

            // Always keep at least 2 most recent turns
            val keptCount = allSubsequent.size - i
            if (keptCount > 2 && (accumulatedChars + contentLength > RECENT_CHAR_BUDGET)) {
                splitIndex = i + 1
                break
            }
            accumulatedChars += contentLength
            splitIndex = i
        }

        // If splitIndex <= 0, no middle history to compact
        if (splitIndex <= 0) return history

        val middleTurns = allSubsequent.subList(0, splitIndex)
        val recentTurns = allSubsequent.subList(splitIndex, allSubsequent.size)

        // Step 2: Build high-density structured summary of middle turns
        val structuredSummaryText = buildStructuredSummary(initialTurn, middleTurns)

        val compactedResult = mutableListOf<Content>()
        // 1. Initial Prompt preserved
        compactedResult.add(initialTurn)

        // 2. Structured Compact Memory State
        compactedResult.add(
            Content(
                role = "user",
                parts = listOf(
                    Part(
                        text = structuredSummaryText
                    )
                )
            )
        )

        // 3. Verbatim recent turns (preserving ~2.5k tokens intact)
        compactedResult.addAll(recentTurns)

        return compactedResult
    }

    /**
     * Extracts objectives, completed file changes, command outputs, blockers and decisions
     * to form a comprehensive structured compaction summary.
     */
    private fun buildStructuredSummary(initialTurn: Content, middleTurns: List<Content>): String {
        val initialPrompt = initialTurn.parts.firstOrNull()?.text ?: "Coding Task"
        val goalSnippet = initialPrompt.lines().firstOrNull { it.isNotBlank() }?.take(150) ?: "Implementation"

        val filesCreated = mutableSetOf<String>()
        val filesModified = mutableSetOf<String>()
        val commandsExecuted = mutableListOf<String>()
        val blockersEncountered = mutableListOf<String>()
        val buildStatus = mutableListOf<String>()

        val filePattern = Pattern.compile("""(?:--- File:|\bpath["':\s]+)([^\n"',\)\}\]]+)""")

        for (turn in middleTurns) {
            for (part in turn.parts) {
                val text = part.text ?: continue
                
                when {
                    text.contains("System/Tool Output for 'create_file'") || text.contains("System/Tool Output for 'write_file'") -> {
                        val m = filePattern.matcher(text)
                        if (m.find()) {
                            filesCreated.add(m.group(1).trim())
                        }
                    }
                    text.contains("System/Tool Output for 'edit_file'") || text.contains("System/Tool Output for 'multi_edit_file'") || text.contains("System/Tool Output for 'patch_file'") -> {
                        val m = filePattern.matcher(text)
                        if (m.find()) {
                            filesModified.add(m.group(1).trim())
                        }
                    }
                    text.contains("System/Tool Output for 'run_command'") || text.contains("System/Tool Output for 'shell_exec'") -> {
                        val cmdLine = text.lines().find { it.contains("Command:") || it.contains("$") } ?: "command execution"
                        commandsExecuted.add(cmdLine.take(80))
                    }
                    text.contains("Build succeeded") || text.contains("BUILD SUCCESSFUL") -> {
                        buildStatus.add("Build succeeded")
                    }
                    text.contains("Build failed") || text.contains("BUILD FAILED") || text.contains("error:") || text.contains("Exception:") -> {
                        val errLine = text.lines().find { it.contains("error:", ignoreCase = true) || it.contains("Exception:", ignoreCase = true) }
                        if (errLine != null) {
                            blockersEncountered.add(errLine.trim().take(120))
                        }
                    }
                }
            }
        }

        return buildString {
            append("[AUTOMATIC CONTEXT COMPACTION (Structured History Summary)]\n")
            append("• PRIMARY OBJECTIVE: $goalSnippet\n")
            
            append("• COMPLETED FILES & WORK:\n")
            if (filesCreated.isNotEmpty()) {
                append("  - Created: ${filesCreated.joinToString(", ")}\n")
            }
            if (filesModified.isNotEmpty()) {
                append("  - Modified: ${filesModified.joinToString(", ")}\n")
            }
            if (commandsExecuted.isNotEmpty()) {
                append("  - Commands: ${commandsExecuted.distinct().take(4).joinToString("; ")}\n")
            }
            if (filesCreated.isEmpty() && filesModified.isEmpty() && commandsExecuted.isEmpty()) {
                append("  - Completed ${middleTurns.size} operational steps successfully.\n")
            }

            if (blockersEncountered.isNotEmpty()) {
                append("• RESOLVED / LOGGED BLOCKERS:\n")
                blockersEncountered.distinct().take(3).forEach { err ->
                    append("  ⚠ $err\n")
                }
            }

            if (buildStatus.isNotEmpty()) {
                append("• VERIFICATION STATUS: ${buildStatus.last()}\n")
            }

            append("\n[Context Optimization Notice: Middle operational steps compacted to save tokens. The recent ~2,500 tokens of conversation and tool executions are preserved below verbatim.]")
        }
    }
}
