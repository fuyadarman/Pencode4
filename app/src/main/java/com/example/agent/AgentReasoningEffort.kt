package com.example.agent

import android.content.Context

/**
 * AI Reasoning Effort configuration.
 * Controls the depth of cognitive reasoning, internal thinking token budget,
 * and pre-action architectural scrutiny for AI models and agent planning loops.
 */
enum class ReasoningEffort(
    val id: String,
    val label: String,
    val thinkingBudget: Int,
    val reasoningEffortParam: String,
    val description: String,
    val directive: String
) {
    SMALL(
        id = "small",
        label = "Small",
        thinkingBudget = 1024,
        reasoningEffortParam = "low",
        description = "Fast, lightweight thinking. Best for quick edits and simple tasks.",
        directive = "EFFORT LEVEL: SMALL. Prioritize high execution speed. Perform lean pre-planning and proceed directly to surgical actions."
    ),
    NORMAL(
        id = "normal",
        label = "Normal",
        thinkingBudget = 4096,
        reasoningEffortParam = "medium",
        description = "Balanced thinking depth and speed. Standard autonomous reasoning (Default).",
        directive = "EFFORT LEVEL: NORMAL. Balance thoughtful planning with execution speed. Inspect necessary context, check code boundaries, and execute with precision."
    ),
    MEDIUM(
        id = "medium",
        label = "Medium",
        thinkingBudget = 8192,
        reasoningEffortParam = "high",
        description = "Deep architectural analysis. Scrutinizes edge cases and dependencies.",
        directive = "EFFORT LEVEL: MEDIUM. Conduct deeper architectural analysis in 'thought'. Systematically scan for edge-cases, error handling, and potential regressions before editing."
    ),
    MAX(
        id = "max",
        label = "Max",
        thinkingBudget = 32768,
        reasoningEffortParam = "high",
        description = "Exhaustive reasoning and self-debugging. Highest cognitive power.",
        directive = "EFFORT LEVEL: MAX. Apply maximum cognitive depth and exhaustive multi-angle verification. Formulate extensive architecture plans, verify contract correctness, trace error paths, and self-debug before executing changes."
    );

    companion object {
        private const val PREFS_KEY_EFFORT = "key_ai_reasoning_effort"

        fun fromId(id: String?): ReasoningEffort {
            if (id.isNullOrBlank()) return NORMAL
            return entries.find { it.id.equals(id.trim(), ignoreCase = true) } ?: NORMAL
        }

        fun getSavedEffort(context: Context): ReasoningEffort {
            return try {
                val prefs = context.getSharedPreferences("vibe_coder_prefs", Context.MODE_PRIVATE)
                val savedId = prefs.getString(PREFS_KEY_EFFORT, NORMAL.id)
                fromId(savedId)
            } catch (e: Exception) {
                NORMAL
            }
        }

        fun saveEffort(context: Context, effort: ReasoningEffort) {
            try {
                val prefs = context.getSharedPreferences("vibe_coder_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString(PREFS_KEY_EFFORT, effort.id).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
