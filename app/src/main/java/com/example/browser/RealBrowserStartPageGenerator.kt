package com.example.browser

/**
 * Generates an interactive modern Start Page for the real preview browser.
 * Provides instant search (Google / DuckDuckGo), quick shortcuts, and live agent status.
 */
object RealBrowserStartPageGenerator {

    fun generateStartPageHtml(agentStatus: String? = null): String {
        val statusBanner = if (!agentStatus.isNullOrBlank()) {
            """
            <div class="agent-banner">
                <span class="pulse"></span>
                <span>$agentStatus</span>
            </div>
            """
        } else {
            """
            <div class="agent-banner idle">
                <span>🤖 AI Browser Agent: Standby (Ready to navigate, search, or automate)</span>
            </div>
            """
        }

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Browser Home</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    background: radial-gradient(circle at 50% 20%, #171b26 0%, #0a0c10 100%);
                    color: #f0f6fc;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    min-height: 100vh;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    padding: 30px 16px;
                }
                .container {
                    width: 100%;
                    max-width: 640px;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    gap: 24px;
                }
                .logo-container {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    margin-top: 20px;
                }
                .logo-icon {
                    width: 48px;
                    height: 48px;
                    background: linear-gradient(135deg, #38bdf8, #818cf8);
                    border-radius: 14px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 26px;
                    box-shadow: 0 4px 20px rgba(56, 189, 248, 0.3);
                }
                .logo-text {
                    font-size: 28px;
                    font-weight: 700;
                    letter-spacing: -0.5px;
                    background: linear-gradient(135deg, #ffffff, #94a3b8);
                    -webkit-background-clip: text;
                    -webkit-text-fill-color: transparent;
                }
                .agent-banner {
                    width: 100%;
                    padding: 10px 16px;
                    background: rgba(30, 41, 59, 0.7);
                    border: 1px solid #38bdf8;
                    border-radius: 12px;
                    font-size: 13px;
                    color: #38bdf8;
                    display: flex;
                    align-items: center;
                    gap: 10px;
                    backdrop-filter: blur(8px);
                }
                .agent-banner.idle {
                    border-color: #334155;
                    color: #94a3b8;
                }
                .pulse {
                    width: 8px;
                    height: 8px;
                    background: #38bdf8;
                    border-radius: 50%;
                    animation: pulse 1.5s infinite;
                }
                @keyframes pulse {
                    0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(56, 189, 248, 0.7); }
                    70% { transform: scale(1); box-shadow: 0 0 0 8px rgba(56, 189, 248, 0); }
                    100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(56, 189, 248, 0); }
                }
                .search-card {
                    width: 100%;
                    background: #161b22;
                    border: 1px solid #30363d;
                    border-radius: 16px;
                    padding: 6px 12px;
                    display: flex;
                    align-items: center;
                    gap: 10px;
                    box-shadow: 0 8px 24px rgba(0,0,0,0.4);
                }
                .search-input {
                    flex: 1;
                    background: transparent;
                    border: none;
                    outline: none;
                    color: #fff;
                    font-size: 15px;
                    padding: 10px 4px;
                }
                .search-btn {
                    background: #238636;
                    color: #fff;
                    border: none;
                    border-radius: 10px;
                    padding: 8px 16px;
                    font-size: 13px;
                    font-weight: 600;
                    cursor: pointer;
                }
                .shortcuts-grid {
                    width: 100%;
                    display: grid;
                    grid-template-columns: repeat(4, 1fr);
                    gap: 12px;
                }
                .shortcut-item {
                    background: #161b22;
                    border: 1px solid #21262d;
                    border-radius: 14px;
                    padding: 14px 8px;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    gap: 8px;
                    text-decoration: none;
                    color: #c9d1d9;
                    font-size: 12px;
                    font-weight: 500;
                    transition: all 0.2s ease;
                }
                .shortcut-item:active {
                    transform: scale(0.96);
                    background: #21262d;
                }
                .shortcut-icon {
                    width: 36px;
                    height: 36px;
                    border-radius: 10px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 18px;
                }
                .tag-bar {
                    display: flex;
                    gap: 8px;
                    flex-wrap: wrap;
                    justify-content: center;
                }
                .tag {
                    background: #1f242c;
                    border: 1px solid #30363d;
                    border-radius: 20px;
                    padding: 5px 12px;
                    font-size: 11.5px;
                    color: #8b949e;
                    text-decoration: none;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="logo-container">
                    <div class="logo-icon">🌐</div>
                    <div class="logo-text">PenCode Browser</div>
                </div>

                $statusBanner

                <form class="search-card" onsubmit="handleSearch(event)">
                    <span style="font-size: 18px;">🔍</span>
                    <input id="searchInput" class="search-input" type="text" placeholder="Search Google or enter web address..." autofocus autocomplete="off" />
                    <button type="submit" class="search-btn">Go</button>
                </form>

                <div class="shortcuts-grid">
                    <a class="shortcut-item" href="https://www.google.com">
                        <div class="shortcut-icon" style="background:#ea4335;">G</div>
                        Google
                    </a>
                    <a class="shortcut-item" href="https://github.com">
                        <div class="shortcut-icon" style="background:#24292e;">🐙</div>
                        GitHub
                    </a>
                    <a class="shortcut-item" href="https://developer.android.com">
                        <div class="shortcut-icon" style="background:#34a853;">🤖</div>
                        Android
                    </a>
                    <a class="shortcut-item" href="https://stackoverflow.com">
                        <div class="shortcut-icon" style="background:#f48024;">📚</div>
                        StackOverflow
                    </a>
                    <a class="shortcut-item" href="https://www.youtube.com">
                        <div class="shortcut-icon" style="background:#ff0000;">▶️</div>
                        YouTube
                    </a>
                    <a class="shortcut-item" href="https://huggingface.co">
                        <div class="shortcut-icon" style="background:#ffd21e; color: #000;">🤗</div>
                        HuggingFace
                    </a>
                    <a class="shortcut-item" href="https://en.wikipedia.org">
                        <div class="shortcut-icon" style="background:#555;">W</div>
                        Wikipedia
                    </a>
                    <a class="shortcut-item" href="https://duckduckgo.com">
                        <div class="shortcut-icon" style="background:#de5833;">🦆</div>
                        DuckDuckGo
                    </a>
                </div>

                <div class="tag-bar">
                    <span class="tag">⚡ Real Browser Engine</span>
                    <span class="tag">🤖 AI Browser Agent</span>
                    <span class="tag">🛡️ SSL Protected</span>
                    <span class="tag">📱 Responsive Viewport</span>
                </div>
            </div>

            <script>
                function handleSearch(e) {
                    e.preventDefault();
                    var q = document.getElementById('searchInput').value.trim();
                    if (!q) return;
                    if (q.startsWith('http://') || q.startsWith('https://')) {
                        window.location.href = q;
                    } else if (q.includes('.') && !q.includes(' ')) {
                        window.location.href = 'https://' + q;
                    } else {
                        window.location.href = 'https://www.google.com/search?q=' + encodeURIComponent(q);
                    }
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}
