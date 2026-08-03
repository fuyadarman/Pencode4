package com.example.context

import com.example.api.Content
import com.example.api.Part

/**
 * Handles Rolling Context Window & Memory Compression for AI Agent conversations.
 * Compresses middle conversation history into concise memory milestones to keep token usage low.
 */
object ContextSummarizer {

    private const val MAX_RECENT_TURNS_TO_KEEP = 8
    private const val ESTIMATED_CHAR_PER_TOKEN = 4

    /**
     * Estimates total token count for a list of conversation contents.
     */
    fun estimateTokenCount(history: List<Content>): Int {
        var totalChars = 0
        for (content in history) {
            for (part in content.parts) {
                totalChars += part.text?.length ?: 0
            }
        }
        return totalChars / ESTIMATED_CHAR_PER_TOKEN
    }

    /**
     * Compresses older turns into a structured summary state if the history is long or token heavy.
     */
    fun compressHistory(history: List<Content>, maxTokenThreshold: Int = 12000): List<Content> {
        if (history.size <= MAX_RECENT_TURNS_TO_KEEP + 2) {
            return history
        }

        val estimatedTokens = estimateTokenCount(history)
        if (estimatedTokens < maxTokenThreshold && history.size < 16) {
            return history
        }

        val compressed = mutableListOf<Content>()

        // 1. Preserve initial user goal / prompt (Index 0)
        val initialContent = history.first()
        compressed.add(initialContent)

        // 2. Extract key milestones from middle turns (Index 1 until size - MAX_RECENT_TURNS_TO_KEEP)
        val middleTurns = history.subList(1, history.size - MAX_RECENT_TURNS_TO_KEEP)
        val summaryMilestones = extractMilestonesFromMiddle(middleTurns)

        val memorySummaryText = StringBuilder().apply {
            append("[Context Memory State - Summarized Previous Operations to Save Tokens]\n")
            append("Summary of Completed Operations:\n")
            summaryMilestones.forEach { milestone ->
                append("• $milestone\n")
            }
            append("\n[Note: Previous full tool outputs were archived. Proceeding with active task context.]")
        }.toString()

        compressed.add(
            Content(
                role = "user",
                parts = listOf(Part(text = memorySummaryText))
            )
        )

        // 3. Preserve the most recent turns intact for working memory
        compressed.addAll(history.takeLast(MAX_RECENT_TURNS_TO_KEEP))

        return compressed
    }

    private fun extractMilestonesFromMiddle(middleHistory: List<Content>): List<String> {
        val milestones = mutableListOf<String>()
        val fileActionsSeen = mutableSetOf<String>()

        for (content in middleHistory) {
            for (part in content.parts) {
                val text = part.text ?: continue

                when {
                    text.contains("System/Tool Output for 'edit_file'") -> {
                        val fileMatch = Regex("""--- File:\s*([^\n\(\-]+)""").find(text)
                        val fileName = fileMatch?.groupValues?.get(1)?.trim() ?: "code file"
                        if (fileActionsSeen.add("edit:$fileName")) {
                            milestones.add("Modified file: $fileName")
                        }
                    }
                    text.contains("System/Tool Output for 'create_file'") || text.contains("System/Tool Output for 'write_file'") -> {
                        val fileMatch = Regex("""--- File:\s*([^\n\(\-]+)""").find(text)
                        val fileName = fileMatch?.groupValues?.get(1)?.trim() ?: "new file"
                        if (fileActionsSeen.add("create:$fileName")) {
                            milestones.add("Created file: $fileName")
                        }
                    }
                    text.contains("System/Tool Output for 'read_file'") -> {
                        val fileMatch = Regex("""--- File:\s*([^\n\(\-]+)""").find(text)
                        val fileName = fileMatch?.groupValues?.get(1)?.trim() ?: "file"
                        if (fileActionsSeen.add("read:$fileName")) {
                            milestones.add("Inspected file: $fileName")
                        }
                    }
                    text.contains("System/Tool Output for 'run_command'") || text.contains("System/Tool Output for 'shell_exec'") -> {
                        if (fileActionsSeen.add("command_exec")) {
                            milestones.add("Executed build/search shell commands")
                        }
                    }
                    text.contains("Build succeeded") -> {
                        if (fileActionsSeen.add("build_pass")) {
                            milestones.add("Verified app build compilation successfully")
                        }
                    }
                    text.contains("Build failed") -> {
                        if (fileActionsSeen.add("build_fail")) {
                            milestones.add("Encountered build compilation issue and fixed errors")
                        }
                    }
                }
            }
        }

        if (milestones.isEmpty()) {
            milestones.add("Executed ${middleHistory.size} operational steps successfully.")
        }

        return milestones
    }
}
