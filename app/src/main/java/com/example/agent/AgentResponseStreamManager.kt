package com.example.agent

import com.example.api.ToolCallResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real-time Streaming State and Parser for AI Responses.
 * Manages live thought, reasoning, and normal text token streaming.
 */
object AgentResponseStreamManager {

    private val _streamingThought = MutableStateFlow("")
    val streamingThought: StateFlow<String> = _streamingThought.asStateFlow()

    private val _streamingMessage = MutableStateFlow("")
    val streamingMessage: StateFlow<String> = _streamingMessage.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    fun startStreaming() {
        _streamingThought.value = ""
        _streamingMessage.value = ""
        _isStreaming.value = true
    }

    fun appendChunk(rawAccumulated: String) {
        // Extract live thought or message from accumulated buffer
        val extractedThought = extractPartialThought(rawAccumulated)
        if (extractedThought.isNotBlank()) {
            _streamingThought.value = extractedThought
        }

        val extractedMsg = extractPartialMessage(rawAccumulated)
        if (extractedMsg.isNotBlank()) {
            _streamingMessage.value = extractedMsg
        }
    }

    fun endStreaming() {
        _isStreaming.value = false
        _streamingThought.value = ""
        _streamingMessage.value = ""
    }

    private fun extractPartialThought(raw: String): String {
        // Extract from "thought": "..." or <thought>...</thought>
        val thoughtMatch = Regex(""""thought"\s*:\s*"((?:\\.|[^"\\])*)""").find(raw)
        if (thoughtMatch != null) {
            return unescapeJsonString(thoughtMatch.groupValues[1])
        }

        val xmlMatch = Regex("""<thought>([\s\S]*?)(?:</thought>|$)""", RegexOption.IGNORE_CASE).find(raw)
        if (xmlMatch != null) {
            return xmlMatch.groupValues[1].trim()
        }

        return ""
    }

    private fun extractPartialMessage(raw: String): String {
        // Extract from "message": "..." or "content": "..."
        val msgMatch = Regex(""""(?:message|content|response)"\s*:\s*"((?:\\.|[^"\\])*)""").find(raw)
        if (msgMatch != null) {
            return unescapeJsonString(msgMatch.groupValues[1])
        }

        val xmlMatch = Regex("""<message>([\s\S]*?)(?:</message>|$)""", RegexOption.IGNORE_CASE).find(raw)
        if (xmlMatch != null) {
            return xmlMatch.groupValues[1].trim()
        }

        // If it's plain text (not JSON)
        if (!raw.trimStart().startsWith("{")) {
            return raw.trim()
        }

        return ""
    }

    private fun unescapeJsonString(text: String): String {
        return text.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}
