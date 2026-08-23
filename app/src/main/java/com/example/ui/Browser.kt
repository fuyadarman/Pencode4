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
            try {
                WebView.enableSlowWholeDocumentDraw()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            webView = WebView(context).apply {
                val defaultWidth = 1080
                val defaultHeight = 1920
                layoutParams = android.view.ViewGroup.LayoutParams(defaultWidth, defaultHeight)
                measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(defaultWidth, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(defaultHeight, android.view.View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, defaultWidth, defaultHeight)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    allowFileAccess = true
                    allowContentAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    // Set a modern mobile user agent
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                }
                
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.let { wv ->
                            val w = if (wv.width > 0) wv.width else 1080
                            val h = if (wv.height > 0) wv.height else 1920
                            wv.measure(
                                android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                                android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY)
                            )
                            wv.layout(0, 0, w, h)
                        }
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

    suspend fun getPageSource(): BrowserResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext BrowserResult.Error("WebView is not initialized")
        val js = """
            (function() {
                return document.documentElement ? document.documentElement.outerHTML : (document.body ? document.body.outerHTML : '');
            })()
        """.trimIndent()
        val rawHtml = evaluateJavascript(js) ?: ""
        val decoded = try {
            org.json.JSONTokener(rawHtml).nextValue()?.toString() ?: rawHtml
        } catch (e: Exception) {
            rawHtml
        }
        val clean = if (decoded.length > 60000) decoded.take(60000) + "\n... [Truncated remaining source code for size]" else decoded
        BrowserResult.Success(url = wv.url ?: "", title = getPageTitle(), content = clean)
    }

    suspend fun inspectDom(selector: String?): BrowserResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext BrowserResult.Error("WebView is not initialized")
        val target = if (selector.isNullOrBlank()) "body" else selector.replace("'", "\\'")
        val js = """
            (function() {
                var el = document.querySelector('$target');
                if (!el) {
                    try {
                        var xpath = document.evaluate('$target', document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                        el = xpath.singleNodeValue;
                    } catch(e) {}
                }
                if (!el) return JSON.stringify({ error: "Element not found for selector: " + '$target' });
                
                function summarizeNode(node, depth) {
                    if (!node || depth > 4) return null;
                    if (node.nodeType === 3) {
                        var text = node.nodeValue.trim();
                        return text ? { text: text } : null;
                    }
                    if (node.nodeType !== 1) return null;
                    var attrs = {};
                    for (var i = 0; i < node.attributes.length; i++) {
                        attrs[node.attributes[i].name] = node.attributes[i].value;
                    }
                    var children = [];
                    for (var j = 0; j < node.childNodes.length; j++) {
                        var child = summarizeNode(node.childNodes[j], depth + 1);
                        if (child) children.push(child);
                    }
                    var rect = node.getBoundingClientRect();
                    return {
                        tag: node.tagName.toLowerCase(),
                        id: node.id || undefined,
                        className: node.className || undefined,
                        attributes: Object.keys(attrs).length > 0 ? attrs : undefined,
                        box: { width: Math.round(rect.width), height: Math.round(rect.height), top: Math.round(rect.top), left: Math.round(rect.left) },
                        childrenCount: node.children.length,
                        children: children.length > 0 ? children : undefined,
                        outerHTML: (depth <= 1 && node.outerHTML) ? node.outerHTML.substring(0, 4000) : undefined
                    };
                }
                return JSON.stringify(summarizeNode(el, 0), null, 2);
            })()
        """.trimIndent()
        val raw = evaluateJavascript(js) ?: ""
        val decoded = try {
            org.json.JSONTokener(raw).nextValue()?.toString() ?: raw
        } catch (e: Exception) {
            raw
        }
        BrowserResult.Success(url = wv.url ?: "", title = getPageTitle(), content = decoded)
    }

    suspend fun inspectCss(selector: String?): BrowserResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext BrowserResult.Error("WebView is not initialized")
        val target = if (selector.isNullOrBlank()) "body" else selector.replace("'", "\\'")
        val js = """
            (function() {
                var el = document.querySelector('$target');
                if (!el) return JSON.stringify({ error: "Element not found for selector: " + '$target' });
                
                var matchedRules = [];
                try {
                    for (var s = 0; s < document.styleSheets.length; s++) {
                        var sheet = document.styleSheets[s];
                        var rules = sheet.cssRules || sheet.rules;
                        if (!rules) continue;
                        for (var r = 0; r < rules.length; r++) {
                            var rule = rules[r];
                            if (rule.selectorText && el.matches && el.matches(rule.selectorText)) {
                                matchedRules.push({
                                    selector: rule.selectorText,
                                    cssText: rule.style.cssText
                                });
                            }
                        }
                    }
                } catch(e) {
                    matchedRules.push({ note: "Some cross-origin stylesheets could not be parsed: " + e.message });
                }
                
                return JSON.stringify({
                    target: '$target',
                    tagName: el.tagName.toLowerCase(),
                    inlineStyles: el.getAttribute('style') || '',
                    classes: el.className || '',
                    matchedCssRules: matchedRules
                }, null, 2);
            })()
        """.trimIndent()
        val raw = evaluateJavascript(js) ?: ""
        val decoded = try {
            org.json.JSONTokener(raw).nextValue()?.toString() ?: raw
        } catch (e: Exception) {
            raw
        }
        BrowserResult.Success(url = wv.url ?: "", title = getPageTitle(), content = decoded)
    }

    suspend fun getComputedStyles(selector: String?, properties: String?): BrowserResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext BrowserResult.Error("WebView is not initialized")
        val target = if (selector.isNullOrBlank()) "body" else selector.replace("'", "\\'")
        val propsList = properties?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val propsJsonArray = if (propsList.isNotEmpty()) {
            "[" + propsList.joinToString(",") { "'$it'" } + "]"
        } else {
            "['color', 'background-color', 'background', 'font-family', 'font-size', 'font-weight', 'line-height', 'letter-spacing', 'display', 'flex-direction', 'justify-content', 'align-items', 'grid-template-columns', 'gap', 'margin', 'padding', 'width', 'height', 'max-width', 'border', 'border-radius', 'box-shadow', 'opacity', 'position', 'z-index', 'overflow']"
        }
        val js = """
            (function() {
                var el = document.querySelector('$target');
                if (!el) return JSON.stringify({ error: "Element not found for selector: " + '$target' });
                var style = window.getComputedStyle(el);
                var reqProps = $propsJsonArray;
                var result = {};
                for (var i = 0; i < reqProps.length; i++) {
                    var p = reqProps[i];
                    result[p] = style.getPropertyValue(p) || style[p] || '';
                }
                var rect = el.getBoundingClientRect();
                result._boxModel = {
                    width: Math.round(rect.width) + 'px',
                    height: Math.round(rect.height) + 'px',
                    top: Math.round(rect.top) + 'px',
                    left: Math.round(rect.left) + 'px'
                };
                return JSON.stringify({ selector: '$target', computedStyles: result }, null, 2);
            })()
        """.trimIndent()
        val raw = evaluateJavascript(js) ?: ""
        val decoded = try {
            org.json.JSONTokener(raw).nextValue()?.toString() ?: raw
        } catch (e: Exception) {
            raw
        }
        BrowserResult.Success(url = wv.url ?: "", title = getPageTitle(), content = decoded)
    }

    suspend fun typeText(selector: String, text: String): BrowserResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext BrowserResult.Error("WebView is not initialized")
        val escapedSelector = selector.replace("'", "\\'")
        val escapedText = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        val js = """
            (function() {
                var el = document.querySelector('$escapedSelector');
                if (!el) {
                    try {
                        var xpath = document.evaluate('$escapedSelector', document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                        el = xpath.singleNodeValue;
                    } catch(e) {}
                }
                if (!el) return 'not_found';
                el.focus();
                if ('value' in el) {
                    el.value = '$escapedText';
                } else {
                    el.innerText = '$escapedText';
                }
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return 'typed';
            })()
        """.trimIndent()
        val res = evaluateJavascript(js)
        if (res == "\"typed\"") {
            BrowserResult.Success(url = wv.url ?: "", title = getPageTitle(), content = "Successfully typed into '$selector': \"$text\"")
        } else {
            BrowserResult.Error("Could not find element matching '$selector' to type text.")
        }
    }

    suspend fun scrollPage(direction: String?, amount: Int?): BrowserResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext BrowserResult.Error("WebView is not initialized")
        val px = amount ?: 600
        val dir = direction?.lowercase() ?: "down"
        val js = when (dir) {
            "up" -> "window.scrollBy({ top: -$px, behavior: 'smooth' }); 'scrolled up $px px'"
            "top" -> "window.scrollTo({ top: 0, behavior: 'smooth' }); 'scrolled to top'"
            "bottom" -> "window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' }); 'scrolled to bottom'"
            else -> "window.scrollBy({ top: $px, behavior: 'smooth' }); 'scrolled down $px px'"
        }
        evaluateJavascript(js)
        kotlinx.coroutines.delay(800)
        BrowserResult.Success(url = wv.url ?: "", title = getPageTitle(), content = "Scrolled page $dir ($px px).")
    }

    suspend fun getLinks(): BrowserResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext BrowserResult.Error("WebView is not initialized")
        val js = """
            (function() {
                var links = Array.from(document.querySelectorAll('a[href]')).map(function(a) {
                    return {
                        text: (a.innerText || a.textContent || '').trim().replace(/\s+/g, ' '),
                        href: a.href,
                        target: a.target || undefined
                    };
                }).filter(function(l) { return l.text.length > 0 || l.href.length > 0; });
                return JSON.stringify(links.slice(0, 100), null, 2);
            })()
        """.trimIndent()
        val raw = evaluateJavascript(js) ?: "[]"
        val decoded = try {
            org.json.JSONTokener(raw).nextValue()?.toString() ?: raw
        } catch (e: Exception) {
            raw
        }
        BrowserResult.Success(url = wv.url ?: "", title = getPageTitle(), content = decoded)
    }

    suspend fun getImages(): BrowserResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext BrowserResult.Error("WebView is not initialized")
        val js = """
            (function() {
                var imgs = Array.from(document.querySelectorAll('img, svg, picture source')).map(function(el) {
                    var src = el.src || el.getAttribute('srcset') || el.getAttribute('data-src') || '';
                    var alt = el.alt || el.getAttribute('aria-label') || '';
                    var rect = el.getBoundingClientRect();
                    return {
                        tag: el.tagName.toLowerCase(),
                        src: src,
                        alt: alt,
                        width: Math.round(rect.width),
                        height: Math.round(rect.height),
                        isSvg: el.tagName.toLowerCase() === 'svg'
                    };
                }).filter(function(img) { return img.src.length > 0 || img.isSvg; });
                return JSON.stringify(imgs.slice(0, 60), null, 2);
            })()
        """.trimIndent()
        val raw = evaluateJavascript(js) ?: "[]"
        val decoded = try {
            org.json.JSONTokener(raw).nextValue()?.toString() ?: raw
        } catch (e: Exception) {
            raw
        }
        BrowserResult.Success(url = wv.url ?: "", title = getPageTitle(), content = decoded)
    }

    suspend fun getFonts(): BrowserResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext BrowserResult.Error("WebView is not initialized")
        val js = """
            (function() {
                var fontLinks = Array.from(document.querySelectorAll('link[href*="fonts"], link[href*="font"]')).map(function(l) { return l.href; });
                var fontFamilies = new Set();
                var elements = Array.from(document.querySelectorAll('body, h1, h2, h3, h4, h5, h6, p, a, button, span, input, header, nav'));
                elements.forEach(function(el) {
                    var cs = window.getComputedStyle(el);
                    if (cs && cs.fontFamily) {
                        fontFamilies.add(cs.fontFamily + ' (weight: ' + cs.fontWeight + ', size: ' + cs.fontSize + ')');
                    }
                });
                return JSON.stringify({
                    externalFontLinks: fontLinks,
                    activeTypography: Array.from(fontFamilies)
                }, null, 2);
            })()
        """.trimIndent()
        val raw = evaluateJavascript(js) ?: "{}"
        val decoded = try {
            org.json.JSONTokener(raw).nextValue()?.toString() ?: raw
        } catch (e: Exception) {
            raw
        }
        BrowserResult.Success(url = wv.url ?: "", title = getPageTitle(), content = decoded)
    }

    suspend fun runJavascript(script: String): String = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext "Error: WebView is not initialized"
        val raw = evaluateJavascript(script) ?: "null"
        try {
            org.json.JSONTokener(raw).nextValue()?.toString() ?: raw
        } catch (e: Exception) {
            raw
        }
    }

    suspend fun captureScreenshot(): Bitmap? = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext null
        try {
            val width = if (wv.width > 0) wv.width else 1080
            val height = if (wv.height > 0) wv.height else 1920

            wv.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
            )
            wv.layout(0, 0, width, height)

            kotlinx.coroutines.delay(300)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            wv.draw(canvas)

            if (!isBitmapBlank(bitmap)) {
                return@withContext bitmap
            }

            // Fallback drawing cache
            try {
                wv.isDrawingCacheEnabled = true
                wv.buildDrawingCache()
                val cacheBitmap = wv.drawingCache
                if (cacheBitmap != null && !isBitmapBlank(cacheBitmap)) {
                    val copy = cacheBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    wv.isDrawingCacheEnabled = false
                    return@withContext copy
                }
                wv.isDrawingCacheEnabled = false
            } catch (e: Exception) {
                // Ignore fallback error
            }

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isBitmapBlank(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return true
        var firstPixel: Int? = null
        var allSame = true
        for (stepX in 1..9) {
            for (stepY in 1..9) {
                val px = bitmap.getPixel((w * stepX) / 10, (h * stepY) / 10)
                if (firstPixel == null) {
                    firstPixel = px
                } else if (px != firstPixel) {
                    allSame = false
                    break
                }
            }
            if (!allSame) break
        }
        return allSame && (firstPixel == -1 || firstPixel == 0)
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
