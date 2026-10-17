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
            // Replace deprecated stage-2 / stage-3 with env, keep react and env intact
            val sanitized = originalPresets
                .replace(Regex("""\bstage-[23]\b""", RegexOption.IGNORE_CASE), "env")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(",")
            "data-presets=\"$sanitized\""
        }

        // 2. Inject ImportMap for react and react/jsx-runtime if absent
        if (!content.contains("type=\"importmap\"", ignoreCase = true) && !content.contains("type='importmap'", ignoreCase = true)) {
            val importMapScript = """
<script type="importmap">
{
  "imports": {
    "react": "https://esm.sh/react@18.2.0",
    "react/": "https://esm.sh/react@18.2.0/",
    "react/jsx-runtime": "https://esm.sh/react@18.2.0/jsx-runtime",
    "react/jsx-dev-runtime": "https://esm.sh/react@18.2.0/jsx-dev-runtime",
    "react-dom": "https://esm.sh/react-dom@18.2.0",
    "react-dom/": "https://esm.sh/react-dom@18.2.0/",
    "react-dom/client": "https://esm.sh/react-dom@18.2.0/client"
  }
}
</script>
            """.trimIndent()
            val headMatch = Regex("""(<head[^>]*>)""", RegexOption.IGNORE_CASE).find(content)
            if (headMatch != null) {
                content = content.replaceFirst(headMatch.value, "${headMatch.value}\n$importMapScript")
            } else {
                content = "$importMapScript\n$content"
            }
        }

        // 3. Inject configuration script immediately after babel.min.js or in <head>
        val babelConfigScript = """
<script>
(function() {
    function configureBabel() {
        if (!window.Babel) return;
        try {
            var reactPreset = window.Babel.availablePresets ? window.Babel.availablePresets['react'] : null;
            if (reactPreset) {
                // Override default 'react' preset to use classic runtime and prevent missing react/jsx-runtime errors
                window.Babel.registerPreset('react', {
                    presets: [
                        [reactPreset, { runtime: 'classic', pragma: 'React.createElement', pragmaFrag: 'React.Fragment' }]
                    ]
                });
                window.Babel.registerPreset('react-classic', {
                    presets: [
                        [reactPreset, { runtime: 'classic', pragma: 'React.createElement', pragmaFrag: 'React.Fragment' }]
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
            val headRegex = Regex("""(<head[^>]*>)""", RegexOption.IGNORE_CASE)
            if (headRegex.containsMatchIn(content)) {
                content = headRegex.replace(content) { matchResult ->
                    matchResult.value + "\n" + babelConfigScript
                }
            }
        }

        // 4. Ensure script tags of type text/babel has data-type="module" to allow ES imports
        val babelScriptRegex = Regex("""<script\s+type\s*=\s*["']text/babel["'](?![^>]*data-type\s*=)([^>]*)>""", RegexOption.IGNORE_CASE)
        content = babelScriptRegex.replace(content) { matchResult ->
            val attrs = matchResult.groups[1]?.value ?: ""
            "<script type=\"text/babel\" data-type=\"module\"$attrs>"
        }

        // 5. Convert tsx/jsx scripts without text/babel type to text/babel with data-type="module"
        val tsxScriptRegex = Regex("""<script\s+([^>]*src\s*=\s*["'][^"']*\.(?:[tj]sx|ts)["'][^>]*)>""", RegexOption.IGNORE_CASE)
        content = tsxScriptRegex.replace(content) { matchResult ->
            val attrs = matchResult.groups[1]?.value ?: ""
            if (!attrs.contains("text/babel", ignoreCase = true)) {
                if (attrs.contains("type=", ignoreCase = true)) {
                    val replaced = attrs.replace(Regex("""type\s*=\s*["'][^"']*["']""", RegexOption.IGNORE_CASE), "type=\"text/babel\" data-type=\"module\"")
                    "<script $replaced>"
                } else {
                    "<script type=\"text/babel\" data-type=\"module\" $attrs>"
                }
            } else {
                matchResult.value
            }
        }

        return content
    }
}
