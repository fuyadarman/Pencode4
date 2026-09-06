package com.example.api

import org.json.JSONArray
import org.json.JSONObject
import android.util.Log

/**
 * Robust extractor for OpenAI/Cline/Anthropic/Custom API model responses.
 * Handles:
 * 1. String content
 * 2. Array content (Anthropic/OpenAI part blocks: [{"type": "text", "text": "..."}])
 * 3. Reasoning content (DeepSeek R1, Claude 3.7 Thinking, o1/o3: reasoning_content, reasoning, thought)
 * 4. Native Tool calls (tool_calls: [{"function": {"name": "...", "arguments": "..."}}])
 * 5. Direct completions (text, response, output)
 */
object CustomModelResponseParser {
    private const val TAG = "CustomModelParser"

    data class ExtractedResult(
        val text: String?,
        val toolCallResponse: ToolCallResponse?
    )

    fun extractContentOrTool(rawResponse: String, provider: String = "custom"): ExtractedResult {
        if (rawResponse.isBlank()) {
            return ExtractedResult(null, null)
        }

        try {
            val json = JSONObject(rawResponse)

            // 1. Direct tool_calls or function_call inside choices[0].message
            if (json.has("choices")) {
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.optJSONObject(0)
                    if (firstChoice != null) {
                        // Check message object
                        val messageObj = firstChoice.optJSONObject("message")
                        if (messageObj != null) {
                            // Check native tool_calls
                            if (messageObj.has("tool_calls")) {
                                val toolCalls = messageObj.optJSONArray("tool_calls")
                                val converted = parseNativeToolCalls(toolCalls)
                                if (converted != null) {
                                    return ExtractedResult(null, converted)
                                }
                            }

                            // Check content (can be String, JSONArray, or JSONObject)
                            val contentVal = messageObj.opt("content")
                            val parsedContent = parseContentValue(contentVal)
                            if (!parsedContent.isNullOrBlank()) {
                                return ExtractedResult(parsedContent, null)
                            }

                            // Check reasoning fields for reasoning models (DeepSeek-R1, QwQ, etc.)
                            val reasoning = messageObj.optString("reasoning_content", "")
                                .ifBlank { messageObj.optString("reasoning", "") }
                                .ifBlank { messageObj.optString("thought", "") }
                            if (reasoning.isNotBlank()) {
                                Log.d(TAG, "Using reasoning_content as response text")
                                return ExtractedResult(reasoning, null)
                            }
                        }

                        // Check legacy choice.text
                        val choiceText = firstChoice.optString("text", "")
                        if (choiceText.isNotBlank()) {
                            return ExtractedResult(choiceText, null)
                        }
                    }
                }
            }

            // 2. Direct Anthropic style responses: {"content": [{"type": "text", "text": "..."}]}
            if (json.has("content")) {
                val directContent = parseContentValue(json.opt("content"))
                if (!directContent.isNullOrBlank()) {
                    return ExtractedResult(directContent, null)
                }
            }

            // 3. Ollama / custom direct output: {"response": "..."} or {"output": "..."}
            val simpleResponse = json.optString("response", "")
                .ifBlank { json.optString("output", "") }
                .ifBlank { json.optString("text", "") }
            if (simpleResponse.isNotBlank()) {
                return ExtractedResult(simpleResponse, null)
            }

        } catch (e: Exception) {
            Log.w(TAG, "Non-JSON or parsing error in extractContentOrTool: ${e.message}")
        }

        return ExtractedResult(null, null)
    }

    private fun parseContentValue(contentVal: Any?): String? {
        if (contentVal == null || contentVal == JSONObject.NULL) return null
        if (contentVal is String) {
            return contentVal.ifBlank { null }
        }
        if (contentVal is JSONArray) {
            val sb = java.lang.StringBuilder()
            for (i in 0 until contentVal.length()) {
                val item = contentVal.opt(i)
                if (item is String) {
                    sb.append(item).append("\n")
                } else if (item is JSONObject) {
                    val text = item.optString("text", "")
                    if (text.isNotBlank()) {
                        sb.append(text).append("\n")
                    } else {
                        val thinking = item.optString("thinking", "")
                        if (thinking.isNotBlank()) {
                            sb.append(thinking).append("\n")
                        }
                    }
                }
            }
            val result = sb.toString().trim()
            return result.ifBlank { null }
        }
        if (contentVal is JSONObject) {
            val text = contentVal.optString("text", "")
            return text.ifBlank { null }
        }
        return contentVal.toString().ifBlank { null }
    }

    private fun parseNativeToolCalls(toolCalls: JSONArray?): ToolCallResponse? {
        if (toolCalls == null || toolCalls.length() == 0) return null
        try {
            val items = mutableListOf<ToolCallItem>()
            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.optJSONObject(i) ?: continue
                val fn = tc.optJSONObject("function") ?: continue
                val name = fn.optString("name", "")
                val argsRaw = fn.optString("arguments", "{}")
                if (name.isNotBlank()) {
                    val argsObj = try { JSONObject(argsRaw) } catch (e: Exception) { JSONObject() }
                    val args = com.example.agent.MultiEditChunkParser.parseFullArguments(argsObj)
                    items.add(ToolCallItem(tool = name, arguments = args))
                }
            }

            if (items.isNotEmpty()) {
                return if (items.size == 1) {
                    ToolCallResponse(
                        thought = "Executing tool call",
                        tool = items[0].tool,
                        arguments = items[0].arguments,
                        tools = items
                    )
                } else {
                    ToolCallResponse(
                        thought = "Executing batch tool calls",
                        tool = items[0].tool,
                        arguments = items[0].arguments,
                        tools = items
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing native tool calls: ${e.message}")
        }
        return null
    }
}
