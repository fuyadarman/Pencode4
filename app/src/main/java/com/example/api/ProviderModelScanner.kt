package com.example.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ProviderModelScanner {
    private const val TAG = "ProviderModelScanner"

    sealed class ScanResult {
        data class Success(val models: List<String>) : ScanResult()
        data class Error(val message: String) : ScanResult()
    }

    suspend fun scanModels(
        provider: String,
        apiKey: String,
        baseUrl: String
    ): ScanResult = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            // Handle special static providers
            if (provider == "cloudflare") {
                val cfModels = listOf(
                    "@cf/meta/llama-3.3-70b-instruct",
                    "@cf/meta/llama-3-8b-instruct",
                    "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b",
                    "@cf/qwen/qwen1.5-14b-chat",
                    "@cf/mistral/mistral-7b-instruct-v0.1"
                )
                return@withContext ScanResult.Success(cfModels)
            }

            val targetUrls = resolveTargetUrls(provider, baseUrl, apiKey)
            if (targetUrls.isEmpty()) {
                return@withContext ScanResult.Error("No valid scanning URL could be determined for provider '$provider'.")
            }

            var lastError = ""
            for (url in targetUrls) {
                try {
                    val requestBuilder = Request.Builder().url(url)
                    if (apiKey.isNotBlank() && provider != "gemini") {
                        requestBuilder.header("Authorization", "Bearer $apiKey")
                    }
                    if (provider == "openrouter" || provider == "cline") {
                        requestBuilder.header("HTTP-Referer", "https://ai.studio/build")
                        requestBuilder.header("X-Title", "AI Studio Android")
                    }

                    val request = requestBuilder.get().build()
                    val response = client.newCall(request).execute()
                    val bodyStr = response.body?.string()

                    if (response.isSuccessful && !bodyStr.isNullOrBlank()) {
                        val parsedModels = extractModelsFromJson(bodyStr)
                        if (parsedModels.isNotEmpty()) {
                            return@withContext ScanResult.Success(parsedModels)
                        }
                    } else {
                        lastError = "HTTP ${response.code}: ${bodyStr?.take(200) ?: "No body"}"
                        Log.w(TAG, "Scan attempt failed for $url: $lastError")
                    }
                } catch (e: Exception) {
                    lastError = e.localizedMessage ?: "Connection error"
                    Log.w(TAG, "Scan exception for $url: $lastError")
                }
            }

            // If remote scan failed but it's a known provider like cline/mistral/groq, provide curated defaults with notice
            val defaults = getCuratedProviderDefaults(provider)
            if (defaults.isNotEmpty()) {
                return@withContext ScanResult.Success(defaults)
            }

            return@withContext ScanResult.Error("Model scan failed ($lastError). Please verify the Base URL and API Key.")
        } catch (e: Exception) {
            return@withContext ScanResult.Error("Scan failed: ${e.localizedMessage}")
        }
    }

    private fun resolveTargetUrls(provider: String, baseUrl: String, apiKey: String): List<String> {
        val urls = mutableListOf<String>()
        val cleanBase = baseUrl.trim().trimEnd('/')

        when (provider.lowercase()) {
            "gemini" -> {
                val key = apiKey.ifBlank { com.example.BuildConfig.GEMINI_API_KEY }
                urls.add("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
            }
            "cohere" -> {
                urls.add("https://api.cohere.com/v1/models")
            }
            "openrouter" -> {
                if (cleanBase.isNotBlank()) {
                    if (cleanBase.endsWith("/v1")) urls.add("$cleanBase/models")
                    urls.add("$cleanBase/v1/models")
                    urls.add("$cleanBase/models")
                }
                urls.add("https://openrouter.ai/api/v1/models")
            }
            "cline" -> {
                if (cleanBase.isNotBlank()) {
                    if (cleanBase.endsWith("/v1")) urls.add("$cleanBase/models")
                    urls.add("$cleanBase/v1/models")
                    urls.add("$cleanBase/models")
                }
                urls.add("https://api.cline.bot/v1/models")
                urls.add("https://openrouter.ai/api/v1/models")
            }
            "openai" -> {
                if (cleanBase.isNotBlank()) {
                    if (cleanBase.endsWith("/v1")) urls.add("$cleanBase/models")
                    urls.add("$cleanBase/v1/models")
                    urls.add("$cleanBase/models")
                }
                urls.add("https://api.openai.com/v1/models")
            }
            "mistral" -> {
                if (cleanBase.isNotBlank()) {
                    if (cleanBase.endsWith("/v1")) urls.add("$cleanBase/models")
                    urls.add("$cleanBase/v1/models")
                    urls.add("$cleanBase/models")
                }
                urls.add("https://api.mistral.ai/v1/models")
            }
            "groq" -> {
                if (cleanBase.isNotBlank()) {
                    if (cleanBase.endsWith("/openai") || cleanBase.endsWith("/v1")) urls.add("$cleanBase/models")
                    urls.add("$cleanBase/openai/v1/models")
                    urls.add("$cleanBase/v1/models")
                }
                urls.add("https://api.groq.com/openai/v1/models")
            }
            "opencode_zen", "opencode" -> {
                if (cleanBase.isNotBlank()) {
                    urls.add("$cleanBase/models")
                    urls.add("$cleanBase/v1/models")
                }
                urls.add("https://opencode.ai/zen/v1/models")
            }
            "ollama_cloud", "ollama" -> {
                if (cleanBase.isNotBlank()) {
                    urls.add("$cleanBase/api/tags")
                    urls.add("$cleanBase/v1/models")
                    urls.add("$cleanBase/models")
                }
                urls.add("https://api.ollama.com/v1/models")
            }
            else -> {
                if (cleanBase.isNotBlank()) {
                    if (cleanBase.endsWith("/v1")) {
                        urls.add("$cleanBase/models")
                    } else {
                        urls.add("$cleanBase/v1/models")
                        urls.add("$cleanBase/models")
                    }
                }
            }
        }

        return urls.distinct()
    }

    private fun extractModelsFromJson(bodyStr: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val json = JSONObject(bodyStr)
            if (json.has("data")) {
                val dataArray = json.getJSONArray("data")
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.optJSONObject(i) ?: continue
                    val id = item.optString("id", "")
                    if (id.isNotBlank()) list.add(id)
                }
            } else if (json.has("models")) {
                val modelsArray = json.getJSONArray("models")
                for (i in 0 until modelsArray.length()) {
                    val item = modelsArray.optJSONObject(i) ?: continue
                    val name = item.optString("name", "").ifBlank { item.optString("id", "") }
                    if (name.isNotBlank()) {
                        list.add(name)
                        if (name.startsWith("models/")) {
                            list.add(name.substringAfter("models/"))
                        }
                    }
                }
            } else if (json.has("tags")) { // Ollama /api/tags
                val tagsArray = json.getJSONArray("tags")
                for (i in 0 until tagsArray.length()) {
                    val item = tagsArray.optJSONObject(i) ?: continue
                    val name = item.optString("name", "")
                    if (name.isNotBlank()) list.add(name)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "JSON parsing error in extractModelsFromJson: ${e.message}")
        }
        return list.distinct()
    }

    private fun getCuratedProviderDefaults(provider: String): List<String> {
        return when (provider.lowercase()) {
            "cline" -> listOf(
                "claude-3-7-sonnet-20250219",
                "claude-3-5-sonnet-20241022",
                "anthropic/claude-3.7-sonnet",
                "anthropic/claude-3.5-sonnet",
                "openai/gpt-4o",
                "openai/o3-mini",
                "deepseek/deepseek-r1",
                "deepseek/deepseek-chat"
            )
            "openrouter" -> listOf(
                "google/gemini-2.5-flash",
                "anthropic/claude-3.7-sonnet",
                "anthropic/claude-3.5-sonnet",
                "deepseek/deepseek-r1",
                "openai/gpt-4o",
                "meta-llama/llama-3.3-70b-instruct"
            )
            "mistral" -> listOf(
                "mistral-large-latest",
                "mistral-small-latest",
                "codestral-latest",
                "pixtral-large-latest"
            )
            else -> emptyList()
        }
    }
}
