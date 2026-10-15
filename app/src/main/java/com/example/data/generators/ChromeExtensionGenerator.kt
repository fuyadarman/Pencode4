package com.example.data.generators

import com.example.data.ProjectFileEntity

object ChromeExtensionGenerator {

    fun getStarterFiles(projectName: String): List<ProjectFileEntity> {
        val sanitizedName = projectName.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
            .ifEmpty { "My Chrome Extension" }

        return listOf(
            // 1. Manifest V3 Configuration
            ProjectFileEntity(
                projectName = projectName,
                path = "manifest.json",
                content = """{
  "manifest_version": 3,
  "name": "$sanitizedName",
  "version": "1.0.0",
  "description": "A modern Manifest V3 Chrome Extension created with PenCode Studio.",
  "permissions": [
    "storage",
    "activeTab",
    "scripting",
    "tabs",
    "contextMenus"
  ],
  "host_permissions": [
    "<all_urls>"
  ],
  "action": {
    "default_popup": "popup/popup.html",
    "default_title": "$sanitizedName"
  },
  "background": {
    "service_worker": "background/service_worker.js",
    "type": "module"
  },
  "content_scripts": [
    {
      "matches": [
        "<all_urls>"
      ],
      "js": [
        "content/content_script.js"
      ],
      "css": [
        "content/content_style.css"
      ],
      "run_at": "document_idle"
    }
  ],
  "options_page": "options/options.html"
}"""
            ),

            // 2. Hub / In-App Live Preview Page (index.html)
            ProjectFileEntity(
                projectName = projectName,
                path = "index.html",
                content = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$sanitizedName - Extension Preview Hub</title>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg: #0B0D14;
            --card-bg: #141724;
            --card-border: #232738;
            --primary: #38BDF8;
            --primary-gradient: linear-gradient(135deg, #38BDF8, #818CF8);
            --text-main: #F8FAFC;
            --text-muted: #94A3B8;
            --accent-green: #34D399;
        }

        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
            font-family: 'Plus Jakarta Sans', sans-serif;
        }

        body {
            background-color: var(--bg);
            color: var(--text-main);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 24px 16px;
        }

        .container {
            max-width: 800px;
            width: 100%;
            display: flex;
            flex-direction: column;
            gap: 20px;
        }

        .header {
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            border-radius: 16px;
            padding: 24px;
            display: flex;
            align-items: center;
            gap: 16px;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.4);
        }

        .icon-box {
            width: 54px;
            height: 54px;
            border-radius: 14px;
            background: var(--primary-gradient);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 26px;
            font-weight: 800;
            color: #0B0D14;
            flex-shrink: 0;
        }

        .header-content h1 {
            font-size: 22px;
            font-weight: 800;
            background: var(--primary-gradient);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }

        .header-content p {
            color: var(--text-muted);
            font-size: 13px;
            margin-top: 4px;
        }

        .badge-mv3 {
            display: inline-block;
            background: rgba(56, 189, 248, 0.15);
            border: 1px solid rgba(56, 189, 248, 0.4);
            color: var(--primary);
            font-size: 11px;
            font-weight: 700;
            padding: 2px 8px;
            border-radius: 6px;
            margin-left: 8px;
        }

        .preview-grid {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
        }

        @media (max-width: 700px) {
            .preview-grid {
                grid-template-columns: 1fr;
            }
        }

        .card {
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            border-radius: 16px;
            padding: 20px;
            display: flex;
            flex-direction: column;
            gap: 14px;
        }

        .card-title {
            font-size: 16px;
            font-weight: 700;
            color: var(--text-main);
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .popup-frame-wrapper {
            background: #000;
            border: 2px solid #232738;
            border-radius: 12px;
            overflow: hidden;
            height: 480px;
            box-shadow: 0 8px 24px rgba(0, 0, 0, 0.6);
        }

        iframe {
            width: 100%;
            height: 100%;
            border: none;
        }

        .nav-links {
            display: flex;
            flex-direction: column;
            gap: 10px;
        }

        .nav-button {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 12px 16px;
            background: #1C2030;
            border: 1px solid #2D3349;
            border-radius: 10px;
            color: var(--text-main);
            text-decoration: none;
            font-size: 14px;
            font-weight: 600;
            transition: all 0.2s ease;
        }

        .nav-button:hover {
            border-color: var(--primary);
            background: #23283D;
            transform: translateX(4px);
        }

        .install-guide {
            background: rgba(52, 211, 153, 0.08);
            border: 1px solid rgba(52, 211, 153, 0.25);
            border-radius: 12px;
            padding: 16px;
        }

        .install-guide h3 {
            color: var(--accent-green);
            font-size: 14px;
            font-weight: 700;
            margin-bottom: 8px;
        }

        .install-guide ol {
            padding-left: 20px;
            color: #CBD5E1;
            font-size: 12.5px;
            line-height: 1.6;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="icon-box">🧩</div>
            <div class="header-content">
                <h1>$sanitizedName <span class="badge-mv3">Manifest V3</span></h1>
                <p>Chrome / Brave / Edge Extension Development & Live Simulator</p>
            </div>
        </div>

        <div class="preview-grid">
            <div class="card">
                <div class="card-title">
                    <span>📱 Live Popup Preview</span>
                    <span style="font-size: 12px; color: var(--accent-green);">● Active</span>
                </div>
                <div class="popup-frame-wrapper">
                    <iframe src="popup/popup.html"></iframe>
                </div>
            </div>

            <div class="card">
                <div class="card-title">
                    <span>⚡ Quick Navigation</span>
                </div>
                <div class="nav-links">
                    <a class="nav-button" href="popup/popup.html" target="_blank">
                        <span>🔍 Open Popup Standalone</span>
                        <span>↗</span>
                    </a>
                    <a class="nav-button" href="options/options.html" target="_blank">
                        <span>⚙️ Open Extension Options</span>
                        <span>↗</span>
                    </a>
                    <a class="nav-button" href="manifest.json" target="_blank">
                        <span>📄 Inspect manifest.json</span>
                        <span>↗</span>
                    </a>
                </div>

                <div class="install-guide">
                    <h3>🚀 How to Load in Chrome / Brave:</h3>
                    <ol>
                        <li>Push or download this repository.</li>
                        <li>Open <code>chrome://extensions</code> in your browser.</li>
                        <li>Enable <strong>Developer mode</strong> (top right switch).</li>
                        <li>Click <strong>Load unpacked</strong> and choose this folder!</li>
                    </ol>
                </div>
            </div>
        </div>
    </div>
</body>
</html>"""
            ),

            // 3. Popup UI (popup/popup.html)
            ProjectFileEntity(
                projectName = projectName,
                path = "popup/popup.html",
                content = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$sanitizedName Popup</title>
    <link rel="stylesheet" href="popup.css">
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
</head>
<body>
    <div class="popup-container">
        <!-- Header -->
        <header class="popup-header">
            <div class="logo-wrapper">
                <span class="logo-icon">⚡</span>
                <div>
                    <h2 class="app-title">$sanitizedName</h2>
                    <span class="app-status" id="connectionStatus">Ready</span>
                </div>
            </div>
            <button class="settings-btn" id="openOptionsBtn" title="Extension Settings">⚙️</button>
        </header>

        <!-- Main Stats / Active Tab Card -->
        <section class="tab-info-card">
            <div class="info-label">ACTIVE TAB</div>
            <div class="tab-title" id="tabTitle">Loading active tab...</div>
            <div class="tab-url" id="tabUrl">https://example.com</div>
        </section>

        <!-- Feature Controls -->
        <section class="controls-card">
            <div class="control-row">
                <div class="control-text">
                    <span class="control-title">Inspector Overlay</span>
                    <span class="control-desc">Highlight elements on page</span>
                </div>
                <label class="toggle-switch">
                    <input type="checkbox" id="toggleOverlay">
                    <span class="slider"></span>
                </label>
            </div>

            <div class="control-row">
                <div class="control-text">
                    <span class="control-title">Dark Mode Theme</span>
                    <span class="control-desc">Force dark reader theme</span>
                </div>
                <label class="toggle-switch">
                    <input type="checkbox" id="toggleDarkMode" checked>
                    <span class="slider"></span>
                </label>
            </div>
        </section>

        <!-- Action Buttons -->
        <div class="action-grid">
            <button class="btn btn-primary" id="pingWorkerBtn">
                <span>⚡ Ping Service Worker</span>
            </button>
            <button class="btn btn-secondary" id="injectContentBtn">
                <span>💉 Run Content Script</span>
            </button>
        </div>

        <!-- Output / Terminal Log Box -->
        <section class="log-container">
            <div class="log-header">
                <span>CONSOLE LOGS</span>
                <button id="clearLogsBtn" class="clear-btn">Clear</button>
            </div>
            <div class="log-body" id="logBody">
                <div class="log-entry log-info">[System] Extension popup initialized.</div>
            </div>
        </section>

        <!-- Footer -->
        <footer class="popup-footer">
            <span>Manifest V3 Extension</span>
            <span class="version-tag">v1.0.0</span>
        </footer>
    </div>

    <script src="popup.js" type="module"></script>
</body>
</html>"""
            ),

            // 4. Popup CSS (popup/popup.css)
            ProjectFileEntity(
                projectName = projectName,
                path = "popup/popup.css",
                content = """* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
    font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
}

:root {
    --bg-primary: #0F111A;
    --bg-card: #181B28;
    --border-color: #272B3F;
    --text-primary: #F8FAFC;
    --text-secondary: #94A3B8;
    --accent-blue: #38BDF8;
    --accent-purple: #818CF8;
    --accent-green: #34D399;
}

body {
    width: 360px;
    min-height: 440px;
    background-color: var(--bg-primary);
    color: var(--text-primary);
    overflow-x: hidden;
}

.popup-container {
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.popup-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--border-color);
}

.logo-wrapper {
    display: flex;
    align-items: center;
    gap: 10px;
}

.logo-icon {
    font-size: 20px;
    background: rgba(56, 189, 248, 0.15);
    border: 1px solid rgba(56, 189, 248, 0.3);
    padding: 6px;
    border-radius: 8px;
}

.app-title {
    font-size: 15px;
    font-weight: 700;
    color: var(--text-primary);
}

.app-status {
    font-size: 11px;
    color: var(--accent-green);
    font-weight: 600;
}

.settings-btn {
    background: transparent;
    border: none;
    font-size: 16px;
    cursor: pointer;
    padding: 4px;
    border-radius: 6px;
    transition: transform 0.2s ease;
}

.settings-btn:hover {
    transform: rotate(30deg);
}

.tab-info-card {
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: 12px;
    padding: 12px 14px;
}

.info-label {
    font-size: 10px;
    font-weight: 700;
    color: var(--accent-blue);
    letter-spacing: 0.5px;
    margin-bottom: 4px;
}

.tab-title {
    font-size: 13px;
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.tab-url {
    font-size: 11px;
    color: var(--text-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    margin-top: 2px;
}

.controls-card {
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: 12px;
    padding: 6px 14px;
}

.control-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 0;
}

.control-row:not(:last-child) {
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.control-title {
    display: block;
    font-size: 13px;
    font-weight: 600;
}

.control-desc {
    display: block;
    font-size: 11px;
    color: var(--text-secondary);
}

/* Switch */
.toggle-switch {
    position: relative;
    display: inline-block;
    width: 38px;
    height: 22px;
}

.toggle-switch input {
    opacity: 0;
    width: 0;
    height: 0;
}

.slider {
    position: absolute;
    cursor: pointer;
    top: 0; left: 0; right: 0; bottom: 0;
    background-color: #2D3349;
    transition: .3s;
    border-radius: 34px;
}

.slider:before {
    position: absolute;
    content: "";
    height: 16px;
    width: 16px;
    left: 3px;
    bottom: 3px;
    background-color: white;
    transition: .3s;
    border-radius: 50%;
}

input:checked + .slider {
    background-color: var(--accent-blue);
}

input:checked + .slider:before {
    transform: translateX(16px);
}

.action-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
}

.btn {
    padding: 10px 12px;
    border-radius: 10px;
    border: none;
    font-size: 12px;
    font-weight: 700;
    cursor: pointer;
    transition: all 0.2s ease;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
}

.btn-primary {
    background: linear-gradient(135deg, #38BDF8, #818CF8);
    color: #0B0D14;
}

.btn-primary:hover {
    filter: brightness(1.1);
    transform: translateY(-1px);
}

.btn-secondary {
    background: #23283D;
    color: var(--text-primary);
    border: 1px solid var(--border-color);
}

.btn-secondary:hover {
    background: #2D3349;
}

.log-container {
    background: #08090E;
    border: 1px solid var(--border-color);
    border-radius: 10px;
    padding: 10px;
}

.log-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 10px;
    font-weight: 700;
    color: var(--text-secondary);
    margin-bottom: 6px;
}

.clear-btn {
    background: none;
    border: none;
    color: var(--accent-blue);
    font-size: 10px;
    cursor: pointer;
}

.log-body {
    height: 70px;
    overflow-y: auto;
    font-family: monospace;
    font-size: 11px;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.log-entry {
    color: #CBD5E1;
    word-break: break-all;
}

.popup-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 11px;
    color: var(--text-secondary);
    padding-top: 6px;
    border-top: 1px solid var(--border-color);
}

.version-tag {
    background: rgba(255, 255, 255, 0.08);
    padding: 2px 6px;
    border-radius: 4px;
}"""
            ),

            // 5. Popup Logic (popup/popup.js)
            ProjectFileEntity(
                projectName = projectName,
                path = "popup/popup.js",
                content = """// Chrome Extension Popup Script (Manifest V3)

function appendLog(message, type = 'info') {
    const logBody = document.getElementById('logBody');
    if (!logBody) return;
    const entry = document.createElement('div');
    entry.className = 'log-entry log-' + type;
    const timestamp = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    entry.textContent = '[' + timestamp + '] ' + message;
    logBody.appendChild(entry);
    logBody.scrollTop = logBody.scrollHeight;
}

// Safely get active browser tab (supports Chrome Extension environment and fallback)
async function getActiveTab() {
    if (typeof chrome !== 'undefined' && chrome.tabs && chrome.tabs.query) {
        try {
            const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
            return tab;
        } catch (e) {
            console.warn('Error querying tabs:', e);
        }
    }
    return {
        title: 'Developer Preview Tab',
        url: window.location.href
    };
}

document.addEventListener('DOMContentLoaded', async () => {
    const tabTitleEl = document.getElementById('tabTitle');
    const tabUrlEl = document.getElementById('tabUrl');
    const openOptionsBtn = document.getElementById('openOptionsBtn');
    const pingWorkerBtn = document.getElementById('pingWorkerBtn');
    const injectContentBtn = document.getElementById('injectContentBtn');
    const clearLogsBtn = document.getElementById('clearLogsBtn');
    const toggleOverlay = document.getElementById('toggleOverlay');
    const toggleDarkMode = document.getElementById('toggleDarkMode');

    // 1. Fetch current tab info
    const tab = await getActiveTab();
    if (tab) {
        tabTitleEl.textContent = tab.title || 'Active Tab';
        tabUrlEl.textContent = tab.url || 'No URL';
        appendLog('Target tab: ' + (tab.title || 'Unknown'));
    }

    // 2. Load stored settings
    if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
        chrome.storage.local.get(['overlayEnabled', 'darkMode'], (result) => {
            if (result.overlayEnabled !== undefined) toggleOverlay.checked = result.overlayEnabled;
            if (result.darkMode !== undefined) toggleDarkMode.checked = result.darkMode;
            appendLog('Restored settings from chrome.storage');
        });
    }

    // 3. Save toggle changes
    toggleOverlay.addEventListener('change', (e) => {
        const isChecked = e.target.checked;
        if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
            chrome.storage.local.set({ overlayEnabled: isChecked });
        }
        appendLog('Overlay toggled: ' + (isChecked ? 'ON' : 'OFF'));
    });

    toggleDarkMode.addEventListener('change', (e) => {
        const isChecked = e.target.checked;
        if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
            chrome.storage.local.set({ darkMode: isChecked });
        }
        appendLog('Dark mode toggled: ' + (isChecked ? 'ON' : 'OFF'));
    });

    // 4. Ping Service Worker
    pingWorkerBtn.addEventListener('click', () => {
        appendLog('Sending ping to background service worker...');
        if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.sendMessage) {
            chrome.runtime.sendMessage({ action: 'PING', payload: { time: Date.now() } }, (response) => {
                if (chrome.runtime.lastError) {
                    appendLog('Worker Ping: ' + (chrome.runtime.lastError.message || 'Error'), 'error');
                } else if (response) {
                    appendLog('Worker Response: ' + (response.message || JSON.stringify(response)), 'success');
                }
            });
        } else {
            // Simulated response for in-app simulator
            setTimeout(() => {
                appendLog('Worker Response (Simulated): PONG from service worker!', 'success');
            }, 300);
        }
    });

    // 5. Inject Content Script Action
    injectContentBtn.addEventListener('click', async () => {
        appendLog('Dispatching message to content script...');
        if (typeof chrome !== 'undefined' && chrome.tabs && chrome.tabs.sendMessage) {
            const currentTab = await getActiveTab();
            if (currentTab && currentTab.id) {
                chrome.tabs.sendMessage(currentTab.id, { action: 'TRIGGER_HIGHLIGHT' }, (response) => {
                    if (chrome.runtime.lastError) {
                        appendLog('Content Script: ' + (chrome.runtime.lastError.message || 'Error'), 'error');
                    } else if (response) {
                        appendLog('Content Script: ' + (response.status || 'OK'), 'success');
                    }
                });
                return;
            }
        }
        // Simulated response for in-app simulator
        setTimeout(() => {
            appendLog('Content Script: Highlighting activated on page (Simulated).', 'success');
        }, 300);
    });

    // 6. Open Options Page
    openOptionsBtn.addEventListener('click', () => {
        if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.openOptionsPage) {
            chrome.runtime.openOptionsPage();
        } else {
            window.open('../options/options.html', '_blank');
        }
    });

    // 7. Clear Logs
    clearLogsBtn.addEventListener('click', () => {
        const logBody = document.getElementById('logBody');
        if (logBody) logBody.innerHTML = '';
    });
});"""
            ),

            // 6. Background Service Worker (background/service_worker.js)
            ProjectFileEntity(
                projectName = projectName,
                path = "background/service_worker.js",
                content = """// Background Service Worker (Manifest V3)

console.log('[Service Worker] Initializing extension background service worker...');

// 1. Installation Lifecycle Event
chrome.runtime.onInstalled.addListener((details) => {
    console.log('[Service Worker] Installed event triggered:', details.reason);

    // Initialize default local storage values
    chrome.storage.local.set({
        installedAt: Date.now(),
        overlayEnabled: false,
        darkMode: true,
        badgeCounter: 0
    });

    // Create a right-click context menu item
    if (chrome.contextMenus) {
        chrome.contextMenus.create({
            id: 'pencode_quick_inspect',
            title: 'Inspect with $sanitizedName',
            contexts: ['page', 'selection', 'link']
        });
    }

    // Set badge text
    if (chrome.action) {
        chrome.action.setBadgeText({ text: 'ON' });
        chrome.action.setBadgeBackgroundColor({ color: '#38BDF8' });
    }
});

// 2. Context Menu Click Listener
if (chrome.contextMenus) {
    chrome.contextMenus.onClicked.addListener((info, tab) => {
        console.log('[Service Worker] Context menu clicked:', info.menuItemId, 'on tab:', tab?.id);
        if (info.menuItemId === 'pencode_quick_inspect' && tab?.id) {
            chrome.tabs.sendMessage(tab.id, {
                action: 'CONTEXT_MENU_CLICKED',
                selectionText: info.selectionText || ''
            });
        }
    });
}

// 3. Message Dispatcher Listener
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    console.log('[Service Worker] Message received from:', sender.tab ? 'Content Script' : 'Popup', message);

    if (message.action === 'PING') {
        sendResponse({
            status: 'SUCCESS',
            message: 'PONG! Service worker is alive and responsive.',
            timestamp: Date.now()
        });
        return true; // Keep message channel open for async response
    }

    if (message.action === 'UPDATE_BADGE') {
        const count = message.count || '';
        chrome.action.setBadgeText({ text: String(count) });
        sendResponse({ status: 'BADGE_UPDATED' });
        return true;
    }

    sendResponse({ status: 'UNKNOWN_ACTION' });
    return false;
});"""
            ),

            // 7. Content Script (content/content_script.js)
            ProjectFileEntity(
                projectName = projectName,
                path = "content/content_script.js",
                content = """// Content Script injected into web pages

console.log('[$sanitizedName] Content script active on:', window.location.href);

// Create and inject a subtle floating status badge on the webpage
function createFloatingBadge() {
    if (document.getElementById('pencode-extension-badge')) return;

    const badge = document.createElement('div');
    badge.id = 'pencode-extension-badge';
    badge.innerHTML = `
        <div class="pencode-badge-inner">
            <span class="pencode-badge-dot"></span>
            <span class="pencode-badge-text">$sanitizedName</span>
        </div>
    `;
    document.body.appendChild(badge);
}

// Listen for messages from popup or background service worker
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    console.log('[$sanitizedName Content Script] Received message:', request);

    if (request.action === 'TRIGGER_HIGHLIGHT') {
        createFloatingBadge();
        
        // Highlight active headings on the page
        const headings = document.querySelectorAll('h1, h2, h3');
        headings.forEach(h => {
            h.style.outline = '2px dashed #38BDF8';
            h.style.outlineOffset = '4px';
        });

        sendResponse({ status: 'Page highlighted (' + headings.length + ' headings found)' });
        return true;
    }

    if (request.action === 'CONTEXT_MENU_CLICKED') {
        createFloatingBadge();
        alert('Selected text: ' + (request.selectionText || 'No text selected'));
        sendResponse({ status: 'PROCESSED' });
        return true;
    }

    return false;
});

// Auto-run badge creation when page loads
if (document.readyState === 'complete') {
    createFloatingBadge();
} else {
    window.addEventListener('load', createFloatingBadge);
}"""
            ),

            // 8. Content CSS (content/content_style.css)
            ProjectFileEntity(
                projectName = projectName,
                path = "content/content_style.css",
                content = """#pencode-extension-badge {
    position: fixed;
    bottom: 20px;
    right: 20px;
    z-index: 999999;
    font-family: 'Plus Jakarta Sans', -apple-system, sans-serif;
    pointer-events: auto;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

#pencode-extension-badge .pencode-badge-inner {
    display: flex;
    align-items: center;
    gap: 8px;
    background: rgba(15, 17, 26, 0.92);
    backdrop-filter: blur(10px);
    border: 1px solid rgba(56, 189, 248, 0.35);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
    padding: 8px 14px;
    border-radius: 9999px;
    color: #F8FAFC;
    font-size: 12px;
    font-weight: 700;
}

#pencode-extension-badge .pencode-badge-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background-color: #34D399;
    box-shadow: 0 0 8px #34D399;
}

#pencode-extension-badge:hover {
    transform: translateY(-2px) scale(1.04);
}"""
            ),

            // 9. Options Page (options/options.html)
            ProjectFileEntity(
                projectName = projectName,
                path = "options/options.html",
                content = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$sanitizedName - Options & Settings</title>
    <link rel="stylesheet" href="options.css">
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
</head>
<body>
    <div class="options-card">
        <header class="options-header">
            <h1>⚙️ Extension Options</h1>
            <p>Customize behavior and default configuration for $sanitizedName.</p>
        </header>

        <form id="optionsForm">
            <div class="form-group">
                <label for="apiKey">Custom API Key (Optional)</label>
                <input type="password" id="apiKey" placeholder="sk-...">
                <span class="help-text">Saved locally to chrome.storage.local.</span>
            </div>

            <div class="form-group">
                <label for="defaultTheme">Default Theme</label>
                <select id="defaultTheme">
                    <option value="dark">Dark Theme (Default)</option>
                    <option value="light">Light Theme</option>
                    <option value="system">Follow System</option>
                </select>
            </div>

            <div class="form-group">
                <label for="badgeCount">Custom Badge Text</label>
                <input type="text" id="badgeCount" placeholder="e.g. ON, 1, NEW" maxlength="4">
            </div>

            <div class="button-row">
                <button type="submit" class="btn-save">Save Preferences</button>
                <span id="statusMessage" class="status-msg"></span>
            </div>
        </form>
    </div>

    <script src="options.js" type="module"></script>
</body>
</html>"""
            ),

            // 10. Options CSS (options/options.css)
            ProjectFileEntity(
                projectName = projectName,
                path = "options/options.css",
                content = """* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
    font-family: 'Plus Jakarta Sans', sans-serif;
}

body {
    background-color: #0B0D14;
    color: #F8FAFC;
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
}

.options-card {
    background: #141724;
    border: 1px solid #232738;
    border-radius: 16px;
    padding: 32px;
    max-width: 500px;
    width: 100%;
    box-shadow: 0 12px 36px rgba(0, 0, 0, 0.5);
}

.options-header h1 {
    font-size: 22px;
    font-weight: 800;
    margin-bottom: 6px;
}

.options-header p {
    color: #94A3B8;
    font-size: 13px;
    margin-bottom: 24px;
}

.form-group {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-bottom: 20px;
}

label {
    font-size: 13px;
    font-weight: 600;
    color: #E2E8F0;
}

input, select {
    background: #0B0D14;
    border: 1px solid #2D3349;
    border-radius: 10px;
    padding: 12px 14px;
    color: #F8FAFC;
    font-size: 14px;
    outline: none;
    transition: border-color 0.2s;
}

input:focus, select:focus {
    border-color: #38BDF8;
}

.help-text {
    font-size: 11px;
    color: #64748B;
}

.button-row {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-top: 10px;
}

.btn-save {
    background: linear-gradient(135deg, #38BDF8, #818CF8);
    color: #0B0D14;
    border: none;
    border-radius: 10px;
    padding: 12px 24px;
    font-size: 14px;
    font-weight: 700;
    cursor: pointer;
    transition: filter 0.2s;
}

.btn-save:hover {
    filter: brightness(1.1);
}

.status-msg {
    font-size: 13px;
    color: #34D399;
    font-weight: 600;
}"""
            ),

            // 11. Options JS (options/options.js)
            ProjectFileEntity(
                projectName = projectName,
                path = "options/options.js",
                content = """document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('optionsForm');
    const apiKeyInput = document.getElementById('apiKey');
    const defaultThemeSelect = document.getElementById('defaultTheme');
    const badgeCountInput = document.getElementById('badgeCount');
    const statusMsg = document.getElementById('statusMessage');

    // Load saved options
    if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
        chrome.storage.local.get(['apiKey', 'defaultTheme', 'badgeText'], (data) => {
            if (data.apiKey) apiKeyInput.value = data.apiKey;
            if (data.defaultTheme) defaultThemeSelect.value = data.defaultTheme;
            if (data.badgeText) badgeCountInput.value = data.badgeText;
        });
    }

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        const settings = {
            apiKey: apiKeyInput.value.trim(),
            defaultTheme: defaultThemeSelect.value,
            badgeText: badgeCountInput.value.trim()
        };

        if (typeof chrome !== 'undefined' && chrome.storage && chrome.storage.local) {
            chrome.storage.local.set(settings, () => {
                if (settings.badgeText && chrome.action) {
                    chrome.action.setBadgeText({ text: settings.badgeText });
                }
                statusMsg.textContent = '✓ Saved successfully!';
                setTimeout(() => { statusMsg.textContent = ''; }, 2500);
            });
        } else {
            statusMsg.textContent = '✓ Saved locally (Simulated)!';
            setTimeout(() => { statusMsg.textContent = ''; }, 2500);
        }
    });
});"""
            ),

            // 12. GitHub Action Workflow to package extension (.github/workflows/build_extension.yml)
            ProjectFileEntity(
                projectName = projectName,
                path = ".github/workflows/build_extension.yml",
                content = """name: Build & Package Chrome Extension

on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  package:
    name: Validate & Bundle Extension (.zip)
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Validate manifest.json Syntax
        run: |
          python3 -c "import json; json.load(open('manifest.json'))"
          echo "manifest.json is valid JSON"

      - name: Create Chrome Extension ZIP Archive
        run: |
          zip -r chrome-extension.zip . -x ".git/*" -x ".github/*" -x "*.md"

      - name: Upload Extension Artifact
        uses: actions/upload-artifact@v4
        with:
          name: chrome-extension-package
          path: chrome-extension.zip
"""
            ),

            // 13. Documentation (README.md)
            ProjectFileEntity(
                projectName = projectName,
                path = "README.md",
                content = """# $sanitizedName

A Manifest V3 Chrome / Brave / Edge Extension scaffolded with PenCode Studio.

## 📁 Architecture Overview
- **`manifest.json`**: Manifest V3 specifications, permissions, action, background, content scripts, and options page.
- **`popup/`**: The popup menu that opens when users click the extension icon.
  - `popup.html`: Structure & layout
  - `popup.css`: Modern dark theme styling
  - `popup.js`: State management, tab communications, and storage
- **`background/`**:
  - `service_worker.js`: Modern event-driven background service worker for lifecycle, alarms, context menus, and storage.
- **`content/`**:
  - `content_script.js`: Injected into websites for DOM manipulation and event listening.
  - `content_style.css`: Styles for injected elements.
- **`options/`**:
  - `options.html` / `options.js`: Dedicated configuration page for user settings.
- **`index.html`**: Live in-browser development simulator hub.

## 🚀 How to Install & Test in Chrome / Brave / Edge:
1. Push your code to GitHub or download the repository ZIP.
2. In your browser, navigate to `chrome://extensions` (or `brave://extensions` / `edge://extensions`).
3. Toggle on **Developer mode** in the top right corner.
4. Click **Load unpacked**.
5. Select this project root folder.
6. Your extension is now live and testable!
"""
            )
        )
    }
}
