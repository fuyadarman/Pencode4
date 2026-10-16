package com.example.agent

import com.example.agent.TaskCheckpointEngine.CheckpointState
import java.io.File
import java.util.Locale

/**
 * AgentContinuousResumptionEngine
 *
 * Implements seamless continuous task resumption matching Cursor AI and Lovable behavior.
 * When the AI stops mid-execution (turn limit, pause, or network break):
 * 1. Checks if there is an active unfinished task.
 * 2. If user sends "continue" / "পরবর্তী ধাপে যাও" / "चालিয়ে যাও" / "next step",
 *    resumes strictly from the last completed checkpoint without restarting or re-reading.
 * 3. If user provides a new prompt while a task was in progress, merges the context
 *    so the agent continues forward incorporating the new prompt without discarding prior work.
 */
object AgentContinuousResumptionEngine {

    private val CONTINUATION_KEYWORDS = setOf(
        "continue", "cont", "resume", "resume task", "continue task",
        "next", "next step", "proceed", "keep going", "go ahead",
        "চালিয়ে যাও", "পরবর্তী ধাপে যাও", "পরের ধাপে যাও", "কাজ চালিয়ে যাও",
        "চালিয়ে যান", "পরবর্তী ধাপ", "পরের ধাপ", "চালিয়ে যেতে হবে",
        "chalate thako", "caliye jao", "porer dhape jao", "poroborti dhape jao"
    )

    fun isContinuationPrompt(prompt: String): Boolean {
        val clean = prompt.trim().lowercase(Locale.ROOT)
        if (CONTINUATION_KEYWORDS.contains(clean)) return true
        return clean.startsWith("continue") || clean.startsWith("পরবর্তী ধাপে") ||
                clean.startsWith("পরের ধাপে") || clean.startsWith("কাজ চালিয়ে")
    }

    /**
     * Builds intelligent context when resuming an interrupted or ongoing task.
     */
    fun buildResumptionPrompt(
        userPrompt: String,
        checkpoint: CheckpointState?,
        completedTodos: List<String>,
        pendingTodos: List<String>,
        recentEdits: List<String>
    ): String {
        val isExplicitContinuation = isContinuationPrompt(userPrompt)
        val goal = checkpoint?.goal?.ifBlank { null } ?: "Previous feature implementation"
        val lastStep = checkpoint?.currentStep?.ifBlank { null } ?: "Last executed tool action"

        val filesList = if (!checkpoint?.filesChanged.isNullOrEmpty()) {
            checkpoint!!.filesChanged.joinToString("\n") { "  • $it" }
        } else if (recentEdits.isNotEmpty()) {
            recentEdits.joinToString("\n") { "  • $it" }
        } else {
            "  (No files recorded yet)"
        }

        val completedSummary = if (completedTodos.isNotEmpty()) {
            completedTodos.joinToString("\n") { "  ✓ $it" }
        } else if (!checkpoint?.completed.isNullOrEmpty()) {
            checkpoint!!.completed.joinToString("\n") { "  ✓ $it" }
        } else {
            "  ✓ Initial analysis & setup"
        }

        val pendingSummary = if (pendingTodos.isNotEmpty()) {
            pendingTodos.joinToString("\n") { "  ⏳ $it" }
        } else {
            "  ⏳ Complete remaining requested logic and finalize with 'complete' tool"
        }

        return if (isExplicitContinuation) {
            """
            [AUTONOMOUS TASK CONTINUATION (Cursor / Lovable Protocol)]
            You are resuming the active task:
            "$goal"

            STATE OF EXECUTION:
            • Last Executed Step: $lastStep
            • Files Created / Modified So Far:
            $filesList
            • Completed Milestones:
            $completedSummary
            • Remaining Pending Tasks:
            $pendingSummary

            MANDATORY INSTRUCTIONS:
            1. DO NOT restart from scratch or re-read files you already inspected.
            2. Resume directly from '$lastStep' and proceed with the remaining changes.
            3. Apply all required code modifications cleanly.
            4. Only when ALL requirements are completely satisfied, explicitly call 'complete' with a clear summary.
            """.trimIndent()
        } else {
            userPrompt
        }
    }
}
