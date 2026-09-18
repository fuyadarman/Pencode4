package com.example.agent

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * SmartReadBudgetPolicy
 *
 * Implements Claude Code & OpenCode style smart read budgeting:
 * 1. Tracks exact range reads (prevents reading identical line slices repetitively).
 * 2. Provides Progressive Soft-Nudges (after 6-8 reads, encourages agent to start writing code).
 * 3. Prevents endless multi-file read loops without breaking genuine exploration.
 */
object SmartReadBudgetPolicy {

    // Tracks specific slice reads: "normalizedPath:startLine-endLine" -> count
    private val sliceReadCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val totalConsecutiveReads = AtomicInteger(0)

    fun reset() {
        sliceReadCounts.clear()
        totalConsecutiveReads.set(0)
    }

    fun onActionExecuted() {
        // When edit, create or run_command happens, reset budget
        totalConsecutiveReads.set(0)
    }

    /**
     * Inspects the read request and returns guidance or restriction.
     */
    fun checkRead(
        path: String,
        startLine: Int?,
        endLine: Int?
    ): BudgetEvaluation {
        val normPath = AgentLoopProtectionEngine.normalizePath(path)
        val sliceKey = "$normPath:${startLine ?: 1}-${endLine ?: -1}"
        
        val sliceCount = sliceReadCounts.computeIfAbsent(sliceKey) { AtomicInteger(0) }.incrementAndGet()
        val consecutive = totalConsecutiveReads.incrementAndGet()

        // 1. Stagnant Identical Slice Read: Same file slice read 3+ times without any edits
        if (sliceCount >= 3) {
            return BudgetEvaluation.InterceptDuplicate(
                message = "[SYSTEM NOTICE: You have already inspected this exact code slice in '$path' multiple times. Please use the context you have gathered to make changes with 'edit_file' or 'create_file'.]"
            )
        }

        // 2. Extreme loop protection: 15+ consecutive reads across different files without action
        if (consecutive >= 15) {
            return BudgetEvaluation.ForceAction(
                message = "[SYSTEM ADVISORY: You have performed $consecutive consecutive file reads. You have ample context across the codebase. Please proceed to execute your implementation using 'edit_file' or 'create_file'.]"
            )
        }

        // 3. Progressive Soft Nudge: Between 6 and 9 reads, gently suggest synthesizing action
        if (consecutive in 6..9) {
            return BudgetEvaluation.SoftNudge(
                nudge = "\n\n[PRO-TIP: You have reviewed $consecutive file sections. If you have identified the root cause or target lines, proceed with 'edit_file'.]"
            )
        }

        return BudgetEvaluation.Proceed
    }

    sealed class BudgetEvaluation {
        object Proceed : BudgetEvaluation()
        data class SoftNudge(val nudge: String) : BudgetEvaluation()
        data class InterceptDuplicate(val message: String) : BudgetEvaluation()
        data class ForceAction(val message: String) : BudgetEvaluation()
    }
}
