package com.example.agent

import com.example.api.ToolArguments
import com.example.api.ToolCallItem

/**
 * AgentLoopProtectionEngine
 *
 * State-of-the-art loop prevention and thrashing circuit-breaker engine,
 * inspired by modern agent runtimes (Antigravity, OpenCode, Codex, Lovable).
 *
 * Core Pillars:
 * 1. Post-Edit Re-read Interception (Zero-token short-circuit)
 * 2. Read Thrashing Circuit Breaker (Same file read deduplication regardless of line ranges)
 * 3. Consecutive Inspection Ceiling (Force transition from Read -> Act -> Complete)
 * 4. Completion Thought Sniffer (Auto-completing when model confirms fix is in place)
 * 5. Edit Thrashing & Oscillation Detection (Breaks 2-cycle oscillation loops)
 */
class AgentLoopProtectionEngine {

    data class ActionRecord(
        val tool: String,
        val path: String,
        val search: String = "",
        val replace: String = "",
        val isSuccess: Boolean = true,
        val turn: Int = 1
    )

    sealed class LoopDecision {
        object Proceed : LoopDecision()

        data class InterceptWithResult(
            val toolOutput: String,
            val logTitle: String,
            val logStatus: String = "thinking",
            val logDetails: String = ""
        ) : LoopDecision()

        data class AutoComplete(
            val summary: String,
            val reason: String
        ) : LoopDecision()

        data class AbortLoop(
            val reason: String
        ) : LoopDecision()
    }

    private val filesRead = mutableSetOf<String>()
    private val filesModified = mutableSetOf<String>()
    private val fileEditFailures = mutableMapOf<String, Int>()
    private val actionHistory = mutableListOf<ActionRecord>()
    private var consecutiveReadCount = 0

    companion object {
        private val COMPLETION_INDICATORS = listOf(
            "already been fixed",
            "already fixed",
            "error has been fixed",
            "now correctly uses",
            "correctly implemented",
            "everything looks good",
            "looks good with",
            "already resolved",
            "fix is in place",
            "no further changes needed",
            "no further changes required",
            "task is complete",
            "task completed",
            "changes have been applied",
            "all changes applied",
            "everything is in place",
            "now properly implemented",
            "successfully implemented",
            "successfully resolved",
            "issue is resolved"
        )

        fun isReadTool(tool: String): Boolean {
            val t = tool.trim().lowercase()
            return t == "read_file" ||
                    t == "read_file_range" ||
                    t == "multi_read_file" ||
                    t == "multi_read" ||
                    t == "view_file"
        }

        fun isEditTool(tool: String): Boolean {
            val t = tool.trim().lowercase()
            return t == "edit_file" ||
                    t == "multi_edit_file" ||
                    t == "multi_edit" ||
                    t == "multi_patch" ||
                    t == "patch_file" ||
                    t == "append" ||
                    t == "write_file" ||
                    t == "create_file"
        }

        fun normalizePath(path: String?): String {
            if (path == null) return ""
            return path.trim().trimStart('/', '.').replace('\\', '/').lowercase()
        }
    }

    /**
     * Resets state for a new agent task prompt.
     */
    fun reset() {
        filesRead.clear()
        filesModified.clear()
        fileEditFailures.clear()
        actionHistory.clear()
        consecutiveReadCount = 0
    }

    /**
     * Evaluates the candidate tool call BEFORE execution.
     * Can short-circuit the execution with a cached notice, auto-complete the task, or abort loops.
     */
    fun evaluatePreExecution(
        currentCall: ToolCallItem,
        currentThought: String?,
        turn: Int,
        maxTurns: Int = 30
    ): LoopDecision {
        // Disabled: No intrusive directives or loop warnings injected.
        // The AI is allowed to use tools freely up to the configured maxActionSteps.
        return LoopDecision.Proceed
    }

    /**
     * Ingests the outcome of an action AFTER it finishes executing.
     */
    fun recordActionOutcome(
        tool: String,
        args: ToolArguments?,
        isSuccess: Boolean,
        turn: Int
    ) {
        val normTool = tool.trim().lowercase()
        val path = normalizePath(args?.path ?: args?.targetFile ?: args?.destinationPath)

        actionHistory.add(
            ActionRecord(
                tool = normTool,
                path = path,
                search = args?.search?.trim() ?: "",
                replace = args?.replace?.trim() ?: "",
                isSuccess = isSuccess,
                turn = turn
            )
        )

        if (isReadTool(normTool)) {
            consecutiveReadCount++
            if (path.isNotEmpty()) {
                filesRead.add(path)
            }
        } else if (normTool != "ai_think" && normTool != "ai_response") {
            consecutiveReadCount = 0
        }

        if (isEditTool(normTool)) {
            if (isSuccess) {
                if (path.isNotEmpty()) {
                    filesModified.add(path)
                    fileEditFailures[path] = 0 // Reset failure count on success
                }
            } else {
                if (path.isNotEmpty()) {
                    val currentFails = fileEditFailures[path] ?: 0
                    fileEditFailures[path] = currentFails + 1
                }
            }
        }
    }

    fun hasModifiedAnyFiles(): Boolean = filesModified.isNotEmpty()

    fun getModifiedFiles(): Set<String> = filesModified.toSet()
}
