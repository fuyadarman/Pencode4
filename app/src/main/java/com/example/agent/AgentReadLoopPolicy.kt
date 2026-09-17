package com.example.agent

import com.example.api.ToolArguments
import com.example.api.ToolCallItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * AgentReadLoopPolicy
 *
 * Provides intelligent, robust read loop protection that prevents models from
 * entering infinite 'read_file_range' or 'read_file' cycles without making code edits.
 */
object AgentReadLoopPolicy {

    private val fileReadCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val consecutiveReads = AtomicInteger(0)
    @Volatile
    private var lastModifiedFile: String? = null

    sealed class LoopCheckResult {
        object Allow : LoopCheckResult()
        data class Warn(val directive: String) : LoopCheckResult()
        data class Intercept(val responseMessage: String, val logDetails: String) : LoopCheckResult()
    }

    fun resetSession() {
        fileReadCounts.clear()
        consecutiveReads.set(0)
        lastModifiedFile = null
    }

    /**
     * Resets consecutive read counters and records last modified file whenever an edit or mutation succeeds.
     */
    fun onFileModified(path: String? = null) {
        consecutiveReads.set(0)
        lastModifiedFile = path?.let { AgentLoopProtectionEngine.normalizePath(it) }
    }

    /**
     * Evaluates candidate read operations.
     * Prevents infinite reading loops across read_file, read_file_range, and multi_read,
     * and strictly blocks re-reading files to verify code changes immediately after editing.
     */
    fun evaluateRead(
        tool: String,
        path: String,
        lineRangeDesc: String? = null
    ): LoopCheckResult {
        val normPath = AgentLoopProtectionEngine.normalizePath(path)

        // 0. Strict Anti-Verification Rule: Block re-reading the exact file just modified
        if (lastModifiedFile != null && normPath == lastModifiedFile) {
            return LoopCheckResult.Intercept(
                responseMessage = "--- File: $path (Changes Already Applied & Saved) ---\n" +
                        "[SYSTEM DIRECTIVE (ANTI-VERIFICATION RULE): You just modified '$path'. Re-reading files with '$tool' to verify code changes is strictly prohibited. Your changes are successfully applied and stored in the project. Do NOT re-read to verify. Proceed immediately to your next action or invoke 'complete'.]",
                logDetails = "Blocked post-edit verification read for $path"
            )
        }
        val currentConsecutive = consecutiveReads.incrementAndGet()
        val readCount = fileReadCounts.computeIfAbsent(normPath) { AtomicInteger(0) }.incrementAndGet()

        // 1. Pathological single-file read loop: Reading the same file 3 or more times
        if (readCount >= 3) {
            val rangeNotice = if (!lineRangeDesc.isNullOrBlank()) " ($lineRangeDesc)" else ""
            return LoopCheckResult.Intercept(
                responseMessage = "--- File: $path$rangeNotice (Already in Context) ---\n" +
                        "[SYSTEM NOTICE (ANTI-READ-LOOP): File '$path' has already been provided to you $readCount times in this session. " +
                        "Further repetitive reading is suppressed to break the read loop. You have sufficient context. " +
                        "You MUST now proceed to apply your changes using 'edit_file' or 'create_file', or call 'complete' if the task is finished.]",
                logDetails = "Intercepted repeated read for $path (Read $readCount times)"
            )
        }

        // 2. Pathological global read loop: Calling read tools 4 or more times in a row without any edits
        if (currentConsecutive >= 4) {
            val rangeNotice = if (!lineRangeDesc.isNullOrBlank()) " ($lineRangeDesc)" else ""
            return LoopCheckResult.Intercept(
                responseMessage = "--- File: $path$rangeNotice (Already in Context) ---\n" +
                        "[SYSTEM NOTICE (ANTI-READ-LOOP): You have performed $currentConsecutive consecutive file reading operations without making any code edits. " +
                        "Reading is now halted to break the read loop. You MUST now proceed to implement your planned code changes using 'create_file' or 'edit_file', or call 'complete'.]",
                logDetails = "Halted consecutive read loop ($currentConsecutive reads without edits)"
            )
        }

        // 3. Gentle directive when reading the same file a 2nd time or after 2 consecutive reads
        if (readCount == 2 || currentConsecutive == 3) {
            return LoopCheckResult.Warn(
                directive = "\n\n[SYSTEM DIRECTIVE: You have gathered sufficient context for '$path'. Do NOT call read_file or read_file_range again. In your NEXT step, execute your changes using 'edit_file' or 'create_file'.]"
            )
        }

        return LoopCheckResult.Allow
    }

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
