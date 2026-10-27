package com.example.browser

import com.example.data.VibeRepository
import com.example.ui.BackgroundBrowser
import com.example.ui.BrowserResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Autonomous Browser Controller AI Agent Engine.
 * Enables AI to act like a real browser controller agent / human:
 * - Scans & indexes interactive elements (buttons, inputs, typebars, links, forms) with numeric tags [1], [2], etc.
 * - Performs human-like actions: click, type, clear, submit, scroll, select options.
 * - Deeply inspects and clones complete website UI designs into clean, editable code templates.
 */
object BrowserControllerAgentEngine {

    suspend fun getInteractiveSnapshot(backgroundBrowser: BackgroundBrowser): String = withContext(Dispatchers.Main) {
        val js = """
            (function() {
                var interactiveElements = [];
                var all = document.querySelectorAll('button, input, textarea, select, a[href], [role="button"], [role="link"], [role="checkbox"], [tabindex="0"]');
                var count = 0;
                
                for (var i = 0; i < all.length && count < 60; i++) {
                    var el = all[i];
                    var rect = el.getBoundingClientRect();
                    // Must be visible on screen
                    if (rect.width === 0 && rect.height === 0) continue;
                    var style = window.getComputedStyle(el);
                    if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') continue;
                    
                    count++;
                    el.setAttribute('data-agent-index', count);
                    
                    var tag = el.tagName.toLowerCase();
                    var type = el.getAttribute('type') || '';
                    var text = (el.innerText || el.textContent || el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.getAttribute('value') || '').trim().replace(/\s+/g, ' ');
                    var id = el.id ? '#' + el.id : '';
                    var name = el.getAttribute('name') ? '[name="' + el.getAttribute('name') + '"]' : '';
                    var placeholder = el.getAttribute('placeholder') || '';
                    var currentVal = el.value || '';
                    var href = el.getAttribute('href') || '';
                    
                    interactiveElements.push({
                        index: count,
                        tag: tag,
                        type: type,
                        id: el.id || undefined,
                        text: text.substring(0, 80),
                        placeholder: placeholder || undefined,
                        value: currentVal ? currentVal.substring(0, 50) : undefined,
                        href: href ? href.substring(0, 80) : undefined,
                        selector: id || name || (tag + (type ? '[type="' + type + '"]' : ''))
                    });
                }
                
                return JSON.stringify({
                    url: window.location.href,
                    title: document.title,
                    elementCount: interactiveElements.length,
                    elements: interactiveElements
                });
            })()
        """.trimIndent()

        val raw = if (LivePreviewBrowserManager.isAvailable()) {
            LivePreviewBrowserManager.runJavascript(js)
        } else {
            backgroundBrowser.runJavascript(js)
        }
        try {
            val decoded = try {
                val trimmed = raw.trim()
                if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                    org.json.JSONTokener(trimmed).nextValue()?.toString() ?: trimmed.removeSurrounding("\"")
                } else {
                    trimmed
                }
            } catch (_: Exception) {
                raw.removeSurrounding("\"")
            }
            val obj = JSONObject(decoded)
            val title = obj.optString("title", "Untitled")
            val url = obj.optString("url", "")
            val elements = obj.optJSONArray("elements") ?: JSONArray()

            val sb = StringBuilder()
            sb.append("=== BROWSER INTERACTIVE SNAPSHOT ===\n")
            sb.append("Page: $title\nURL: $url\n\nInteractive Elements List (use index or selector):\n")

            if (elements.length() == 0) {
                sb.append("No active clickable/input elements found on current viewport.\n")
            } else {
                for (i in 0 until elements.length()) {
                    val el = elements.getJSONObject(i)
                    val idx = el.optInt("index", i + 1)
                    val tag = el.optString("tag", "")
                    val type = el.optString("type", "")
                    val text = el.optString("text", "")
                    val placeholder = el.optString("placeholder", "")
                    val href = el.optString("href", "")
                    val selector = el.optString("selector", "")

                    val typeStr = if (type.isNotBlank()) " type='$type'" else ""
                    val infoStr = when {
                        placeholder.isNotBlank() -> "placeholder=\"$placeholder\""
                        text.isNotBlank() -> "\"$text\""
                        href.isNotBlank() -> "-> $href"
                        else -> ""
                    }
                    sb.append("[$idx] <$tag$typeStr> $infoStr  (selector: '$selector')\n")
                }
            }
            sb.toString()
        } catch (e: Exception) {
            "Browser Interactive Snapshot (raw):\n$raw"
        }
    }

    suspend fun executeBrowserAction(
        action: String, // "click", "type", "scroll", "select", "download", "upload", "submit"
        selector: String? = null,
        elementIndex: Int? = null,
        text: String? = null,
        clearBefore: Boolean = true,
        pressEnter: Boolean = false,
        direction: String? = "down",
        amount: Int? = 600,
        backgroundBrowser: BackgroundBrowser,
        filePath: String? = null,
        destinationPath: String? = null,
        projectName: String? = null,
        repository: VibeRepository? = null,
        normalizePath: ((String) -> String)? = null
    ): String = withContext(Dispatchers.Main) {
        val cleanSelector = selector?.trim()?.let {
            if (it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true) || it.isBlank()) null else it
        }
        val parsedIndex = cleanSelector?.let { s ->
            if (s.startsWith("[") && s.endsWith("]")) {
                s.substring(1, s.length - 1).trim().toIntOrNull()
            } else if (s.all { it.isDigit() }) {
                s.toIntOrNull()
            } else null
        }
        val effectiveIndex = elementIndex ?: parsedIndex
        val targetSelector = when {
            effectiveIndex != null && effectiveIndex > 0 -> "[data-agent-index='$effectiveIndex']"
            cleanSelector != null && parsedIndex == null -> cleanSelector
            else -> ""
        }

        val escapedSelector = JSONObject.quote(targetSelector)

        when (action.lowercase().trim()) {
            "click" -> {
                if (targetSelector.isBlank()) {
                    return@withContext "Error: Please specify 'selector' or 'elementIndex' to click."
                }
                val js = """
                    (function() {
                        try {
                            var sel = $escapedSelector;
                            var el = null;
                            try { el = document.querySelector(sel); } catch(e) {}
                            if (!el) {
                                try {
                                    var xp = document.evaluate(sel, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                                    el = xp.singleNodeValue;
                                } catch(e) {}
                            }
                            if (!el) {
                                try { el = document.getElementById(sel); } catch(e) {}
                            }
                            if (!el) {
                                try { el = document.querySelector('[name="' + sel + '"], [aria-label="' + sel + '"], [placeholder="' + sel + '"]'); } catch(e) {}
                            }
                            if (!el) {
                                try {
                                    var lower = sel.toLowerCase();
                                    var candidates = Array.from(document.querySelectorAll('button, a, input, [role="button"], [role="link"], span, div'));
                                    for (var i = 0; i < candidates.length; i++) {
                                        var c = candidates[i];
                                        var txt = (c.innerText || c.value || c.getAttribute('aria-label') || '').trim().toLowerCase();
                                        if (txt === lower || (txt.length > 0 && lower.length > 2 && txt.indexOf(lower) !== -1)) {
                                            el = c;
                                            break;
                                        }
                                    }
                                } catch(e) {}
                            }
                            if (!el) return 'Element not found: ' + sel;
                            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            el.focus();
                            // Prevent accidental full page reload when clicking submit buttons or hash links
                            try {
                                var tagName = el.tagName.toLowerCase();
                                if ((tagName === 'button' || (tagName === 'input' && (el.type === 'submit' || el.type === 'button'))) && el.form) {
                                    el.addEventListener('click', function(ev) { ev.preventDefault(); }, { once: true, capture: true });
                                } else if (tagName === 'a') {
                                    var href = (el.getAttribute('href') || '').trim();
                                    if (href === '#' || href === '' || href.indexOf('javascript:') === 0) {
                                        el.addEventListener('click', function(ev) { ev.preventDefault(); }, { once: true, capture: true });
                                    }
                                }
                            } catch(pe) {}
                            el.click();
                            return 'OK';
                        } catch(err) {
                            return 'JS Exception: ' + (err.message || err.toString());
                        }
                    })()
                """.trimIndent()
                val rawRes = if (LivePreviewBrowserManager.isAvailable()) {
                    LivePreviewBrowserManager.runJavascript(js)
                } else {
                    backgroundBrowser.runJavascript(js)
                }
                val res = rawRes.removeSurrounding("\"").trim()
                if (res.contains("OK")) {
                    kotlinx.coroutines.delay(800)
                    val title = if (LivePreviewBrowserManager.isAvailable()) {
                        LivePreviewBrowserManager.getActiveWebView()?.title ?: ""
                    } else {
                        backgroundBrowser.readPageContent().let { if (it is BrowserResult.Success) it.title else "" }
                    }
                    "Successfully clicked '$targetSelector'. Current Page Title: '$title'"
                } else if (res == "null" || res.isBlank()) {
                    "Error clicking element '$targetSelector': Page did not respond. Verify the element exists on current webpage."
                } else {
                    "Error clicking element: $res"
                }
            }

            "type" -> {
                BrowserActionExecutionEngine.executeTypeAction(
                    selector = cleanSelector,
                    elementIndex = effectiveIndex,
                    text = text,
                    clearBefore = clearBefore,
                    pressEnter = pressEnter,
                    backgroundBrowser = backgroundBrowser
                )
            }

            "scroll" -> {
                val dir = direction?.lowercase() ?: "down"
                val px = amount ?: 600
                val js = when (dir) {
                    "up" -> "window.scrollBy({ top: -$px, behavior: 'smooth' }); 'scrolled up $px px'"
                    "top" -> "window.scrollTo({ top: 0, behavior: 'smooth' }); 'scrolled to top'"
                    "bottom" -> "window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' }); 'scrolled to bottom'"
                    else -> "window.scrollBy({ top: $px, behavior: 'smooth' }); 'scrolled down $px px'"
                }
                if (LivePreviewBrowserManager.isAvailable()) {
                    LivePreviewBrowserManager.runJavascript(js)
                } else {
                    backgroundBrowser.runJavascript(js)
                }
                kotlinx.coroutines.delay(600)
                "Successfully scrolled page $dir by $px px."
            }

            "select" -> {
                if (targetSelector.isBlank()) {
                    return@withContext "Error: Please specify 'selector' or 'elementIndex' for select."
                }
                val optionVal = JSONObject.quote(text ?: "")
                val js = """
                    (function() {
                        try {
                            var sel = $escapedSelector;
                            var el = document.querySelector(sel);
                            if (!el) return 'Element not found: ' + sel;
                            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            el.value = $optionVal;
                            el.dispatchEvent(new Event('change', { bubbles: true }));
                            return 'OK';
                        } catch(err) {
                            return 'JS Exception: ' + (err.message || err.toString());
                        }
                    })()
                """.trimIndent()
                val rawRes = if (LivePreviewBrowserManager.isAvailable()) {
                    LivePreviewBrowserManager.runJavascript(js)
                } else {
                    backgroundBrowser.runJavascript(js)
                }
                val res = rawRes.removeSurrounding("\"").trim()
                if (res.contains("OK")) {
                    "Successfully selected option \"$text\" on '$targetSelector'."
                } else {
                    "Error selecting option: $res"
                }
            }

            "download" -> {
                if (projectName != null && repository != null && normalizePath != null) {
                    BrowserTransferEngine.downloadFromBrowser(
                        urlOrSelector = text?.ifBlank { null } ?: targetSelector.ifBlank { null },
                        destinationPath = destinationPath,
                        projectName = projectName,
                        repository = repository,
                        backgroundBrowser = backgroundBrowser,
                        normalizePath = normalizePath
                    )
                } else {
                    "Error: Missing workspace repository context for browser download."
                }
            }

            "upload" -> {
                val targetFile = filePath ?: text
                if (targetFile.isNullOrBlank()) {
                    return@withContext "Error: Please specify 'filePath' or 'text' with the project file path to upload."
                }
                if (projectName != null && repository != null && normalizePath != null) {
                    BrowserTransferEngine.uploadToBrowser(
                        filePath = targetFile,
                        selector = targetSelector.ifBlank { null },
                        elementIndex = elementIndex,
                        projectName = projectName,
                        repository = repository,
                        backgroundBrowser = backgroundBrowser,
                        normalizePath = normalizePath
                    )
                } else {
                    "Error: Missing workspace repository context for browser upload."
                }
            }

            "submit" -> {
                val js = """
                    (function() {
                        try {
                            var sel = $escapedSelector;
                            var el = null;
                            if (sel) {
                                try { el = document.querySelector(sel); } catch(e) {}
                                if (!el) try { el = document.getElementById(sel); } catch(e) {}
                            }
                            if (!el) {
                                el = document.querySelector('form, button[type="submit"], input[type="submit"]');
                            }
                            if (!el) return 'No form or submit button found';
                            if (el.tagName.toLowerCase() === 'form') {
                                var ev = new Event('submit', { bubbles: true, cancelable: true });
                                el.dispatchEvent(ev);
                            } else {
                                el.click();
                            }
                            return 'OK';
                        } catch(err) {
                            return 'JS Exception: ' + (err.message || err.toString());
                        }
                    })()
                """.trimIndent()
                val rawRes = if (LivePreviewBrowserManager.isAvailable()) {
                    LivePreviewBrowserManager.runJavascript(js)
                } else {
                    backgroundBrowser.runJavascript(js)
                }
                val res = rawRes.removeSurrounding("\"").trim()
                if (res.contains("OK")) {
                    "Successfully submitted form/element '$targetSelector'."
                } else {
                    "Error submitting form: $res"
                }
            }

            else -> "Error: Unknown browser controller action '$action'."
        }
    }

    suspend fun deepCloneWebUi(
        url: String,
        targetFilePath: String?,
        projectName: String,
        repository: VibeRepository,
        backgroundBrowser: BackgroundBrowser,
        normalizePath: (String) -> String
    ): String = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim().let {
            if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
        }

        try {
            // 1. Navigate to target URL
            val navResult = backgroundBrowser.navigate(cleanUrl)
            val pageTitle = if (navResult is BrowserResult.Success) navResult.title else "Cloned Website"

            // 2. Extract DOM & Full Design Tokens via JS in Browser
            val extractJs = """
                (function() {
                    var colors = new Set();
                    var fonts = new Set();
                    var components = [];

                    var elements = document.querySelectorAll('header, nav, main, section, footer, [class*="card"], [class*="hero"], button, h1, h2, h3, a');
                    for (var i = 0; i < elements.length && i < 150; i++) {
                        var el = elements[i];
                        var cs = window.getComputedStyle(el);
                        if (cs.backgroundColor && cs.backgroundColor !== 'rgba(0, 0, 0, 0)') colors.add(cs.backgroundColor);
                        if (cs.color) colors.add(cs.color);
                        if (cs.fontFamily) fonts.add(cs.fontFamily);
                    }

                    return JSON.stringify({
                        title: document.title,
                        colors: Array.from(colors).slice(0, 15),
                        fonts: Array.from(fonts).slice(0, 8),
                        bodyHtml: document.body ? document.body.innerHTML.substring(0, 30000) : ''
                    });
                })()
            """.trimIndent()

            val rawData = backgroundBrowser.runJavascript(extractJs)
            var extractedColors = "[]"
            var extractedFonts = "[]"
            var htmlSnippet = ""

            try {
                val json = JSONObject(rawData)
                extractedColors = json.optJSONArray("colors")?.toString() ?: "[]"
                extractedFonts = json.optJSONArray("fonts")?.toString() ?: "[]"
                htmlSnippet = json.optString("bodyHtml", "")
            } catch (e: Exception) {
                // Ignore parsing errors, proceed with raw source
            }

            val destPath = normalizePath(targetFilePath ?: "cloned_ui/scraped_page.html")
            val tokenPath = normalizePath("cloned_ui/design_tokens.json")

            val fullHtml = if (htmlSnippet.isNotBlank()) "<!DOCTYPE html>\n<html>\n<head><title>$pageTitle</title></head>\n<body>\n$htmlSnippet\n</body>\n</html>" else (if (navResult is BrowserResult.Success) navResult.content else "")
            repository.saveFile(projectName, destPath, fullHtml)

            val tokenContent = """
                {
                  "sourceUrl": "$cleanUrl",
                  "title": "$pageTitle",
                  "colors": $extractedColors,
                  "fonts": $extractedFonts,
                  "scrapedHtmlFile": "$destPath"
                }
            """.trimIndent()
            repository.saveFile(projectName, tokenPath, tokenContent)

            """
            Successfully deeply cloned website UI from '$cleanUrl' (Title: '$pageTitle').
            • HTML Structure saved to: '$destPath'
            • Design tokens & CSS color palette saved to: '$tokenPath'
            • Colors extracted: $extractedColors
            • Typography extracted: $extractedFonts
            
            You can now build 1:1 matching responsive React/Tailwind/Compose UI components based on these design tokens and HTML structure!
            """.trimIndent()
        } catch (e: Exception) {
            "Error deeply cloning web UI: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }
}
