package com.example.browser

import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * UniversalMediaDownloadDetector
 *
 * Detects all downloadable media (videos, audio, images, files, documents) on any webpage
 * even if there is no explicit download button!
 */
object UniversalMediaDownloadDetector {

    data class DetectedMedia(
        val type: String, // "video", "audio", "image", "document", "file"
        val url: String,
        val title: String,
        val extension: String
    )

    suspend fun scanPageForMedia(webView: WebView?): List<DetectedMedia> = withContext(Dispatchers.Main) {
        if (webView == null) return@withContext emptyList()

        val js = """
            (function() {
                var items = [];
                var seen = {};

                function add(type, url, title, ext) {
                    if (!url || url.startsWith('javascript:') || url.startsWith('about:') || seen[url]) return;
                    seen[url] = true;
                    items.push({ type: type, url: url, title: title || type, ext: ext || '' });
                }

                // 1. Videos
                document.querySelectorAll('video').forEach(function(v, idx) {
                    if (v.src) add('video', v.src, 'Video ' + (idx + 1), 'mp4');
                    v.querySelectorAll('source').forEach(function(s, sIdx) {
                        if (s.src) add('video', s.src, 'Video Source ' + (sIdx + 1), 'mp4');
                    });
                });

                // 2. Audios
                document.querySelectorAll('audio').forEach(function(a, idx) {
                    if (a.src) add('audio', a.src, 'Audio ' + (idx + 1), 'mp3');
                    a.querySelectorAll('source').forEach(function(s, sIdx) {
                        if (s.src) add('audio', s.src, 'Audio Source ' + (sIdx + 1), 'mp3');
                    });
                });

                // 3. Images
                document.querySelectorAll('img').forEach(function(img, idx) {
                    if (img.src && !img.src.startsWith('data:image/svg')) {
                        var alt = img.alt || ('Image ' + (idx + 1));
                        add('image', img.src, alt, 'png');
                    }
                });

                // 4. Downloadable file links
                var fileExtensions = ['zip', 'tar', 'gz', 'apk', 'pdf', 'mp3', 'mp4', 'wav', 'png', 'jpg', 'jpeg', 'webp', 'svg', 'json', 'csv'];
                document.querySelectorAll('a[href]').forEach(function(a) {
                    var href = a.href;
                    var lower = href.toLowerCase();
                    for (var i = 0; i < fileExtensions.length; i++) {
                        var ext = fileExtensions[i];
                        if (lower.indexOf('.' + ext) !== -1 || a.hasAttribute('download')) {
                            var text = a.innerText.trim() || a.getAttribute('download') || ('File ' + ext.toUpperCase());
                            add('file', href, text, ext);
                            break;
                        }
                    }
                });

                return JSON.stringify(items.slice(0, 50));
            })()
        """.trimIndent()

        val deferred = CompletableDeferred<String>()
        webView.evaluateJavascript(js) { res ->
            deferred.complete(res ?: "[]")
        }
        val rawJson = deferred.await()

        val list = mutableListOf<DetectedMedia>()
        try {
            val unquoted = if (rawJson.startsWith("\"") && rawJson.endsWith("\"")) {
                org.json.JSONTokener(rawJson).nextValue().toString()
            } else rawJson
            val array = JSONArray(unquoted)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DetectedMedia(
                        type = obj.optString("type", "file"),
                        url = obj.optString("url", ""),
                        title = obj.optString("title", "Media"),
                        extension = obj.optString("ext", "")
                    )
                )
            }
        } catch (_: Exception) {}

        list
    }
}
