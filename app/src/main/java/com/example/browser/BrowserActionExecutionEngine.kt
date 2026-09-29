package com.example.browser

import android.util.Log
import com.example.ui.BackgroundBrowser
import com.example.ui.BrowserResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * BrowserActionExecutionEngine
 * Robust, fault-tolerant execution engine for browser controller actions (type, click, scroll, select).
 * Eliminates "Error typing into element: null" by providing multi-stage selector fallback,
 * event synthesis, and page state verification.
 */
object BrowserActionExecutionEngine {

    private const val TAG = "BrowserActionEngine"

    suspend fun executeTypeAction(
        selector: String?,
        elementIndex: Int?,
        text: String?,
        clearBefore: Boolean,
        pressEnter: Boolean,
        backgroundBrowser: BackgroundBrowser
    ): String = withContext(Dispatchers.Main) {
        val inputVal = text ?: ""
        
        // Clean selector to eliminate literal "null" / "undefined" strings
        val cleanSelector = selector?.trim()?.let {
            if (it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true) || it.isBlank()) {
                null
            } else {
                it
            }
        }

        val targetSelector = when {
            elementIndex != null && elementIndex > 0 -> "[data-agent-index='$elementIndex']"
            cleanSelector != null -> cleanSelector
            else -> ""
        }

        val escapedSelector = JSONObject.quote(targetSelector)
        val escapedVal = JSONObject.quote(inputVal)
        val clearJs = if (clearBefore) "try { el.value = ''; } catch(e) {}" else ""

        val enterJs = if (pressEnter) """
            try {
                el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                el.dispatchEvent(new KeyboardEvent('keypress', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                el.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
            } catch(ke) {}
            try {
                if (el.form) { el.form.submit(); }
            } catch(fe) {}
        """.trimIndent() else ""

        val typeScript = """
            (function() {
                try {
                    var sel = $escapedSelector;
                    var el = null;

                    // 1. Selector resolution if provided
                    if (sel && sel.length > 0 && sel !== 'null' && sel !== 'undefined') {
                        try { el = document.querySelector(sel); } catch(e) {}
                        if (!el) {
                            try { el = document.getElementById(sel); } catch(e) {}
                        }
                        if (!el) {
                            try { el = document.querySelector('[name="' + sel + '"], [placeholder*="' + sel + '" i], [aria-label*="' + sel + '" i]'); } catch(e) {}
                        }
                        if (!el) {
                            try {
                                var xp = document.evaluate(sel, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                                el = xp.singleNodeValue;
                            } catch(e) {}
                        }
                    }

                    // 2. Active element check
                    if (!el && document.activeElement && (
                        document.activeElement.tagName === 'INPUT' ||
                        document.activeElement.tagName === 'TEXTAREA' ||
                        document.activeElement.isContentEditable
                    )) {
                        el = document.activeElement;
                    }

                    // 3. Fallback to common search and input elements
                    if (!el) {
                        el = document.querySelector('input[type="search"], input[name*="q" i], input[name*="search" i], input[placeholder*="search" i], input[aria-label*="search" i]');
                    }
                    if (!el) {
                        el = document.querySelector('input:not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="checkbox"]):not([type="radio"]), textarea, [contenteditable="true"]');
                    }

                    if (!el) {
                        var bodyText = document.body ? (document.body.innerText || '').substring(0, 100) : 'No body';
                        var isBlank = window.location.href === 'about:blank' || !document.body;
                        if (isBlank) {
                            return 'PAGE_BLANK: Current page is about:blank. Please navigate to a URL first using open_url or browser_search.';
                        }
                        return 'NO_INPUT_FOUND: Could not find any input element matching \"' + sel + '\". Page title: ' + document.title;
                    }

                    try { el.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch(se) {}
                    try { el.focus(); } catch(fe) {}
                    $clearJs

                    var typed = false;
                    if ('value' in el) {
                        try {
                            var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                            if (nativeSetter && nativeSetter.set && el instanceof HTMLInputElement) {
                                nativeSetter.set.call(el, $escapedVal);
                                typed = true;
                            }
                        } catch(nse) {}

                        if (!typed) {
                            try {
                                var areaSetter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value');
                                if (areaSetter && areaSetter.set && el instanceof HTMLTextAreaElement) {
                                    areaSetter.set.call(el, $escapedVal);
                                    typed = true;
                                }
                            } catch(tse) {}
                        }

                        if (!typed) {
                            el.value = $escapedVal;
                        }
                    } else {
                        el.innerText = $escapedVal;
                    }

                    try { el.dispatchEvent(new Event('input', { bubbles: true })); } catch(ie) {}
                    try { el.dispatchEvent(new Event('change', { bubbles: true })); } catch(ce) {}
                    $enterJs

                    var tagName = el.tagName.toLowerCase();
                    var elId = el.id ? '#' + el.id : (el.name ? '[name=' + el.name + ']' : tagName);
                    return 'OK:' + elId;
                } catch(fatal) {
                    return 'EXCEPTION:' + (fatal.message || fatal.toString());
                }
            })()
        """.trimIndent()

        val rawRes = backgroundBrowser.runJavascript(typeScript)
        val res = rawRes.removeSurrounding("\"").trim()

        when {
            res.startsWith("OK") -> {
                val targetInfo = res.substringAfter("OK:").ifBlank { targetSelector.ifBlank { "input field" } }
                if (pressEnter) delay(1000)
                "Successfully typed \"$inputVal\" into $targetInfo${if (pressEnter) " and submitted" else ""}."
            }

            res.startsWith("PAGE_BLANK") -> {
                "Error: Browser has not loaded any website (currently at about:blank). Please use 'open_url' or 'browser_search' to navigate to a website first."
            }

            res.startsWith("NO_INPUT_FOUND") -> {
                val detail = res.substringAfter("NO_INPUT_FOUND:").trim()
                "Error: No typable input field found on page. Details: $detail"
            }

            res.startsWith("EXCEPTION") -> {
                val err = res.substringAfter("EXCEPTION:").trim()
                "Error executing browser typing script: $err"
            }

            res == "null" || res.isBlank() || res.equals("undefined", ignoreCase = true) -> {
                // Secondary fallback attempt: check page state
                val pageStatus = backgroundBrowser.readPageContent().let {
                    if (it is BrowserResult.Success) "URL: ${it.url}, Title: ${it.title}" else "Page unavailable"
                }
                "Error typing into element: Page returned null/unresponsive ($pageStatus). Please verify the page is loaded."
            }

            else -> {
                "Error typing into element: $res"
            }
        }
    }
}
