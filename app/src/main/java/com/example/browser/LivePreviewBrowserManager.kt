package com.example.browser

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * Real-time Live Browser Controller for AI Agent.
 * Allows the AI Agent to control the user's interactive preview browser live:
 * - Live navigation, human-like typing, clicking with visual feedback, scrolling, DOM inspection.
 * - Manages real browser cookies, local storage, sessions, and viewport emulation.
 */
object LivePreviewBrowserManager {

    private var activeWebViewRef: WeakReference<WebView>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _agentActionStatus = MutableStateFlow<String?>(null)
    val agentActionStatus: StateFlow<String?> = _agentActionStatus.asStateFlow()

    private val _currentUrl = MutableStateFlow<String>("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    fun registerActiveWebView(webView: WebView?) {
        if (webView != null) {
            activeWebViewRef = WeakReference(webView)
            _currentUrl.value = webView.url ?: ""
            // Ensure cookies and storage are active
            try {
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                cm.setAcceptThirdPartyCookies(webView, true)
            } catch (_: Exception) {}
        } else {
            activeWebViewRef = null
        }
    }

    fun unregisterActiveWebView(webView: WebView?) {
        if (activeWebViewRef?.get() == webView) {
            activeWebViewRef = null
        }
    }

    fun getActiveWebView(): WebView? {
        val wv = activeWebViewRef?.get()
        return if (wv != null && wv.isAttachedToWindow) wv else null
    }

    fun isAvailable(): Boolean = getActiveWebView() != null

    fun setActionStatus(message: String?) {
        _agentActionStatus.value = message
    }

    fun clearActionStatus() {
        _agentActionStatus.value = null
    }

    /**
     * AI Agent Live Navigation
     */
    suspend fun navigate(url: String): String = withContext(Dispatchers.Main) {
        val wv = getActiveWebView() ?: return@withContext "Error: Live preview browser is not currently active."
        setActionStatus("🤖 AI Agent: Navigating to $url...")

        val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        wv.loadUrl(cleanUrl)
        _currentUrl.value = cleanUrl

        // Give page 1.2s to start loading
        kotlinx.coroutines.delay(1200)
        clearActionStatus()
        "Successfully navigated live preview to '$cleanUrl'. Page Title: '${wv.title ?: ""}'"
    }

    /**
     * AI Agent Live Element Click with human-like visual ripple feedback
     */
    suspend fun clickElement(selector: String): String = withContext(Dispatchers.Main) {
        val wv = getActiveWebView() ?: return@withContext "Error: Live preview browser is not currently active."
        setActionStatus("🤖 AI Agent: Clicking '$selector'...")

        val safeSel = selector.replace("'", "\\'")
        val js = """
            (function() {
                var el = document.querySelector('$safeSel');
                if (!el) {
                    try {
                        var xpath = document.evaluate('$safeSel', document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                        el = xpath.singleNodeValue;
                    } catch(e) {}
                }
                if (!el) return 'not_found';

                // Scroll smoothly into view
                el.scrollIntoView({ behavior: 'smooth', block: 'center' });

                // Create visual AI agent click highlight ring
                var rect = el.getBoundingClientRect();
                var ripple = document.createElement('div');
                ripple.style.position = 'fixed';
                ripple.style.left = (rect.left + rect.width / 2 - 18) + 'px';
                ripple.style.top = (rect.top + rect.height / 2 - 18) + 'px';
                ripple.style.width = '36px';
                ripple.style.height = '36px';
                ripple.style.borderRadius = '50%';
                ripple.style.background = 'rgba(56, 189, 248, 0.4)';
                ripple.style.border = '2px solid #38BDF8';
                ripple.style.boxShadow = '0 0 14px #38BDF8';
                ripple.style.zIndex = '9999999';
                ripple.style.pointerEvents = 'none';
                ripple.style.transition = 'transform 0.4s ease-out, opacity 0.4s ease-out';
                document.body.appendChild(ripple);

                setTimeout(function() {
                    ripple.style.transform = 'scale(1.8)';
                    ripple.style.opacity = '0';
                    setTimeout(function() { ripple.remove(); }, 400);
                }, 50);

                el.focus();
                el.click();
                return 'clicked';
            })()
        """.trimIndent()

        val deferred = CompletableDeferred<String>()
        wv.evaluateJavascript(js) { res ->
            deferred.complete(res ?: "null")
        }
        val res = deferred.await()
        kotlinx.coroutines.delay(600)
        clearActionStatus()

        if (res.contains("clicked")) {
            "Successfully clicked element '$selector' in live preview."
        } else {
            "Could not find element matching '$selector' in live preview."
        }
    }

    /**
     * AI Agent Live Typing with input events and visual focus
     */
    suspend fun typeText(selector: String, text: String, clearFirst: Boolean = false): String = withContext(Dispatchers.Main) {
        val wv = getActiveWebView() ?: return@withContext "Error: Live preview browser is not currently active."
        setActionStatus("🤖 AI Agent: Typing in '$selector'...")

        val safeSel = selector.replace("'", "\\'")
        val safeText = JSONObject.quote(text)
        val js = """
            (function() {
                var el = document.querySelector('$safeSel');
                if (!el) {
                    try {
                        var xpath = document.evaluate('$safeSel', document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                        el = xpath.singleNodeValue;
                    } catch(e) {}
                }
                if (!el) return 'not_found';

                el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                el.focus();

                // Highlight input with AI glow
                var oldOutline = el.style.outline;
                el.style.outline = '2px solid #38BDF8';
                setTimeout(function() { el.style.outline = oldOutline; }, 1200);

                var newText = $safeText;
                if (${if (clearFirst) "true" else "false"}) {
                    el.value = '';
                }
                el.value = (el.value || '') + newText;
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return 'typed';
            })()
        """.trimIndent()

        val deferred = CompletableDeferred<String>()
        wv.evaluateJavascript(js) { res ->
            deferred.complete(res ?: "null")
        }
        val res = deferred.await()
        kotlinx.coroutines.delay(400)
        clearActionStatus()

        if (res.contains("typed")) {
            "Successfully typed text into '$selector' in live preview."
        } else {
            "Could not find input element '$selector' in live preview."
        }
    }

    /**
     * AI Agent Live Scrolling
     */
    suspend fun scrollPage(direction: String, amount: Int = 400): String = withContext(Dispatchers.Main) {
        val wv = getActiveWebView() ?: return@withContext "Error: Live preview browser is not currently active."
        val dy = when (direction.lowercase()) {
            "up" -> -amount
            "down" -> amount
            "top" -> -99999
            "bottom" -> 99999
            else -> amount
        }
        setActionStatus("🤖 AI Agent: Scrolling ${direction.lowercase()}...")

        val js = "window.scrollBy({ top: $dy, behavior: 'smooth' }); 'scrolled';"
        val deferred = CompletableDeferred<String>()
        wv.evaluateJavascript(js) { res -> deferred.complete(res ?: "") }
        deferred.await()
        kotlinx.coroutines.delay(400)
        clearActionStatus()
        "Scrolled live preview $direction by $amount px."
    }

    /**
     * AI Agent Live Page Text Extraction
     */
    suspend fun readPageContent(): String = withContext(Dispatchers.Main) {
        val wv = getActiveWebView() ?: return@withContext "Error: Live preview browser is not currently active."
        val js = """
            (function() {
                var clone = document.body.cloneNode(true);
                var toRemove = clone.querySelectorAll('script, style, noscript, svg');
                for (var i = 0; i < toRemove.length; i++) toRemove[i].remove();
                return JSON.stringify({
                    url: window.location.href,
                    title: document.title,
                    text: (clone.innerText || clone.textContent || '').substring(0, 15000)
                });
            })()
        """.trimIndent()

        val deferred = CompletableDeferred<String>()
        wv.evaluateJavascript(js) { res -> deferred.complete(res ?: "{}") }
        val raw = deferred.await()
        try {
            val json = JSONObject(raw)
            "Title: ${json.optString("title")}\nURL: ${json.optString("url")}\n\nVisible Page Text:\n${json.optString("text")}"
        } catch (_: Exception) {
            raw
        }
    }

    /**
     * AI Agent Live JavaScript Evaluation
     */
    suspend fun runJavascript(script: String): String = withContext(Dispatchers.Main) {
        val wv = getActiveWebView() ?: return@withContext "Error: Live preview browser is not currently active."
        setActionStatus("🤖 AI Agent: Executing browser script...")
        val deferred = CompletableDeferred<String>()
        wv.evaluateJavascript(script) { res -> deferred.complete(res ?: "null") }
        val res = deferred.await()
        clearActionStatus()
        res
    }

    /**
     * Capture Live Preview Screenshot safely
     */
    suspend fun captureScreenshot(): Bitmap? = withContext(Dispatchers.Main) {
        val wv = getActiveWebView() ?: return@withContext null
        try {
            val w = wv.width.coerceAtLeast(360)
            val h = wv.height.coerceAtLeast(640)
            val scale = if (w > 720) 720f / w else 1.0f
            val targetW = (w * scale).toInt()
            val targetH = (h * scale).toInt()

            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.scale(scale, scale)
            wv.draw(canvas)
            bitmap
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Clear all browser cookies and cache
     */
    fun clearBrowserData(onCompleted: () -> Unit = {}) {
        mainHandler.post {
            try {
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                    getActiveWebView()?.clearCache(true)
                    onCompleted()
                }
            } catch (_: Exception) {
                onCompleted()
            }
        }
    }
}
