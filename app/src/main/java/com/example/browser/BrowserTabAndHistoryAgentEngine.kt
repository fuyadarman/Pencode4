package com.example.browser

import com.example.ui.preview.WebPreviewLifecycleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * BrowserTabAndHistoryAgentEngine
 * Enables autonomous and program-level management of Browser Tabs and History
 * for both User Interface actions and AI Agent tool calls.
 */
object BrowserTabAndHistoryAgentEngine {

    /**
     * Lists all open tabs in real browser session.
     */
    fun listOpenTabs(): String {
        val tabs = RealBrowserTabManager.tabs.value
        val activeId = RealBrowserTabManager.activeTabId.value

        val jsonArr = JSONArray()
        for (tab in tabs) {
            val obj = JSONObject().apply {
                put("id", tab.id)
                put("title", tab.title)
                put("url", tab.url)
                put("isActive", tab.id == activeId)
                put("isLoading", tab.isLoading)
            }
            jsonArr.put(obj)
        }

        val sb = StringBuilder("=== OPEN BROWSER TABS (${tabs.size}) ===\n")
        tabs.forEachIndexed { index, tab ->
            val activeMark = if (tab.id == activeId) "[ACTIVE] " else ""
            sb.append("${index + 1}. $activeMark'${tab.title}' | URL: ${tab.url.ifBlank { "(Empty/Blank)" }} (Tab ID: ${tab.id})\n")
        }
        return sb.toString().trim()
    }

    /**
     * Opens a new tab with optional URL and switches to it.
     */
    fun openNewTab(url: String = "", title: String = "New Tab"): String {
        val cleanUrl = url.trim()
        val finalUrl = when {
            cleanUrl.isBlank() || cleanUrl.equals("about:blank", ignoreCase = true) -> "https://www.google.com"
            cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") -> cleanUrl
            cleanUrl.contains(".") -> "https://$cleanUrl"
            else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(cleanUrl, "UTF-8")
        }
        val cleanTitle = if (title.isNotBlank() && title != "New Tab") title else finalUrl
        val newTab = RealBrowserTabManager.openNewTab(finalUrl, cleanTitle)
        return "Opened new tab '${newTab.title}' (ID: ${newTab.id}) at URL: $finalUrl"
    }

    /**
     * Switches to an existing tab by tab ID, exact URL, or title match.
     */
    fun switchTab(queryOrId: String): String {
        val q = queryOrId.trim()
        val tabs = RealBrowserTabManager.tabs.value
        if (tabs.isEmpty()) return "Error: No tabs open."

        val target = tabs.find { it.id.equals(q, ignoreCase = true) }
            ?: tabs.find { it.title.contains(q, ignoreCase = true) }
            ?: tabs.find { it.url.contains(q, ignoreCase = true) }

        return if (target != null) {
            RealBrowserTabManager.switchTab(target.id)
            "Switched to tab '${target.title}' (ID: ${target.id}) with URL: ${target.url}"
        } else {
            "Error: Tab matching '$q' not found. Available tabs:\n" + listOpenTabs()
        }
    }

    /**
     * Closes an existing tab by ID or search query.
     */
    fun closeTab(queryOrId: String): String {
        val q = queryOrId.trim()
        val tabs = RealBrowserTabManager.tabs.value
        val target = tabs.find { it.id.equals(q, ignoreCase = true) }
            ?: tabs.find { it.title.contains(q, ignoreCase = true) }
            ?: tabs.find { it.url.contains(q, ignoreCase = true) }

        return if (target != null) {
            RealBrowserTabManager.closeTab(target.id)
            "Closed tab '${target.title}' (ID: ${target.id}). Remaining tabs count: ${RealBrowserTabManager.tabs.value.size}"
        } else {
            "Error: Could not find tab matching '$q' to close."
        }
    }

    /**
     * Closes all tabs except the currently active one or resets to a single blank tab.
     */
    fun closeOtherTabs(): String {
        val activeId = RealBrowserTabManager.activeTabId.value
        val tabs = RealBrowserTabManager.tabs.value
        val toClose = tabs.filterNot { it.id == activeId }
        toClose.forEach { RealBrowserTabManager.closeTab(it.id) }
        return "Closed ${toClose.size} background tabs. 1 active tab retained."
    }

    /**
     * Queries browser history records with optional keyword filter.
     */
    fun queryHistory(filter: String? = null, maxCount: Int = 50): String {
        val items = RealBrowserHistoryManager.history.value
        val filtered = if (!filter.isNullOrBlank()) {
            items.filter { it.title.contains(filter, ignoreCase = true) || it.url.contains(filter, ignoreCase = true) }
        } else {
            items
        }.take(maxCount)

        if (filtered.isEmpty()) {
            return if (filter.isNullOrBlank()) "Browser history is currently empty." else "No history items match query '$filter'."
        }

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val sb = StringBuilder("=== BROWSER HISTORY (${filtered.size} records) ===\n")
        filtered.forEachIndexed { idx, item ->
            val dateStr = sdf.format(java.util.Date(item.timestamp))
            sb.append("${idx + 1}. [${dateStr}] ${item.title}\n   ${item.url}\n")
        }
        return sb.toString().trim()
    }

    /**
     * Clears all recorded browser history.
     */
    fun clearHistory(): String {
        RealBrowserHistoryManager.clearHistory()
        return "Browser history has been completely cleared."
    }
}
