package com.example.agent

/**
 * ActivePromptFocusGuard
 *
 * Ensures the autonomous agent focuses 100% on the user's latest active prompt
 * and eliminates context drift where the model mistakenly works on past/previous prompts.
 */
object ActivePromptFocusGuard {

    /**
     * Builds an authoritative prompt directive that anchors the agent to the current request.
     */
    fun buildActivePromptDirective(currentPrompt: String): String {
        val cleanPrompt = currentPrompt.trim().replace("""\[IMAGE_BASE64: data:.*?;base64,.*?\]""".toRegex(), "[Attached Image]")
        return """
            === ACTIVE USER REQUEST (CURRENT TARGET TASK) ===
            Current Task: "$cleanPrompt"
            
            STRICT PRIORITY RULES:
            1. You MUST ONLY work on, solve, and execute the CURRENT ACTIVE USER REQUEST above.
            2. Any previous user requests in the chat history are ALREADY FINISHED and CLOSED. Do NOT re-execute, repeat, or get sidetracked by past tasks.
            3. All tool calls, file edits, and outputs in this turn must exclusively serve the active request above.
        """.trimIndent()
    }

    /**
     * Formats the current user message in conversation history to stand out with unmistakable salience.
     */
    fun formatActiveUserPrompt(currentPrompt: String): String {
        return """
            [CURRENT ACTIVE USER REQUEST - FOCUS EXCLUSIVELY ON THIS]
            $currentPrompt
        """.trimIndent()
    }

    /**
     * Formats historical completed requests so the model knows they are archived and must not be worked on.
     */
    fun formatHistoricalPastRequest(pastPrompt: String): String {
        val clean = pastPrompt.trim().replace("""\[IMAGE_BASE64: data:.*?;base64,.*?\]""".toRegex(), "[Attached Image]")
        return """
            [ARCHIVED PAST REQUEST - COMPLETED & CLOSED]
            Previous Turn: "$clean"
            Status: Fully resolved in previous turn. DO NOT re-execute.
        """.trimIndent()
    }

    /**
     * Detects if the model's reasoning is drifting to an old/previous prompt instead of the current one.
     */
    fun isDriftingToPastPrompt(modelThought: String, currentPrompt: String, pastPrompt: String?): Boolean {
        if (pastPrompt.isNullOrBlank()) return false
        val thoughtLower = modelThought.lowercase()
        val pastLower = pastPrompt.lowercase().trim()
        val currentLower = currentPrompt.lowercase().trim()

        if (pastLower == currentLower) return false

        // Extract key distinguishing keywords from past prompt
        val pastKeywords = pastLower.split(Regex("\\s+"))
            .map { it.trim('.', ',', '!', '?', ':', ';', '"', '\'') }
            .filter { it.length > 3 && !currentLower.contains(it) && !isCommonStopword(it) }

        if (pastKeywords.isEmpty()) return false

        val matchingPastKeywords = pastKeywords.count { thoughtLower.contains(it) }
        val currentKeywords = currentLower.split(Regex("\\s+"))
            .map { it.trim('.', ',', '!', '?', ':', ';', '"', '\'') }
            .filter { it.length > 3 && !isCommonStopword(it) }

        val matchingCurrentKeywords = currentKeywords.count { thoughtLower.contains(it) }

        return matchingPastKeywords >= 2 && matchingCurrentKeywords == 0
    }

    private fun isCommonStopword(word: String): Boolean {
        return word in setOf(
            "this", "that", "with", "from", "have", "make", "need", "want", "file",
            "code", "app", "task", "user", "project", "work", "create", "edit", "using"
        )
    }
}
