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
    val parts: List<Part> = emptyList()
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
    val content: Content? = null,
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
data class EditChunk(
    val search: String? = null,
    val replace: String? = null,
    val targetContent: String? = null,
    val replacementContent: String? = null
)

@JsonClass(generateAdapter = true)
data class ReadRangeItem(
    val startLine: Int? = null,
    val endLine: Int? = null,
    val range: String? = null
)

@JsonClass(generateAdapter = true)
data class ToolArguments(
    val path: String? = null,
    val targetFile: String? = null,
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
    val format: String? = null,
    val chunks: List<EditChunk>? = null,
    val replacementChunks: List<EditChunk>? = null,
    val edits: List<EditChunk>? = null,
    val ranges: List<ReadRangeItem>? = null,
    val rangeList: List<String>? = null,
    val mcpServerId: String? = null,
    val mcpServerName: String? = null,
    val toolName: String? = null,
    val mcpArgsJson: String? = null,
    val resourceUri: String? = null,
    val url: String? = null,
    val selector: String? = null,
    val properties: String? = null,
    val script: String? = null,
    val direction: String? = null,
    val amount: Int? = null,
    val text: String? = null,
    val targetImage: String? = null
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

    var onRetryListener: ((provider: String, attempt: Int, maxAttempts: Int, error: String) -> Unit)? = null
    var onRetrySuccessListener: (() -> Unit)? = null

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
        
        // Strip or extract inner content from <tool_call> ... </tool_call> tags if present
        if (text.contains("<tool_call>", ignoreCase = true)) {
            val startIdx = text.indexOf("<tool_call>", ignoreCase = true)
            val endIdx = text.indexOf("</tool_call>", startIdx, ignoreCase = true)
            text = if (endIdx != -1) {
                text.substring(startIdx + 11, endIdx).trim()
            } else {
                text.replace(Regex("(?i)</?tool_call>"), "").trim()
            }
        }
        
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

    private fun extractXmlField(text: String, tag: String): String? {
        val pattern = java.util.regex.Pattern.compile("<$tag>(.*?)</$tag>", java.util.regex.Pattern.DOTALL or java.util.regex.Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }
        return null
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

    private fun parseArgKeyXmlToolCall(rawText: String, finishReason: String? = null): ToolCallResponse? {
        if (!rawText.contains("<arg_key>", ignoreCase = true) && !rawText.contains("<arg_value>", ignoreCase = true)) {
            return null
        }

        var tool: String? = null
        val toolRegex = Regex("""(?:\.|\b)([a_zA-Z0-9_]{2,30})\s*<arg_key>""", RegexOption.IGNORE_CASE)
        val toolMatch = toolRegex.find(rawText)
        if (toolMatch != null) {
            tool = toolMatch.groupValues[1].lowercase().trim()
        }

        if (tool == null) {
            tool = extractField(rawText, "tool")
                ?: extractXmlField(rawText, "tool")
                ?: extractXmlField(rawText, "tool_name")
                ?: extractXmlField(rawText, "name")
        }

        val argMap = mutableMapOf<String, String>()
        val pairRegex = Regex("""<arg_key>(.*?)</arg_key>\s*<arg_value>(.*?)</arg_value>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        pairRegex.findAll(rawText).forEach { matchResult ->
            val key = matchResult.groupValues[1].trim().lowercase()
            val value = matchResult.groupValues[2]
            argMap[key] = value
        }

        if (tool != null || argMap.isNotEmpty()) {
            val inferredTool = tool ?: "read_file"
            val path = argMap["path"] ?: argMap["file"] ?: argMap["filepath"]
            val content = argMap["content"] ?: argMap["code"] ?: argMap["text"]
            val search = argMap["search"] ?: argMap["target"]
            val replace = argMap["replace"] ?: argMap["replacement"]
            val command = argMap["command"] ?: argMap["cmd"]
            val message = argMap["message"] ?: argMap["msg"] ?: argMap["reasoning"]
            val query = argMap["query"]
            val destPath = argMap["destinationpath"] ?: argMap["dest"]
            val destSearch = argMap["destinationsearch"]
            val lineRange = argMap["linerange"] ?: argMap["range"]
            val startLine = argMap["startline"]?.toIntOrNull()
            val endLine = argMap["endline"]?.toIntOrNull()
            val prompt = argMap["prompt"]
            val url = argMap["url"] ?: argMap["link"] ?: argMap["href"]
            val selector = argMap["selector"] ?: argMap["css"] ?: argMap["target"]
            val properties = argMap["properties"] ?: argMap["props"] ?: argMap["property"]
            val script = argMap["script"] ?: argMap["code"] ?: argMap["js"]
            val direction = argMap["direction"] ?: argMap["dir"]
            val amount = argMap["amount"]?.toIntOrNull()
            val text = argMap["text"] ?: argMap["value"]
            val targetImage = argMap["targetimage"] ?: argMap["image"]

            val thoughtText = rawText.substringBefore("<arg_key>").substringBefore(".$inferredTool").replace(Regex("<[^>]+>"), "").trim()

            return ToolCallResponse(
                thought = if (thoughtText.isNotBlank()) thoughtText else "Parsed from arg_key tags.",
                tool = inferredTool,
                arguments = ToolArguments(
                    path = path,
                    content = content,
                    search = search,
                    replace = replace,
                    command = command,
                    message = message,
                    query = query,
                    destinationPath = destPath,
                    destinationSearch = destSearch,
                    lineRange = lineRange,
                    startLine = startLine,
                    endLine = endLine,
                    prompt = prompt,
                    url = url,
                    selector = selector,
                    properties = properties,
                    script = script,
                    direction = direction,
                    amount = amount,
                    text = text,
                    targetImage = targetImage,
                    chunks = com.example.agent.MultiEditChunkParser.parseChunksFromRawText(rawText).ifEmpty { null },
                    replacementChunks = com.example.agent.MultiEditChunkParser.parseChunksFromRawText(rawText).ifEmpty { null }
                ),
                finishReason = finishReason
            )
        }
        return null
    }

    private fun parseFallbackToolCall(rawText: String, finishReason: String? = null): ToolCallResponse {
        val argKeyParsed = parseArgKeyXmlToolCall(rawText, finishReason)
        if (argKeyParsed != null) {
            return argKeyParsed
        }

        val tool = extractField(rawText, "tool") 
            ?: extractXmlField(rawText, "tool")
            ?: extractXmlField(rawText, "tool_name")
            ?: extractXmlField(rawText, "name")
            
        val thought = extractField(rawText, "thought") ?: extractXmlField(rawText, "thought")
        val message = extractField(rawText, "message") ?: extractXmlField(rawText, "message")
        val path = extractField(rawText, "path") ?: extractXmlField(rawText, "path") ?: extractField(rawText, "targetFile") ?: extractXmlField(rawText, "targetFile")
        val content = extractField(rawText, "content") ?: extractXmlField(rawText, "content")
        val command = extractField(rawText, "command") ?: extractXmlField(rawText, "command")
        val search = extractField(rawText, "search") ?: extractXmlField(rawText, "search") ?: extractField(rawText, "targetContent") ?: extractXmlField(rawText, "targetContent")
        val replace = extractField(rawText, "replace") ?: extractXmlField(rawText, "replace") ?: extractField(rawText, "replacementContent") ?: extractXmlField(rawText, "replacementContent")
        val query = extractField(rawText, "query") ?: extractXmlField(rawText, "query")
        val parsedChunks = com.example.agent.MultiEditChunkParser.parseChunksFromRawString(rawText).ifEmpty {
            com.example.agent.MultiEditChunkParser.parseChunksFromRawText(rawText)
        }
        
        if (tool != null) {
            return ToolCallResponse(
                thought = thought ?: "Parsed via fallback parser.",
                tool = tool,
                arguments = ToolArguments(
                    message = message,
                    path = path,
                    targetFile = path,
                    content = content,
                    command = command,
                    search = search,
                    replace = replace,
                    query = query,
                    chunks = parsedChunks.ifEmpty { null },
                    replacementChunks = parsedChunks.ifEmpty { null }
                ),
                finishReason = finishReason
            )
        }
        
        val cleanedText = if (rawText.trim().startsWith("{") && rawText.trim().contains("\"message\"")) {
            message ?: stripToolCallXml(rawText)
        } else {
            stripToolCallXml(rawText)
        }

        return ToolCallResponse(
            thought = "Fallback: Plain-text response.",
            tool = "complete",
            arguments = ToolArguments(message = cleanedText),
            finishReason = finishReason
        )
    }

    private fun parseToolCallResponse(cleaned: String, rawText: String, finishReason: String? = null): ToolCallResponse {
        val argKeyParsed = parseArgKeyXmlToolCall(rawText, finishReason)
        if (argKeyParsed != null) {
            return argKeyParsed
        }

        return try {
            val toolCallAdapter = moshi.adapter(ToolCallResponse::class.java).lenient()
            val parsed = toolCallAdapter.fromJson(cleaned)
            if (parsed != null && (parsed.tool != null || parsed.tools != null)) {
                val enhancedTools = parsed.tools?.map { item ->
                    if (item.tool in listOf("multi_edit_file", "multi_edit", "multi_patch")) {
                        val c = com.example.agent.MultiEditChunkParser.resolveChunks(item.arguments, rawText)
                        item.copy(arguments = item.arguments?.copy(chunks = c, replacementChunks = c))
                    } else item
                }
                val enhancedArgs = if (parsed.tool in listOf("multi_edit_file", "multi_edit", "multi_patch")) {
                    val c = com.example.agent.MultiEditChunkParser.resolveChunks(parsed.arguments, rawText)
                    parsed.arguments?.copy(chunks = c, replacementChunks = c)
                } else parsed.arguments

                parsed.copy(
                    arguments = enhancedArgs,
                    tools = enhancedTools,
                    finishReason = finishReason ?: parsed.finishReason
                )
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
            useCustom && (provider == "cloudflare" || provider == "cloudrafer") -> {
                val baseUrl = if (!customBaseUrl.isNullOrBlank()) customBaseUrl.trimEnd('/') else "https://api.cloudflare.com/client/v4/accounts/YOUR_ACCOUNT_ID/ai/run"
                val url = if (baseUrl.endsWith("/")) "$baseUrl$modelId" else "$baseUrl/$modelId"

                val messages = mutableListOf<Map<String, Any>>()
                messages.add(mapOf("role" to "system", "content" to systemInstruction))
                conversationHistory.forEach { content ->
                    val textPart = content.parts.firstOrNull()?.text ?: ""
                    val role = if (content.role == "model") "assistant" else "user"
                    messages.add(mapOf("role" to role, "content" to textPart))
                }

                val bodyMap = mapOf(
                    "messages" to messages
                )

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
                            Log.d(TAG, "Cloudflare Raw Response code: $lastCode")

                             if (!response.isSuccessful) {
                                attempt++
                                if (attempt < maxAttempts) {
                                    val isTransientError = (lastCode == 429 || lastCode == 503 || lastCode == 502 || lastCode == 504) || (rawResponse ?: "").contains("quota", ignoreCase = true) || (rawResponse ?: "").contains("rate limit", ignoreCase = true) || (rawResponse ?: "").contains("overloaded", ignoreCase = true) || (rawResponse ?: "").contains("unavailable", ignoreCase = true) || (rawResponse ?: "").contains("RESOURCE_EXHAUSTED", ignoreCase = true) || (rawResponse ?: "").contains("exhausted", ignoreCase = true) || (rawResponse ?: "").contains("503", ignoreCase = true) || (rawResponse ?: "").contains("429", ignoreCase = true)
                                    val isRateLimit = lastCode == 429 || (rawResponse ?: "").contains("rate limit", ignoreCase = true) || (rawResponse ?: "").contains("quota", ignoreCase = true) || (rawResponse ?: "").contains("RESOURCE_EXHAUSTED", ignoreCase = true) || (rawResponse ?: "").contains("exhausted", ignoreCase = true)
                                    val backoff = if (isRateLimit) {
                                        val base = Math.min(5000L * (1 shl (attempt - 1)), 60000L)
                                        val jitter = (Math.random() * 1000).toLong()
                                        base + jitter
                                    } else if (isTransientError) {
                                        val base = Math.min(3000L * (1 shl (attempt - 1)), 60000L)
                                        val jitter = (Math.random() * 1000).toLong()
                                        base + jitter
                                    } else {
                                        1000L * attempt
                                    }
                                    val errStr = "API Error $lastCode"
                                    onRetryListener?.invoke("Cloudflare", attempt, maxAttempts, errStr)
                                    Log.w(TAG, "Cloudflare API Error $lastCode. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                                    Thread.sleep(backoff)
                                    continue
                                }
                            }
                            if (attempt > 0 && response.isSuccessful) {
                                onRetrySuccessListener?.invoke()
                            }
                            break
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during Cloudflare execute", e)
                            attempt++
                            if (attempt < maxAttempts) {
                                val msg = e.message?.lowercase() ?: ""
                                val isRateLimit = msg.contains("429") || msg.contains("rate limit") || msg.contains("quota") || msg.contains("exhausted")
                                val isTransientError = isRateLimit || msg.contains("503") || msg.contains("502") || msg.contains("504") || msg.contains("overloaded") || msg.contains("unavailable")
                                val backoff = if (isRateLimit) {
                                    val base = Math.min(5000L * (1 shl (attempt - 1)), 60000L)
                                    val jitter = (Math.random() * 1000).toLong()
                                    base + jitter
                                } else if (isTransientError) {
                                    val base = 2000L * (1 shl (attempt - 1))
                                    val jitter = (Math.random() * 500).toLong()
                                    base + jitter
                                } else {
                                    1000L * attempt
                                }
                                val errStr = e.message ?: "Network Exception"
                                onRetryListener?.invoke("Cloudflare", attempt, maxAttempts, errStr)
                                Log.w(TAG, "Cloudflare API call threw exception. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                                Thread.sleep(backoff)
                                continue
                            } else {
                                throw e
                            }
                        }
                    }

                    if (response == null || !response.isSuccessful || rawResponse == null) {
                        Log.e(TAG, "Cloudflare Error response: $rawResponse")
                        return@withContext ToolCallResponse(
                            thought = "Cloudflare API call failed with status $lastCode.",
                            tool = "complete",
                            arguments = ToolArguments(message = "Cloudflare API Provider Error (Code $lastCode): $rawResponse")
                        )
                    }

                    val responseMap = moshi.adapter(Map::class.java).fromJson(rawResponse) as? Map<*, *>
                    val resultObj = responseMap?.get("result") as? Map<*, *>
                    val responseText = resultObj?.get("response") as? String

                    if (responseText == null) {
                        return@withContext ToolCallResponse(
                            thought = "Empty content from Cloudflare response.",
                            tool = "complete",
                            arguments = ToolArguments(message = "Empty text content received from Cloudflare model. Response was: $rawResponse")
                        )
                    }

                    val cleaned = cleanJsonString(responseText)
                    return@withContext parseToolCallResponse(cleaned, responseText)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during Cloudflare API call", e)
                    return@withContext ToolCallResponse(
                        thought = "Exception caught.",
                        tool = "complete",
                        arguments = ToolArguments(message = "An error occurred during Cloudflare communication: ${e.localizedMessage}")
                    )
                }
            }

            useCustom && (provider == "mistral" || provider == "openai" || provider == "custom" || provider == "groq" || provider == "cohere" || provider == "ollama_cloud" || provider == "ollama" || provider == "openrouter" || provider == "opencode_zen" || provider == "opencode" || provider == "cline") -> {
                val rawBase = when {
                    !customBaseUrl.isNullOrBlank() -> customBaseUrl.trim()
                    provider == "cline" -> "https://api.cline.bot/v1"
                    provider == "opencode_zen" || provider == "opencode" -> "https://opencode.ai/zen/v1"
                    provider == "mistral" -> "https://api.mistral.ai"
                    provider == "openai" -> "https://api.openai.com"
                    provider == "groq" -> "https://api.groq.com/openai"
                    provider == "cohere" -> "https://api.cohere.com"
                    provider == "openrouter" -> "https://openrouter.ai/api"
                    provider == "ollama_cloud" || provider == "ollama" -> "https://api.ollama.com"
                    else -> "https://api.openai.com"
                }

                val baseUrl = if (!rawBase.startsWith("http://") && !rawBase.startsWith("https://")) {
                    "https://${rawBase.trimEnd('/')}"
                } else {
                    rawBase.trimEnd('/')
                }
                
                val url = when {
                    baseUrl.contains("/chat/completions") || baseUrl.contains("/completions") -> baseUrl
                    baseUrl.endsWith("/v1") -> "$baseUrl/chat/completions"
                    baseUrl.startsWith("http://") || baseUrl.startsWith("https://") -> {
                        val path = try { java.net.URI(baseUrl).path } catch (e: Exception) { "" }
                        if (path.isNullOrBlank() || path == "/") {
                            "$baseUrl/v1/chat/completions"
                        } else if (baseUrl.endsWith("/v1")) {
                            "$baseUrl/chat/completions"
                        } else {
                            baseUrl
                        }
                    }
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
                
                // Use response_format for OpenAI, Mistral and Groq to enforce JSON mode
                val supportsJsonMode = provider == "openai" || provider == "mistral" || provider == "groq" || provider == "openrouter" ||
                                      (provider == "custom" && !baseUrl.contains("anthropic") && !baseUrl.contains("groq"))
                
                if (supportsJsonMode) {
                    bodyMap["response_format"] = mapOf("type" to "json_object")
                }

                val bodyJson = moshi.adapter(Map::class.java).lenient().toJson(bodyMap)
                val body = bodyJson.toRequestBody(mediaType)

                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $activeApiKey")
                    .post(body)

                if (provider == "openrouter") {
                    requestBuilder.header("HTTP-Referer", "https://ai.studio/build")
                    requestBuilder.header("X-Title", "AI Studio Android")
                }

                val request = requestBuilder.build()

                val modelDisplayName = if (modelId.isNotBlank()) modelId else when (provider.lowercase()) {
                    "cline" -> "Cline"
                    "opencode_zen", "opencode" -> "OpenCode Zen"
                    "ollama_cloud", "ollama" -> "Ollama"
                    "openai" -> "OpenAI"
                    "mistral" -> "Mistral"
                    "groq" -> "Groq"
                    "cohere" -> "Cohere"
                    "openrouter" -> "OpenRouter"
                    "cloudflare" -> "Cloudflare"
                    else -> provider
                }

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
                                    val isTransientError = (lastCode == 429 || lastCode == 503 || lastCode == 502 || lastCode == 504) || (rawResponse ?: "").contains("quota", ignoreCase = true) || (rawResponse ?: "").contains("rate limit", ignoreCase = true) || (rawResponse ?: "").contains("overloaded", ignoreCase = true) || (rawResponse ?: "").contains("unavailable", ignoreCase = true) || (rawResponse ?: "").contains("RESOURCE_EXHAUSTED", ignoreCase = true) || (rawResponse ?: "").contains("exhausted", ignoreCase = true) || (rawResponse ?: "").contains("503", ignoreCase = true) || (rawResponse ?: "").contains("429", ignoreCase = true)
                                    val isRateLimit = lastCode == 429 || (rawResponse ?: "").contains("rate limit", ignoreCase = true) || (rawResponse ?: "").contains("quota", ignoreCase = true) || (rawResponse ?: "").contains("RESOURCE_EXHAUSTED", ignoreCase = true) || (rawResponse ?: "").contains("exhausted", ignoreCase = true)
                                    val backoff = if (isRateLimit) {
                                        val base = Math.min(5000L * (1 shl (attempt - 1)), 60000L)
                                        val jitter = (Math.random() * 1000).toLong()
                                        base + jitter
                                    } else if (isTransientError) {
                                        val base = Math.min(3000L * (1 shl (attempt - 1)), 60000L)
                                        val jitter = (Math.random() * 1000).toLong()
                                        base + jitter
                                    } else {
                                        1000L * attempt
                                    }
                                    val errStr = if (isRateLimit) "Rate Limit (429)" else "API Error $lastCode"
                                    onRetryListener?.invoke(modelDisplayName, attempt, maxAttempts, errStr)
                                    Log.w(TAG, "$provider API Error $lastCode. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                                    Thread.sleep(backoff)
                                    continue
                                }
                            }
                            if (attempt > 0 && response.isSuccessful) {
                                onRetrySuccessListener?.invoke()
                            }
                            break
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during $provider call execution", e)
                            attempt++
                            if (attempt < maxAttempts) {
                                val msg = e.message?.lowercase() ?: ""
                                val isRateLimit = msg.contains("429") || msg.contains("rate limit") || msg.contains("quota") || msg.contains("exhausted")
                                val isTransientError = isRateLimit || msg.contains("503") || msg.contains("502") || msg.contains("504") || msg.contains("overloaded") || msg.contains("unavailable") || msg.contains("unable to resolve host") || msg.contains("timeout")
                                val backoff = if (isRateLimit) {
                                    val base = Math.min(5000L * (1 shl (attempt - 1)), 60000L)
                                    val jitter = (Math.random() * 1000).toLong()
                                    base + jitter
                                } else if (isTransientError) {
                                    val base = 2500L * (1 shl (attempt - 1))
                                    val jitter = (Math.random() * 500).toLong()
                                    base + jitter
                                } else {
                                    1000L * attempt
                                }
                                val errStr = e.message ?: "Network Exception"
                                onRetryListener?.invoke(modelDisplayName, attempt, maxAttempts, errStr)
                                Log.w(TAG, "$provider API call threw exception. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
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
                            val errorMap = moshi.adapter(Map::class.java).lenient().fromJson(rawResponse ?: "")
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

                    // Use robust CustomModelResponseParser to handle string, array content blocks, reasoning_content, and tool calls
                    val extracted = CustomModelResponseParser.extractContentOrTool(rawResponse, provider)
                    if (extracted.toolCallResponse != null) {
                        return@withContext extracted.toolCallResponse
                    }

                    val responseText = extracted.text
                    if (responseText == null) {
                        // Fallback: If rawResponse is non-empty and contains XML or JSON tool calls directly
                        if (!rawResponse.isNullOrBlank() && (rawResponse.contains("<tool") || rawResponse.contains("<arg_key") || rawResponse.contains("\"tool\""))) {
                            val fallbackParsed = parseFallbackToolCall(rawResponse)
                            return@withContext fallbackParsed
                        }

                        // Last resort fallback: check if rawResponse itself has message or text
                        val fallbackText = rawResponse.trim()
                        if (fallbackText.isNotBlank() && !fallbackText.startsWith("{") && !fallbackText.startsWith("[")) {
                            return@withContext parseToolCallResponse(cleanJsonString(fallbackText), fallbackText)
                        }

                        return@withContext ToolCallResponse(
                            thought = "Empty content from $provider response.",
                            tool = "complete",
                            arguments = ToolArguments(message = "Received response without text content from $provider model. Raw response: ${rawResponse.take(250)}")
                        )
                    }

                    val cleaned = cleanJsonString(responseText)
                    return@withContext parseToolCallResponse(cleaned, responseText)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during $provider API call", e)
                    return@withContext ToolCallResponse(
                        thought = "Exception caught.",
                        tool = "complete",
                        arguments = ToolArguments(message = "An error occurred during $provider communication: ${formatNetworkError(e)}")
                    )
                }
            }

            useCustom && provider == "claude" -> {
                val baseUrl = if (!customBaseUrl.isNullOrBlank()) customBaseUrl.trimEnd('/') else "https://api.anthropic.com"
                val url = if (baseUrl.contains("/messages")) baseUrl else if (baseUrl.endsWith("/v1")) "$baseUrl/messages" else "$baseUrl/v1/messages"

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
                                    val isTransientError = (lastCode == 429 || lastCode == 503 || lastCode == 502 || lastCode == 504) || (rawResponse ?: "").contains("quota", ignoreCase = true) || (rawResponse ?: "").contains("rate limit", ignoreCase = true) || (rawResponse ?: "").contains("overloaded", ignoreCase = true) || (rawResponse ?: "").contains("unavailable", ignoreCase = true) || (rawResponse ?: "").contains("RESOURCE_EXHAUSTED", ignoreCase = true) || (rawResponse ?: "").contains("exhausted", ignoreCase = true) || (rawResponse ?: "").contains("503", ignoreCase = true) || (rawResponse ?: "").contains("429", ignoreCase = true)
                                    val isRateLimit = lastCode == 429 || (rawResponse ?: "").contains("rate limit", ignoreCase = true) || (rawResponse ?: "").contains("quota", ignoreCase = true) || (rawResponse ?: "").contains("RESOURCE_EXHAUSTED", ignoreCase = true) || (rawResponse ?: "").contains("exhausted", ignoreCase = true)
                                    val backoff = if (isRateLimit) {
                                        val base = Math.min(5000L * (1 shl (attempt - 1)), 60000L)
                                        val jitter = (Math.random() * 1000).toLong()
                                        base + jitter
                                    } else if (isTransientError) {
                                        val base = Math.min(3000L * (1 shl (attempt - 1)), 60000L)
                                        val jitter = (Math.random() * 1000).toLong()
                                        base + jitter
                                    } else {
                                        1000L * attempt
                                    }
                                    val errStr = "API Error $lastCode"
                                    onRetryListener?.invoke("Claude", attempt, maxAttempts, errStr)
                                    Log.w(TAG, "Claude API Error $lastCode. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                                    Thread.sleep(backoff)
                                    continue
                                }
                            }
                            if (attempt > 0 && response.isSuccessful) {
                                onRetrySuccessListener?.invoke()
                            }
                            break
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during Claude execute", e)
                            attempt++
                            if (attempt < maxAttempts) {
                                val msg = e.message?.lowercase() ?: ""
                                val isRateLimit = msg.contains("429") || msg.contains("rate limit") || msg.contains("quota") || msg.contains("exhausted")
                                val isTransientError = isRateLimit || msg.contains("503") || msg.contains("502") || msg.contains("504") || msg.contains("overloaded") || msg.contains("unavailable")
                                val backoff = if (isRateLimit) {
                                    val base = Math.min(5000L * (1 shl (attempt - 1)), 60000L)
                                    val jitter = (Math.random() * 1000).toLong()
                                    base + jitter
                                } else if (isTransientError) {
                                    val base = 2000L * (1 shl (attempt - 1))
                                    val jitter = (Math.random() * 500).toLong()
                                    base + jitter
                                } else {
                                    1000L * attempt
                                }
                                val errStr = e.message ?: "Network Exception"
                                onRetryListener?.invoke("Claude", attempt, maxAttempts, errStr)
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
                val url = when {
                    baseUrl.contains(":generateContent") -> if (baseUrl.contains("key=")) baseUrl else "$baseUrl?key=$activeApiKey"
                    baseUrl.contains("/models/") -> "$baseUrl:generateContent?key=$activeApiKey"
                    else -> "$baseUrl/v1beta/models/$activeModel:generateContent?key=$activeApiKey"
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
                                    val isTransientError = (lastCode == 429 || lastCode == 503 || lastCode == 502 || lastCode == 504) || (rawResponse ?: "").contains("quota", ignoreCase = true) || (rawResponse ?: "").contains("rate limit", ignoreCase = true) || (rawResponse ?: "").contains("overloaded", ignoreCase = true) || (rawResponse ?: "").contains("unavailable", ignoreCase = true) || (rawResponse ?: "").contains("RESOURCE_EXHAUSTED", ignoreCase = true) || (rawResponse ?: "").contains("exhausted", ignoreCase = true) || (rawResponse ?: "").contains("503", ignoreCase = true) || (rawResponse ?: "").contains("429", ignoreCase = true)
                                    val isRateLimit = lastCode == 429 || (rawResponse ?: "").contains("rate limit", ignoreCase = true) || (rawResponse ?: "").contains("quota", ignoreCase = true) || (rawResponse ?: "").contains("RESOURCE_EXHAUSTED", ignoreCase = true) || (rawResponse ?: "").contains("exhausted", ignoreCase = true)
                                    val backoff = if (isRateLimit) {
                                        val base = Math.min(5000L * (1 shl (attempt - 1)), 60000L)
                                        val jitter = (Math.random() * 1000).toLong()
                                        base + jitter
                                    } else if (isTransientError) {
                                        val base = Math.min(3000L * (1 shl (attempt - 1)), 60000L)
                                        val jitter = (Math.random() * 1000).toLong()
                                        base + jitter
                                    } else {
                                        1000L * attempt
                                    }
                                    val errStr = "API Error $lastCode"
                                    onRetryListener?.invoke("Gemini", attempt, maxAttempts, errStr)
                                    Log.w(TAG, "Gemini API Error $lastCode. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                                    Thread.sleep(backoff)
                                    continue
                                }
                            }
                            if (attempt > 0 && response.isSuccessful) {
                                onRetrySuccessListener?.invoke()
                            }
                            break
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during Gemini execute", e)
                            attempt++
                            if (attempt < maxAttempts) {
                                val msg = e.message?.lowercase() ?: ""
                                val isTransientError = msg.contains("429") || msg.contains("503") || msg.contains("502") || msg.contains("504") || msg.contains("rate limit") || msg.contains("overloaded") || msg.contains("unavailable")
                                val backoff = if (isTransientError) {
                                    val base = 2000L * (1 shl (attempt - 1))
                                    val jitter = (Math.random() * 500).toLong()
                                    base + jitter
                                } else {
                                    1000L * attempt
                                }
                                val errStr = e.message ?: "Network Exception"
                                onRetryListener?.invoke("Gemini", attempt, maxAttempts, errStr)
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
                        val isRateLimit = lastCode == 429 || (rawResponse ?: "").contains("quota", ignoreCase = true) || (rawResponse ?: "").contains("rate limit", ignoreCase = true) || (rawResponse ?: "").contains("RESOURCE_EXHAUSTED", ignoreCase = true) || (rawResponse ?: "").contains("exhausted", ignoreCase = true)
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
                        arguments = ToolArguments(message = "An error occurred during communication: ${formatNetworkError(e)}")
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
                            val isTransientError = (lastCode == 429 || lastCode == 503 || lastCode == 502 || lastCode == 504) || (rawResponse ?: "").contains("quota", ignoreCase = true) || (rawResponse ?: "").contains("rate limit", ignoreCase = true) || (rawResponse ?: "").contains("overloaded", ignoreCase = true) || (rawResponse ?: "").contains("unavailable", ignoreCase = true) || (rawResponse ?: "").contains("RESOURCE_EXHAUSTED", ignoreCase = true) || (rawResponse ?: "").contains("exhausted", ignoreCase = true) || (rawResponse ?: "").contains("503", ignoreCase = true) || (rawResponse ?: "").contains("429", ignoreCase = true)
                            val isRateLimit = lastCode == 429 || (rawResponse ?: "").contains("rate limit", ignoreCase = true) || (rawResponse ?: "").contains("quota", ignoreCase = true) || (rawResponse ?: "").contains("RESOURCE_EXHAUSTED", ignoreCase = true) || (rawResponse ?: "").contains("exhausted", ignoreCase = true)
                            val backoff = if (isRateLimit) {
                                val base = Math.min(5000L * (1 shl (attempt - 1)), 60000L)
                                val jitter = (Math.random() * 1000).toLong()
                                base + jitter
                            } else if (isTransientError) {
                                val base = Math.min(3000L * (1 shl (attempt - 1)), 60000L)
                                val jitter = (Math.random() * 1000).toLong()
                                base + jitter
                            } else {
                                1000L * attempt
                            }
                            val errStr = "API Error $lastCode"
                            onRetryListener?.invoke("Direct Gemini", attempt, maxAttempts, errStr)
                            Log.w(TAG, "Direct Gemini API Error $lastCode. Retrying in ${backoff}ms (Attempt $attempt of $maxAttempts)...")
                            Thread.sleep(backoff)
                            continue
                        }
                    }
                    if (attempt > 0 && response.isSuccessful) {
                        onRetrySuccessListener?.invoke()
                    }
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during direct Gemini execute", e)
                    attempt++
                    if (attempt < maxAttempts) {
                        val msg = e.message?.lowercase() ?: ""
                        val isRateLimit = msg.contains("429") || msg.contains("rate limit") || msg.contains("quota") || msg.contains("exhausted")
                        val isTransientError = isRateLimit || msg.contains("503") || msg.contains("502") || msg.contains("504") || msg.contains("overloaded") || msg.contains("unavailable")
                        val backoff = if (isRateLimit) {
                            val base = Math.min(5000L * (1 shl (attempt - 1)), 60000L)
                            val jitter = (Math.random() * 1000).toLong()
                            base + jitter
                        } else if (isTransientError) {
                            val base = 2000L * (1 shl (attempt - 1))
                            val jitter = (Math.random() * 500).toLong()
                            base + jitter
                        } else {
                            1000L * attempt
                        }
                        val errStr = e.message ?: "Network Exception"
                        onRetryListener?.invoke("Direct Gemini", attempt, maxAttempts, errStr)
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
                message = "An error occurred during communication: ${formatNetworkError(e)}"
            )
        }
    }

    fun formatNetworkError(e: Throwable): String {
        val msg = e.localizedMessage ?: e.message ?: "Unknown error"
        if (e is java.net.UnknownHostException || 
            msg.contains("Unable to resolve host", ignoreCase = true) || 
            msg.contains("UnknownHostException", ignoreCase = true) ||
            msg.contains("No address associated with hostname", ignoreCase = true)
        ) {
            val hostMatcher = Regex("""\"([^\"]+)\"""").find(msg)
            val failedHost = hostMatcher?.groupValues?.get(1) ?: "the API server"
            return "Network / DNS Connection Failed (হোস্ট অ্যাড্রেস পাওয়া যায়নি):\n" +
                    "Unable to resolve host \"$failedHost\".\n\n" +
                    "Why this happens / কারণ:\n" +
                    "1. The provider ($failedHost) is temporarily unavailable, down, or blocked by ISP/DNS.\n" +
                    "2. The API Base URL entered in Settings may be incorrect or missing 'https://'.\n" +
                    "3. Rate Limit / Network throttle occurred.\n\n" +
                    "How to fix / সমাধান:\n" +
                    "1. Check if your internet connection is active.\n" +
                    "2. Verify the Base URL in Settings (ensure it uses a valid reachable endpoint like https://api.openai.com/v1, https://api.groq.com/openai/v1, etc.).\n" +
                    "3. If using a proxy or VPN, try switching or reconnecting."
        }
        return msg
    }
}
