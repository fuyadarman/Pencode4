package com.example.agent

import java.io.File

/**
 * TaskInterruptionResumeManager
 *
 * Implements the Interrupted Task Resume Loop.
 * When the user asks a question midway through an active task:
 * 1. Pauses the active task state without losing or wiping completed progress.
 * 2. Instructs the agent to answer the user's question directly.
 * 3. Keeps the structured checkpoint in `task.json` intact.
 * 4. Enables seamless resumption when the user says "continue" or taps "Resume Task".
 */
object TaskInterruptionResumeManager {

    private val QUESTION_PREFIXES = listOf(
        "what", "how", "why", "where", "who", "when", "can you", "could you", "explain", "tell me",
        "ki", "kivabe", "keno", "kothay", "kake", "kobe", "bolen", "bolte paro", "bujhalam na", "কেন", "কী", "কি", "কীভাবে", "কিভাবে", "কোথায়"
    )

    /**
     * Checks if a user prompt is an explicit continuation or resume keyword.
     */
    fun isExplicitResumePrompt(prompt: String): Boolean {
        val p = prompt.trim().lowercase()
        return p == "continue" || p == "cont" || p == "resume" || p == "resume task" ||
                p == "continue task" || p == "continue please" || p == "resume please" ||
                p == "chalate thako" || p == "caliye jao" || p == "চালিয়ে যাও" || p == "কাজ চালিয়ে যান" ||
                p == "শুরু করো" || p == "রেজিউম করো" || p == "next"
    }

    /**
     * Determines whether the user's message is a side question or inquiry during an ongoing/unfinished task.
     */
    fun isSideQuestionDuringTask(prompt: String, checkpoint: TaskCheckpointEngine.CheckpointState?): Boolean {
        if (checkpoint == null) return false
        if (checkpoint.status == "completed") return false

        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return false
        if (isExplicitResumePrompt(trimmed)) return false

        val lower = trimmed.lowercase()

        // 1. Direct question punctuation
        if (trimmed.endsWith("?")) return true

        // 2. Starts with interrogative question word
        val words = lower.split("\\s+".toRegex())
        val firstWord = words.firstOrNull() ?: ""
        if (QUESTION_PREFIXES.contains(firstWord)) return true
        if (words.size >= 2) {
            val firstTwo = "${words[0]} ${words[1]}"
            if (QUESTION_PREFIXES.contains(firstTwo)) return true
        }

        // 3. Common inquiry indicators
        return lower.contains("what does") ||
                lower.contains("how does") ||
                lower.contains("explain") ||
                lower.contains("can you tell") ||
                lower.contains("কীভাবে কাজ করে") ||
                lower.contains("কেন এমন হলো") ||
                lower.contains("কি পরিবর্তন হলো")
    }

    /**
     * Builds dynamic prompt guidance for the model when answering a side question during a paused task.
     */
    fun buildSideQuestionGuidance(checkpoint: TaskCheckpointEngine.CheckpointState): String {
        return """
            [TASK STATUS: TEMPORARILY PAUSED]
            An ongoing task is currently paused:
            • Paused Task Goal: "${checkpoint.goal}"
            • Current Step / Progress: ${checkpoint.currentStep.ifBlank { "In Progress" }}
            • Completed Milestones: ${checkpoint.completed.take(3).joinToString(", ")}

            DIRECTIVE FOR THIS TURN:
            1. The user has asked a side question or requested clarification.
            2. Answer the user's question clearly, directly, and concisely.
            3. Do not erase, discard, or overwrite the paused task state.
            4. At the end of your response, mention that the previous task is safely paused, and they can continue anytime by typing 'continue' or tapping 'Resume Task'.
        """.trimIndent()
    }

    /**
     * Generates a concise message informing the user about the paused checkpoint.
     */
    fun buildPausedTaskNotice(checkpoint: TaskCheckpointEngine.CheckpointState): String {
        val goalSnippet = checkpoint.goal.take(50)
        return "Task '$goalSnippet...' paused at step '${checkpoint.currentStep}'. Ready to resume whenever you wish."
    }
}
