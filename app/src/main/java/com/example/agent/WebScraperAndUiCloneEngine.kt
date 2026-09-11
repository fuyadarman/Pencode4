package com.example.agent

import com.example.data.ProjectEntity
import com.example.data.VibeRepository
import com.example.ui.BackgroundBrowser
import com.example.ui.BrowserResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Advanced web scraper, UI cloner, and website/article reader engine.
 * Fetches remote HTML/CSS/DOM structures or articles and extracts design tokens for UI duplication.
 */
object WebScraperAndUiCloneEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun cloneWebUi(
        url: String,
        targetFilePath: String?,
        projectName: String,
        repository: VibeRepository,
        backgroundBrowser: BackgroundBrowser,
        normalizePath: (String) -> String
    ): String = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            return@withContext "Error: 'url' parameter is required for clone_web_ui."
        }

        val fullUrl = if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            "https://$cleanUrl"
        } else cleanUrl

        try {
            // First attempt to navigate in background browser to render dynamic JS DOM
            val navResult = backgroundBrowser.navigate(fullUrl)
            val (pageTitle, htmlContent) = when (navResult) {
                is BrowserResult.Success -> {
                    val srcResult = backgroundBrowser.getPageSource()
                    val src = if (srcResult is BrowserResult.Success) srcResult.content else navResult.content
                    Pair(navResult.title, src)
                }
                is BrowserResult.Error -> {
                    // Fallback to direct HTTP OkHttp request
                    val request = Request.Builder()
                        .url(fullUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .build()
                    val response = httpClient.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    Pair("Scraped Website", body)
                }
            }

            // Save cloned raw HTML reference if requested or default to cloned_ui/source.html
            val destPath = normalizePath(targetFilePath ?: "cloned_ui/scraped_page.html")
            repository.saveFile(projectName, destPath, htmlContent)

            // Extract styling overview and layout summary for AI to replicate UI accurately
            val cssSummary = extractCssAndColorsOverview(htmlContent)

            """
            Successfully scraped & cloned website UI from '$fullUrl' (Title: '$pageTitle').
            Source code saved to '$destPath' (${htmlContent.length} chars).
            
            === UI & DESIGN TOKENS OVERVIEW ===
            $cssSummary
            
            You can now inspect '$destPath' or use 'read_file' to build identical React/HTML/Tailwind components matching this exact UI design.
            """.trimIndent()
        } catch (e: Exception) {
            "Error cloning web UI from '$cleanUrl': ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    suspend fun fetchUrlContent(
        url: String,
        projectName: String,
        repository: VibeRepository,
        saveToWorkspace: Boolean = true,
        targetFile: String? = null,
        normalizePath: (String) -> String
    ): String = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            return@withContext "Error: 'url' parameter cannot be empty."
        }

        val fullUrl = if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            "https://$cleanUrl"
        } else cleanUrl

        try {
            val request = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext "Error: Failed to fetch URL (HTTP ${response.code})"
            }

            val bodyText = response.body?.string() ?: ""
            if (saveToWorkspace) {
                val savePath = normalizePath(targetFile ?: "fetched_content.txt")
                repository.saveFile(projectName, savePath, bodyText)
            }

            val textSnippet = if (bodyText.length > 3000) bodyText.take(3000) + "\n...[truncated ${bodyText.length - 3000} more chars]" else bodyText
            "Successfully fetched URL '$fullUrl' (HTTP ${response.code}):\n\n$textSnippet"
        } catch (e: Exception) {
            "Error fetching URL '$cleanUrl': ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    private fun extractCssAndColorsOverview(html: String): String {
        val colors = Regex("#(?:[0-9a-fA-F]{3}){1,2}|rgba?\\([^)]+\\)").findAll(html)
            .map { it.value }
            .distinct()
            .take(12)
            .toList()

        val fonts = Regex("font-family:[^;\"']+").findAll(html)
            .map { it.value.removePrefix("font-family:").trim() }
            .distinct()
            .take(6)
            .toList()

        val sb = StringBuilder()
        if (colors.isNotEmpty()) {
            sb.append("• Extracted Colors: ").append(colors.joinToString(", ")).append("\n")
        }
        if (fonts.isNotEmpty()) {
            sb.append("• Typography & Fonts: ").append(fonts.joinToString(", ")).append("\n")
        }
        return sb.toString().ifBlank { "• HTML parsed successfully with standard layout hierarchy." }
    }
}
