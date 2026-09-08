package com.example.agent

import com.example.api.ToolCallItem
import com.example.ui.AiActionLog

sealed class SequenceLoopResult {
    object Proceed : SequenceLoopResult()
    data class WarningInjected(val warningText: String, val logTitle: String, val logDetails: String) : SequenceLoopResult()
    data class Abort(val reason: String, val logTitle: String) : SequenceLoopResult()
}

object AgentSequenceLoopChecker {

    fun checkSequenceLoop(
        recentToolCallsHistory: MutableList<ToolCallItem>,
        call: ToolCallItem,
        tool: String,
        argsString: String?,
        recentLogs: List<AiActionLog>,
        isSameWork: (ToolCallItem, ToolCallItem) -> Boolean
    ): SequenceLoopResult {
        recentToolCallsHistory.add(call)
        val size = recentToolCallsHistory.size

        for (k in 1..4) {
            if (size >= k * 2) {
                var isMatch = true
                for (i in 0 until k) {
                    if (!isSameWork(recentToolCallsHistory[size - 1 - i], recentToolCallsHistory[size - 1 - k - i])) {
                        isMatch = false
                        break
                    }
                }
                if (isMatch) {
                    var cycles = 2
                    var offset = size - 1 - 2 * k
                    while (offset - k + 1 >= 0) {
                        var cycleMatch = true
                        for (i in 0 until k) {
                            if (!isSameWork(recentToolCallsHistory[size - 1 - i], recentToolCallsHistory[offset - i])) {
                                cycleMatch = false
                                break
                            }
                        }
                        if (cycleMatch) {
                            cycles++
                            offset -= k
                        } else {
                            break
                        }
                    }

                    if (k == 1) {
                        if (cycles >= 5) {
                            val reason = "Aborted execution: AI was stuck repeating the same action '$tool' $cycles times consecutively."
                            return SequenceLoopResult.Abort(reason, "Infinite Loop Blocked")
                        } else if (cycles >= 3) {
                            val warningText = """
                                SYSTEM ALERT (INFINITE LOOP WARNING):
                                You have performed the exact same action $cycles times consecutively.
                                Tool: $tool
                                Arguments: ${argsString ?: "None"}
                                
                                This has resulted in the same outcome!
                                You MUST stop repeating this action. Use 'grep' or check the file state first before taking another action.
                            """.trimIndent()
                            return SequenceLoopResult.WarningInjected(
                                warningText,
                                "Loop warning injected",
                                "Injected warning: AI repeated tool '$tool' $cycles times consecutively."
                            )
                        }
                    } else {
                        val seqNames = (0 until k).map { idx -> recentToolCallsHistory[size - k + idx].tool }.joinToString(" -> ")
                        if (cycles >= 4) {
                            val reason = "Aborted execution: AI was stuck in a $k-step sequence loop ($seqNames) repeated $cycles cycles."
                            return SequenceLoopResult.Abort(reason, "Sequence Loop Blocked")
                        } else if (cycles >= 2) {
                            val warningText = """
                                SYSTEM ALERT (SEQUENCE LOOP DETECTED):
                                You are repeating a $k-step sequence cycle ($seqNames) for $cycles cycles!
                                This indicates an oscillating loop where previous steps keep failing or reverting.
                                
                                You MUST stop this sequence cycle immediately! Try a different approach or inspect code before proceeding.
                            """.trimIndent()
                            return SequenceLoopResult.WarningInjected(
                                warningText,
                                "Sequence loop warning injected",
                                "Injected warning: $k-step sequence pattern ($seqNames) repeated $cycles times."
                            )
                        }
                    }
                }
            }
        }

        // Check consecutive failures
        val last3Logs = recentLogs.takeLast(3)
        if (last3Logs.size >= 3 && last3Logs.all { it.status == "failed" }) {
            val warningText = """
                SYSTEM WARNING (CONSECUTIVE FAILURES):
                Your last 3 consecutive tool executions have failed.
                Stop guessing file contents or line ranges!
                
                Before you try any more edits:
                1. Run a 'grep' command to locate the file and exact lines.
                2. Read the surrounding lines of the target file to verify its structure and syntax.
                3. Adjust your path or content matching to be perfectly accurate.
            """.trimIndent()
            return SequenceLoopResult.WarningInjected(
                warningText,
                "Consecutive Failures Warning",
                "Injected warning: last 3 actions failed."
            )
        }

        return SequenceLoopResult.Proceed
    }
}
