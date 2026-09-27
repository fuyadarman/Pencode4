package com.example.ui.preview

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.data.ProjectFileEntity
import com.example.ui.AttachedFile

/**
 * WebPreviewElementInspector
 * 
 * Provides interactive Element Inspector capabilities for WebView preview:
 * 1. Visual DOM highlighting with animated bounding box & element tag badge.
 * 2. Mobile-optimized Touch and Pointer event interception (preventing accidental link navigation
 *    or form submissions while inspecting).
 * 3. Thread-safe JavaScript bridge dispatching callbacks to the Android Main Looper.
 * 4. Automatic source file matching and line-range resolution across project files.
 */
object WebPreviewElementInspector {

    const val JAVASCRIPT_INTERFACE_NAME = "AndroidInspector"

    /**
     * JavaScript bridge interface to safely receive clicked element info on the UI thread.
     */
    class InspectorBridge(private val onElementSelected: (String, String) -> Unit) {
        private val mainHandler = Handler(Looper.getMainLooper())

        @JavascriptInterface
        fun onElementClicked(identifier: String, outerHTML: String) {
            mainHandler.post {
                onElementSelected(identifier, outerHTML)
            }
        }
    }

    /**
     * Injects or updates the Inspector script into the WebView.
     */
    fun injectInspectorScript(webView: WebView?, isActive: Boolean) {
        if (webView == null) return
        val js = """
            (function() {
                window.isInspectorModeActive = $isActive;
                
                // Helper to safely get element selector identifier (handles SVGAnimatedString and plain strings)
                function getSafeIdentifier(el) {
                    if (!el) return 'element';
                    var tag = (el.tagName || 'element').toLowerCase();
                    var id = el.id ? '#' + el.id : '';
                    var className = '';
                    if (typeof el.className === 'string' && el.className.trim()) {
                        className = '.' + el.className.trim().split(/\s+/).slice(0, 3).join('.');
                    } else if (el.className && typeof el.className.baseVal === 'string' && el.className.baseVal.trim()) {
                        className = '.' + el.className.baseVal.trim().split(/\s+/).slice(0, 3).join('.');
                    }
                    return tag + id + className;
                }

                // Ensure overlay elements exist
                var overlay = document.getElementById('pencode-inspector-overlay');
                var badge = document.getElementById('pencode-inspector-badge');
                
                if (!overlay) {
                    overlay = document.createElement('div');
                    overlay.id = 'pencode-inspector-overlay';
                    overlay.style.position = 'fixed';
                    overlay.style.pointerEvents = 'none';
                    overlay.style.zIndex = '2147483647';
                    overlay.style.border = '2px solid #38BDF8';
                    overlay.style.backgroundColor = 'rgba(56, 189, 248, 0.15)';
                    overlay.style.borderRadius = '3px';
                    overlay.style.boxShadow = '0 0 10px rgba(56, 189, 248, 0.35)';
                    overlay.style.transition = 'all 0.05s ease';
                    overlay.style.display = 'none';
                    document.documentElement.appendChild(overlay);
                }

                if (!badge) {
                    badge = document.createElement('div');
                    badge.id = 'pencode-inspector-badge';
                    badge.style.position = 'fixed';
                    badge.style.pointerEvents = 'none';
                    badge.style.zIndex = '2147483647';
                    badge.style.backgroundColor = '#0D1117';
                    badge.style.color = '#58A6FF';
                    badge.style.fontFamily = 'monospace, sans-serif';
                    badge.style.fontSize = '11px';
                    badge.style.fontWeight = 'bold';
                    badge.style.padding = '3px 6px';
                    badge.style.borderRadius = '4px';
                    badge.style.border = '1px solid #388BFD';
                    badge.style.boxShadow = '0 2px 8px rgba(0, 0, 0, 0.6)';
                    badge.style.display = 'none';
                    document.documentElement.appendChild(badge);
                }

                function updateHighlight(el) {
                    if (!el || el === overlay || el === badge || el === document.documentElement || el === document.body) {
                        overlay.style.display = 'none';
                        badge.style.display = 'none';
                        return;
                    }
                    var rect = el.getBoundingClientRect();
                    overlay.style.top = rect.top + 'px';
                    overlay.style.left = rect.left + 'px';
                    overlay.style.width = rect.width + 'px';
                    overlay.style.height = rect.height + 'px';
                    overlay.style.display = 'block';

                    var badgeTop = rect.top - 24;
                    if (badgeTop < 4) badgeTop = rect.bottom + 4;
                    var badgeLeft = Math.max(4, rect.left);

                    badge.style.top = badgeTop + 'px';
                    badge.style.left = badgeLeft + 'px';
                    badge.textContent = '<' + getSafeIdentifier(el) + '>';
                    badge.style.display = 'block';
                }

                function triggerSelect(el) {
                    if (!el || el === overlay || el === badge) return;
                    var identifier = getSafeIdentifier(el);
                    
                    // Flash effect
                    overlay.style.backgroundColor = 'rgba(56, 189, 248, 0.45)';
                    overlay.style.borderColor = '#FFFFFF';
                    
                    var html = el.outerHTML || '';
                    if (html.length > 8000) {
                        html = html.substring(0, 8000) + '\n<!-- content truncated for inspector payload -->';
                    }

                    if (window.AndroidInspector && typeof window.AndroidInspector.onElementClicked === 'function') {
                        window.AndroidInspector.onElementClicked(identifier, html);
                    }
                    
                    // Hide overlay after selection
                    setTimeout(function() {
                        overlay.style.display = 'none';
                        badge.style.display = 'none';
                    }, 250);
                }

                if (!window._pencodeInspectorInitialized) {
                    window._pencodeInspectorInitialized = true;

                    // Mouse & Pointer events for desktop / emulator cursor
                    document.addEventListener('mouseover', function(e) {
                        if (window.isInspectorModeActive) {
                            updateHighlight(e.target);
                        }
                    }, true);

                    // Touch events for mobile screens
                    document.addEventListener('touchstart', function(e) {
                        if (window.isInspectorModeActive) {
                            e.preventDefault();
                            e.stopPropagation();
                            if (e.touches && e.touches.length > 0) {
                                var touch = e.touches[0];
                                var target = document.elementFromPoint(touch.clientX, touch.clientY);
                                updateHighlight(target);
                            }
                        }
                    }, { passive: false, capture: true });

                    document.addEventListener('touchmove', function(e) {
                        if (window.isInspectorModeActive) {
                            e.preventDefault();
                            e.stopPropagation();
                            if (e.touches && e.touches.length > 0) {
                                var touch = e.touches[0];
                                var target = document.elementFromPoint(touch.clientX, touch.clientY);
                                updateHighlight(target);
                            }
                        }
                    }, { passive: false, capture: true });

                    document.addEventListener('touchend', function(e) {
                        if (window.isInspectorModeActive) {
                            e.preventDefault();
                            e.stopPropagation();
                            if (e.changedTouches && e.changedTouches.length > 0) {
                                var touch = e.changedTouches[0];
                                var target = document.elementFromPoint(touch.clientX, touch.clientY);
                                if (target) {
                                    triggerSelect(target);
                                }
                            }
                        }
                    }, { passive: false, capture: true });

                    document.addEventListener('click', function(e) {
                        if (window.isInspectorModeActive) {
                            e.preventDefault();
                            e.stopPropagation();
                            triggerSelect(e.target);
                        }
                    }, true);
                }

                if (!$isActive) {
                    overlay.style.display = 'none';
                    badge.style.display = 'none';
                }
            })();
        """.trimIndent()
        try {
            webView.evaluateJavascript(js, null)
        } catch (e: Exception) {
            // Ignore WebView errors
        }
    }

    /**
     * Toggles the inspector active state on the WebView instance.
     */
    fun setInspectorActive(webView: WebView?, isActive: Boolean) {
        if (webView == null) return
        try {
            webView.evaluateJavascript("window.isInspectorModeActive = $isActive;", null)
            if (!isActive) {
                webView.evaluateJavascript("""
                    (function() {
                        var o = document.getElementById('pencode-inspector-overlay');
                        var b = document.getElementById('pencode-inspector-badge');
                        if (o) o.style.display = 'none';
                        if (b) b.style.display = 'none';
                    })();
                """.trimIndent(), null)
            }
        } catch (e: Exception) {
            // Ignore JS evaluation errors
        }
    }

    /**
     * Resolves the source file, calculates line ranges, and builds an AttachedFile entity
     * for the inspected DOM element.
     */
    fun resolveElementAttachment(
        files: List<ProjectFileEntity>,
        identifier: String,
        outerHTML: String
    ): AttachedFile {
        // Priority 1: Match index.html or primary HTML/JSX files
        val primaryCandidate = files.find { it.path.equals("index.html", ignoreCase = true) || it.path.endsWith("/index.html", ignoreCase = true) }
            ?: files.find { it.path.endsWith(".html", ignoreCase = true) }
            ?: files.find { it.path.endsWith(".tsx", ignoreCase = true) || it.path.endsWith(".jsx", ignoreCase = true) }

        // Find best matching file containing outerHTML or normalized snippet
        var matchedFile = primaryCandidate
        var startLine = -1
        var endLine = -1

        val filesToSearch = if (primaryCandidate != null) {
            listOf(primaryCandidate) + files.filter { it != primaryCandidate }
        } else {
            files
        }

        val whitespaceRegex = Regex("[\\s\"']")
        val normalizedOuter = outerHTML.replace(whitespaceRegex, "")
        val truncatedNormalizedOuter = if (normalizedOuter.length > 40) normalizedOuter.substring(0, 40) else normalizedOuter

        for (file in filesToSearch) {
            val content = file.content
            if (content.isBlank()) continue

            val exactIndex = content.indexOf(outerHTML)
            if (exactIndex != -1) {
                startLine = content.substring(0, exactIndex).count { it == '\n' } + 1
                endLine = startLine + outerHTML.count { it == '\n' }
                matchedFile = file
                break
            }

            val normalizedContent = content.replace(whitespaceRegex, "")
            val matchIndex = normalizedContent.indexOf(truncatedNormalizedOuter)
            if (matchIndex != -1) {
                var originalStart = 0
                var normCount = 0
                while (originalStart < content.length && normCount < matchIndex) {
                    if (!content[originalStart].toString().matches(whitespaceRegex)) {
                        normCount++
                    }
                    originalStart++
                }
                startLine = content.substring(0, originalStart).count { it == '\n' } + 1
                endLine = startLine + outerHTML.count { it == '\n' }
                matchedFile = file
                break
            }
        }

        val fileName = matchedFile?.path ?: "index.html"
        val label = if (startLine != -1 && endLine != -1) {
            if (startLine == endLine) "@$fileName (line $startLine)" else "@$fileName (lines $startLine-$endLine)"
        } else {
            "@$fileName"
        }

        val promptBody = buildString {
            appendLine("User inspected element `$identifier` in the live preview.")
            if (startLine != -1) {
                appendLine("Target source file: `$fileName` (around line $startLine)")
            }
            appendLine()
            appendLine("```html")
            appendLine(outerHTML)
            appendLine("```")
            appendLine("Please apply edits or adjustments to this component as requested.")
        }

        return AttachedFile(
            uri = android.net.Uri.EMPTY,
            name = label,
            mimeType = "text/html",
            isImage = false,
            contentAsText = promptBody
        )
    }
}
