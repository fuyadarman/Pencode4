package com.example.agent

import java.util.Locale

/**
 * AgentFastConvergencePolicy
 * 
 * Replicates the fast, decisive task execution of OpenCode and Claude Code for heavy models
 * (Claude 3.5/3.7 Sonnet, GPT-4o, Gemini 1.5/2.5 Pro).
 * 
 * Prevents heavy models from overthinking, entering infinite reading/verification loops,
 * or repeatedly executing redundant tool calls after the user's task is already done.
 */
object AgentFastConvergencePolicy {

    val FAST_CONVERGENCE_DIRECTIVE = """
        === FAST CONVERGENCE & MINIMAL OPERATIONS (Claude Code / OpenCode Standard) ===
        - Be decisive, precise, and fast: Complete the user's task in the fewest necessary turns (typically 1-3 operations).
        - Plan in your 'thought', locate the relevant code with grep/read_file, make the exact surgical edits with 'edit_file' or 'create_file', and call 'complete' immediately with a concise summary.
        - NEVER enter repetitive verification loops or wander into unrelated files once your changes are in place.
        - If the requested change is already applied or the question is answered, STOP and invoke 'complete'.
    """.trimIndent()

    sealed class ConvergenceDecision {
        object ContinueExecution : ConvergenceDecision()
        data class ConcludeImmediately(val summary: String, val reason: String) : ConvergenceDecision()
    }

    /**
     * Determines whether the agent should decisively conclude the task to prevent infinite loops.
     */
    fun evaluateConvergence(
        turn: Int,
        actionsCount: Int,
        hasModifiedFiles: Boolean,
        hasPlannedActionTools: Boolean,
        modelThought: String?,
        modelMessage: String?,
        userPrompt: String
    ): ConvergenceDecision {
        val thought = (modelThought ?: "").trim().lowercase(Locale.ROOT)
        val message = (modelMessage ?: "").trim()
        val lowerMessage = message.lowercase(Locale.ROOT)

        // 0. Safety Guard: NEVER conclude if the model has planned action tools or explicitly indicates pending work!
        if (hasPlannedActionTools || AgentUnfinishedWorkGuard.hasPendingWork(modelThought)) {
            return ConvergenceDecision.ContinueExecution
        }

        // 1. If files were modified and model produced a concluding message without requesting further actions
        if (hasModifiedFiles) {
            val hasExplicitFinish = AgentUnfinishedWorkGuard.isExplicitlyFinished(modelThought, modelMessage)

            val indicatesDone = thought.contains("done") ||
                    thought.contains("finished") ||
                    thought.contains("completed") ||
                    thought.contains("successfully updated") ||
                    thought.contains("successfully created") ||
                    thought.contains("changes are in place") ||
                    thought.contains("changes are complete") ||
                    thought.contains("now ready") ||
                    thought.contains("fixed the issue") ||
                    thought.contains("implemented the requested") ||
                    lowerMessage.contains("done") ||
                    lowerMessage.contains("finished") ||
                    lowerMessage.contains("completed") ||
                    lowerMessage.contains("successfully")

            if (hasExplicitFinish || indicatesDone || (message.isNotBlank() && turn >= 2)) {
                val finalSummary = when {
                    message.isNotBlank() -> message
                    thought.isNotBlank() -> AgentThoughtLoopGuard.sanitizeThoughtForUserResponse(modelThought!!)
                    else -> "The requested changes have been successfully implemented and applied to your project."
                }
                return ConvergenceDecision.ConcludeImmediately(
                    summary = finalSummary,
                    reason = "Natural convergence: All requested changes applied and model completed."
                )
            }
        }

        return ConvergenceDecision.ContinueExecution
    }
}
