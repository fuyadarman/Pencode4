package com.example.agent

import android.util.Log
import com.example.api.ToolArguments
import com.example.api.ToolCallItem
import com.example.api.ToolCallResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * AgentThoughtAndToolParser
 *
 * Dedicated parser for model responses that solves:
 * 1. Orphan thought blocks: e.g. {"thought": "I have the full picture of the template..."}
 *    Converts orphan thought blocks to 'ai_think' actions instead of terminating with
 *    "Fallback: Plain-text response." and tool="complete".
 * 2. Multi-block JSON responses: separates thought and subsequent tool/tools blocks.
 * 3. Fallback extraction: extracts actual thought text instead of placeholder fallback strings.
 */
object AgentThoughtAndToolParser {

    private const val TAG = "AgentThoughtToolParser"
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    /**
     * Attempts to parse raw model text into a valid ToolCallResponse.
     * Guarantees that pure thought responses do not prematurely exit the agent loop.
     */
    fun parseResponse(
        cleaned: String,
        rawText: String,
        finishReason: String? = null,
        fallbackParser: (String, String?) -> ToolCallResponse
    ): ToolCallResponse {
        val trimmedRaw = rawText.trim()

        // 0. Check for DSML or XML-like tool calls (<｜DSML｜ calls> or <invoke name="...">)
        val dsmlResult = DsmlToolCallParser.parse(rawText, finishReason)
        if (dsmlResult != null) {
            return dsmlResult
        }

        // 1. Try standard JSON parsing on cleaned text
        val adapter = moshi.adapter(ToolCallResponse::class.java).lenient()
        val standardParsed = try {
            adapter.fromJson(cleaned)
        } catch (e: Exception) {
            null
        }

        if (standardParsed != null && (standardParsed.tool != null || !standardParsed.tools.isNullOrEmpty())) {
            return standardParsed.copy(finishReason = finishReason ?: standardParsed.finishReason)
        }

        // 2. Multi-block check: Did model emit {"thought": "..."} followed by {"tool": "..."} or {"tools": [...]}?
        val multiBlockResult = parseMultiBlockJson(trimmedRaw, finishReason)
        if (multiBlockResult != null) {
            return multiBlockResult
        }

        // 3. Check if standardParsed had ONLY a thought/thinking
        val thoughtText: String? = standardParsed?.thought?.takeIf { it.isNotBlank() }
            ?: extractField(trimmedRaw, "thought")
            ?: extractField(trimmedRaw, "thinking")
            ?: extractField(trimmedRaw, "reasoning")

        // 4. Check if there is any tool specified in raw text
        val toolInRaw = extractField(trimmedRaw, "tool") ?: extractXmlField(trimmedRaw, "tool")
        val toolsArrayInRaw = extractJsonArray(trimmedRaw, "tools")

        if (toolInRaw != null || toolsArrayInRaw != null) {
            return fallbackParser(rawText, finishReason)
        }

        // 5. If there is a thought block and NO tool, treat it as 'ai_think'
        // This prevents the agent from halting or outputting {"thought": "..."} as a complete message!
        if (!thoughtText.isNullOrBlank()) {
            Log.d(TAG, "Parsed orphan thought block as ai_think action: ${thoughtText.take(60)}...")
            return ToolCallResponse(
                thought = thoughtText,
                tool = "ai_think",
                arguments = ToolArguments(message = thoughtText),
                finishReason = finishReason
            )
        }

        // 5.5. If the model outputs plain text that is internal reasoning/monologue deliberation
        // (e.g. "The user hasn't given...", "Let me build...", "Decision: Create car.js...")
        // Treat it as 'ai_think' under Reasoning so it renders in a collapsible block and doesn't get stuck!
        if (AgentReasoningDetector.isReasoningText(trimmedRaw)) {
            val extractedReasoning = AgentReasoningDetector.extractReasoning(trimmedRaw)
            Log.d(TAG, "Parsed internal monologue/reasoning text as ai_think action: ${extractedReasoning.take(60)}...")
            return ToolCallResponse(
                thought = extractedReasoning,
                tool = "ai_think",
                arguments = ToolArguments(message = extractedReasoning),
                finishReason = finishReason
            )
        }

        // 6. If it's truly plain-text (e.g. conversational answer without JSON)
        val plainTextCleaned = stripToolCallXml(trimmedRaw)
        return ToolCallResponse(
            thought = "Direct response",
            tool = "complete",
            arguments = ToolArguments(message = plainTextCleaned),
            finishReason = finishReason
        )
    }

    /**
     * Parses responses where the model outputs multiple JSON objects:
     * e.g.:
     * {"thought": "Analyzing files..."}
     * {"tools": [{"tool": "edit_file", ...}]}
     */
    private fun parseMultiBlockJson(text: String, finishReason: String?): ToolCallResponse? {
        val blocks = extractAllJsonObjects(text)
        if (blocks.size < 2) return null

        var combinedThought: String? = null
        var foundTool: String? = null
        var foundTools: List<ToolCallItem>? = null
        var foundArgs: ToolArguments? = null

        val adapter = moshi.adapter(ToolCallResponse::class.java).lenient()

        for (block in blocks) {
            try {
                val parsed = adapter.fromJson(block) ?: continue
                if (!parsed.thought.isNullOrBlank()) {
                    combinedThought = parsed.thought
                }
                if (parsed.tool != null) {
                    foundTool = parsed.tool
                    foundArgs = parsed.arguments
                }
                if (!parsed.tools.isNullOrEmpty()) {
                    foundTools = parsed.tools
                }
            } catch (_: Exception) {
                // Ignore parse errors on individual blocks
            }
        }

        if (foundTool != null || !foundTools.isNullOrEmpty()) {
            return ToolCallResponse(
                thought = combinedThought ?: "Multi-step action planned.",
                tool = foundTool,
                tools = foundTools,
                arguments = foundArgs,
                finishReason = finishReason
            )
        }

        return null
    }

    /**
     * Extracts individual top-level JSON objects {...} from a string
     */
    private fun extractAllJsonObjects(text: String): List<String> {
        val results = mutableListOf<String>()
        var depth = 0
        var startIndex = -1
        var inString = false
        var escape = false

        for (i in text.indices) {
            val c = text[i]

            if (escape) {
                escape = false
                continue
            }

            if (c == '\\') {
                escape = true
                continue
            }

            if (c == '"') {
                inString = !inString
                continue
            }

            if (!inString) {
                if (c == '{') {
                    if (depth == 0) startIndex = i
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0 && startIndex != -1) {
                        results.add(text.substring(startIndex, i + 1))
                        startIndex = -1
                    }
                }
            }
        }

        return results
    }

    private fun extractField(json: String, fieldName: String): String? {
        val pattern = java.util.regex.Pattern.compile("\"$fieldName\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", java.util.regex.Pattern.DOTALL)
        val matcher = pattern.matcher(json)
        if (matcher.find()) {
            val group = matcher.group(1) ?: return null
            return group.replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
        }
        return null
    }

    private fun extractJsonArray(json: String, fieldName: String): String? {
        val pattern = java.util.regex.Pattern.compile("\"$fieldName\"\\s*:\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL)
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun extractXmlField(text: String, tag: String): String? {
        val pattern = java.util.regex.Pattern.compile("<$tag>(.*?)</$tag>", java.util.regex.Pattern.DOTALL or java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun stripToolCallXml(text: String): String {
        return text.replace(Regex("(?i)</?tool_call>"), "")
            .replace(Regex("(?i)</?tool_input>"), "")
            .replace(Regex("(?i)</?function_call>"), "")
            .replace(Regex("(?i)</?tool_name>"), "")
            .replace(Regex("(?i)</?arg_key>"), "")
            .replace(Regex("(?i)</?arg_value>"), "")
            .trim()
    }
}
