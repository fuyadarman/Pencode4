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
        SmartReadBudgetPolicy.reset()
    }

    /**
     * Resets consecutive read counters and records last modified file whenever an edit or mutation succeeds.
     */
    fun onFileModified(path: String? = null) {
        consecutiveReads.set(0)
        lastModifiedFile = path?.let { AgentLoopProtectionEngine.normalizePath(it) }
        SmartReadBudgetPolicy.onActionExecuted()
    }

    /**
     * Evaluates candidate read operations.
     * Integrates SmartReadBudgetPolicy for slice-level tracking and progressive nudges.
     */
    fun evaluateRead(
        tool: String,
        path: String,
        lineRangeDesc: String? = null
    ): LoopCheckResult {
        val normPath = AgentLoopProtectionEngine.normalizePath(path)

        val currentConsecutive = consecutiveReads.incrementAndGet()
        val readCount = fileReadCounts.computeIfAbsent(normPath) { AtomicInteger(0) }.incrementAndGet()

        // Smart budget evaluation (handles identical slice checks and progressive nudges)
        val budgetResult = SmartReadBudgetPolicy.checkRead(path, null, null)
        when (budgetResult) {
            is SmartReadBudgetPolicy.BudgetEvaluation.InterceptDuplicate -> {
                return LoopCheckResult.Intercept(
                    responseMessage = budgetResult.message,
                    logDetails = "Intercepted repeated slice read for $path"
                )
            }
            is SmartReadBudgetPolicy.BudgetEvaluation.ForceAction -> {
                return LoopCheckResult.Intercept(
                    responseMessage = budgetResult.message,
                    logDetails = "Forced action after excessive multi-file reads ($currentConsecutive)"
                )
            }
            is SmartReadBudgetPolicy.BudgetEvaluation.SoftNudge -> {
                return LoopCheckResult.Warn(directive = budgetResult.nudge)
            }
            SmartReadBudgetPolicy.BudgetEvaluation.Proceed -> {}
        }

        // Only intercept truly pathological infinite loops (10+ reads of exact same file or 20+ reads total without edit)
        if (readCount >= 10) {
            val rangeNotice = if (!lineRangeDesc.isNullOrBlank()) " ($lineRangeDesc)" else ""
            return LoopCheckResult.Intercept(
                responseMessage = "--- File: $path$rangeNotice (Already in Context) ---\n" +
                        "[SYSTEM NOTICE: File '$path' has already been read $readCount times. " +
                        "Please proceed to apply code changes using 'edit_file' or 'create_file'.]",
                logDetails = "Intercepted excessive read loop for $path ($readCount reads)"
            )
        }

        if (currentConsecutive >= 20) {
            val rangeNotice = if (!lineRangeDesc.isNullOrBlank()) " ($lineRangeDesc)" else ""
            return LoopCheckResult.Intercept(
                responseMessage = "--- File: $path$rangeNotice (Already in Context) ---\n" +
                        "[SYSTEM NOTICE: $currentConsecutive consecutive file reading operations performed without making any code edits. " +
                        "Please apply your planned changes using 'edit_file' or 'create_file'.]",
                logDetails = "Halted extreme consecutive read loop ($currentConsecutive reads without edits)"
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
