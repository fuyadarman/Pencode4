package com.example.agent

/**
 * AgentRepetitionGuardEngine
 * Prevents small-context-window models (and over-iterating AI models) from entering infinite loops
 * by detecting repetitive sequences of identical or cyclic tool calls.
 *
 * Provides:
 * - Repetitive action detection across sliding history windows.
 * - Progressive warnings when loops start to form.
 * - Hard halting with a polite summary when an AI gets trapped in an unbreakable loop.
 */
object AgentRepetitionGuardEngine {

    data class ActionSignature(
        val tool: String,
        val target: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    sealed class LoopVerdict {
        object Proceed : LoopVerdict()
        data class Warn(val message: String) : LoopVerdict()
        data class Halt(val reason: String) : LoopVerdict()
    }

    private val sessionActionHistory = mutableListOf<ActionSignature>()
    private var warningIssuedCount = 0

    /**
     * Resets loop guard state for a new prompt/task turn.
     */
    fun resetSession() {
        sessionActionHistory.clear()
        warningIssuedCount = 0
    }

    /**
     * Registers a new tool call and verifies whether a loop or repeated pattern is occurring.
     */
    fun evaluateAction(tool: String, argsSummary: String): LoopVerdict {
        val normalizedTool = tool.trim().lowercase()
        // ai_think and complete are benign
        if (normalizedTool == "ai_think" || normalizedTool == "complete") {
            return LoopVerdict.Proceed
        }

        val sig = ActionSignature(tool = normalizedTool, target = argsSummary.trim())
        sessionActionHistory.add(sig)

        val total = sessionActionHistory.size
        if (total < 4) return LoopVerdict.Proceed

        // 1. Check for immediate consecutive identical tool call repetition (e.g. 3+ times identical)
        if (total >= 3) {
            val last1 = sessionActionHistory[total - 1]
            val last2 = sessionActionHistory[total - 2]
            val last3 = sessionActionHistory[total - 3]

            if (last1.tool == last2.tool && last2.tool == last3.tool &&
                last1.target == last2.target && last2.target == last3.target &&
                last1.target.isNotBlank()
            ) {
                warningIssuedCount++
                return LoopVerdict.Warn(
                    "[SYSTEM ALERT: REPETITIVE OPERATION] You have repeatedly executed '$tool' on target '${last1.target}' $warningIssuedCount times. " +
                            "Please review your edits and proceed with the next task step or invoke 'complete' without repeating this operation."
                )
            }
        }

        // 2. Check for 2-step or 3-step repeating cycles (e.g. [read A, edit A, read A, edit A])
        // Common in models that edit a file and immediately re-read it repeatedly
        if (total >= 6) {
            // Check cycle length 2
            val s1 = sessionActionHistory[total - 1]
            val s2 = sessionActionHistory[total - 2]
            val s3 = sessionActionHistory[total - 3]
            val s4 = sessionActionHistory[total - 4]
            val s5 = sessionActionHistory[total - 5]
            val s6 = sessionActionHistory[total - 6]

            if (s1 == s3 && s3 == s5 && s2 == s4 && s4 == s6) {
                warningIssuedCount++
                return LoopVerdict.Warn(
                    "[SYSTEM ALERT: CYCLICAL SEQUENCE] Alternating sequence detected between [${s2.tool} -> ${s1.tool}]. " +
                            "Do not repeat this cycle. Proceed with your implementation or call 'complete'."
                )
            }
        }

        // 3. Check for high repetitive count of the same tool and file in recent 10 actions
        val recentWindow = sessionActionHistory.takeLast(10)
        val matchesInWindow = recentWindow.count { it.tool == sig.tool && it.target == sig.target && sig.target.isNotBlank() }
        if (matchesInWindow >= 4) {
            warningIssuedCount++
            return LoopVerdict.Warn(
                "[SYSTEM ALERT: EXCESSIVE OPERATION] '$tool' was called $matchesInWindow times on '${sig.target}' recently. " +
                        "Avoid redundant inspections and continue toward task completion."
            )
        }

        return LoopVerdict.Proceed
    }
}
