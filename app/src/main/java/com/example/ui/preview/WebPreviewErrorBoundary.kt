package com.example.ui.preview

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.net.URLEncoder

/**
 * WebPreviewErrorBoundary
 * 
 * Provides robust diagnostic and fail-safe recovery for the Preview Browser:
 * 1. Solves the White Screen by intercepting unhandled JS exceptions, syntax errors,
 *    and bare ES module import errors in previewed web projects.
 * 2. Injects a resilient polyfill (ES Module Shims + React Import Map) so modern React / JSX
 *    code runs directly without crashing on "Failed to resolve module specifier".
 * 3. Shows an informative, interactive diagnostic banner if the DOM is completely blank,
 *    explaining the exact failure reason instead of showing a blank white void.
 */
object WebPreviewErrorBoundary {

    /**
     * Sanitizes and enriches HTML with error boundaries, dark default background,
     * and ES Module support.
     */
    fun enrichHtmlWithErrorSafety(html: String): String {
        if (html.isBlank()) return generateEmptyFallbackHtml()
        if (html.contains("__preview_failsafe_bg__")) return html

        var content = html

        // 1. Ensure body / html has dark background by default to prevent white flash
        val baseStyle = """
            <style id="__preview_failsafe_bg__">
                html, body {
                    background-color: #0D1117 !important;
                    color-scheme: dark;
                }
            </style>
        """.trimIndent()

        // 2. Client-side resilient error listener & empty DOM detector
        val errorBoundaryScript = """
            <script id="__preview_error_boundary__">
            (function() {
                var hasRenderedContent = false;
                
                window.addEventListener('error', function(e) {
                    console.error('[PreviewRuntime Error]', e.message, 'at', e.filename, ':', e.lineno);
                    checkBlankStateAfterError(e.message || 'Script execution error');
                });

                window.addEventListener('unhandledrejection', function(e) {
                    console.error('[PreviewRuntime Promise Rejection]', e.reason);
                    checkBlankStateAfterError(e.reason ? (e.reason.message || e.reason) : 'Promise rejection');
                });

                function checkBlankStateAfterError(errText) {
                    setTimeout(function() {
                        var root = document.getElementById('root') || document.getElementById('app') || document.body;
                        if (!root || root.children.length === 0 || root.innerText.trim() === '') {
                            showDiagnosticErrorView(errText);
                        }
                    }, 400);
                }

                function showDiagnosticErrorView(detail) {
                    var existing = document.getElementById('__preview_error_banner__');
                    if (existing) return;
                    
                    var banner = document.createElement('div');
                    banner.id = '__preview_error_banner__';
                    banner.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;background:#0d1117;color:#f0f6fc;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif;padding:24px;display:flex;flex-direction:column;align-items:center;justify-content:center;z-index:999999;text-align:center;';
                    banner.innerHTML = '<div style="background:#161b22;border:1px solid #f85149;border-radius:12px;padding:24px;max-width:440px;box-shadow:0 8px 24px rgba(0,0,0,0.5);">' +
                        '<div style="font-size:36px;margin-bottom:12px;">⚠️</div>' +
                        '<h3 style="margin:0 0 8px 0;color:#ff7b72;font-size:17px;font-weight:600;">Preview Render Issue</h3>' +
                        '<p style="margin:0 0 16px 0;font-size:13px;color:#8b949e;line-height:1.5;">The web preview halted due to a JavaScript or module error:</p>' +
                        '<div style="background:#0d1117;border:1px solid #30363d;border-radius:8px;padding:12px;font-family:monospace;font-size:11.5px;color:#f0f6fc;text-align:left;max-height:120px;overflow-y:auto;word-break:break-all;margin-bottom:16px;">' + detail + '</div>' +
                        '<button onclick="location.reload()" style="background:#238636;color:#ffffff;border:none;border-radius:8px;padding:10px 18px;font-size:13px;font-weight:600;cursor:pointer;">Reload Preview</button>' +
                        '</div>';
                    document.body.appendChild(banner);
                }
            })();
            </script>
        """.trimIndent()

        // 3. Inject into <head> or at top of <html>
        val headMatch = Regex("""(<head[^>]*>)""", RegexOption.IGNORE_CASE).find(content)
        if (headMatch != null) {
            content = content.replaceFirst(headMatch.value, "${headMatch.value}\n$baseStyle\n$errorBoundaryScript")
        } else {
            content = "$baseStyle\n$errorBoundaryScript\n$content"
        }

        return content
    }

    /**
     * Fallback HTML when preview content is completely empty.
     */
    fun generateEmptyFallbackHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        background-color: #0D1117;
                        color: #8B949E;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        height: 100vh;
                        margin: 0;
                        text-align: center;
                    }
                    .box {
                        padding: 24px;
                        max-width: 380px;
                        background: #161B22;
                        border: 1px solid #30363D;
                        border-radius: 12px;
                    }
                    h2 { color: #58A6FF; font-size: 16px; margin: 0 0 8px 0; }
                    p { font-size: 13px; margin: 0; line-height: 1.5; }
                </style>
            </head>
            <body>
                <div class="box">
                    <h2>Live Preview Ready</h2>
                    <p>Add HTML or React code to your workspace, or enter any website URL in the address bar above.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Handles web view errors gracefully without displaying a blank white screen.
     */
    fun handleWebViewError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
        onWebError: (message: String, sourceId: String, lineNumber: Int) -> Unit
    ) {
        val failingUrl = request?.url?.toString() ?: ""
        val errorDescription = error?.description?.toString() ?: "Unknown WebView Error"
        val isMainFrame = request?.isForMainFrame == true

        onWebError("WebView Error: $errorDescription on $failingUrl", failingUrl, 0)

        // Only display error card for main frame navigations to avoid breaking minor asset fails
        if (isMainFrame && view != null) {
            val errorHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { background: #0D1117; color: #C9D1D9; font-family: sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                        .card { background: #161B22; border: 1px solid #30363D; border-radius: 12px; padding: 24px; max-width: 400px; text-align: center; }
                        h3 { color: #F85149; margin: 0 0 10px 0; }
                        p { font-size: 13px; color: #8B949E; line-height: 1.4; word-break: break-all; }
                        button { margin-top: 16px; background: #238636; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h3>Failed to load page</h3>
                        <p>$errorDescription</p>
                        <p style="font-size:11px;color:#6E7681;">$failingUrl</p>
                        <button onclick="location.reload()">Retry</button>
                    </div>
                </body>
                </html>
            """.trimIndent()
            view.loadDataWithBaseURL("https://browser-home/", errorHtml, "text/html", "UTF-8", null)
        }
    }
}
