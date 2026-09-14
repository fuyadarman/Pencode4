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
        val tool = currentCall.tool.trim().lowercase()
        val args = currentCall.arguments
        val path = normalizePath(args?.path ?: args?.targetFile ?: args?.destinationPath)
        val thought = currentThought?.trim() ?: ""

        // Skip non-action completion tools
        if (tool == "complete") {
            return LoopDecision.Proceed
        }

        // 1. Completion Thought Sniffer:
        // If AI thought explicitly states the problem is fixed or changes are complete,
        // and it has already modified code, AUTO-FINISH!
        val indicatesFixed = COMPLETION_INDICATORS.any { thought.contains(it, ignoreCase = true) }
        if (indicatesFixed && filesModified.isNotEmpty()) {
            if (isReadTool(tool) || tool == "ai_think" || tool == "ai_response") {
                return LoopDecision.AutoComplete(
                    summary = thought.ifBlank { "Task completed: All requested changes and inspections are complete." },
                    reason = "AI confirmed fix/feature completion in thoughts. Auto-completing to prevent verification loop."
                )
            }
        }

        // 2. Post-Edit Re-read Interception:
        // Allow the AI to re-read files if it needs to inspect lines for further edits,
        // unless it is stuck in a pathological loop (> 5 consecutive identical reads).
        val previousReadsOfThisFile = actionHistory.count {
            isReadTool(it.tool) && it.path == path
        }
        val isPermitted = AgentReadLoopPolicy.shouldPermitRead(
            tool = tool,
            args = args,
            path = path,
            consecutiveSameFileReads = previousReadsOfThisFile,
            hasModifiedFile = filesModified.contains(path)
        )

        if (isReadTool(tool) && !isPermitted && previousReadsOfThisFile >= 6) {
            return LoopDecision.InterceptWithResult(
                toolOutput = AgentReadLoopPolicy.buildGentleActionGuidance(path),
                logTitle = "Gentle edit guidance",
                logStatus = "thinking",
                logDetails = "Provided edit guidance for '$path'."
            )
        }

        // 3. Consecutive Read Check:
        // Complex multi-file projects require reading and inspecting many files.
        // We do not intercept normal multi-file reading loops unless the exact same file
        // is being read pathologically without edits (which is already evaluated by AgentReadLoopPolicy above).


        // 5. Sequence Oscillation Breaker (e.g. Read -> Edit -> Read -> Edit):
        val historySize = actionHistory.size
        if (historySize >= 4) {
            // Check 2-step oscillation (A -> B -> A -> B)
            val a1 = actionHistory[historySize - 1]
            val b1 = actionHistory[historySize - 2]
            val a2 = actionHistory[historySize - 3]
            val b2 = actionHistory[historySize - 4]

            if (a1.tool == a2.tool && a1.path == a2.path &&
                b1.tool == b2.tool && b1.path == b2.path) {
                if (filesModified.isNotEmpty()) {
                    return LoopDecision.AutoComplete(
                        summary = "Task completed: Desired modifications have been finalized.",
                        reason = "Detected 2-cycle tool oscillation between '${a1.tool}' and '${b1.tool}'. Auto-completing since changes are in place."
                    )
                }
            }
        }

        // 6. Direct Identical Tool Repetition Circuit Breaker:
        if (historySize >= 3) {
            val lastThree = actionHistory.takeLast(3)
            val allSameTool = lastThree.all { it.tool == tool && it.path == path }
            if (allSameTool) {
                if (isEditTool(tool) && (fileEditFailures[path] ?: 0) >= 2) {
                    return LoopDecision.InterceptWithResult(
                        toolOutput = "SYSTEM ERROR (EDIT THRASHING): Edit on '$path' has failed multiple times. " +
                                "The search block does not match the actual file contents. " +
                                "Read the exact lines with 'read_file_range' or use 'create_file' to write the complete content.",
                        logTitle = "Edit thrashing blocked",
                        logStatus = "failed",
                        logDetails = "Stopped repetitive failing edits on '$path'."
                    )
                }
                if (isReadTool(tool)) {
                    return LoopDecision.InterceptWithResult(
                        toolOutput = "SYSTEM DIRECTIVE: You have repeated '$tool' on '$path' 3 times. " +
                                "Stop re-inspecting. Apply changes now or call 'complete'.",
                        logTitle = "Repetitive action blocked",
                        logStatus = "thinking",
                        logDetails = "Injected directive to break repetition on '$path'."
                    )
                }
            }
        }

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
