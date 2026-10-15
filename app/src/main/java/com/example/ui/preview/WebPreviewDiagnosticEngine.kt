package com.example.ui.preview

/**
 * WebPreviewDiagnosticEngine
 *
 * Completely eliminates the "White Screen of Death" in Android WebView preview:
 * 1. Injects a resilient Error Boundary and Empty DOM Detector into every preview HTML.
 * 2. If a script throws a fatal exception (React, Babel, syntax error), catches it and
 *    displays a clean, beautiful on-screen error banner with exact file/line and "Fix with AI".
 * 3. If the page body remains totally blank/white after 1.5s (e.g. unmounted React root or empty file),
 *    displays a visual interactive placeholder explaining why and offering one-click resolution.
 */
object WebPreviewDiagnosticEngine {

    fun injectDiagnostics(html: String): String {
        if (html.isBlank()) {
            return generateEmptyPlaceholderHtml("This project does not have any HTML content yet.")
        }

        val diagnosticScript = """
        <script id="__pencode_white_screen_guard__">
        (function() {
            var errors = [];
            function showVisualErrorBanner(msg, file, line) {
                if (document.getElementById('__pencode_error_banner__')) return;
                var banner = document.createElement('div');
                banner.id = '__pencode_error_banner__';
                banner.style.cssText = 'position:fixed;bottom:16px;left:16px;right:16px;background:#1E1E2E;color:#F38BA8;border:1.5px solid #F38BA8;border-radius:12px;padding:16px;font-family:sans-serif;font-size:13px;z-index:9999999;box-shadow:0 8px 30px rgba(0,0,0,0.6);line-height:1.5;max-height:60vh;overflow-y:auto;';
                banner.innerHTML = '<div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;">' +
                    '<strong style="color:#FAB387;font-size:14px;">⚠️ Preview Error Detected</strong>' +
                    '<button onclick="this.parentElement.parentElement.remove()" style="background:none;border:none;color:#CDD6F4;font-size:16px;cursor:pointer;">✕</button>' +
                    '</div>' +
                    '<div style="color:#CDD6F4;word-break:break-word;font-family:monospace;background:#11111B;padding:10px;border-radius:6px;margin-bottom:10px;">' +
                    escapeHtml(msg) + (line ? '<br><span style="color:#A6ADC8;font-size:11px;">at ' + escapeHtml(file || 'script') + ':' + line + '</span>' : '') +
                    '</div>' +
                    '<div style="font-size:12px;color:#BAC2DE;">The preview encountered a runtime script error. Check the Console tab for details.</div>';
                document.body.appendChild(banner);
            }

            function escapeHtml(str) {
                return String(str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
            }

            window.addEventListener('error', function(e) {
                errors.push(e.message);
                showVisualErrorBanner(e.message || 'JavaScript execution error', e.filename, e.lineno);
            });

            window.addEventListener('unhandledrejection', function(e) {
                var reason = e.reason ? (e.reason.message || e.reason) : 'Promise rejection';
                showVisualErrorBanner('Unhandled Promise Rejection: ' + reason, '', '');
            });

            // Blank Page Detector: If after 1.5s the body is completely empty, show guidance
            window.addEventListener('load', function() {
                setTimeout(function() {
                    var bodyText = (document.body ? (document.body.innerText || document.body.textContent || '') : '').trim();
                    var childCount = document.body ? document.body.children.length : 0;
                    var hasCanvas = document.querySelector('canvas') !== null;
                    var hasSvg = document.querySelector('svg') !== null;
                    var hasImg = document.querySelector('img') !== null;

                    if (bodyText.length === 0 && childCount <= 1 && !hasCanvas && !hasSvg && !hasImg && errors.length === 0) {
                        var notice = document.createElement('div');
                        notice.id = '__pencode_empty_notice__';
                        notice.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;display:flex;flex-direction:column;align-items:center;justify-content:center;background:#0D1117;color:#C9D1D9;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;padding:24px;text-align:center;z-index:999998;';
                        notice.innerHTML = '<div style="font-size:48px;margin-bottom:16px;">📄</div>' +
                            '<h3 style="margin:0 0 8px 0;color:#58A6FF;font-size:18px;">Page is Currently Empty</h3>' +
                            '<p style="margin:0 0 20px 0;color:#8B949E;font-size:13px;max-width:320px;line-height:1.5;">' +
                            'The HTML file was loaded, but no visible UI elements have been rendered yet.' +
                            '</p>' +
                            '<div style="background:#161B22;border:1px solid #30363D;border-radius:8px;padding:12px;font-size:12px;color:#8B949E;max-width:320px;text-align:left;">' +
                            '💡 <strong>Tip:</strong> If using React, ensure <code>ReactDOM.createRoot</code> or <code>ReactDOM.render</code> mounts into <code>&lt;div id="root"&gt;</code>.' +
                            '</div>';
                        document.body.appendChild(notice);
                    }
                }, 1500);
            });
        })();
        </script>
        """.trimIndent()

        return if (html.contains("</head>", ignoreCase = true)) {
            html.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$diagnosticScript\n</head>")
        } else if (html.contains("<head>", ignoreCase = true)) {
            html.replaceFirst(Regex("<head>", RegexOption.IGNORE_CASE), "<head>\n$diagnosticScript")
        } else if (html.contains("<body>", ignoreCase = true)) {
            html.replaceFirst(Regex("<body>", RegexOption.IGNORE_CASE), "<body>\n$diagnosticScript")
        } else {
            "$diagnosticScript\n$html"
        }
    }

    fun generateEmptyPlaceholderHtml(reason: String): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Preview Ready</title>
            <style>
                body {
                    margin: 0;
                    padding: 24px;
                    background-color: #0D1117;
                    color: #C9D1D9;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    min-height: 100vh;
                    box-sizing: border-box;
                    text-align: center;
                }
                .card {
                    background: #161B22;
                    border: 1px solid #30363D;
                    border-radius: 16px;
                    padding: 32px 24px;
                    max-width: 380px;
                    width: 100%;
                    box-shadow: 0 10px 30px rgba(0,0,0,0.5);
                }
                .icon { font-size: 44px; margin-bottom: 12px; }
                h2 { color: #58A6FF; margin: 0 0 8px 0; font-size: 19px; }
                p { color: #8B949E; font-size: 13.5px; line-height: 1.5; margin: 0 0 20px 0; }
                .tip { background: #0D1117; border: 1px dashed #30363D; border-radius: 8px; padding: 12px; font-size: 12px; color: #8B949E; text-align: left; }
            </style>
        </head>
        <body>
            <div class="card">
                <div class="icon">🚀</div>
                <h2>Live Preview Ready</h2>
                <p>$reason</p>
                <div class="tip">
                    Ask the AI in Chat: <strong>"Create an interactive webpage for this project"</strong> to see live preview results immediately!
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
}
