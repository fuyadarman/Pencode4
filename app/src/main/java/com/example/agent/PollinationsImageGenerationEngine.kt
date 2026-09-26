package com.example.agent

import com.example.data.VibeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Pollinations AI Image and Logo Generation Engine.
 * Supports image, logo, icon, and illustration generation with custom models, aspect ratios, and styles.
 */
object PollinationsImageGenerationEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    suspend fun generateImageOrLogo(
        projectName: String,
        prompt: String,
        targetPath: String?,
        width: Int = 1024,
        height: Int = 1024,
        isLogo: Boolean = false,
        model: String = "flux",
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): String = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim().ifBlank { "modern high resolution digital logo graphic" }
        val enhancedPrompt = if (isLogo && !cleanPrompt.lowercase().contains("logo")) {
            "professional vector app logo icon, $cleanPrompt, clean minimalist vector graphics, transparent background style, highly detailed"
        } else cleanPrompt

        val defaultFileName = if (isLogo) "assets/logo.png" else "assets/image.png"
        val filePath = normalizePath(targetPath?.ifBlank { defaultFileName } ?: defaultFileName)

        val candidateModels = listOf(model, "turbo", "unity", "")
        var lastError = "Unknown error"
        var successfulBytes: ByteArray? = null
        var usedModel = model

        for (candidate in candidateModels) {
            try {
                val encodedPrompt = URLEncoder.encode(enhancedPrompt, "UTF-8")
                val randomSeed = (1..9999999).random()
                val targetW = if (candidate == "turbo" || candidate.isEmpty()) minOf(width, 768) else width
                val targetH = if (candidate == "turbo" || candidate.isEmpty()) minOf(height, 768) else height
                val modelParam = if (candidate.isNotBlank()) "&model=$candidate" else ""
                val url = "https://image.pollinations.ai/prompt/$encodedPrompt?width=$targetW&height=$targetH&seed=$randomSeed$modelParam&nologo=true"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        successfulBytes = bytes
                        usedModel = if (candidate.isNotBlank()) candidate else "default"
                        break
                    }
                } else {
                    lastError = "Pollinations HTTP ${response.code}"
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: e.javaClass.simpleName
            }
        }

        val bytes = successfulBytes
        if (bytes == null) {
            return@withContext "Error: Failed to fetch image from Pollinations ($lastError). All fallback models were exhausted."
        }

        try {
            val mimeType = when (filePath.substringAfterLast(".", "").lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "ico" -> "image/x-icon"
                else -> "image/png"
            }

            val base64Content = "data:$mimeType;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            repository.saveFile(projectName, filePath, base64Content)

            "Successfully generated and saved ${if (isLogo) "logo" else "image"} to '$filePath' (model: $usedModel)."
        } catch (e: Exception) {
            "Error saving generated image: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }
}
