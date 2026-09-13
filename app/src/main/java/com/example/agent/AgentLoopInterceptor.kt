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
        // Never auto-finish or intercept if the AI has planned file modifications/edits in this turn
        if (hasPlannedEdits) {
            return AgentLoopDecision.Continue
        }

        // Never auto-finish on initial actions or early turns
        if (turn <= 2 || recentToolCalls.size < 4) {
            return AgentLoopDecision.Continue
        }

        val tool = currentCall.tool.trim().lowercase()
        val isRead = ReadLoopSafetyManager.isReadTool(tool)
        val currentPath = currentCall.arguments?.path?.trim() 
            ?: currentCall.arguments?.targetFile?.trim() 
            ?: ""

        val thought = currentThought?.trim() ?: ""

        // Exclude the current thought if it was already added to the history list
        val pastThoughts = if (recentThoughts.isNotEmpty() && recentThoughts.last().trim() == thought) {
            recentThoughts.dropLast(1)
        } else {
            recentThoughts
        }

        // 1. Check if model's thought explicitly indicates the error is already fixed
        val indicatesFixed = FIXED_INDICATORS.any { thought.contains(it, ignoreCase = true) }
        if (indicatesFixed && isRead && pastThoughts.isNotEmpty()) {
            val priorFixedCount = pastThoughts.count { prev ->
                FIXED_INDICATORS.any { prev.contains(it, ignoreCase = true) }
            }
            if (priorFixedCount >= 2) {
                return AgentLoopDecision.AutoFinish(
                    summary = "Task completed: $thought",
                    reason = "AI confirmed the fix is already complete and verified across multiple steps. Auto-completing to prevent verification loop."
                )
            }
        }

        // 2. Check read loops on the same file
        if (isRead && currentPath.isNotEmpty()) {
            val normalizedCurrent = normalizePath(currentPath)
            var consecutiveSameFileReads = 0
            for (i in recentToolCalls.indices.reversed()) {
                val pastCall = recentToolCalls[i]
                if (!ReadLoopSafetyManager.isReadTool(pastCall.tool)) {
                    break // Stop if a write/edit or non-read tool was executed
                }
                val pastPath = normalizePath(pastCall.arguments?.path ?: pastCall.arguments?.targetFile ?: "")
                if (pastPath == normalizedCurrent) {
                    consecutiveSameFileReads++
                }
            }

            // If the call specifies line ranges or queries, it is not a pathological loop
            val isPermitted = AgentReadLoopPolicy.shouldPermitRead(
                tool = tool,
                args = currentCall.arguments,
                path = currentPath,
                consecutiveSameFileReads = consecutiveSameFileReads,
                hasModifiedFile = false
            )

            if (!isPermitted && consecutiveSameFileReads >= 5) {
                val warning = """
                    SYSTEM DIRECTIVE:
                    You have inspected '$currentPath' $consecutiveSameFileReads times.
                    Please proceed to apply your code edits using 'edit_file' or 'multi_edit_file'.
                """.trimIndent()
                return AgentLoopDecision.InjectWarning(
                    warning = warning,
                    logTitle = "Action suggestion on $currentPath"
                )
            }
        }

        return AgentLoopDecision.Continue
    }

    private fun normalizePath(p: String): String {
        return p.trim().trimStart('/', '.').lowercase()
    }
}
