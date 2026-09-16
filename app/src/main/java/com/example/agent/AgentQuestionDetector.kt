package com.example.agent

import com.example.api.ToolCallItem

/**
 * AgentQuestionDetector
 * Identifies if an AI tool call, completion message, or model response is asking the user a question
 * or requesting clarification/options. When detected, the agent should immediately stop execution
 * and await the user's answer.
 */
object AgentQuestionDetector {

    private val QUESTION_TOOLS = setOf(
        "ask_user", "ask_question", "clarify_with_user", "ask", "prompt_user", "user_question"
    )

    fun isQuestionTool(toolName: String): Boolean {
        return QUESTION_TOOLS.contains(toolName.lowercase().trim())
    }

    fun hasQuestionToolCall(toolCalls: List<ToolCallItem>): ToolCallItem? {
        return toolCalls.find { isQuestionTool(it.tool) }
    }

    fun isClarificationOrQuestion(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        val lower = trimmed.lowercase()

        // Explicit question mark at the end
        if (trimmed.endsWith("?")) return true

        // Common clarification and question phrasing
        return lower.contains("would you like") ||
                lower.contains("do you want me to") ||
                lower.contains("should i ") ||
                lower.contains("please choose") ||
                lower.contains("which option") ||
                lower.contains("could you please clarify") ||
                lower.contains("please provide more details") ||
                lower.contains("what would you prefer")
    }
}
