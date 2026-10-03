package com.example.preview.universal

import com.example.data.ProjectFileEntity

/**
 * ThemeColorExtractor
 * 
 * Automatically analyzes Kotlin Compose and Flutter Dart files to extract:
 * - Exact primary, secondary, inversePrimary, and container colors (hex / Color(...) / Colors.*)
 * - Material 3 color harmony
 * - Dark vs Light theme mode
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
            primaryColor = if (isFlutter) "#6750A4" else "#6750A4"
        }

        val palette = computeM3Palette(primaryColor, isDark)

        return PreviewTheme(
            primaryColor = primaryColor,
            onPrimaryColor = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            secondaryColor = palette.secondary,
            backgroundColor = palette.background,
            surfaceColor = palette.surface,
            onSurfaceColor = palette.onSurface,
            inversePrimary = palette.inversePrimary,
            isDark = isDark
        )
    }

    private data class Palette(
        val onPrimary: String,
        val primaryContainer: String,
        val secondary: String,
        val background: String,
        val surface: String,
        val onSurface: String,
        val inversePrimary: String
    )

    private fun computeM3Palette(primary: String, isDark: Boolean): Palette {
        val lower = primary.lowercase()

        // Material 3 deepPurple / purple palette (Flutter & Compose default)
        if (lower.contains("6750a4") || lower.contains("6200ee") || lower.contains("7c4dff")) {
            return if (isDark) {
                Palette(
                    onPrimary = "#381E72",
                    primaryContainer = "#4F378B",
                    secondary = "#CCC2DC",
                    background = "#141218",
                    surface = "#141218",
                    onSurface = "#E6E0E9",
                    inversePrimary = "#6750A4"
                )
            } else {
                Palette(
                    onPrimary = "#FFFFFF",
                    primaryContainer = "#EADDFF",
                    secondary = "#625B71",
                    background = "#FEF7FF",
                    surface = "#FEF7FF",
                    onSurface = "#1D1B20",
                    inversePrimary = "#D0BCFF"
                )
            }
        }

        // Blue palette
        if (lower.contains("2196f3") || lower.contains("1976d2") || lower.contains("02569b") || lower.contains("0061a4")) {
            return if (isDark) {
                Palette(
                    onPrimary = "#003258",
                    primaryContainer = "#00497D",
                    secondary = "#B0C6FF",
                    background = "#111418",
                    surface = "#111418",
                    onSurface = "#E2E2E6",
                    inversePrimary = "#2196F3"
                )
            } else {
                Palette(
                    onPrimary = "#FFFFFF",
                    primaryContainer = "#D1E4FF",
                    secondary = "#535F70",
                    background = "#FDFBFF",
                    surface = "#FDFBFF",
                    onSurface = "#1A1C1E",
                    inversePrimary = "#9FCAFF"
                )
            }
        }

        // Default M3 generation
        return if (isDark) {
            Palette(
                onPrimary = "#FFFFFF",
                primaryContainer = "#333333",
                secondary = "#B0B0B0",
                background = "#121212",
                surface = "#1C1C1E",
                onSurface = "#FFFFFF",
                inversePrimary = primary
            )
        } else {
            Palette(
                onPrimary = "#FFFFFF",
                primaryContainer = "#E8DEF8",
                secondary = "#625B71",
                background = "#FEF7FF",
                surface = "#FFFFFF",
                onSurface = "#1D1B20",
                inversePrimary = "#D0BCFF"
            )
        }
    }

    private fun extractPrimaryHex(content: String): String? {
        val hexMatch = Regex("""Color\(\s*0x(?:FF)?([0-9a-fA-F]{6})\s*\)""").find(content)
        if (hexMatch != null) {
            return "#" + hexMatch.groupValues[1]
        }

        val seedColorHex = Regex("""seedColor\s*:\s*Color\(\s*0x(?:FF)?([0-9a-fA-F]{6})\s*\)""").find(content)
        if (seedColorHex != null) {
            return "#" + seedColorHex.groupValues[1]
        }

        val seedColorNamed = Regex("""seedColor\s*:\s*Colors\.([a-zA-Z]+)""").find(content)
        if (seedColorNamed != null) {
            return mapNamedColorToHex(seedColorNamed.groupValues[1].lowercase())
        }

        val flutterNamed = Regex("""Colors\.([a-zA-Z]+)""").find(content)
        if (flutterNamed != null) {
            val name = flutterNamed.groupValues[1].lowercase()
            return mapNamedColorToHex(name)
        }

        val composeNamed = Regex("""Color\.([A-Z][a-zA-Z]+)""").find(content)
        if (composeNamed != null) {
            val name = composeNamed.groupValues[1].lowercase()
            return mapNamedColorToHex(name)
        }

        return null
    }

    fun parseColorExpression(expr: String): String? {
        val trimmed = expr.trim()

        val hexMatch = Regex("""Color\(\s*0x(?:FF)?([0-9a-fA-F]{6})\s*\)""").find(trimmed)
        if (hexMatch != null) return "#" + hexMatch.groupValues[1]

        val namedMatch = Regex("""(?:Colors|Color)\.([a-zA-Z]+)""").find(trimmed)
        if (namedMatch != null) {
            return mapNamedColorToHex(namedMatch.groupValues[1].lowercase())
        }

        if (trimmed.contains("inversePrimary")) {
            return "var(--md-sys-color-inverse-primary)"
        }
        if (trimmed.contains("primaryContainer")) {
            return "var(--md-sys-color-primary-container)"
        }
        if (trimmed.contains("primary")) {
            return "var(--md-sys-color-primary)"
        }

        return null
    }

    private fun mapNamedColorToHex(name: String): String {
        return when (name) {
            "deeppurple", "purple" -> "#6750A4"
            "blue" -> "#2196F3"
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
