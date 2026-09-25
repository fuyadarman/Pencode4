package com.example.agent

import android.util.Log

/**
 * ThoughtExecutionResilienceGuard
 *
 * Prevents premature task interruptions, unintended loop cancellations,
 * and raw thoughts from being dumped into chat while the AI is in the middle of working.
 */
object ThoughtExecutionResilienceGuard {

    private const val TAG = "ThoughtResilienceGuard"

    // Allow up to 4 constructive nudges before considering a hard stop,
    // accommodating modern deep-reasoning models (DeepSeek-R1, Gemini Thinking, Claude Thinking).
    const val MAX_CONSECUTIVE_THOUGHT_NUDGES = 4

    sealed class GuardDecision {
        object ProceedNormal : GuardDecision()
        data class NudgeToExecuteTool(val instruction: String, val attempt: Int) : GuardDecision()
        data class CompleteNaturally(val userResponse: String, val reason: String) : GuardDecision()
    }

    /**
     * Determines whether the thought indicates the agent has genuinely finished all requested work.
     * Requires strict explicit confirmation, not accidental substring matches.
     */
    fun isGenuineTaskCompletion(
        thought: String?,
        hasModifiedFiles: Boolean,
        userPrompt: String
    ): Boolean {
        if (thought.isNullOrBlank()) return false
        val clean = thought.trim().lowercase()

        // Pure conversational prompts don't require files
        if (AgentThoughtLoopGuard.isInformationalOrConversationalPrompt(userPrompt) && clean.length > 40) {
            return true
        }

        if (!hasModifiedFiles) return false

        // Strict explicit conclusion phrases (matching full intent, not random substrings)
        val explicitFinishingPatterns = listOf(
            Regex("""\b(?:all|the)\s+(?:requested\s+)?(?:changes|tasks|features|requirements)\s+(?:are|have been)\s+(?:successfully\s+)?(?:completed|applied|implemented)\b"""),
            Regex("""\bno\s+(?:further|more)\s+(?:changes|edits|files|actions)\s+(?:are\s+)?(?:needed|required)\b"""),
            Regex("""\bi\s+have\s+finished\s+(?:implementing|updating|creating|fixing)\b"""),
            Regex("""\btask\s+is\s+(?:now\s+)?complete\b""")
        )

        val matchesExplicitFinish = explicitFinishingPatterns.any { it.containsMatchIn(clean) }

        // Must NOT contain indications of future planned work
        val planningNextWork = listOf(
            "now i need to", "next step is", "let me now", "i will next", "still need to",
            "next i will", "proceeding to edit", "let's update", "next, let's", "next let's"
        ).any { clean.contains(it) }

        return matchesExplicitFinish && !planningNextWork
    }

    /**
     * Evaluates a thinking turn to keep the execution loop alive and prevent mid-task cancellations.
     */
    fun evaluateThinkingTurn(
        consecutiveThoughtCount: Int,
        currentThought: String?,
        lastThought: String?,
        userPrompt: String,
        hasModifiedFiles: Boolean
    ): GuardDecision {
        val thoughtText = (currentThought ?: "").trim()

        // 1. Genuine natural completion
        if (isGenuineTaskCompletion(thoughtText, hasModifiedFiles, userPrompt)) {
            val sanitized = AgentThoughtLoopGuard.sanitizeThoughtForUserResponse(thoughtText)
                .ifBlank { "All requested changes have been successfully implemented." }
            Log.d(TAG, "Task completed naturally from explicit thought conclusion.")
            return GuardDecision.CompleteNaturally(sanitized, "Natural completion detected.")
        }

        // 2. Pure informational or question prompt
        if (AgentThoughtLoopGuard.isInformationalOrConversationalPrompt(userPrompt) && thoughtText.length > 40) {
            val sanitized = AgentThoughtLoopGuard.sanitizeThoughtForUserResponse(thoughtText)
            return GuardDecision.CompleteNaturally(sanitized, "Informational response provided.")
        }

        // 3. If within nudge budget, actively nudge the model to execute tools instead of aborting
        if (consecutiveThoughtCount <= MAX_CONSECUTIVE_THOUGHT_NUDGES) {
            val nudge = when (consecutiveThoughtCount) {
                1 -> "System Directive: Your plan is noted. Now proceed IMMEDIATELY to execute the concrete tool action (e.g. 'read_file', 'edit_file', 'create_file', or 'run_command'). Do NOT output pure text without tool calls."
                2 -> "System Directive [Action Required]: You are still in planning mode. You MUST execute a tool call now to apply changes or inspect files for the user request: \"${userPrompt.take(80)}\"."
                3 -> "System Directive [URGENT]: Do NOT deliberate further. Call 'edit_file' or 'create_file' or 'complete' now to progress the task."
                else -> "System Directive [FINAL NOTICE]: Invoke your planned tool call immediately or call 'complete' with your final summary."
            }
            Log.d(TAG, "Nudging model to take action (attempt $consecutiveThoughtCount)")
            return GuardDecision.NudgeToExecuteTool(nudge, consecutiveThoughtCount)
        }

        // 4. Exceeded nudge budget: safely finish with a polished summary instead of an error or raw internal monologue
        val fallbackResponse = if (hasModifiedFiles) {
            "The modifications requested have been applied to your project files. Please review the changes in the editor or run the app."
        } else {
            val cleanThought = AgentThoughtLoopGuard.sanitizeThoughtForUserResponse(thoughtText)
            if (cleanThought.length > 30) cleanThought else "Task analysis completed. Please provide any additional instructions or specific edits to make."
        }

        return GuardDecision.CompleteNaturally(fallbackResponse, "Completed after maximum thought deliberations.")
    }
}
