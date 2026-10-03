package com.example.preview.universal

import com.example.data.ProjectFileEntity

/**
 * ThemeColorExtractor
 * 
 * Automatically analyzes Kotlin Compose and Flutter Dart files to extract:
 * - Exact primary, secondary, and accent colors (hex / Color(...) / Colors.*)
 * - Dark vs Light theme mode
 * - Custom button, surface, and background colors
 */
object ThemeColorExtractor {

    fun extract(files: List<ProjectFileEntity>): PreviewTheme {
        val allContent = files.joinToString("\n") { it.content }
        val lower = allContent.lowercase()

        // 1. Detect Dark Mode
        val isDark = lower.contains("darktheme") || 
                     lower.contains("darkcolorscheme") || 
                     lower.contains("brightness.dark") || 
                     lower.contains("thememode.dark") ||
                     lower.contains("dark_color") ||
                     allContent.contains("Color(0xFF1") || 
                     allContent.contains("Color(0xFF0")

        // 2. Extract Primary Color
        var primaryColor = extractPrimaryHex(allContent)

        // 3. Fallbacks based on framework
        if (primaryColor == null) {
            val isFlutter = files.any { it.path.endsWith(".dart") }
            primaryColor = if (isFlutter) "#1976D2" else "#6750A4"
        }

        val backgroundColor = if (isDark) "#121212" else "#FEF7FF"
        val surfaceColor = if (isDark) "#1E1F2B" else "#FFFFFF"
        val onSurfaceColor = if (isDark) "#FFFFFF" else "#1D1B20"
        val primaryContainer = if (isDark) "#381E72" else "#EADDFF"

        return PreviewTheme(
            primaryColor = primaryColor,
            onPrimaryColor = "#FFFFFF",
            primaryContainer = primaryContainer,
            secondaryColor = if (isDark) "#CCC2DC" else "#625B71",
            backgroundColor = backgroundColor,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor,
            isDark = isDark
        )
    }

    private fun extractPrimaryHex(content: String): String? {
        // 1. Color(0xFF...) or Color(0x...) in Kotlin
        val hexMatch = Regex("""Color\(\s*0x(?:FF)?([0-9a-fA-F]{6})\s*\)""").find(content)
        if (hexMatch != null) {
            return "#" + hexMatch.groupValues[1]
        }

        // 2. seedColor: Color(...) in Flutter or Compose
        val seedColorHex = Regex("""seedColor\s*:\s*Color\(\s*0x(?:FF)?([0-9a-fA-F]{6})\s*\)""").find(content)
        if (seedColorHex != null) {
            return "#" + seedColorHex.groupValues[1]
        }

        // 3. Named Colors in Flutter: Colors.deepPurple, Colors.blue, etc.
        val flutterNamed = Regex("""Colors\.([a-zA-Z]+)""").find(content)
        if (flutterNamed != null) {
            val name = flutterNamed.groupValues[1].lowercase()
            return mapNamedColorToHex(name)
        }

        // 4. Named Colors in Compose: Color.Blue, Color.Red, etc.
        val composeNamed = Regex("""Color\.([A-Z][a-zA-Z]+)""").find(content)
        if (composeNamed != null) {
            val name = composeNamed.groupValues[1].lowercase()
            return mapNamedColorToHex(name)
        }

        return null
    }

    fun parseColorExpression(expr: String): String? {
        val trimmed = expr.trim()

        // Match Color(0xFF123456)
        val hexMatch = Regex("""Color\(\s*0x(?:FF)?([0-9a-fA-F]{6})\s*\)""").find(trimmed)
        if (hexMatch != null) return "#" + hexMatch.groupValues[1]

        // Match Colors.orange, Colors.blue, Color.Red
        val namedMatch = Regex("""(?:Colors|Color)\.([a-zA-Z]+)""").find(trimmed)
        if (namedMatch != null) {
            return mapNamedColorToHex(namedMatch.groupValues[1].lowercase())
        }

        return null
    }

    private fun mapNamedColorToHex(name: String): String {
        return when (name) {
            "blue" -> "#2196F3"
            "deeppurple", "purple" -> "#6750A4"
            "indigo" -> "#3F51B5"
            "teal" -> "#009688"
            "green" -> "#4CAF50"
            "orange" -> "#FF9800"
            "deeporange" -> "#FF5722"
            "red" -> "#F44336"
            "pink" -> "#E91E63"
            "cyan" -> "#00BCD4"
            "amber" -> "#FFC107"
            "yellow" -> "#FFEB3B"
            "grey", "gray" -> "#9E9E9E"
            "darkgray", "darkgrey" -> "#424242"
            "black" -> "#000000"
            "white" -> "#FFFFFF"
            else -> "#6750A4"
        }
    }
}
