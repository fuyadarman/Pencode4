package com.example.agent

/**
 * AgentLogPrivacySanitizer
 *
 * Sanitizes and shields internal agent loop-handling prompts, system nudges,
 * and directives from user-facing timelines and UI action logs.
 *
 * The raw guidance is preserved for the AI model in background conversation history,
 * while the human user sees clean, polished, professional developer activity.
 */
object AgentLogPrivacySanitizer {

    private val INTERNAL_PROMPT_PATTERNS = listOf(
        Regex("""\[SYSTEM NOTICE:.*?\]""", RegexOption.DOT_MATCHES_ALL),
        Regex("""\[PRO-TIP:.*?\]""", RegexOption.DOT_MATCHES_ALL),
        Regex("""\[SYSTEM DIRECTIVE:.*?\]""", RegexOption.DOT_MATCHES_ALL),
        Regex("""\[SYSTEM ADVISORY:.*?\]""", RegexOption.DOT_MATCHES_ALL),
        Regex("""--- File:.*?\(Already in Context\) ---""", RegexOption.DOT_MATCHES_ALL),
        Regex("""You have reviewed \d+ file sections.*?""", RegexOption.IGNORE_CASE),
        Regex("""Intercepted repeated slice read for .*?""", RegexOption.IGNORE_CASE),
        Regex("""Intercepted repeated read for .*?""", RegexOption.IGNORE_CASE),
        Regex("""Halted extreme consecutive read loop.*?""", RegexOption.IGNORE_CASE),
        Regex("""Forced action after excessive multi-file reads.*?""", RegexOption.IGNORE_CASE)
    )

    /**
     * Cleans internal loop notices from user-facing log titles.
     */
    fun sanitizeTitle(title: String, defaultFallback: String = "Reviewed codebase"): String {
        if (title.contains("suppressed", ignoreCase = true) ||
            title.contains("loop", ignoreCase = true) ||
            title.contains("Already in Context", ignoreCase = true)
        ) {
            val fileName = title.substringAfter("(").substringBefore(")")
            return if (fileName.isNotBlank() && fileName != title) {
                "Inspected $fileName"
            } else {
                defaultFallback
            }
        }
        return title
    }

    /**
     * Cleans internal loop messages and nudges from user-facing details.
     */
    fun sanitizeDetails(details: String?, defaultFallback: String = "Explored project context"): String {
        if (details.isNullOrBlank()) return defaultFallback

        var cleaned: String = details
        for (pattern in INTERNAL_PROMPT_PATTERNS) {
            cleaned = cleaned.replace(pattern, "").trim()
        }

        if (cleaned.isBlank() || cleaned.length < 5) {
            return defaultFallback
        }

        return cleaned
    }

    /**
     * Checks if a message is purely an internal system loop notice.
     */
    fun isPureInternalNotice(text: String?): Boolean {
        if (text == null) return false
        val trimmed = text.trim()
        return trimmed.startsWith("[SYSTEM NOTICE") ||
                trimmed.startsWith("[PRO-TIP") ||
                trimmed.startsWith("[SYSTEM DIRECTIVE") ||
                trimmed.startsWith("[SYSTEM ADVISORY") ||
                trimmed.contains("consecutive file reading operations", ignoreCase = true) ||
                trimmed.contains("You have reviewed", ignoreCase = true)
    }
}
