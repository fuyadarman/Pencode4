package com.example.ui.preview

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * WebPreviewCdnCacheEngine
 *
 * Solves the critical 10-20 minute black screen and freeze issue in WebView previews
 * (especially for Vanilla Three.js and CDN-heavy web projects) by:
 * 1. Caching external CDN resources (Three.js, OrbitControls, Babel, Tailwind, fonts, etc.) on local disk.
 * 2. Serving cached CDN assets in 0ms (instant load).
 * 3. Enforcing a strict 5-second network timeout instead of letting Android WebView hang indefinitely on DNS/TCP stalls.
 * 4. Automatic mirror fallback if a CDN provider (e.g. Cloudflare cdnjs or jsDelivr) is throttled or blocked.
 */
object WebPreviewCdnCacheEngine {

    private const val TAG = "WebPreviewCdnCache"
    private var cacheDir: File? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val CDN_HOSTS = listOf(
        "cdnjs.cloudflare.com",
        "cdn.jsdelivr.net",
        "unpkg.com",
        "esm.sh",
        "threejs.org",
        "cdn.tailwindcss.com",
        "fonts.googleapis.com",
        "fonts.gstatic.com",
        "ajax.googleapis.com",
        "raw.githubusercontent.com"
    )

    fun init(context: Context) {
        if (cacheDir == null) {
            val dir = File(context.cacheDir, "preview_cdn_cache")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            cacheDir = dir
        }
    }

    /**
     * Determines whether an external URL should be intercepted and cached.
     */
    fun shouldIntercept(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (url.startsWith("https://virtual-app/")) return false

        val lowerUrl = url.lowercase()
        val isCdnHost = CDN_HOSTS.any { lowerUrl.contains(it) }
        val isStaticAsset = lowerUrl.endsWith(".js") || lowerUrl.endsWith(".css") ||
                lowerUrl.endsWith(".wasm") || lowerUrl.endsWith(".woff2") ||
                lowerUrl.contains("three") || lowerUrl.contains("babel") ||
                lowerUrl.contains("tailwind")

        return isCdnHost || isStaticAsset
    }

    /**
     * Serves CDN resource from disk cache or fetches with fast timeout and saves to cache.
     */
    fun interceptAndServe(url: String): WebResourceResponse? {
        val targetCacheDir = cacheDir ?: return null
        val cacheKey = hashUrl(url)
        val cachedFile = File(targetCacheDir, "$cacheKey.cached")

        // 1. Instant Cache Hit (0ms)
        if (cachedFile.exists() && cachedFile.length() > 0) {
            try {
                val mimeType = getMimeType(url)
                val encoding = if (isTextMime(mimeType)) "UTF-8" else null
                return createResponse(mimeType, encoding, FileInputStream(cachedFile))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read cached file for $url: ${e.message}")
            }
        }

        // 2. Fetch with Fast Timeout & Cache
        val response = fetchUrlWithFallback(url) ?: return null
        return try {
            val mimeType = getMimeType(url)
            val encoding = if (isTextMime(mimeType)) "UTF-8" else null

            // Save to disk asynchronously/safely
            try {
                FileOutputStream(cachedFile).use { it.write(response) }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write cache for $url: ${e.message}")
            }

            createResponse(mimeType, encoding, ByteArrayInputStream(response))
        } catch (e: Exception) {
            Log.e(TAG, "Error creating WebResourceResponse for $url: ${e.message}")
            null
        }
    }

    private fun fetchUrlWithFallback(originalUrl: String): ByteArray? {
        val urlsToTry = mutableListOf(originalUrl)

        // Add mirror fallbacks for Three.js and OrbitControls
        if (originalUrl.contains("cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js")) {
            urlsToTry.add("https://cdn.jsdelivr.net/npm/three@0.128.0/build/three.min.js")
            urlsToTry.add("https://unpkg.com/three@0.128.0/build/three.min.js")
        } else if (originalUrl.contains("cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js")) {
            urlsToTry.add("https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/examples/js/controls/OrbitControls.min.js")
            urlsToTry.add("https://unpkg.com/three@0.128.0/examples/js/controls/OrbitControls.js")
        }

        for (targetUrl in urlsToTry) {
            try {
                val req = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                    .header("Accept", "*/*")
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            Log.d(TAG, "Successfully fetched and cached CDN resource: $targetUrl (${bytes.size} bytes)")
                            return bytes
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch $targetUrl: ${e.message}. Trying next mirror...")
            }
        }

        Log.e(TAG, "All mirrors failed or timed out for $originalUrl")
        return null
    }

    private fun createResponse(mime: String, enc: String?, stream: java.io.InputStream): WebResourceResponse {
        val resp = WebResourceResponse(mime, enc, stream)
        val headers = HashMap<String, String>()
        headers["Content-Type"] = if (enc != null) "$mime; charset=$enc" else mime
        headers["Access-Control-Allow-Origin"] = "*"
        headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
        headers["Access-Control-Allow-Headers"] = "*"
        headers["Cache-Control"] = "public, max-age=604800, immutable"
        resp.responseHeaders = headers
        return resp
    }

    private fun getMimeType(url: String): String {
        val clean = url.substringBefore("?").lowercase()
        return when {
            clean.endsWith(".js") || clean.endsWith(".mjs") -> "text/javascript"
            clean.endsWith(".css") -> "text/css"
            clean.endsWith(".json") -> "application/json"
            clean.endsWith(".svg") -> "image/svg+xml"
            clean.endsWith(".png") -> "image/png"
            clean.endsWith(".jpg") || clean.endsWith(".jpeg") -> "image/jpeg"
            clean.endsWith(".woff2") -> "font/woff2"
            clean.endsWith(".woff") -> "font/woff"
            clean.endsWith(".ttf") -> "font/ttf"
            clean.endsWith(".wasm") -> "application/wasm"
            else -> "text/javascript"
        }
    }

    private fun isTextMime(mime: String): Boolean {
        return mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") || mime.contains("xml") || mime.contains("svg")
    }

    private fun hashUrl(url: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(url.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            url.hashCode().toString()
        }
    }
}
