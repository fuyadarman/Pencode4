package com.example.agent

import java.util.Locale

/**
 * AgentGoalAlignmentEngine
 * Prevents autonomous AI agents from getting confused, losing track of the user's main prompt,
 * hallucinating unrequested tasks, or wandering into endless unrelated loops.
 * Inspired by OpenCode and Claude Code goal-anchoring architectures.
 */
object AgentGoalAlignmentEngine {

    /**
     * Builds an ultra-lean Goal Anchor (~15-25 tokens) appended to tool outputs.
     * Keeps the model laser-focused on the user's active request across all conversation turns.
     */
    fun buildToolOutputGoalAnchor(userPrompt: String): String {
        val sanitized = ActivePromptFocusGuard.sanitizePromptForDirective(userPrompt)
        if (sanitized.isBlank()) return ""
        val targetSnippet = if (sanitized.length > 120) sanitized.take(120) + "..." else sanitized
        return "\n\n[ACTIVE GOAL ANCHOR: Target is exclusively \"$targetSnippet\". Do not perform unrequested changes or drift to unrelated code.]"
    }

    sealed class AlignmentCheckResult {
        object Aligned : AlignmentCheckResult()
        data class NudgeBackToGoal(val directive: String) : AlignmentCheckResult()
        data class ConcludeCompletedTask(val summary: String) : AlignmentCheckResult()
    }

    /**
     * Inspects the model's formulating thought and planned tool actions to detect goal drift
     * or confusion where the model starts working on random, unrequested tasks.
     */
    fun inspectModelAlignment(
        thought: String,
        plannedTools: List<String>,
        userPrompt: String,
        turn: Int,
        actionsCount: Int,
        hasModifiedFiles: Boolean
    ): AlignmentCheckResult {
        val cleanPrompt = ActivePromptFocusGuard.sanitizePromptForDirective(userPrompt).lowercase(Locale.ROOT)
        val lowerThought = thought.lowercase(Locale.ROOT)

        // 1. If files have already been modified and the model's thought indicates satisfaction or completion
        if (hasModifiedFiles) {
            val indicatesDone = lowerThought.contains("changes are complete") ||
                    lowerThought.contains("implemented successfully") ||
                    lowerThought.contains("all requested changes") ||
                    lowerThought.contains("task is complete") ||
                    lowerThought.contains("everything is set up") ||
                    lowerThought.contains("successfully created") ||
                    lowerThought.contains("successfully updated")

            if (indicatesDone && !plannedTools.contains("complete")) {
                return AlignmentCheckResult.ConcludeCompletedTask(
                    "Task finished: All modifications for '$cleanPrompt' are in place and successfully verified."
                )
            }

            // If changes are applied and model continues reading 3+ unrelated files without editing
            val readOnlyPlanned = plannedTools.isNotEmpty() && plannedTools.all { 
                it == "read_file" || it == "view_file" || it == "scan_dir" || it == "list_dir" || it == "global_search"
            }
            if (turn >= 4 && readOnlyPlanned && actionsCount >= 4) {
                return AlignmentCheckResult.NudgeBackToGoal(
                    "SYSTEM GOAL DIRECTIVE: You have already applied code modifications for the active user prompt. Do NOT continue aimlessly exploring or reading additional files. If your implementation is complete, call 'complete' immediately with a concise summary."
                )
            }
        }

        // 2. Detect explicit confusion or drift in thought reasoning
        val indicatesConfusion = lowerThought.contains("i should also add") ||
                lowerThought.contains("let's also implement") ||
                lowerThought.contains("now i will create a new feature") ||
                lowerThought.contains("additionally, let me") ||
                lowerThought.contains("since i noticed a todo") ||
                lowerThought.contains("let's refactor the entire")

        val promptWantsFullRefactor = cleanPrompt.contains("refactor entire") || cleanPrompt.contains("rewrite all")

        if (indicatesConfusion && !promptWantsFullRefactor && hasModifiedFiles) {
            return AlignmentCheckResult.NudgeBackToGoal(
                "SYSTEM SCOPE GUARD: Stay strictly within the scope of the user's active request (\"$cleanPrompt\"). Do NOT perform unrequested refactors, unrelated feature additions, or side-tasks. Finalize the active request by calling 'complete'."
            )
        }

        return AlignmentCheckResult.Aligned
    }
}
