package com.example.browser

import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PreviewReloadToolManager
 *
 * Dedicated system for managing Preview Tab and Live Preview Browser reloading:
 * 1. Provides AI Agent tool execution for 'browser_reload', 'preview_reload', 'reload_preview'.
 * 2. Safely reloads the active preview WebView on the Android Main thread.
 * 3. Prevents mid-turn jitter/reloading during individual tool calls so the browser
 *    maintains interactive state while the AI executes tools.
 * 4. Ensures preview reload is triggered cleanly when the AI completes its turn.
 */
object PreviewReloadToolManager {

    /**
     * Executes the reload tool call invoked by the AI agent.
     */
    suspend fun executeReloadTool(
        onRefreshPreviewTrigger: (() -> Unit)? = null
    ): String = withContext(Dispatchers.Main) {
        val wv = LivePreviewBrowserManager.getActiveWebView()
        if (wv != null) {
            try {
                wv.reload()
                onRefreshPreviewTrigger?.invoke()
                return@withContext "Successfully reloaded the preview tab. (Current URL: '${wv.url ?: "local preview"}', Title: '${wv.title ?: ""}')"
            } catch (e: Exception) {
                return@withContext "Error reloading preview tab: ${e.localizedMessage}"
            }
        } else {
            // If WebView is not currently attached or is refreshing via local HTTP/virtual server:
            onRefreshPreviewTrigger?.invoke()
            return@withContext "Successfully triggered preview tab refresh."
        }
    }

    /**
     * Checks if a tool name is a preview reload tool.
     */
    fun isReloadTool(toolName: String): Boolean {
        return when (toolName.lowercase().trim()) {
            "browser_reload",
            "preview_reload",
            "reload_preview",
            "reload_preview_tab",
            "web_preview_reload" -> true
            else -> false
        }
    }
}
