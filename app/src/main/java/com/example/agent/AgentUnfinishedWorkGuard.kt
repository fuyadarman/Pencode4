package com.example.agent

import java.util.Locale

/**
 * AgentUnfinishedWorkGuard
 * 
 * Prevents premature task termination. Ensures that when the user requests a multi-step
 * or multi-file task, the agent is never cut off midway before finishing all required work.
 */
object AgentUnfinishedWorkGuard {

    private val FORWARD_LOOKING_PATTERNS = listOf(
        "now i need to",
        "next step",
        "next, i will",
        "next i will",
        "now let me",
        "now let's",
        "still need to",
        "proceeding to",
        "proceed to",
        "moving on to",
        "let's also",
        "also need to",
        "now updating",
        "now creating",
        "will now edit",
        "will now create",
        "next, let's",
        "next let's",
        "remaining work",
        "second step",
        "third step",
        "in addition, i need",
        "before completing",
        "need to add",
        "need to modify",
        "need to update",
        "haven't updated yet",
        "yet to be"
    )

    /**
     * Checks if the model's current thought or reasoning explicitly indicates that
     * more work remains to be done.
     */
    fun hasPendingWork(thought: String?): Boolean {
        if (thought.isNullOrBlank()) return false
        val lower = thought.lowercase(Locale.ROOT)
        return FORWARD_LOOKING_PATTERNS.any { lower.contains(it) }
    }

    /**
     * Checks if the model output indicates it has fully concluded all work
     * for the current user prompt.
     */
    fun isExplicitlyFinished(thought: String?, message: String?): Boolean {
        val t = (thought ?: "").lowercase(Locale.ROOT)
        val m = (message ?: "").lowercase(Locale.ROOT)

        val completionIndicators = listOf(
            "all changes have been implemented",
            "all changes are complete",
            "task is complete",
            "task is fully complete",
            "implementation is complete",
            "everything is in place",
            "successfully finished",
            "i have completed all the requested",
            "all requested files have been created",
            "the feature is now fully implemented",
            "all requested updates are applied"
        )

        val indicatesDone = completionIndicators.any { t.contains(it) || m.contains(it) }
        val hasPending = hasPendingWork(thought)

        return indicatesDone && !hasPending
    }
}
