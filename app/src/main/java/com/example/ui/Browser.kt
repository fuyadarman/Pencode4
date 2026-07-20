package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class BackgroundBrowser(private val context: Context) {
    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        mainHandler.post {
            webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    // Set a modern mobile user agent
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                }
                
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        synchronized(loadLock) {
                            pageLoadDeferred?.complete(url ?: "")
                        }
                    }
                }
            }
        }
    }

    private val loadLock = Any()
    private var pageLoadDeferred: CompletableDeferred<String>? = null

    private fun isBlockedOrError(result: BrowserResult): Boolean {
        if (result is BrowserResult.Error) return true
        if (result is BrowserResult.Success) {
            val contentLower = result.content.lowercase()
            val isShort = result.content.trim().length < 150
            val hasCaptchaWords = contentLower.contains("captcha") || 
                                 contentLower.contains("cloudflare") || 
                                 contentLower.contains("robot check") || 
                                 contentLower.contains("unusual traffic") || 
                                 contentLower.contains("security check") ||
                                 contentLower.contains("access denied") ||
                                 contentLower.contains("please enable js") ||
                                 contentLower.contains("enable javascript")
            return isShort || hasCaptchaWords
        }
        return false
    }

    suspend fun searchBrave(query: String): BrowserResult = withContext(Dispatchers.Main) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        
        // 1. Try Brave Search first
        val braveUrl = "https://search.brave.com/search?q=$encodedQuery"
        var result = loadUrl(braveUrl)
        if (!isBlockedOrError(result)) {
            return@withContext result
        }
        
        // 2. Fallback to Bing
        val bingUrl = "https://www.bing.com/search?q=$encodedQuery"
        result = loadUrl(bingUrl)
        if (!isBlockedOrError(result)) {
            return@withContext result
        }
        
        // 3. Fallback to DuckDuckGo
        val ddgUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"
        result = loadUrl(ddgUrl)
        if (!isBlockedOrError(result)) {
            return@withContext result
        }
        
        // 4. Fallback to Yahoo
        val yahooUrl = "https://search.yahoo.com/search?p=$encodedQuery"
        result = loadUrl(yahooUrl)
        return@withContext result
    }

    suspend fun navigate(url: String): BrowserResult = withContext(Dispatchers.Main) {
        var formattedUrl = url
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "https://$formattedUrl"
        }
        loadUrl(formattedUrl)
    }

    private suspend fun loadUrl(url: String): BrowserResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext BrowserResult.Error("WebView is not initialized")
        val deferred = CompletableDeferred<String>()
        synchronized(loadLock) {
            pageLoadDeferred = deferred
        }
        
        wv.loadUrl(url)
        
        try {
            val finalUrl = deferred.await()
            // Give Javascript 1.5 seconds to settle on the page
            kotlinx.coroutines.delay(1500)
            val title = getPageTitle()
            val content = extractPageText()
            BrowserResult.Success(
                url = finalUrl,
                title = title,
                content = content
            )
        } catch (e: Exception) {
            BrowserResult.Error("Failed to load page: ${e.localizedMessage}")
        }
    }

    suspend fun readPageContent(): BrowserResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext BrowserResult.Error("WebView is not initialized")
        val url = wv.url ?: ""
        val title = getPageTitle()
        val content = extractPageText()
        BrowserResult.Success(url = url, title = title, content = content)
    }

    suspend fun clickElement(selector: String): BrowserResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext BrowserResult.Error("WebView is not initialized")
        
        val js = """
            (function() {
                var el = document.querySelector('$selector');
                if (el) {
                    el.click();
                    return 'clicked';
                }
                var xpathResult = document.evaluate('$selector', document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                if (xpathResult.singleNodeValue) {
                    xpathResult.singleNodeValue.click();
                    return 'clicked via xpath';
                }
                return 'not_found';
            })()
        """.trimIndent()

        val clickResult = evaluateJavascript(js)
        if (clickResult == "\"clicked\"" || clickResult == "\"clicked via xpath\"") {
            val deferred = CompletableDeferred<String>()
            synchronized(loadLock) {
                pageLoadDeferred = deferred
            }
            val navigatedUrl = try {
                kotlinx.coroutines.withTimeout(3000) {
                    deferred.await()
                }
            } catch (e: Exception) {
                wv.url ?: ""
            }
            kotlinx.coroutines.delay(1000)
            val title = getPageTitle()
            val content = extractPageText()
            BrowserResult.Success(
                url = navigatedUrl, 
                title = title, 
                content = "Clicked element '$selector' successfully. Current page title: '$title'. Content:\n$content"
            )
        } else {
            BrowserResult.Error("Could not find element matching selector/XPath: '$selector'")
        }
    }

    private suspend fun getPageTitle(): String = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext ""
        wv.title ?: evaluateJavascript("document.title") ?: ""
    }

    private suspend fun extractPageText(): String = withContext(Dispatchers.Main) {
        val js = """
            (function() {
                // Remove visual styling and scripts to get clean readable text
                var cloned = document.body.cloneNode(true);
                var toRemove = cloned.querySelectorAll('script, style, noscript, header, footer, nav');
                toRemove.forEach(function(el) { el.remove(); });
                return cloned.innerText || cloned.textContent || '';
            })()
        """.trimIndent()
        
        val rawText = evaluateJavascript(js) ?: ""
        val decodedText = try {
            org.json.JSONTokener(rawText).nextValue()?.toString() ?: rawText
        } catch (e: Exception) {
            rawText
        }
        
        decodedText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .take(15000)
    }

    private suspend fun evaluateJavascript(script: String): String? = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext null
        val deferred = CompletableDeferred<String?>()
        wv.evaluateJavascript(script) { result ->
            deferred.complete(result)
        }
        deferred.await()
    }
}

sealed class BrowserResult {
    data class Success(val url: String, val title: String, val content: String) : BrowserResult()
    data class Error(val message: String) : BrowserResult()
}
