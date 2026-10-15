package com.example.agent

import android.util.Log
import com.example.api.ToolCallItem
import java.util.Locale

/**
 * AgentThoughtLoopGuard
 *
 * Dedicated circuit breaker and protection system against AI Thought-Only Infinite Loops.
 *
 * Solves:
 * 1. AI model emitting pure thoughts or reasoning tokens repeatedly without executing any tools.
 * 2. Looping thought patterns where the model repeats identical or similar deliberation.
 * 3. Question/informational prompts where the model outputs an explanation in its thought but the system
 *    unnecessarily keeps prompting for code edits.
 * 4. Hard circuit breaker after maximum allowed consecutive thought turns (2 turns warning, 3rd turn hard break).
 */
object AgentThoughtLoopGuard {

    private const val TAG = "ThoughtLoopGuard"
    private const val MAX_CONSECUTIVE_THOUGHTS = 2

    sealed class ThoughtDecision {
        object ProceedNormal : ThoughtDecision()
        data class NudgeAction(val guidance: String, val attempt: Int) : ThoughtDecision()
        data class BreakAndComplete(val finalResponse: String, val reason: String) : ThoughtDecision()
    }

    /**
     * Checks if a user's prompt is primarily informational or conversational,
     * where a thoughtful explanation is the desired outcome and does not require file edits.
     */
    fun isInformationalOrConversationalPrompt(prompt: String): Boolean {
        val lower = prompt.trim().lowercase(Locale.ROOT)
        if (lower.isBlank()) return false

        val questionKeywords = listOf(
            "explain", "why", "how does", "what is", "what are", "which", "where is",
            "tell me about", "describe", "can you clarify", "summary", "summarize",
            "কেন", "কি", "কী", "কিভাবে", "কেন হচ্ছে", "কারন", "কারণ", "ব্যাখ্যা", "বলুন", "জানাও"
        )

        val actionKeywords = listOf(
            "create", "add", "make", "edit", "update", "fix", "delete", "remove", "build",
            "install", "change", "write", "বানাও", "তৈরি", "ঠিক কর", "যোগ কর", "মুছে ফেল"
        )

        val hasQuestion = questionKeywords.any { lower.contains(it) }
        val hasAction = actionKeywords.any { lower.contains(it) }

        return hasQuestion && !hasAction
    }

    /**
     * Evaluates the current turn to intercept and break thought-only infinite loops.
     */
    fun evaluateTurn(
        consecutiveThoughtCount: Int,
        lastThought: String?,
        currentThought: String?,
        userPrompt: String,
        hasModifiedFiles: Boolean,
        hasReadFiles: Boolean
    ): ThoughtDecision {
        val cleanCurrent = (currentThought ?: "").trim()
        val cleanLast = (lastThought ?: "").trim()

        // 1. If user asked an informational question and the thought contains an explanation
        if (isInformationalOrConversationalPrompt(userPrompt) && cleanCurrent.length > 30) {
            val responseText = sanitizeThoughtForUserResponse(cleanCurrent)
            Log.d(TAG, "Completed conversational prompt from thought response.")
            return ThoughtDecision.BreakAndComplete(
                finalResponse = responseText,
                reason = "Informational answer provided in reasoning."
            )
        }

        // 2. If files have already been modified and the model has thought without further action
        if (hasModifiedFiles && consecutiveThoughtCount >= 1) {
            val responseText = sanitizeThoughtForUserResponse(cleanCurrent.ifBlank { cleanLast })
                .ifBlank { "All requested changes and implementations have been successfully applied." }
            Log.d(TAG, "Auto-completing: files already modified and thought confirmed completion.")
            return ThoughtDecision.BreakAndComplete(
                finalResponse = responseText,
                reason = "All requested file modifications were applied. Completed after reasoning."
            )
        }

        // 3. Check for repetitive identical or near-identical thoughts (2-cycle thought loop)
        if (consecutiveThoughtCount >= 1 && cleanLast.isNotBlank() && cleanCurrent.isNotBlank()) {
            val similarity = computeThoughtSimilarity(cleanLast, cleanCurrent)
            if (similarity > 0.70f) {
                Log.w(TAG, "Detected repetitive thought loop (similarity: $similarity). Circuit breaker triggered.")
                val responseText = sanitizeThoughtForUserResponse(cleanCurrent)
                    .ifBlank { "Task plan analyzed. Proceeding with the formulated design." }
                return ThoughtDecision.BreakAndComplete(
                    finalResponse = responseText,
                    reason = "Repetitive thought pattern intercepted (similarity: ${(similarity * 100).toInt()}%)."
                )
            }
        }

        // 4. Hard threshold check: if model spent >= MAX_CONSECUTIVE_THOUGHTS (>= 2) without real tool calls
        if (consecutiveThoughtCount >= MAX_CONSECUTIVE_THOUGHTS) {
            Log.w(TAG, "Hard circuit breaker: consecutive thoughts reached $consecutiveThoughtCount. Terminating thought loop.")
            val responseText = if (cleanCurrent.length > 20) {
                sanitizeThoughtForUserResponse(cleanCurrent)
            } else if (cleanLast.length > 20) {
                sanitizeThoughtForUserResponse(cleanLast)
            } else {
                "Task analysis completed. If further edits are needed, please provide the specific file or action to perform."
            }
            return ThoughtDecision.BreakAndComplete(
                finalResponse = responseText,
                reason = "Halted consecutive thought loop ($consecutiveThoughtCount thinking turns without tool calls)."
            )
        }

        // 5. If it's the first thought, nudge with progressive strictness
        val guidance = if (consecutiveThoughtCount == 1) {
            "System Directive: You have analyzed the requirements. You MUST now proceed immediately with concrete tool actions (e.g. 'create_file', 'edit_file', 'multi_edit_file', or 'read_file'). Do NOT output thoughts alone in your next response."
        } else {
            "System Directive [WARNING]: This is your final planning turn. You MUST execute a tool call ('create_file', 'edit_file', etc.) or call 'complete'. Any further thought without tools will conclude the turn."
        }

        return ThoughtDecision.NudgeAction(guidance = guidance, attempt = consecutiveThoughtCount + 1)
    }

    /**
     * Cleans internal markdown markers, JSON wrappers, or monologue tokens
     * so thoughts can be presented cleanly to the user when auto-completing.
     */
    fun sanitizeThoughtForUserResponse(raw: String): String {
        var clean = raw.trim()
        clean = clean.removePrefix("```json").removePrefix("```thought").removePrefix("```thinking").removePrefix("```")
        clean = clean.removeSuffix("```").trim()

        val tagRegex = Regex("""(?s)<(?:thought|thinking|reasoning|deliberation)>(.*?)</(?:thought|thinking|reasoning|deliberation)>""")
        val match = tagRegex.find(clean)
        if (match != null) {
            clean = match.groupValues[1].trim()
        }

        val prefixes = listOf(
            "thought:", "thinking:", "reasoning:", "internal reasoning:",
            "decision:", "let me", "i will", "first,"
        )
        for (p in prefixes) {
            if (clean.startsWith(p, ignoreCase = true)) {
                clean = clean.substring(p.length).trim()
            }
        }

        return clean.ifBlank { "Analysis complete." }
    }

    /**
     * Computes word overlap Jaccard similarity between two thought strings.
     */
    private fun computeThoughtSimilarity(a: String, b: String): Float {
        val wordsA = a.lowercase(Locale.ROOT).split(Regex("""\W+""")).filter { it.length > 3 }.toSet()
        val wordsB = b.lowercase(Locale.ROOT).split(Regex("""\W+""")).filter { it.length > 3 }.toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0f

        val intersection = wordsA.intersect(wordsB).size
        val union = wordsA.union(wordsB).size
        return if (union == 0) 0f else intersection.toFloat() / union.toFloat()
    }
}
