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
        data class WarnAndNudge(val message: String) : RepetitionResult()
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
        val target = (args?.path ?: args?.targetFile ?: args?.destinationPath ?: args?.search ?: "").trim()
        val signature = ActionSignature(tool.lowercase().trim(), target, turn)
        actionHistory.add(signature)

        val isRead = signature.tool == "read_file" || signature.tool == "view_file" || signature.tool == "read_file_range"

        // If repeating the exact same read 4+ times consecutively
        if (isRead && actionHistory.size >= 4) {
            val lastFour = actionHistory.takeLast(4)
            if (lastFour.all { it.tool == signature.tool && it.target == signature.target }) {
                return RepetitionResult.WarnAndNudge(
                    "You have already read '$target' multiple times. Content has already been provided. Please proceed with making your edits using 'edit_file' or 'create_file'."
                )
            }
        }

        // If repeating the exact same modification/command action 3+ times
        if (!isRead && actionHistory.size >= 3) {
            val lastThree = actionHistory.takeLast(3)
            val allSameTool = lastThree.all { it.tool == signature.tool }
            val allSameTarget = lastThree.all { it.target == signature.target }
            if (allSameTool && allSameTarget) {
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
