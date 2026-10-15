package com.example.agent

import com.example.data.ChatMessageEntity
import com.example.ui.AiActionLog
import com.example.ui.EditRecord
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File

/**
 * AgentSessionMemoryEngine
 *
 * Implements a token-bounded, Claude Code / OpenCode style Session & Workspace Memory ledger.
 * Enables the AI to maintain complete situational awareness of what has been asked,
 * what files were edited, and what actions were performed in the active project session,
 * while strictly capping token usage to prevent bloating the context window (max ~300-350 tokens).
 */
object AgentSessionMemoryEngine {

    // Strict character budget to ensure token limit (~300 tokens ≈ 1200 chars)
    private const val MAX_TOTAL_LEDGER_CHARS = 1300
    private const val MAX_TURNS_TO_SUMMARIZE = 5

    /**
     * Builds a compact, token-bounded session memory block for injection into the model's context.
     */
    fun buildSessionMemoryLedger(
        historyEntities: List<ChatMessageEntity>,
        editRecords: List<EditRecord> = emptyList(),
        moshi: Moshi
    ): String {
        val sb = StringBuilder()
        sb.append("=== SESSION & PROJECT WORKSPACE LEDGER (Compact Memory) ===\n")

        // 1. Files modified/created in this project session
        if (editRecords.isNotEmpty()) {
            val fileGroup = editRecords.groupBy { File(it.path).name.ifEmpty { it.path } }
            val fileSummary = fileGroup.entries.take(8).joinToString(", ") { (fileName, edits) ->
                val primaryTool = edits.lastOrNull()?.tool ?: "edit"
                "$fileName ($primaryTool x${edits.size})"
            }
            sb.append("• Files Touched: $fileSummary\n")
        }

        // 2. Chronological summary of completed user requests & agent outcomes
        val completedTurns = extractCompletedTurns(historyEntities, moshi)
        if (completedTurns.isNotEmpty()) {
            sb.append("• Session Activity (Recent First):\n")
            // Take the most recent turns within token budget
            val recentTurns = completedTurns.takeLast(MAX_TURNS_TO_SUMMARIZE)
            for ((index, turn) in recentTurns.withIndex()) {
                val turnNum = index + 1
                val reqTruncated = turn.userRequest.take(70).replace("\n", " ")
                val actionTruncated = if (turn.actionsSummary.isNotBlank()) " -> ${turn.actionsSummary}" else ""
                val line = "  $turnNum. \"$reqTruncated\"$actionTruncated\n"
                
                if (sb.length + line.length < MAX_TOTAL_LEDGER_CHARS - 100) {
                    sb.append(line)
                } else {
                    break
                }
            }
        }

        sb.append("=== END SESSION LEDGER ===")

        // Hard cap guarantee
        return if (sb.length > MAX_TOTAL_LEDGER_CHARS) {
            sb.substring(0, MAX_TOTAL_LEDGER_CHARS - 28) + "\n=== END SESSION LEDGER ==="
        } else {
            sb.toString()
        }
    }

    private data class CompletedTurnSummary(
        val userRequest: String,
        val actionsSummary: String,
        val timestamp: Long
    )

    private fun extractCompletedTurns(
        historyEntities: List<ChatMessageEntity>,
        moshi: Moshi
    ): List<CompletedTurnSummary> {
        val turns = mutableListOf<CompletedTurnSummary>()
        val historicalMessages = if (historyEntities.size > 1) historyEntities.dropLast(1) else emptyList()

        var currentUserMsg: ChatMessageEntity? = null
        for (msg in historicalMessages) {
            if (msg.role == "user") {
                currentUserMsg = msg
            } else if (msg.role == "assistant" && currentUserMsg != null) {
                val cleanedUserPrompt = currentUserMsg.content
                    .replace("""\[IMAGE_BASE64: data:.*?;base64,.*?\]""".toRegex(), "[Attached Image]")
                    .trim()

                val actionsList = extractActionsFromLogs(msg.aiActionLogsJson, moshi)
                val actionsStr = if (actionsList.isNotEmpty()) {
                    actionsList.take(3).joinToString(", ")
                } else {
                    msg.content.take(50).replace("\n", " ").trim()
                }

                turns.add(
                    CompletedTurnSummary(
                        userRequest = cleanedUserPrompt,
                        actionsSummary = actionsStr,
                        timestamp = msg.timestamp
                    )
                )
                currentUserMsg = null
            }
        }

        return turns
    }

    private fun extractActionsFromLogs(aiActionLogsJson: String?, moshi: Moshi): List<String> {
        if (aiActionLogsJson.isNullOrBlank()) return emptyList()
        val actions = mutableListOf<String>()
        try {
            val listType = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
            val logs = moshi.adapter<List<AiActionLog>>(listType).fromJson(aiActionLogsJson)
            if (logs != null) {
                for (log in logs) {
                    val titleLower = log.title.lowercase()
                    val filePath = log.details?.lineSequence()?.firstOrNull()?.let { line ->
                        val match = """(?:from|to|file|written to|read from|for)?\s*([a-zA-Z0-9_\-\./]+)""".toRegex(RegexOption.IGNORE_CASE).find(line)
                        match?.groupValues?.get(1) ?: line
                    } ?: ""
                    val fileName = File(filePath).name.ifEmpty { filePath }

                    if (fileName.isNotEmpty()) {
                        when {
                            titleLower.contains("create") || titleLower.contains("write_file") -> actions.add("created $fileName")
                            titleLower.contains("edit") || titleLower.contains("patch") || titleLower.contains("modify") -> actions.add("modified $fileName")
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore parsing error for safety
        }
        return actions.distinct()
    }
}
