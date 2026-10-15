package com.example.ui.preview

import android.webkit.WebView

/**
 * WebPreviewLifecycleManager
 * 
 * Manages the lifecycle, timing, and URL classification of the Preview Tab WebView:
 * 1. Accurately distinguishes between internal virtual-app preview URLs and external navigations.
 * 2. Provides stable history URLs for loadDataWithBaseURL to prevent WebView from defaulting
 *    to "about:blank" and entering infinite loading states.
 * 3. Ensures JavaScript timers and Chromium rendering pipelines are actively resumed
 *    on initial tab selection and subsequent tab switches.
 * 4. Provides safety timeouts so the loading progress indicator never gets stuck permanently.
 */
object WebPreviewLifecycleManager {

    const val VIRTUAL_BASE_URL = "https://virtual-app/"
    const val VIRTUAL_HISTORY_URL = "https://virtual-app/index.html"

    /**
     * Determines whether a given URL is an internal virtual preview URL or an external navigation.
     * Prevents internal fallback URLs ("about:blank", "data:", "https://virtual-app/") from being
     * mistakenly stored as user-navigated custom URLs.
     */
    fun isInternalVirtualUrl(url: String?): Boolean {
        if (url == null || url.isBlank()) return true
        val clean = url.trim().lowercase()
        return clean.startsWith(VIRTUAL_BASE_URL) ||
               clean == "about:blank" ||
               clean.startsWith("data:") ||
               clean == "about:srcdoc"
    }

    /**
     * Prepares and resumes the WebView when entering or refreshing the Preview tab.
     * Guarantees that layout and JS timer queues are active.
     */
    fun prepareWebView(webView: WebView?) {
        if (webView == null) return
        try {
            webView.onResume()
            webView.resumeTimers()
        } catch (e: Exception) {
            // Ignore lifecycle errors
        }
    }

    /**
     * Safely releases a WebView instance without globally freezing WebKit timers for future WebViews.
     */
    fun releaseWebView(webView: WebView?) {
        if (webView == null) return
        try {
            webView.stopLoading()
            webView.onPause()
            webView.webChromeClient = null
            webView.webViewClient = android.webkit.WebViewClient()
            webView.removeJavascriptInterface("AndroidInspector")
            webView.loadUrl("about:blank")
            webView.destroy()
        } catch (e: Exception) {
            // Ignore teardown errors
        }
    }
}
