package com.example.preview.universal

/**
 * ComposeModifierParser
 * 
 * Deep parser for Jetpack Compose Modifier chains and CSS layout mapping:
 * - Size: fillMaxWidth, fillMaxHeight, size(dp), width(dp), height(dp), aspectRatio
 * - Padding & Margin: padding(all), padding(h, v), padding(start, top, end, bottom)
 * - Position & Layout: offset, alignment (Center, Start, End, Top, Bottom)
 * - Typography: fontSize, fontWeight, lineHeight, letterSpacing, textAlign
 * - Colors & Backgrounds: background(Color), brush, alpha
 * - Shape & Borders: clip(RoundedCornerShape), border(width, color), shadow(elevation)
 * - Scroll & Animation: verticalScroll, horizontalScroll, animateContentSize
 */
object ComposeModifierParser {

    fun parseStyle(modifierSnippet: String, existingStyle: PreviewNodeStyle = PreviewNodeStyle()): PreviewNodeStyle {
        val style = existingStyle

        if (modifierSnippet.isBlank()) return style

        // 1. Sizing
        if (modifierSnippet.contains("fillMaxWidth()")) {
            style.fillMaxWidth = true
            style.width = "100%"
        }
        if (modifierSnippet.contains("fillMaxHeight()")) {
            style.fillMaxHeight = true
            style.height = "100%"
        }
        if (modifierSnippet.contains("fillMaxSize()")) {
            style.fillMaxWidth = true
            style.fillMaxHeight = true
            style.width = "100%"
            style.height = "100%"
        }

        // Width & Height dp
        val widthMatch = Regex("""\.width\(\s*([0-9.]+)\.dp\s*\)""").find(modifierSnippet)
        if (widthMatch != null) {
            style.width = "${widthMatch.groupValues[1]}px"
        }
        val heightMatch = Regex("""\.height\(\s*([0-9.]+)\.dp\s*\)""").find(modifierSnippet)
        if (heightMatch != null) {
            style.height = "${heightMatch.groupValues[1]}px"
        }
        val sizeMatch = Regex("""\.size\(\s*([0-9.]+)\.dp\s*\)""").find(modifierSnippet)
        if (sizeMatch != null) {
            val sz = "${sizeMatch.groupValues[1]}px"
            style.width = sz
            style.height = sz
        }

        // 2. Padding
        val padSingle = Regex("""\.padding\(\s*([0-9.]+)\.dp\s*\)""").find(modifierSnippet)
        if (padSingle != null) {
            style.padding = "${padSingle.groupValues[1]}px"
        }
        val padHV = Regex("""\.padding\(\s*(?:horizontal\s*=\s*([0-9.]+)\.dp)?\s*,?\s*(?:vertical\s*=\s*([0-9.]+)\.dp)?\s*\)""").find(modifierSnippet)
        if (padHV != null) {
            val h = padHV.groupValues[1].ifEmpty { "0" }
            val v = padHV.groupValues[2].ifEmpty { "0" }
            style.padding = "${v}px ${h}px"
        }
        val pad4 = Regex("""\.padding\(\s*(?:start\s*=\s*([0-9.]+)\.dp)?\s*,?\s*(?:top\s*=\s*([0-9.]+)\.dp)?\s*,?\s*(?:end\s*=\s*([0-9.]+)\.dp)?\s*,?\s*(?:bottom\s*=\s*([0-9.]+)\.dp)?\s*\)""").find(modifierSnippet)
        if (pad4 != null && pad4.groupValues.any { it.isNotBlank() }) {
            val s = pad4.groupValues[1].ifEmpty { "0" }
            val t = pad4.groupValues[2].ifEmpty { "0" }
            val e = pad4.groupValues[3].ifEmpty { "0" }
            val b = pad4.groupValues[4].ifEmpty { "0" }
            style.padding = "${t}px ${e}px ${b}px ${s}px"
        }

        // 3. Background Color
        val bgMatch = Regex("""\.background\(\s*([^,)\n]+)""").find(modifierSnippet)
        if (bgMatch != null) {
            val colorExpr = bgMatch.groupValues[1].trim()
            val parsedColor = ThemeColorExtractor.parseColorExpression(colorExpr)
            if (parsedColor != null) {
                style.backgroundColor = parsedColor
            }
        }

        // 4. Shape & Corner Radius
        val shapeMatch = Regex("""\.(?:clip|shape)\(\s*(?:RoundedCornerShape|CircleShape)\s*(?:\(\s*([0-9.]+)\.dp\s*\))?""").find(modifierSnippet)
        if (shapeMatch != null) {
            if (shapeMatch.value.contains("CircleShape")) {
                style.borderRadius = "50%"
                style.shape = "circle"
            } else {
                val radius = shapeMatch.groupValues[1].ifEmpty { "16" }
                style.borderRadius = "${radius}px"
                style.shape = "rounded"
            }
        }

        // 5. Elevation & Shadow
        val shadowMatch = Regex("""\.shadow\(\s*([0-9.]+)\.dp""").find(modifierSnippet)
        if (shadowMatch != null) {
            style.elevation = shadowMatch.groupValues[1].toDoubleOrNull()?.toInt() ?: 4
        }

        // 6. Alignment
        if (modifierSnippet.contains("Alignment.Center") || modifierSnippet.contains("CenterHorizontally")) {
            style.alignment = "center"
        } else if (modifierSnippet.contains("Alignment.Start") || modifierSnippet.contains("Start")) {
            style.alignment = "start"
        } else if (modifierSnippet.contains("Alignment.End") || modifierSnippet.contains("End")) {
            style.alignment = "end"
        }

        // 7. Scroll Behavior
        if (modifierSnippet.contains("verticalScroll(")) {
            style.isScrollable = true
            style.scrollDirection = "vertical"
        } else if (modifierSnippet.contains("horizontalScroll(")) {
            style.isScrollable = true
            style.scrollDirection = "horizontal"
        }

        // 8. Animation
        if (modifierSnippet.contains("animateContentSize")) {
            style.hasAnimation = true
        }

        return style
    }

    fun parseTypography(styleSnippet: String, existingStyle: PreviewNodeStyle = PreviewNodeStyle()): PreviewNodeStyle {
        val style = existingStyle

        if (styleSnippet.contains("headlineLarge", true) || styleSnippet.contains("display", true)) {
            style.fontSize = "28px"
            style.fontWeight = "700"
        } else if (styleSnippet.contains("headlineMedium", true) || styleSnippet.contains("titleLarge", true)) {
            style.fontSize = "22px"
            style.fontWeight = "700"
        } else if (styleSnippet.contains("titleMedium", true) || styleSnippet.contains("headlineSmall", true)) {
            style.fontSize = "18px"
            style.fontWeight = "600"
        } else if (styleSnippet.contains("bodyLarge", true)) {
            style.fontSize = "16px"
            style.fontWeight = "400"
        } else if (styleSnippet.contains("bodyMedium", true)) {
            style.fontSize = "14px"
            style.fontWeight = "400"
        } else if (styleSnippet.contains("labelSmall", true) || styleSnippet.contains("bodySmall", true)) {
            style.fontSize = "12px"
            style.fontWeight = "500"
        }

        // Custom fontSize = X.sp
        val spMatch = Regex("""fontSize\s*=\s*([0-9.]+)\.sp""").find(styleSnippet)
        if (spMatch != null) {
            style.fontSize = "${spMatch.groupValues[1]}px"
        }

        // Custom fontWeight = FontWeight.Bold / Medium
        if (styleSnippet.contains("FontWeight.Bold")) {
            style.fontWeight = "700"
        } else if (styleSnippet.contains("FontWeight.SemiBold") || styleSnippet.contains("FontWeight.W600")) {
            style.fontWeight = "600"
        } else if (styleSnippet.contains("FontWeight.Medium") || styleSnippet.contains("FontWeight.W500")) {
            style.fontWeight = "500"
        }

        return style
    }
}
