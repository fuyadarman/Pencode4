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

        try {
            val encodedPrompt = URLEncoder.encode(enhancedPrompt, "UTF-8")
            val randomSeed = (1..9999999).random()
            val url = "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$height&seed=$randomSeed&model=$model&nologo=true"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext "Error: Failed to fetch image from Pollinations (HTTP ${response.code})."
            }

            val bytes = response.body?.bytes()
                ?: return@withContext "Error: Pollinations image response body was empty."

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

            "Successfully generated and saved ${if (isLogo) "logo" else "image"} to '$filePath' ($width x $height, model: $model)."
        } catch (e: Exception) {
            "Error generating image with Pollinations: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }
}
