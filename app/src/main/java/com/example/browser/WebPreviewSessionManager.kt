package com.example.browser

import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * WebPreviewSessionManager
 *
 * Preserves the WebView instance across Jetpack Compose tab switches and recompositions.
 * Prevents unwanted reloads when switching between Chat, Editor, Preview, and Settings,
 * and ensures browser actions or snapshots never reload the active page.
 */
object WebPreviewSessionManager {

    private var persistentWebView: WebView? = null
    private var lastLoadedUrl: String? = null
    private var lastLoadedContentHash: Int? = null

    /**
     * Gets or initializes the retained WebView instance for the preview session.
     */
    fun getOrCreateWebView(context: Context): WebView {
        val existing = persistentWebView
        if (existing != null) {
            // Detach from previous parent if needed before attaching to new container
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }

        val newWv = WebView(context.applicationContext).apply {
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mediaPlaybackRequiresUserGesture = false
            }
            try {
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                cm.setAcceptThirdPartyCookies(this, true)
            } catch (_: Exception) {}
        }

        persistentWebView = newWv
        LivePreviewBrowserManager.registerActiveWebView(newWv)
        return newWv
    }

    fun isUrlAlreadyLoaded(url: String): Boolean {
        return lastLoadedUrl == url && persistentWebView?.url == url
    }

    fun markUrlLoaded(url: String) {
        lastLoadedUrl = url
    }

    fun isContentAlreadyLoaded(contentHash: Int): Boolean {
        return lastLoadedContentHash == contentHash
    }

    fun markContentLoaded(contentHash: Int) {
        lastLoadedContentHash = contentHash
    }

    fun resetSession() {
        persistentWebView?.stopLoading()
        persistentWebView = null
        lastLoadedUrl = null
        lastLoadedContentHash = null
    }
}
