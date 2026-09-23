package com.example.agent

import com.example.ui.AiActionLog

/**
 * AgentInterruptionStateManager
 *
 * Ensures that when an AI task is paused or stopped (either manually by the user
 * or before calling 'complete'):
 * 1. The actual operations performed in the CURRENT prompt are fully preserved.
 * 2. Operations from earlier completed tasks never bleed into or replace the current prompt.
 * 3. Prevents unwanted auto-fix loops from wiping out interrupted task state.
 */
object AgentInterruptionStateManager {

    fun buildInterruptionLog(userReason: String?): AiActionLog {
        val details = if (!userReason.isNullOrBlank()) {
            userReason
        } else {
            "Execution was paused or stopped before calling 'complete'. You can press 'Continue' or type 'continue' to resume."
        }
        return AiActionLog(
            title = "Task Interrupted",
            status = "failed",
            details = details,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Sanitizes logs to guarantee they only represent operations from the current turn.
     * Prevents previous turn operations from being erroneously appended or displayed.
     */
    fun sanitizeCurrentTurnLogs(
        currentLogs: List<AiActionLog>,
        interruptionLog: AiActionLog?
    ): List<AiActionLog> {
        val mutable = currentLogs.toMutableList()
        if (interruptionLog != null) {
            val alreadyHasCancel = mutable.any { it.title.equals("Task Interrupted", ignoreCase = true) }
            if (!alreadyHasCancel) {
                mutable.add(interruptionLog)
            }
        }
        return mutable
    }

    /**
     * Determines whether auto-fix should be allowed to run.
     * Auto-fix must NEVER trigger if the user intentionally stopped the AI
     * or if the task was interrupted.
     */
    fun shouldAllowAutoFix(isThinking: Boolean, isInterrupted: Boolean, allowAutoFixSetting: Boolean): Boolean {
        if (!allowAutoFixSetting) return false
        if (isThinking) return false
        if (isInterrupted) return false
        return true
    }
}
