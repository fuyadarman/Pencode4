package com.example.browser

import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * BrowserConsoleSyncEngine
 *
 * Ensures that when AI reads browser console logs or error details,
 * the latest runtime console logs, uncaught exceptions, and network errors are dynamically
 * polled, extracted, and synced from the running WebView.
 */
object BrowserConsoleSyncEngine {

    data class CapturedConsoleEntry(
        val level: String,
        val message: String,
        val source: String? = null,
        val line: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Polls the live WebView for recent buffered console entries.
     */
    suspend fun fetchLatestConsoleLogs(webView: WebView?): List<CapturedConsoleEntry> = withContext(Dispatchers.Main) {
        if (webView == null) return@withContext emptyList()

        val js = """
            (function() {
                try {
                    var logs = window.__capturedConsoleLogs || [];
                    return JSON.stringify(logs.slice(-50));
                } catch(e) {
                    return "[]";
                }
            })()
        """.trimIndent()

        val deferred = CompletableDeferred<String>()
        webView.evaluateJavascript(js) { res ->
            deferred.complete(res ?: "[]")
        }
        val rawJson = deferred.await()

        val resultList = mutableListOf<CapturedConsoleEntry>()
        try {
            val unquoted = if (rawJson.startsWith("\"") && rawJson.endsWith("\"")) {
                org.json.JSONTokener(rawJson).nextValue().toString()
            } else rawJson
            val array = JSONArray(unquoted)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                resultList.add(
                    CapturedConsoleEntry(
                        level = obj.optString("level", "log"),
                        message = obj.optString("message", ""),
                        source = obj.optString("source", null),
                        line = obj.optInt("line", 0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}

        resultList
    }

    /**
     * Injects the console interception hook if not already present.
     */
    fun injectConsoleBufferHook(webView: WebView) {
        val hookJs = """
            (function() {
                if (window.__consoleHookInstalled) return;
                window.__consoleHookInstalled = true;
                window.__capturedConsoleLogs = window.__capturedConsoleLogs || [];
                
                function pushLog(level, args) {
                    try {
                        var msg = Array.prototype.slice.call(args).map(function(item) {
                            if (typeof item === 'object') {
                                try { return JSON.stringify(item); } catch(e) { return String(item); }
                            }
                            return String(item);
                        }).join(' ');
                        window.__capturedConsoleLogs.push({
                            level: level,
                            message: msg,
                            timestamp: Date.now()
                        });
                        if (window.__capturedConsoleLogs.length > 100) {
                            window.__capturedConsoleLogs.shift();
                        }
                    } catch(err) {}
                }

                var origLog = console.log;
                console.log = function() { pushLog('info', arguments); origLog.apply(console, arguments); };
                var origWarn = console.warn;
                console.warn = function() { pushLog('warn', arguments); origWarn.apply(console, arguments); };
                var origError = console.error;
                console.error = function() { pushLog('error', arguments); origError.apply(console, arguments); };
                
                window.addEventListener('error', function(e) {
                    pushLog('error', [e.message + ' at ' + (e.filename || '') + ':' + (e.lineno || '')]);
                });
                window.addEventListener('unhandledrejection', function(e) {
                    pushLog('error', ['Unhandled Promise Rejection: ' + (e.reason ? (e.reason.message || e.reason) : '')]);
                });
            })();
        """.trimIndent()

        webView.evaluateJavascript(hookJs, null)
    }
}
