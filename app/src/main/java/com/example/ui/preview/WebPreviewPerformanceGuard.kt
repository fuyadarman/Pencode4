package com.example.ui.preview

import android.util.Base64
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance guard for Android WebView live preview.
 * Prevents ANRs (Application Not Responding) when loading complex WebGL / Three.js
 * or large web projects by:
 * 1. Throttling console logs & error floods from high-FPS render loops.
 * 2. Instant 404 responses for non-existent virtual-app assets (bypassing DNS timeout stalls).
 * 3. Safe stream decoding for large binary / 3D models / textures.
 * 4. Bypassing heavy Babel preprocessing for vanilla JavaScript / Three.js.
 * 5. Clean teardown and release of WebGL contexts.
 */
object WebPreviewPerformanceGuard {

    private const val MAX_LOGS_PER_SECOND = 15
    private val logCounter = AtomicInteger(0)
    private val lastLogResetTime = AtomicLong(System.currentTimeMillis())
    private val lastSeenErrors = ConcurrentHashMap<String, Long>()

    /**
     * Checks if Babel preprocessing should be skipped.
     * Projects like Vanilla Three.js or standard HTML/JS do not need Babel
     * and running regexes over megabytes of HTML/JS blocks the main UI thread.
     */
    fun shouldSkipBabel(html: String): Boolean {
        if (html.isEmpty()) return true
        val hasBabelRef = html.contains("babel", ignoreCase = true)
        val hasBabelType = html.contains("text/babel", ignoreCase = true) || html.contains("text/jsx", ignoreCase = true)
        return !hasBabelRef && !hasBabelType
    }

    /**
     * Efficiently processes HTML for Babel and WebGL/Three.js preview optimizations.
     */
    fun preprocessHtmlSafely(html: String): String {
        var result = html
        if (!shouldSkipBabel(result)) {
            result = preprocessHtmlForBabelCore(result)
        }
        if (result.contains("three", ignoreCase = true) || result.contains("<canvas", ignoreCase = true)) {
            result = enhanceWebGLPreviewHtml(result)
        }
        return result
    }

    private fun enhanceWebGLPreviewHtml(html: String): String {
        var content = html
        // 1. Inject viewport meta if absent to prevent 980px zoom stalls
        if (!content.contains("name=\"viewport\"", ignoreCase = true) && !content.contains("name='viewport'", ignoreCase = true)) {
            val headMatch = Regex("""(<head[^>]*>)""", RegexOption.IGNORE_CASE).find(content)
            if (headMatch != null) {
                content = content.replaceFirst(headMatch.value, "${headMatch.value}\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">")
            }
        }

        // 2. Inject resilient auto-resize triggers so Three.js renders immediately after WebView layout completes
        if (!content.contains("__three_autoresize_guard__", ignoreCase = true)) {
            val resizeScript = """
                <script id="__three_autoresize_guard__">
                (function() {
                    function triggerResize() {
                        try { window.dispatchEvent(new Event('resize')); } catch (e) {}
                    }
                    if (document.readyState === 'complete') {
                        triggerResize();
                    } else {
                        window.addEventListener('load', function() {
                            triggerResize();
                            setTimeout(triggerResize, 80);
                            setTimeout(triggerResize, 300);
                        });
                    }
                })();
                </script>
            """.trimIndent()

            val bodyEndMatch = Regex("""(</body>)""", RegexOption.IGNORE_CASE).find(content)
            if (bodyEndMatch != null) {
                content = content.replaceFirst(bodyEndMatch.value, "$resizeScript\n${bodyEndMatch.value}")
            } else {
                content += "\n$resizeScript"
            }
        }
        return content
    }

    private fun preprocessHtmlForBabelCore(html: String): String {
        var content = html

        val babelCdnRegex = Regex("""(<script\s+[^>]*src=["'][^"']*babel\.min\.js["'][^>]*>\s*</script>)""", RegexOption.IGNORE_CASE)
        if (babelCdnRegex.containsMatchIn(content)) {
            content = babelCdnRegex.replace(content) { matchResult ->
                matchResult.value + "\n<script>\n" +
                        "if (window.Babel) {\n" +
                        "  Babel.registerPreset('react-classic', {\n" +
                        "    presets: [\n" +
                        "      [Babel.availablePresets['react'], { runtime: 'classic' }]\n" +
                        "    ]\n" +
                        "  });\n" +
                        "}\n" +
                        "</script>"
            }
        } else {
            val headRegex = Regex("""(<head>)""", RegexOption.IGNORE_CASE)
            if (headRegex.containsMatchIn(content)) {
                content = headRegex.replace(content) { matchResult ->
                    matchResult.value + "\n<script>\n" +
                            "window.addEventListener('DOMContentLoaded', () => {\n" +
                            "  if (window.Babel) {\n" +
                            "    Babel.registerPreset('react-classic', {\n" +
                            "      presets: [\n" +
                            "        [Babel.availablePresets['react'], { runtime: 'classic' }]\n" +
                            "      ]\n" +
                            "    });\n" +
                            "  }\n" +
                            "});\n" +
                            "</script>"
                }
            }
        }

        val dataPresetsRegex = Regex("""data-presets\s*=\s*["']([^"']*)\breact\b([^"']*)["']""", RegexOption.IGNORE_CASE)
        content = dataPresetsRegex.replace(content) { matchResult ->
            val before = matchResult.groups[1]?.value ?: ""
            val after = matchResult.groups[2]?.value ?: ""
            "data-presets=\"${before}react-classic${after}\""
        }

        val babelScriptRegex = Regex("""<script\s+type\s*=\s*["']text/babel["'](?![^>]*data-type\s*=)([^>]*)>""", RegexOption.IGNORE_CASE)
        content = babelScriptRegex.replace(content) { matchResult ->
            val attrs = matchResult.groups[1]?.value ?: ""
            "<script type=\"text/babel\" data-type=\"module\"$attrs>"
        }

        return content
    }

    /**
     * Determines whether a log should be forwarded or dropped to protect
     * the Compose UI and main thread from 60fps render loop log flooding.
     */
    fun shouldEmitLog(): Boolean {
        val now = System.currentTimeMillis()
        val lastReset = lastLogResetTime.get()
        if (now - lastReset > 1000L) {
            lastLogResetTime.set(now)
            logCounter.set(0)
            return true
        }
        return logCounter.incrementAndGet() <= MAX_LOGS_PER_SECOND
    }

    /**
     * Determines whether an error should be emitted or suppressed as duplicate.
     * Prevents animation loop exceptions (e.g. Three.js render frame errors)
     * from firing 60 times a second and starving the UI looper.
     */
    fun shouldEmitWebError(message: String, sourceId: String, line: Int): Boolean {
        val key = "$sourceId:$line:${message.take(120)}"
        val now = System.currentTimeMillis()
        val last = lastSeenErrors[key]
        if (last != null && (now - last) < 2000L) {
            return false // Suppress identical error within 2 seconds
        }
        lastSeenErrors[key] = now
        // Clean old entries if map grows
        if (lastSeenErrors.size > 200) {
            val cutoff = now - 10000L
            lastSeenErrors.entries.removeIf { it.value < cutoff }
        }
        return true
    }

    /**
     * Returns an immediate HTTP 404 response for virtual-app paths that do not exist.
     * This avoids letting WebView fall back to system DNS lookup for the fictitious
     * "virtual-app" domain, which freezes Chromium IO threads for seconds per missing asset.
     */
    fun createFast404Response(path: String): WebResourceResponse {
        val headers = HashMap<String, String>()
        headers["Access-Control-Allow-Origin"] = "*"
        headers["Content-Type"] = "text/plain; charset=utf-8"
        headers["Cache-Control"] = "no-cache, no-store"

        val response = WebResourceResponse(
            "text/plain",
            "UTF-8",
            404,
            "Not Found",
            headers,
            ByteArrayInputStream("File not found in project: $path".toByteArray(Charsets.UTF_8))
        )
        return response
    }

    /**
     * Safely reads file content into an InputStream without throwing memory exceptions
     * on large strings or corrupting binary files (3D models, textures, images).
     */
    fun createSafeContentStream(
        path: String,
        content: String,
        isBinary: Boolean,
        isIndexHtml: Boolean
    ): InputStream {
        if (!isBinary) {
            val processed = if (isIndexHtml) preprocessHtmlSafely(content) else content
            return ByteArrayInputStream(processed.toByteArray(Charsets.UTF_8))
        }

        // Binary content handling
        if (content.startsWith("data:") && content.contains(";base64,")) {
            val base64Data = content.substringAfter(";base64,")
            return try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                ByteArrayInputStream(bytes)
            } catch (e: Exception) {
                ByteArrayInputStream(content.toByteArray(Charsets.ISO_8859_1))
            }
        }

        // Check if raw string is clean base64
        val isLikelyBase64 = content.length > 20 && !content.contains(" ") && !content.contains("\n") &&
                (content.endsWith("=") || content.matches(Regex("^[A-Za-z0-9+/=]+$")))
        if (isLikelyBase64) {
            try {
                val bytes = Base64.decode(content, Base64.DEFAULT)
                return ByteArrayInputStream(bytes)
            } catch (e: Exception) {
                // Fallback to raw bytes
            }
        }

        return ByteArrayInputStream(content.toByteArray(Charsets.ISO_8859_1))
    }

    /**
     * Cleanly dismantles and releases a WebView to prevent WebGL contexts,
     * requestAnimationFrame loops, and audio/timers from lingering in memory.
     */
    fun safelyReleaseWebView(webView: WebView?) {
        WebPreviewLifecycleManager.releaseWebView(webView)
    }
}
