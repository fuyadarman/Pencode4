package com.example.agent

/**
 * AgentReasoningDetector
 *
 * Solves the issue where AI models output internal chain-of-thought reasoning,
 * monologue deliberation, or planning tokens (e.g., "The user hasn't given an explicit new prompt...",
 * "Let me build...", "Decision: Create car.js...") instead of returning proper JSON tool calls,
 * causing execution to get stuck in the middle or dump messy thoughts as final complete text.
 *
 * This detector:
 * 1. Identifies when a model's plain text response is actually internal reasoning/monologue.
 * 2. Formats it so UI displays it as "Reasoning for X seconds" with a collapsible/expandable button.
 * 3. Prevents premature termination or deadlocks, immediately prompting the model to execute tools.
 */
object AgentReasoningDetector {

    private val REASONING_TAG_REGEX = Regex(
        "(?s)<(?:thought|thinking|reasoning|deliberation)>(.*?)</(?:thought|thinking|reasoning|deliberation)>",
        RegexOption.IGNORE_CASE
    )

    private val REASONING_MARKDOWN_BLOCK_REGEX = Regex(
        "(?s)```(?:thought|thinking|reasoning)\\s*\\n(.*?)\\n```",
        RegexOption.IGNORE_CASE
    )

    private val REASONING_KEYWORDS = listOf(
        "the user hasn't given an explicit",
        "the user has not given an explicit",
        "the previous request summary",
        "given the ambiguity",
        "without a clear prompt",
        "i've been looping",
        "i have been looping",
        "decision: create",
        "decision: write",
        "decision: modify",
        "decision: edit",
        "best move:",
        "key structural components:",
        "let me build",
        "let me write",
        "let me inspect",
        "let me create",
        "let me check",
        "let me produce",
        "i will now create",
        "i'll write",
        "i will write",
        "first, i should check",
        "first let me check",
        "my plan is to",
        "internal reasoning:",
        "chain of thought:"
    )

    /**
     * Checks whether the provided text is an internal reasoning or monologue block.
     */
    fun isReasoningText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()

        if (REASONING_TAG_REGEX.containsMatchIn(trimmed)) return true
        if (REASONING_MARKDOWN_BLOCK_REGEX.containsMatchIn(trimmed)) return true

        val lower = trimmed.lowercase()
        if (lower.startsWith("thought:") ||
            lower.startsWith("thinking:") ||
            lower.startsWith("reasoning:") ||
            lower.startsWith("internal reasoning:") ||
            lower.startsWith("thinking process:") ||
            lower.startsWith("plan:")
        ) {
            return true
        }

        // Check for self-directed monologue patterns where the model talks to itself
        val matchesCount = REASONING_KEYWORDS.count { lower.contains(it) }
        if (matchesCount >= 2) {
            return true
        }

        // Single strong indicators of internal deliberation
        if (lower.startsWith("the user hasn't given") ||
            lower.startsWith("i've been looping") ||
            lower.startsWith("given the ambiguity") ||
            (lower.contains("decision:") && lower.contains("let me"))
        ) {
            return true
        }

        return false
    }

    /**
     * Extracts pure reasoning text from tags or blocks.
     */
    fun extractReasoning(rawText: String): String {
        val trimmed = rawText.trim()

        val tagMatch = REASONING_TAG_REGEX.find(trimmed)
        if (tagMatch != null) {
            val content = tagMatch.groupValues[1].trim()
            if (content.isNotBlank()) return content
        }

        val mdMatch = REASONING_MARKDOWN_BLOCK_REGEX.find(trimmed)
        if (mdMatch != null) {
            val content = mdMatch.groupValues[1].trim()
            if (content.isNotBlank()) return content
        }

        val prefixes = listOf("thought:", "thinking:", "reasoning:", "internal reasoning:", "thinking process:")
        for (prefix in prefixes) {
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                return trimmed.substring(prefix.length).trim()
            }
        }

        return trimmed
    }

    /**
     * Creates guidance prompt after reasoning is logged, compelling the AI to execute tools
     * so it never hangs or stalls mid-task.
     */
    fun buildNextStepGuidance(): String {
        return "System Notice: Your reasoning and plan have been logged under Reasoning. " +
                "Now in your NEXT response, you MUST execute the required tool calls (e.g. 'create_file', 'edit_file', 'multi_edit_file') " +
                "in JSON format to implement your plan. Do NOT output plain-text monologue without tools."
    }
}
