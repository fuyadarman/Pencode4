package com.example.agent

/**
 * Interactive Clarification Engine for AI-User communication.
 * Allows the AI Agent to ask the user questions, request clarifications, or present options,
 * and resume task execution once the user provides their response.
 */
object InteractiveUserClarificationEngine {

    data class ClarificationPrompt(
        val question: String,
        val options: List<String>? = null,
        val contextSummary: String? = null
    )

    fun formatClarificationQuestion(
        question: String,
        options: List<String>? = null,
        contextSummary: String? = null
    ): String {
        val cleanQuestion = question.trim()
        val sb = StringBuilder()
        sb.append(cleanQuestion)

        if (!options.isNullOrEmpty()) {
            sb.append("\n\nOptions:")
            options.forEachIndexed { idx, opt ->
                sb.append("\n${idx + 1}. $opt")
            }
        }

        if (!contextSummary.isNullOrBlank()) {
            sb.append("\n\n(Context: $contextSummary)")
        }

        return sb.toString()
    }
}
