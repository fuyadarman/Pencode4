package com.example.agent

import com.example.api.Content
import com.example.api.Part
import com.example.api.ToolCallItem

sealed class AgentLoopDecision {
    object Continue : AgentLoopDecision()
    data class InjectWarning(val warning: String, val logTitle: String) : AgentLoopDecision()
    data class AutoFinish(val summary: String, val reason: String) : AgentLoopDecision()
}

/**
 * AgentLoopInterceptor
 * Prevents AI models from getting stuck in infinite reading, re-verification,
 * or "already fixed" thought loops.
 */
object AgentLoopInterceptor {

    private val FIXED_INDICATORS = listOf(
        "already been fixed",
        "already fixed",
        "error has been fixed",
        "now correctly uses",
        "correctly implemented",
        "everything looks good",
        "looks good with",
        "already resolved",
        "fix is in place",
        "no further changes needed"
    )

    fun evaluate(
        recentToolCalls: List<ToolCallItem>,
        currentCall: ToolCallItem,
        currentThought: String?,
        recentThoughts: List<String>,
        turn: Int = 1,
        hasPlannedEdits: Boolean = false
    ): AgentLoopDecision {
        // Disabled: No intrusive warnings or directives injected.
        return AgentLoopDecision.Continue
    }

    private fun normalizePath(p: String): String {
        return p.trim().trimStart('/', '.').lowercase()
    }
}
