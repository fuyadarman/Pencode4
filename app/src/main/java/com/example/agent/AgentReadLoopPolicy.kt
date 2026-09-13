package com.example.agent

import com.example.api.ToolArguments
import com.example.api.ToolCallItem

/**
 * AgentReadLoopPolicy
 *
 * Provides intelligent, non-intrusive read loop protection.
 * Fixes issues where normal file reads (such as inspecting 'main.js' before editing,
 * reading line ranges, or performing multiple edits) were being prematurely intercepted
 * with "Suppressed duplicate file dump", confusing the AI and causing it to complete
 * without doing the requested work.
 */
object AgentReadLoopPolicy {

    data class EvaluationResult(
        val shouldAllowRead: Boolean,
        val shouldInjectContent: Boolean = true,
        val customWarning: String? = null,
        val logDetails: String? = null
    )

    /**
     * Checks whether an action is a legitimate read request that must NOT be suppressed.
     * Slices, line ranges, queries, pre-edit inspections, and multi-step edits are always allowed.
     */
    fun shouldPermitRead(
        tool: String,
        args: ToolArguments?,
        path: String,
        consecutiveSameFileReads: Int,
        hasModifiedFile: Boolean
    ): Boolean {
        // If the user specifies line ranges or queries, it is never a duplicate dump
        if (args?.startLine != null || args?.endLine != null || !args?.lineRange.isNullOrBlank()) {
            return true
        }
        if (!args?.query.isNullOrBlank() || !args?.search.isNullOrBlank()) {
            return true
        }

        // Always allow reading if the file has not yet been modified in this turn/session
        // (The AI needs to read the file to locate code and prepare edits)
        if (!hasModifiedFile) {
            return true
        }

        // Even if modified, allow re-reading to find new lines for subsequent edits unless
        // there is a pathological identical loop (> 5 consecutive identical reads)
        return consecutiveSameFileReads < 5
    }

    /**
     * Formats helpful guidance for the model that encourages applying edits
     * rather than confusing it into premature completion.
     */
    fun buildGentleActionGuidance(path: String): String {
        return "SYSTEM NOTICE: File '$path' is available in your context. " +
                "Proceed with applying your code changes using 'edit_file' or 'multi_edit_file'."
    }
}
