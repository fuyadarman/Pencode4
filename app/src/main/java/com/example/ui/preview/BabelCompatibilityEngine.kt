package com.example.ui.preview

/**
 * Dedicated compatibility engine for Babel JSX compilation in WebPreviews.
 * Fixes known Babel standalone issues such as:
 * - Decorators plugin missing 'decoratorsBeforeExport' option when stage-3 is specified
 * - React 18 / JSX classic runtime compatibility
 * - ES module imports inside text/babel script tags
 */
object BabelCompatibilityEngine {

    /**
     * Sanitizes and prepares HTML content for reliable Babel compilation.
     */
    fun sanitizeAndConfigureBabel(html: String): String {
        var content = html

        // 1. Sanitize data-presets: Replace deprecated 'stage-3' or 'stage-2' with 'env'
        // In @babel/standalone, 'stage-3' activates proposal-decorators without decoratorsBeforeExport, causing runtime error.
        val stagePresetRegex = Regex("""data-presets\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        content = stagePresetRegex.replace(content) { matchResult ->
            val originalPresets = matchResult.groups[1]?.value ?: ""
            // Replace stage-2 / stage-3 with env
            var sanitized = originalPresets
                .replace(Regex("""\bstage-[23]\b""", RegexOption.IGNORE_CASE), "env")
                .split(",")
                .map { it.trim() }
                .distinct()
                .joinToString(",")
            
            // Map 'react' to 'react-classic' if not already
            if (sanitized.contains("react") && !sanitized.contains("react-classic")) {
                sanitized = sanitized.replace(Regex("""\breact\b"""), "react-classic")
            }
            "data-presets=\"$sanitized\""
        }

        // 2. Inject configuration script immediately after babel.min.js or in <head>
        val babelConfigScript = """
<script>
(function() {
    function configureBabel() {
        if (!window.Babel) return;
        try {
            // Register 'react-classic' preset to use classic runtime for React CDN
            if (window.Babel.availablePresets && window.Babel.availablePresets['react']) {
                window.Babel.registerPreset('react-classic', {
                    presets: [
                        [window.Babel.availablePresets['react'], { runtime: 'classic' }]
                    ]
                });
            }
            // Register safe stage-3 alias with decoratorsBeforeExport configured
            if (window.Babel.availablePlugins && window.Babel.availablePlugins['proposal-decorators']) {
                window.Babel.registerPlugin('safe-decorators', [
                    window.Babel.availablePlugins['proposal-decorators'],
                    { decoratorsBeforeExport: true }
                ]);
            }
        } catch(e) {
            console.warn('[BabelCompatibilityEngine] Config notice:', e);
        }
    }
    if (window.Babel) {
        configureBabel();
    } else {
        window.addEventListener('DOMContentLoaded', configureBabel);
    }
})();
</script>
""".trimIndent()

        val babelCdnRegex = Regex("""(<script\s+[^>]*src=["'][^"']*babel\.min\.js["'][^>]*>\s*</script>)""", RegexOption.IGNORE_CASE)
        if (babelCdnRegex.containsMatchIn(content)) {
            content = babelCdnRegex.replace(content) { matchResult ->
                matchResult.value + "\n" + babelConfigScript
            }
        } else {
            val headRegex = Regex("""(<head>)""", RegexOption.IGNORE_CASE)
            if (headRegex.containsMatchIn(content)) {
                content = headRegex.replace(content) { matchResult ->
                    matchResult.value + "\n" + babelConfigScript
                }
            }
        }

        // 3. Ensure script tags of type text/babel has data-type="module" to allow ES imports
        val babelScriptRegex = Regex("""<script\s+type\s*=\s*["']text/babel["'](?![^>]*data-type\s*=)([^>]*)>""", RegexOption.IGNORE_CASE)
        content = babelScriptRegex.replace(content) { matchResult ->
            val attrs = matchResult.groups[1]?.value ?: ""
            "<script type=\"text/babel\" data-type=\"module\"$attrs>"
        }

        return content
    }
}
