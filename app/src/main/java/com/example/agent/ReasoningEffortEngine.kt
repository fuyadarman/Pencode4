package com.example.agent

/**
 * ReasoningEffortEngine
 * Dedicated engine to enforce, configure, and calibrate AI Thinking Effort
 * across all AI providers (Gemini, Claude, OpenAI, OpenRouter, DeepSeek, Ollama)
 * and instruction formats (Chat and Autonomous Coding Agent).
 */
object ReasoningEffortEngine {

    /**
     * Calibrated thinking budget for Gemini API (clamped to max 24576).
     */
    fun getGeminiThinkingBudget(effort: ReasoningEffort): Int {
        return when (effort) {
            ReasoningEffort.SMALL -> 1024
            ReasoningEffort.NORMAL -> 4096
            ReasoningEffort.MEDIUM -> 8192
            ReasoningEffort.MAX -> 24576 // 24576 is Gemini's official maximum allowed budget
        }
    }

    /**
     * Thinking level string for Gemini 2.5 / 3.0 series.
     */
    fun getGeminiThinkingLevel(effort: ReasoningEffort): String {
        return when (effort) {
            ReasoningEffort.SMALL -> "low"
            ReasoningEffort.NORMAL -> "medium"
            ReasoningEffort.MEDIUM -> "high"
            ReasoningEffort.MAX -> "high"
        }
    }

    /**
     * Thinking budget for Anthropic Claude extended thinking.
     */
    fun getClaudeThinkingBudget(effort: ReasoningEffort): Int {
        return when (effort) {
            ReasoningEffort.SMALL -> 1024
            ReasoningEffort.NORMAL -> 4096
            ReasoningEffort.MEDIUM -> 8192
            ReasoningEffort.MAX -> 16384
        }
    }

    /**
     * Enforced prompt directive for Agent Coding Mode.
     * Tells the model explicitly how deeply to plan and reason in its "thought" field.
     */
    fun getCodingDirective(effort: ReasoningEffort): String {
        return when (effort) {
            ReasoningEffort.SMALL -> """
                [THINKING EFFORT: SMALL]
                - SPEED OVER ANALYSIS: Keep your internal 'thought' ultra-lean and concise (1 to 2 short sentences max).
                - Minimal pre-planning. Proceed immediately to surgical tool actions without analyzing non-essential edge cases.
                - Do not perform unnecessary directory scans or speculative file reads. Focus directly on the user's prompt.
            """.trimIndent()

            ReasoningEffort.NORMAL -> """
                [THINKING EFFORT: NORMAL]
                - BALANCED REASONING: Formulate a standard 3 to 5 sentence plan in your 'thought' before executing tools.
                - Review necessary file context and verify basic contract compatibility.
                - Execute actions cleanly and verify results before calling 'complete'.
            """.trimIndent()

            ReasoningEffort.MEDIUM -> """
                [THINKING EFFORT: MEDIUM]
                - DEEP ARCHITECTURAL SCRUTINY: In your 'thought' field, conduct an explicit multi-point architectural analysis (6 to 10 sentences).
                - Identify potential edge cases, state management conflicts, lifecycle regressions, and error states.
                - Outline a systematic execution plan before executing tool actions.
            """.trimIndent()

            ReasoningEffort.MAX -> """
                [THINKING EFFORT: MAX - MAXIMUM COGNITIVE DEPTH]
                - EXHAUSTIVE MULTI-ANGLE REASONING: You MUST provide an extensive, deep, step-by-step reasoning plan in your 'thought' field (15+ sentences or comprehensive analytical breakdown).
                - Deeply evaluate:
                  1. Underlying problem architecture and potential hidden failure modes.
                  2. Boundary contracts, edge conditions, null-safety, and concurrency.
                  3. Performance implications and backward compatibility.
                  4. Complete self-verification checklist prior to applying edits.
                - Scrutinize every detail thoroughly before proceeding to tool execution.
            """.trimIndent()
        }
    }

    /**
     * Enforced prompt directive for Conversation / Explanation Mode.
     * Ensures thinking effort is strictly respected even for chat questions.
     */
    fun getConversationDirective(effort: ReasoningEffort): String {
        return when (effort) {
            ReasoningEffort.SMALL -> """
                [THINKING EFFORT: SMALL]
                - In 'thought', write 1 quick sentence outlining your direct response.
                - Provide a fast, concise, straight-to-the-point explanation without unnecessary exposition.
            """.trimIndent()

            ReasoningEffort.NORMAL -> """
                [THINKING EFFORT: NORMAL]
                - In 'thought', write a balanced reasoning outline (2-4 sentences) organizing key points.
                - Provide a clear, well-structured explanation with helpful examples.
            """.trimIndent()

            ReasoningEffort.MEDIUM -> """
                [THINKING EFFORT: MEDIUM]
                - In 'thought', conduct a thorough intellectual breakdown (6-8 sentences) exploring core principles and nuances.
                - Deliver a comprehensive, deeply informative explanation covering nuances, trade-offs, and practical examples.
            """.trimIndent()

            ReasoningEffort.MAX -> """
                [THINKING EFFORT: MAX]
                - In 'thought', formulate an exhaustive, deep multi-dimensional analysis (12+ sentences) exploring theoretical foundations, edge cases, underlying mechanics, and counter-perspectives.
                - Deliver a masterful, in-depth explanation with complete rigor, structural breakdown, and detailed insights.
            """.trimIndent()
        }
    }

    /**
     * Parses and merges thinking parts from Gemini API candidates.
     */
    fun extractGeminiThinkingAndContent(
        parts: List<GeminiPartData>
    ): Pair<String, String> {
        val thinkingSb = StringBuilder()
        val contentSb = StringBuilder()

        for (part in parts) {
            val text = part.text ?: ""
            if (part.thought == true) {
                if (thinkingSb.isNotEmpty()) thinkingSb.append("\n\n")
                thinkingSb.append(text)
            } else {
                contentSb.append(text)
            }
        }

        return Pair(thinkingSb.toString().trim(), contentSb.toString().trim())
    }
}

/**
 * Data interface for Gemini parts with thought metadata.
 */
data class GeminiPartData(
    val text: String?,
    val thought: Boolean? = null
)
