package com.example.api

import com.example.agent.ReasoningEffort
import com.example.agent.ReasoningEffortEngine

/**
 * ApiRequestCompatibilityEngine
 * Normalizes request payloads across Gemini, OpenAI, OpenRouter, Groq, Mistral,
 * Anthropic, and local/custom LLMs to prevent HTTP 400 Parameter Mismatch and
 * Thinking Config errors.
 */
object ApiRequestCompatibilityEngine {

    /**
     * Builds safe Gemini ThinkingConfig.
     * CRITICAL: Gemini API strictly rejects requests with:
     * "You can only set only one of thinking budget and thinking level."
     * Therefore, only thinkingBudget is supplied and thinkingLevel remains null.
     */
    fun buildGeminiThinkingConfig(
        modelId: String,
        effort: ReasoningEffort,
        disableThinking: Boolean = false
    ): ThinkingConfig? {
        if (disableThinking) return null

        val lowerModel = modelId.lowercase()
        // Gemini 1.5, 1.0 and vision legacy models do not support thinkingConfig
        if (lowerModel.contains("1.5") || lowerModel.contains("1.0") || lowerModel.contains("pro-vision")) {
            return null
        }

        val budget = ReasoningEffortEngine.getGeminiThinkingBudget(effort)
        return ThinkingConfig(
            thinkingBudget = budget,
            includeThoughts = true,
            thinkingLevel = null // Strictly null to comply with Gemini API contract
        )
    }

    /**
     * Detects if Gemini returned 400 due to thinking configuration or invalid parameter
     */
    fun isGeminiThinkingConfigError(code: Int, responseBody: String?): Boolean {
        if (code != 400 || responseBody.isNullOrBlank()) return false
        val lower = responseBody.lowercase()
        return lower.contains("thinking budget") ||
                lower.contains("thinking level") ||
                lower.contains("thinkingconfig") ||
                lower.contains("thinking_budget") ||
                lower.contains("thinking_level") ||
                lower.contains("unknown field: thinking")
    }

    /**
     * Builds OpenAI-compatible request payload with safe parameter validation
     */
    fun buildOpenAiCompatiblePayload(
        modelId: String,
        messages: List<Map<String, Any>>,
        effort: ReasoningEffort,
        provider: String,
        baseUrl: String,
        safeFallbackMode: Boolean = false
    ): Map<String, Any> {
        val lowerModel = modelId.lowercase()
        val isOSeries = lowerModel.startsWith("o1") || lowerModel.startsWith("o3") || lowerModel.startsWith("o4")
        val isReasoningModel = isOSeries || lowerModel.contains("r1") || lowerModel.contains("reasoner") || lowerModel.contains("thinking")

        val payload = mutableMapOf<String, Any>(
            "model" to modelId,
            "messages" to messages
        )

        if (safeFallbackMode) {
            // Strip advanced flags to recover from 400 Bad Request
            if (!isOSeries) {
                payload["temperature"] = 0.5f
            }
            return payload
        }

        if (isOSeries) {
            payload["reasoning_effort"] = effort.reasoningEffortParam
            // Note: OpenAI o1/o3 reject temperature parameter
        } else {
            payload["temperature"] = if (effort == ReasoningEffort.MAX) 0.2f else 0.4f

            // OpenRouter reasoning parameter only for known reasoning models
            if (provider == "openrouter" && isReasoningModel) {
                payload["reasoning"] = mapOf("effort" to effort.reasoningEffortParam)
            }
        }

        // Only enforce json_object response_format on verified OpenAI models (gpt-4o, gpt-4o-mini, gpt-3.5-turbo)
        // Many open models on Groq, OpenRouter, Mistral, Ollama reject response_format with HTTP 400
        val isVerifiedOpenAiModel = provider == "openai" && (lowerModel.contains("gpt-4") || lowerModel.contains("gpt-3.5"))
        if (isVerifiedOpenAiModel && !isOSeries) {
            payload["response_format"] = mapOf("type" to "json_object")
        }

        return payload
    }

    /**
     * Detects if an HTTP 400 response from custom providers (Groq, OpenRouter, Mistral, OpenAI, etc.)
     * was caused by unsupported parameters like response_format, temperature, or reasoning.
     */
    fun isProviderParameterError(code: Int, responseBody: String?): Boolean {
        if (code != 400 || responseBody.isNullOrBlank()) return false
        val lower = responseBody.lowercase()
        return lower.contains("response_format") ||
                lower.contains("temperature") ||
                lower.contains("reasoning_effort") ||
                lower.contains("reasoning") ||
                lower.contains("thinking") ||
                lower.contains("unrecognized parameter") ||
                lower.contains("unknown parameter") ||
                lower.contains("unsupported parameter") ||
                lower.contains("extra_forbidden") ||
                lower.contains("schema")
    }
}
