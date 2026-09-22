package com.example.agent

import com.example.api.ToolArguments
import com.example.api.ToolCallItem

/**
 * OpenCodeLoopGuardEngine
 *
 * Implements OpenCode and Claude Code loop prevention standards:
 * 1. NEVER blocks reading files - models are completely free to inspect and read any file.
 * 2. Completion Thought Sniffer - when files have been modified and the model's thought
 *    confirms the task is done, auto-completes gracefully to prevent infinite loops.
 * 3. Repetitive Action Breaker - detects when the exact same tool + arguments are repeated
 *    3+ times in a row without making progress.
 * 4. Constructive Nudges - if a model thinks for multiple turns without taking action,
 *    provides constructive guidance rather than false premature-completion errors.
 */
object OpenCodeLoopGuardEngine {

    private val COMPLETION_PHRASES = listOf(
        "task is complete",
        "task completed",
        "task has been completed",
        "all changes have been applied",
        "all changes applied",
        "changes are complete",
        "fix is in place",
        "already resolved",
        "no further changes needed",
        "no further changes required",
        "everything looks good",
        "everything is in place",
        "successfully implemented",
        "implementation is complete",
        "work is complete"
    )

    data class ActionSignature(
        val tool: String,
        val target: String,
        val turn: Int
    )

    private val actionHistory = mutableListOf<ActionSignature>()

    fun reset() {
        actionHistory.clear()
    }

    /**
     * Checks if the model's thought indicates task completion AFTER files have been modified.
     * This is how OpenCode/Claude Code gracefully prevents models from looping endlessly.
     */
    fun shouldAutoCompleteFromThought(
        thought: String?,
        hasModifiedFiles: Boolean
    ): Boolean {
        if (!hasModifiedFiles || thought.isNullOrBlank()) return false
        val lower = thought.lowercase()
        return COMPLETION_PHRASES.any { lower.contains(it) }
    }

    sealed class RepetitionResult {
        object Proceed : RepetitionResult()
        data class WarnAndNudge(val message: String, val silentInUi: Boolean = false) : RepetitionResult()
        data class AbortRepetition(val summary: String) : RepetitionResult()
    }

    /**
     * Records an action and detects repetitive loops (e.g. repeating the same failed edit or call).
     */
    fun checkAndRecordRepetition(
        tool: String,
        args: ToolArguments?,
        turn: Int,
        hasModifiedFiles: Boolean
    ): RepetitionResult {
        val target = (
            args?.query ?:
            args?.pattern ?:
            args?.search ?:
            args?.find ?:
            args?.command ?:
            args?.path ?:
            args?.targetFile ?:
            args?.targetFilePascal ?:
            args?.filePath ?:
            args?.file_path ?:
            args?.file ?:
            args?.name ?:
            args?.destinationPath ?:
            ""
        ).trim()

        val normalizedTool = tool.lowercase().trim()
        val signature = ActionSignature(normalizedTool, target, turn)
        actionHistory.add(signature)

        val isReadFile = normalizedTool in setOf("read_file", "view_file", "read_file_range")
        if (isReadFile && target.isNotEmpty()) {
            val previousReadsOfTarget = actionHistory.dropLast(1).count {
                it.tool in setOf("read_file", "view_file", "read_file_range") && it.target.equals(target, ignoreCase = true)
            }
            if (previousReadsOfTarget >= 1) {
                // Background warning when the AI reads the same file twice (invisible in app UI)
                return RepetitionResult.WarnAndNudge(
                    message = "Background Warning: You have already read '$target' earlier in this conversation. You already possess its complete contents in your history context. Do NOT re-read '$target'. Proceed directly to applying edits or calling the next necessary action.",
                    silentInUi = true
                )
            }
        }

        val isReadOrSearch = normalizedTool in setOf(
            "read_file", "view_file", "read_file_range",
            "global_search", "grep", "search_files", "find_files", "list_dir"
        )

        // Read or Search operations: allow exploring freely. Only warn if the EXACT same query/path is repeated 4+ times consecutively.
        if (isReadOrSearch) {
            if (target.isNotEmpty() && actionHistory.size >= 4) {
                val lastFour = actionHistory.takeLast(4)
                if (lastFour.all { it.tool == signature.tool && it.target == signature.target }) {
                    return RepetitionResult.WarnAndNudge(
                        "You have already searched or read '$target' multiple times. Please proceed with making your edits using 'edit_file' or 'create_file'."
                    )
                }
            }
            return RepetitionResult.Proceed
        }

        // Modification/Command actions (e.g. edit_file, create_file, execute_command)
        if (actionHistory.size >= 3) {
            val lastThree = actionHistory.takeLast(3)
            val allSameTool = lastThree.all { it.tool == signature.tool }
            val allSameTarget = lastThree.all { it.target == signature.target }
            if (allSameTool && allSameTarget && target.isNotEmpty()) {
                if (actionHistory.size >= 5 && hasModifiedFiles) {
                    return RepetitionResult.AbortRepetition(
                        "Task finished: modifications are already in place and repetitive action on '$target' was halted."
                    )
                }
                return RepetitionResult.WarnAndNudge(
                    "You have attempted '$tool' on '$target' repeatedly without progression. Do NOT repeat the exact same call. If finished, call 'complete'. Otherwise, adjust your target search block or use a different approach."
                )
            }
        }

        return RepetitionResult.Proceed
    }

    /**
     * Builds a constructive, friendly nudge when a model has spent multiple turns thinking
     * without executing file modifications or actions.
     */
    fun buildIdleThinkingNudge(hasModifiedFiles: Boolean): String {
        return if (hasModifiedFiles) {
            "You have completed file modifications. If all requirements are fulfilled, call the 'complete' tool with a summary of your changes. Otherwise, proceed with any remaining edits."
        } else {
            "You have analyzed the plan. Please proceed directly with implementing your changes using 'create_file', 'edit_file', or other required tools."
        }
    }
}
