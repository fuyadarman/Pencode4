package com.example.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * AiBrowserControlBridge
 * Exposes complete browser tab and navigation control to both AI Agent and UI.
 * Allows opening tabs, switching tabs, closing tabs, reading history, and executing page actions.
 */
object AiBrowserControlBridge {

    suspend fun openUrl(url: String, newTab: Boolean = false): String = withContext(Dispatchers.Main) {
        val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("data:")) {
            "https://$url"
        } else url

        if (newTab) {
            val tab = RealBrowserTabManager.openNewTab(cleanUrl, cleanUrl)
            LivePreviewBrowserManager.setRequestedUrl(cleanUrl)
            RealBrowserHistoryManager.recordHistory(cleanUrl, cleanUrl)
            "Opened new tab (${tab.id}) at $cleanUrl"
        } else {
            LivePreviewBrowserManager.setRequestedUrl(cleanUrl)
            RealBrowserTabManager.updateActiveTab(cleanUrl, cleanUrl, isLoading = true)
            RealBrowserHistoryManager.recordHistory(cleanUrl, cleanUrl)
            "Navigated active browser tab to $cleanUrl"
        }
    }

    suspend fun openNewTab(url: String = "", title: String = "New Tab"): String = withContext(Dispatchers.Main) {
        val tab = RealBrowserTabManager.openNewTab(url, title)
        if (url.isNotBlank()) {
            RealBrowserHistoryManager.recordHistory(title.ifBlank { url }, url)
        }
        "Created new browser tab '${tab.title}' (ID: ${tab.id})"
    }

    suspend fun closeTab(tabIdOrIndex: String?): String = withContext(Dispatchers.Main) {
        val currentTabs = RealBrowserTabManager.tabs.value
        val targetId = if (tabIdOrIndex.isNullOrBlank()) {
            RealBrowserTabManager.activeTabId.value
        } else {
            val idx = tabIdOrIndex.toIntOrNull()
            if (idx != null && idx in currentTabs.indices) {
                currentTabs[idx].id
            } else {
                currentTabs.find { it.id == tabIdOrIndex || it.title.contains(tabIdOrIndex, ignoreCase = true) }?.id
                    ?: RealBrowserTabManager.activeTabId.value
            }
        }
        RealBrowserTabManager.closeTab(targetId)
        "Closed browser tab ($targetId). Remaining tabs: ${RealBrowserTabManager.tabs.value.size}"
    }

    suspend fun switchTab(tabIdOrQuery: String): String = withContext(Dispatchers.Main) {
        val currentTabs = RealBrowserTabManager.tabs.value
        val idx = tabIdOrQuery.toIntOrNull()
        val target = if (idx != null && idx in currentTabs.indices) {
            currentTabs[idx]
        } else {
            currentTabs.find {
                it.id.equals(tabIdOrQuery, ignoreCase = true) ||
                it.title.contains(tabIdOrQuery, ignoreCase = true) ||
                it.url.contains(tabIdOrQuery, ignoreCase = true)
            }
        }

        if (target != null) {
            RealBrowserTabManager.switchTab(target.id)
            "Switched to browser tab: '${target.title}' (${target.url})"
        } else {
            "Error: Tab '$tabIdOrQuery' not found. Available tabs: ${listTabs()}"
        }
    }

    fun listTabs(): String {
        val currentTabs = RealBrowserTabManager.tabs.value
        val activeId = RealBrowserTabManager.activeTabId.value
        if (currentTabs.isEmpty()) return "No open browser tabs."

        val sb = StringBuilder("Open Browser Tabs (${currentTabs.size}):\n")
        currentTabs.forEachIndexed { i, tab ->
            val mark = if (tab.id == activeId) "[ACTIVE]" else ""
            sb.append("• [$i] $mark Title: '${tab.title}', URL: '${tab.url}', ID: '${tab.id}'\n")
        }
        return sb.toString().trim()
    }

    fun getHistory(maxItems: Int = 30): String {
        val hist = RealBrowserHistoryManager.history.value
        if (hist.isEmpty()) return "Browser history is empty."

        val sb = StringBuilder("Browser History (${hist.size} items):\n")
        hist.take(maxItems).forEachIndexed { i, item ->
            sb.append("• [$i] '${item.title}' -> ${item.url}\n")
        }
        return sb.toString().trim()
    }

    fun clearHistory(): String {
        RealBrowserHistoryManager.clearHistory()
        return "Browser history cleared successfully."
    }
}
