package com.example.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Data model for a Real Browser Tab in the Preview tab.
 */
data class RealBrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "",
    val faviconUrl: String? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false
)

/**
 * Real Browser Multi-Tab and Session Manager for Preview Tab.
 * Provides Chrome/Firefox-like tab management inside the Android applet.
 */
object RealBrowserTabManager {

    private val defaultTabs = listOf(
        RealBrowserTab(id = "default_tab", title = "Browser Home", url = "")
    )

    private val _tabs = MutableStateFlow<List<RealBrowserTab>>(defaultTabs)
    val tabs: StateFlow<List<RealBrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow("default_tab")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    fun getActiveTab(): RealBrowserTab? {
        return _tabs.value.find { it.id == _activeTabId.value } ?: _tabs.value.firstOrNull()
    }

    fun openNewTab(initialUrl: String = "", initialTitle: String = "New Tab"): RealBrowserTab {
        val newTab = RealBrowserTab(
            title = if (initialUrl.isNotBlank()) initialUrl else initialTitle,
            url = initialUrl
        )
        _tabs.value = _tabs.value + newTab
        _activeTabId.value = newTab.id
        if (initialUrl.isNotBlank()) {
            LivePreviewBrowserManager.setRequestedUrl(initialUrl)
        }
        return newTab
    }

    fun closeTab(tabId: String) {
        val currentList = _tabs.value
        if (currentList.size <= 1) {
            // Keep at least one tab, reset it
            val resetTab = RealBrowserTab(id = UUID.randomUUID().toString(), title = "New Tab", url = "")
            _tabs.value = listOf(resetTab)
            _activeTabId.value = resetTab.id
            LivePreviewBrowserManager.setRequestedUrl("")
            return
        }

        val nextList = currentList.filterNot { it.id == tabId }
        _tabs.value = nextList

        if (_activeTabId.value == tabId) {
            val nextActive = nextList.lastOrNull()?.id ?: ""
            _activeTabId.value = nextActive
            val targetUrl = nextList.lastOrNull()?.url ?: ""
            LivePreviewBrowserManager.setRequestedUrl(targetUrl)
        }
    }

    fun switchTab(tabId: String) {
        val tab = _tabs.value.find { it.id == tabId } ?: return
        _activeTabId.value = tabId
        LivePreviewBrowserManager.setRequestedUrl(tab.url)
    }

    fun updateActiveTab(url: String, title: String? = null, canGoBack: Boolean = false, canGoForward: Boolean = false, isLoading: Boolean = false) {
        val activeId = _activeTabId.value
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == activeId) {
                tab.copy(
                    url = url,
                    title = if (!title.isNullOrBlank()) title else if (url.isNotBlank()) url else "New Tab",
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    isLoading = isLoading
                )
            } else {
                tab
            }
        }
    }
}
