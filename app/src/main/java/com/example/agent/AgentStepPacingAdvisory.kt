package com.example.agent

/**
 * AgentStepPacingAdvisory
 * Advises the AI model on step pacing, especially when approaching maxActionSteps (7 steps prior),
 * ensuring the task finishes successfully before hitting the hard limit.
 */
object AgentStepPacingAdvisory {

    private const val CRITICAL_STEPS_THRESHOLD = 7

    fun buildStepPacingNotice(currentStep: Int, maxSteps: Int, remainingSteps: Int): String {
        return if (remainingSteps <= CRITICAL_STEPS_THRESHOLD) {
            """
            [CRITICAL PACING ALERT: Step $currentStep/$maxSteps | ONLY $remainingSteps ACTIONS REMAINING!]
            You are approaching the maximum step limit ($maxSteps).
            You MUST wrap up and finalize all code changes immediately.
            Do not perform optional or exploratory reads. Complete all remaining essential edits right now and call 'complete' to finalize the task successfully.
            """.trimIndent()
        } else {
            "[Step $currentStep/$maxSteps | Remaining: $remainingSteps actions. Call 'complete' when done.]"
        }
    }
}
