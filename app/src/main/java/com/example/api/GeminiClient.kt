package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String // base64
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content,
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

// Agent response structure
@JsonClass(generateAdapter = true)
data class AgentFileAction(
    val type: String, // "create_or_write", "delete"
    val path: String,
    val content: String? = null
)

@JsonClass(generateAdapter = true)
data class AgentResponse(
    val thought: String,
    val actions: List<AgentFileAction>,
    val message: String
)

@JsonClass(generateAdapter = true)
data class ToolArguments(
    val path: String? = null,
    val content: String? = null,
    val oldPath: String? = null,
    val newPath: String? = null,
    val command: String? = null,
    val message: String? = null,
    val startLine: Int? = null,
    val endLine: Int? = null,
    val search: String? = null,
    val replace: String? = null,
    val query: String? = null,
    val destinationPath: String? = null,
    val destinationSearch: String? = null,
    val lineRange: String? = null,
    val prompt: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val format: String? = null
)

@JsonClass(generateAdapter = true)
data class ToolCallItem(
    val tool: String,
    val arguments: ToolArguments? = null
)

@JsonClass(generateAdapter = true)
data class ToolCallResponse(
    val thought: String? = null,
    val tool: String? = null, // Single call (legacy/simple)
    val arguments: ToolArguments? = null,
    val tools: List<ToolCallItem>? = null, // Support for multiple calls
    val finishReason: String? = null
)

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val MODEL_NAME = "gemini-2.0-flash" // Standard fast model

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.MINUTES)
        .readTimeout(20, TimeUnit.MINUTES)
        .writeTimeout(20, TimeUnit.MINUTES)
        .build()

    private fun cleanJsonString(raw: String): String {
        var text = raw.trim()
        
        // Try to find a markdown JSON code block first
        val jsonBlockStart = text.indexOf("```json")
        if (jsonBlockStart != -1) {
            val blockContentStart = jsonBlockStart + 7
            val jsonBlockEnd = text.indexOf("```", blockContentStart)
            if (jsonBlockEnd != -1) {
                text = text.substring(blockContentStart, jsonBlockEnd).trim()
            }
        } else {
            val codeBlockStart = text.indexOf("```")
            if (codeBlockStart != -1) {
                val blockContentStart = codeBlockStart + 3
                val codeBlockEnd = text.indexOf("```", blockContentStart)
                if (codeBlockEnd != -1) {
                    text = text.substring(blockContentStart, codeBlockEnd).trim()
                }
            }
        }
        
        // Try to extract the JSON object bounded by { and }
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1)
        }
        
        return text
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

    private fun parseFallbackToolCall(rawText: String, finishReason: String? = null): ToolCallResponse {
        val tool = extractField(rawText, "tool")
        val thought = extractField(rawText, "thought")
        val message = extractField(rawText, "message")
        val path = extractField(rawText, "path")
        val content = extractField(rawText, "content")
        val command = extractField(rawText, "command")
        
        if (tool != null) {
            return ToolCallResponse(
                thought = thought ?: "Parsed via regex fallback.",
                tool = tool,
                arguments = ToolArguments(
                    message = message,
                    path = path,
                    content = content,
                    command = command
                ),
                finishReason = finishReason
            )
        }
        
        val cleanedText = if (rawText.trim().startsWith("{") && rawText.trim().contains("\"message\"")) {
            message ?: rawText
        } else {
            rawText
        }

        return ToolCallResponse(
            thought = "Fallback: Plain-text response.",
            tool = "complete",
            arguments = ToolArguments(message = cleanedText),
            finishReason = finishReason
        )
    }

    private fun parseToolCallResponse(cleaned: String, rawText: String, finishReason: String? = null): ToolCallResponse {
        return try {
            val toolCallAdapter = moshi.adapter(ToolCallResponse::class.java).lenient()
            val parsed = toolCallAdapter.fromJson(cleaned)
            if (parsed != null) {
                if (finishReason != null) {
                    parsed.copy(finishReason = finishReason)
                } else {
                    parsed
                }
            } else {
                parseFallbackToolCall(rawText, finishReason)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON ToolCallResponse. Falling back to regex parser.", e)
            parseFallbackToolCall(rawText, finishReason)
        }
    }

    suspend fun generateAgentStep(
        apiKey: String,
        systemInstruction: String,
        conversationHistory: List<Content>,
        provider: String = "gemini",
        modelId: String = "gemini-2.0-flash",
        customBaseUrl: String? = null,
        useCustom: Boolean = false
    ): ToolCallResponse? = withContext(Dispatchers.IO) {
        val activeApiKey = apiKey.trim()
        if (activeApiKey.isEmpty()) {
            return@withContext ToolCallResponse(
                thought = "No API key found.",
                tool = "complete",
                arguments = ToolArguments(message = "API key is missing! Please configure your API key in the Settings to run the Vibe Coding Agent.")
            )
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()

        when {
            useCustom && (provider == "mistral" || provider == "openai" || provider == "custom") -> {
                val baseUrl = when {
                    provider == "mistral" -> "https://api.mistral.ai"
                    provider == "openai" -> "https://api.openai.com"
                    !customBaseUrl.isNullOrBlank() -> customBaseUrl.trimEnd('/')
                    else -> "https://api.openai.com"
                }
                
                val url = when {
                    baseUrl.contains("/chat/completions") -> baseUrl
                    baseUrl.endsWith("/v1") -> "$baseUrl/chat/completions"
                    else -> "$baseUrl/v1/chat/completions"
                }

                // Map conversation history to OpenAI message format
                val messages = mutableListOf<Map<String, Any>>()
                messages.add(mapOf("role" to "system", "content" to systemInstruction))
                conversationHistory.forEach { content ->
                    val textPart = content.parts.firstOrNull()?.text ?: ""
                    val role = if (content.role == "model") "assistant" else "user"
                    messages.add(mapOf("role" to role, "content" to textPart))
                }

                val bodyMap = mutableMapOf<String, Any>(
                    "model" to modelId,
                    "messages" to messages,
                    "temperature" to 0.4f
                )
                
                // Use response_format for OpenAI and Mistral to enforce JSON mode
                // Some custom providers might fail with response_format, so we only apply it to known compatible ones or if not Anthropic-like
                val supportsJsonMode = provider == "openai" || provider == "mistral" || 
                                      (provider == "custom" && !baseUrl.contains("anthropic") && !baseUrl.contains("groq"))
                
                if (supportsJsonMode) {
                    bodyMap["response_format"] = mapOf("type" to "json_object")
                }

                val bodyJson = moshi.adapter(Map::class.java).toJson(bodyMap)
                val body = bodyJson.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $activeApiKey")
                    .post(body)
                    .build()

                try {
                    var attempt = 0
                    val maxAttempts = 10
                    var response: okhttp3.Response? = null
                    var rawResponse: String? = null
                    var lastCode = 0

                    while (attempt < maxAttempts) {
                        try {
                            response?.close()
                            response = client.newCall(request).execute()
                            lastCode = response.code
                            rawResponse = response.body?.string()
                            Log.d(TAG, "$provider Raw Response code: $lastCode")

                             if (!response.isSuccessful) {
                                attempt++
                                if (attempt < maxAttempts) {
                                    val isRateLimit = (lastCode == 429)
                                    val backoff = if (isRateLimit) {
                                        val base = 2000L * (1 shl (attempt - 1))
                                        val jitter = (Math.random() * 500).toLong()
                                        base + jitter
                                    } else {
                                        1000L * attempt
                                    }
                                    Log.w(TAG, "API Error $lastCode. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                                    Thread.sleep(backoff)
                                    continue
                                }
                            }
                            break
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during $provider call execution", e)
                            attempt++
                            if (attempt < maxAttempts) {
                                val isRateLimit = e.message?.contains("429") == true || e.message?.lowercase()?.contains("rate limit") == true
                                val backoff = if (isRateLimit) {
                                    val base = 2000L * (1 shl (attempt - 1))
                                    val jitter = (Math.random() * 500).toLong()
                                    base + jitter
                                } else {
                                    1000L * attempt
                                }
                                Log.w(TAG, "API call threw exception. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                                Thread.sleep(backoff)
                                continue
                            } else {
                                throw e
                            }
                        }
                    }

                    if (response == null || !response.isSuccessful || rawResponse == null) {
                        Log.e(TAG, "$provider Error response: $rawResponse")
                        val errorMsg = try {
                            val errorMap = moshi.adapter(Map::class.java).fromJson(rawResponse ?: "")
                            val errorInner = errorMap?.get("error") as? Map<*, *>
                            errorInner?.get("message")?.toString() ?: rawResponse
                        } catch (e: Exception) {
                            rawResponse
                        }
                        
                        val isRateLimit = lastCode == 429 || (errorMsg ?: "").contains("rate limit", ignoreCase = true)
                        val advice = when {
                            isRateLimit -> {
                                "Rate limit exceeded (Code 429). The API provider is receiving too many requests. Please wait a moment before trying again, or reduce your request frequency."
                            }
                            lastCode == 504 -> {
                                "Gateway Time-out (Code 504). The API server/upstream proxy timed out because the request took too long to complete. Try reducing the query size, selecting a faster model, or retrying in a moment."
                            }
                            lastCode == 401 -> {
                                "Unauthorized (Code 401). Your API Key appears to be invalid or unauthorized. Please verify your credentials in Settings."
                            }
                            lastCode == 404 -> {
                                "Not Found (Code 404). Please verify that your Model Name and Base URL are correct and that the endpoint exists."
                            }
                            lastCode == 502 -> {
                                "Bad Gateway (Code 502). The upstream server is down or unreachable. Please try again later."
                            }
                            lastCode == 503 -> {
                                "Service Unavailable (Code 503). The server is temporarily overloaded or undergoing maintenance. Please try again in a few seconds."
                            }
                            else -> {
                                "Suggestion: Please verify that your API Key, Model Name, and Base URL are correct."
                            }
                        }

                        return@withContext ToolCallResponse(
                            thought = "$provider API call failed with status $lastCode.",
                            tool = "complete",
                            arguments = ToolArguments(message = "API Provider Error (Code $lastCode): $errorMsg\n\n$advice")
                        )
                    }

                    val responseMap = moshi.adapter(Map::class.java).fromJson(rawResponse) as? Map<*, *>
                    val choices = responseMap?.get("choices") as? List<*>
                    val choice = choices?.firstOrNull() as? Map<*, *>
                    val messageMap = choice?.get("message") as? Map<*, *>
                    val responseText = messageMap?.get("content") as? String

                    if (responseText == null) {
                        return@withContext ToolCallResponse(
                            thought = "Empty content from $provider response.",
                            tool = "complete",
                            arguments = ToolArguments(message = "Empty text content received from $provider model.")
                        )
                    }

                    val cleaned = cleanJsonString(responseText)
                    return@withContext parseToolCallResponse(cleaned, responseText)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during $provider API call", e)
                    return@withContext ToolCallResponse(
                        thought = "Exception caught.",
                        tool = "complete",
                        arguments = ToolArguments(message = "An error occurred during $provider communication: ${e.localizedMessage}")
                    )
                }
            }

            useCustom && provider == "claude" -> {
                val baseUrl = if (!customBaseUrl.isNullOrBlank()) customBaseUrl.trimEnd('/') else "https://api.anthropic.com"
                val url = "$baseUrl/v1/messages"

                // Map conversation history to Claude message format (Claude system prompt is in a separate parameter)
                val messages = mutableListOf<Map<String, Any>>()
                conversationHistory.forEach { content ->
                    val textPart = content.parts.firstOrNull()?.text ?: ""
                    val role = if (content.role == "model") "assistant" else "user"
                    messages.add(mapOf("role" to role, "content" to textPart))
                }

                val bodyMap = mapOf(
                    "model" to modelId,
                    "system" to systemInstruction,
                    "messages" to messages,
                    "max_tokens" to 4000,
                    "temperature" to 0.5f
                )

                val bodyJson = moshi.adapter(Map::class.java).toJson(bodyMap)
                val body = bodyJson.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .header("x-api-key", activeApiKey)
                    .header("anthropic-version", "2023-06-01")
                    .post(body)
                    .build()

                try {
                    var attempt = 0
                    val maxAttempts = 10
                    var response: okhttp3.Response? = null
                    var rawResponse: String? = null
                    var lastCode = 0

                    while (attempt < maxAttempts) {
                        try {
                            response?.close()
                            response = client.newCall(request).execute()
                            lastCode = response.code
                            rawResponse = response.body?.string()
                            Log.d(TAG, "Claude Raw Response code: $lastCode")

                             if (!response.isSuccessful) {
                                attempt++
                                if (attempt < maxAttempts) {
                                    val isRateLimit = (lastCode == 429)
                                    val backoff = if (isRateLimit) {
                                        val base = 2000L * (1 shl (attempt - 1))
                                        val jitter = (Math.random() * 500).toLong()
                                        base + jitter
                                    } else {
                                        1000L * attempt
                                    }
                                    Log.w(TAG, "Claude API Error $lastCode. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                                    Thread.sleep(backoff)
                                    continue
                                }
                            }
                            break
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during Claude execute", e)
                            attempt++
                            if (attempt < maxAttempts) {
                                val isRateLimit = e.message?.contains("429") == true || e.message?.lowercase()?.contains("rate limit") == true
                                val backoff = if (isRateLimit) {
                                    val base = 2000L * (1 shl (attempt - 1))
                                    val jitter = (Math.random() * 500).toLong()
                                    base + jitter
                                } else {
                                    1000L * attempt
                                }
                                Log.w(TAG, "Claude API call threw exception. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                                Thread.sleep(backoff)
                                continue
                            } else {
                                throw e
                            }
                        }
                    }

                    if (response == null || !response.isSuccessful || rawResponse == null) {
                        Log.e(TAG, "Claude Error response: $rawResponse")
                        val isRateLimit = lastCode == 429 || (rawResponse ?: "").contains("rate limit", ignoreCase = true)
                        val advice = when {
                            isRateLimit -> {
                                "Rate limit exceeded (Code 429). The API provider is receiving too many requests. Please wait a moment before trying again, or reduce your request frequency."
                            }
                            lastCode == 504 -> {
                                "Gateway Time-out (Code 504). The Claude API server or proxy timed out. Please wait a moment and try again."
                            }
                            lastCode == 401 -> {
                                "Unauthorized (Code 401). Your Claude API Key appears to be invalid. Please verify it in Settings."
                            }
                            lastCode == 404 -> {
                                "Not Found (Code 404). Please verify that your Model Name is correct and valid for Claude."
                            }
                            lastCode == 502 -> {
                                "Bad Gateway (Code 502). The Claude API server or gateway is currently unreachable. Please try again later."
                            }
                            lastCode == 503 -> {
                                "Service Unavailable (Code 503). The server is temporarily overloaded. Please try again in a moment."
                            }
                            else -> {
                                "Suggestion: Please verify that your API Key, Model Name, and Base URL are correct."
                            }
                        }

                        return@withContext ToolCallResponse(
                            thought = "Claude API call failed.",
                            tool = "complete",
                            arguments = ToolArguments(message = "Error calling Claude API (Code $lastCode): $rawResponse\n\n$advice")
                        )
                    }

                    val responseMap = moshi.adapter(Map::class.java).fromJson(rawResponse) as? Map<*, *>
                    val contentList = responseMap?.get("content") as? List<*>
                    val contentItem = contentList?.firstOrNull() as? Map<*, *>
                    val responseText = contentItem?.get("text") as? String

                    if (responseText == null) {
                        return@withContext ToolCallResponse(
                            thought = "Empty content from Claude response.",
                            tool = "complete",
                            arguments = ToolArguments(message = "Empty text content received from Claude model.")
                        )
                    }

                    val cleaned = cleanJsonString(responseText)
                    return@withContext parseToolCallResponse(cleaned, responseText)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during Claude API call", e)
                    return@withContext ToolCallResponse(
                        thought = "Exception caught.",
                        tool = "complete",
                        arguments = ToolArguments(message = "An error occurred during Claude communication: ${e.localizedMessage}")
                    )
                }
            }

            else -> {
                // Gemini flow (default or custom with custom endpoint/modelId)
                val baseUrl = if (useCustom && !customBaseUrl.isNullOrBlank()) customBaseUrl.trimEnd('/') else "https://generativelanguage.googleapis.com"
                val activeModel = if (useCustom && modelId.isNotBlank()) modelId else MODEL_NAME
                val url = "$baseUrl/v1beta/models/$activeModel:generateContent?key=$activeApiKey"

                val requestBodyData = GenerateContentRequest(
                    contents = conversationHistory,
                    systemInstruction = Content(parts = listOf(Part(text = systemInstruction))),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.5f
                    )
                )

                val requestAdapter = moshi.adapter(GenerateContentRequest::class.java)
                val jsonRequest = requestAdapter.toJson(requestBodyData)
                val body = jsonRequest.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                try {
                    var attempt = 0
                    val maxAttempts = 10
                    var response: okhttp3.Response? = null
                    var rawResponse: String? = null
                    var lastCode = 0

                    while (attempt < maxAttempts) {
                        try {
                            response?.close()
                            response = client.newCall(request).execute()
                            lastCode = response.code
                            rawResponse = response.body?.string()
                            Log.d(TAG, "Gemini Raw Response code: $lastCode")

                             if (!response.isSuccessful) {
                                attempt++
                                if (attempt < maxAttempts) {
                                    val isRateLimit = (lastCode == 429)
                                    val backoff = if (isRateLimit) {
                                        val base = 2000L * (1 shl (attempt - 1))
                                        val jitter = (Math.random() * 500).toLong()
                                        base + jitter
                                    } else {
                                        1000L * attempt
                                    }
                                    Log.w(TAG, "Gemini API Error $lastCode. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                                    Thread.sleep(backoff)
                                    continue
                                }
                            }
                            break
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during Gemini execute", e)
                            attempt++
                            if (attempt < maxAttempts) {
                                val isRateLimit = e.message?.contains("429") == true || e.message?.lowercase()?.contains("rate limit") == true
                                val backoff = if (isRateLimit) {
                                    val base = 2000L * (1 shl (attempt - 1))
                                    val jitter = (Math.random() * 500).toLong()
                                    base + jitter
                                } else {
                                    1000L * attempt
                                }
                                Log.w(TAG, "Gemini API call threw exception. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                                Thread.sleep(backoff)
                                continue
                            } else {
                                throw e
                            }
                        }
                    }

                    if (response == null || !response.isSuccessful || rawResponse == null) {
                        Log.e(TAG, "Gemini Error response: $rawResponse")
                        val isRateLimit = lastCode == 429 || (rawResponse ?: "").contains("quota", ignoreCase = true) || (rawResponse ?: "").contains("rate limit", ignoreCase = true)
                        val advice = when {
                            isRateLimit -> {
                                "Rate limit or quota exceeded (Code $lastCode). The Gemini API server is receiving too many requests. Please wait a moment before trying again, or reduce your request frequency."
                            }
                            lastCode == 504 -> {
                                "Gateway Time-out (Code 504). The Gemini server or gateway timed out. Please wait a moment and try again."
                            }
                            lastCode == 401 -> {
                                "Unauthorized (Code 401). Your Gemini API Key is invalid or unauthorized. Please check your key in Settings."
                            }
                            lastCode == 404 -> {
                                "Not Found (Code 404). Please verify that your Model Name and Base URL are correct."
                            }
                            lastCode == 502 -> {
                                "Bad Gateway (Code 502). The Gemini API server or gateway is currently unreachable. Please try again later."
                            }
                            lastCode == 503 -> {
                                "Service Unavailable (Code 503). The server is temporarily overloaded or undergoing maintenance. Please try again in a moment."
                            }
                            else -> {
                                "Suggestion: Please verify that your API Key, Model Name, and Base URL are correct."
                            }
                        }

                        return@withContext ToolCallResponse(
                            thought = "Gemini API call failed.",
                            tool = "complete",
                            arguments = ToolArguments(message = "Error calling Gemini API: Code $lastCode. Response: $rawResponse\n\n$advice")
                        )
                    }

                    val responseAdapter = moshi.adapter(GenerateContentResponse::class.java)
                    val responseObj = responseAdapter.fromJson(rawResponse)
                    val candidate = responseObj?.candidates?.firstOrNull()
                    val responseText = candidate?.content?.parts?.firstOrNull()?.text
                    val finishReason = candidate?.finishReason

                    if (responseText == null) {
                        Log.e(TAG, "Empty text from candidate")
                        return@withContext ToolCallResponse(
                            thought = "Empty response received.",
                            tool = "complete",
                            arguments = ToolArguments(message = "The AI did not return a valid response. Please check your connection, API key, or custom settings.")
                        )
                    }

                    Log.d(TAG, "Response Text: $responseText")
                    val cleaned = cleanJsonString(responseText)
                    return@withContext parseToolCallResponse(cleaned, responseText, finishReason)

                } catch (e: Exception) {
                    Log.e(TAG, "Exception during Gemini API call", e)
                    return@withContext ToolCallResponse(
                        thought = "Exception caught.",
                        tool = "complete",
                        arguments = ToolArguments(message = "An error occurred during communication: ${e.localizedMessage}")
                    )
                }
            }
        }
    }

    suspend fun generateWorkspaceUpdate(
        apiKey: String,
        systemInstruction: String,
        conversationHistory: List<Content>
    ): AgentResponse? = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext AgentResponse(
                thought = "No API key found.",
                actions = emptyList(),
                message = "API key is missing! Please enter your GEMINI_API_KEY securely into the Secrets panel in AI Studio to run the Vibe Coding Agent."
            )
        }

        val requestBodyData = GenerateContentRequest(
            contents = conversationHistory,
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.5f
            )
        )

        val requestAdapter = moshi.adapter(GenerateContentRequest::class.java)
        val jsonRequest = requestAdapter.toJson(requestBodyData)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonRequest.toRequestBody(mediaType)

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$trimmedKey"

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        var attempt = 0
        val maxAttempts = 10
        var response: okhttp3.Response? = null
        var rawResponse: String? = null
        var lastCode = 0

        try {
            while (attempt < maxAttempts) {
                try {
                    response?.close()
                    response = client.newCall(request).execute()
                    lastCode = response.code
                    rawResponse = response.body?.string()
                    Log.d(TAG, "Direct Gemini raw response code: $lastCode")

                    if (!response.isSuccessful) {
                        attempt++
                        if (attempt < maxAttempts) {
                            val isRateLimit = (lastCode == 429)
                            val backoff = if (isRateLimit) {
                                val base = 2000L * (1 shl (attempt - 1))
                                val jitter = (Math.random() * 500).toLong()
                                base + jitter
                            } else {
                                1000L * attempt
                            }
                            Log.w(TAG, "Direct Gemini API Error $lastCode. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                            Thread.sleep(backoff)
                            continue
                        }
                    }
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during direct Gemini execute", e)
                    attempt++
                    if (attempt < maxAttempts) {
                        val isRateLimit = e.message?.contains("429") == true || e.message?.lowercase()?.contains("rate limit") == true
                        val backoff = if (isRateLimit) {
                            val base = 2000L * (1 shl (attempt - 1))
                            val jitter = (Math.random() * 500).toLong()
                            base + jitter
                        } else {
                            1000L * attempt
                        }
                        Log.w(TAG, "Direct Gemini call threw exception. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                        Thread.sleep(backoff)
                        continue
                    } else {
                        throw e
                    }
                }
            }

            if (response == null || !response.isSuccessful || rawResponse == null) {
                Log.e(TAG, "Error response: $rawResponse")
                return@withContext AgentResponse(
                    thought = "API call failed.",
                    actions = emptyList(),
                    message = "Error calling Gemini API: Code $lastCode. Please ensure your API key in AI Studio secrets is active and correct."
                )
            }

            val responseAdapter = moshi.adapter(GenerateContentResponse::class.java)
            val responseObj = responseAdapter.fromJson(rawResponse)
            val responseText = responseObj?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (responseText == null) {
                Log.e(TAG, "Empty text from candidate")
                return@withContext AgentResponse(
                    thought = "Empty response received.",
                    actions = emptyList(),
                    message = "The AI did not return a valid response. Please try reframing your prompt."
                )
            }

            Log.d(TAG, "Response Text: $responseText")
            val agentResponseAdapter = moshi.adapter(AgentResponse::class.java)
            return@withContext agentResponseAdapter.fromJson(responseText)

        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini API call", e)
            return@withContext AgentResponse(
                thought = "Exception caught.",
                actions = emptyList(),
                message = "An error occurred during communication: ${e.localizedMessage}"
            )
        }
    }
}
