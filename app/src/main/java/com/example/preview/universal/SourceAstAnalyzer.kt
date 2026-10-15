package com.example.preview.universal

import com.example.data.ProjectFileEntity
import java.util.UUID

/**
 * SourceAstAnalyzer
 *
 * Real source analyzer and AST extractor for Kotlin Jetpack Compose and Flutter Dart.
 * Converts raw source files into Semantic UI IR with state, events, layouts, and conditions.
 */
object SourceAstAnalyzer {

    fun analyze(files: List<ProjectFileEntity>): SemanticDocument {
        val sourceFiles = files.filter { file ->
            val p = file.path.lowercase()
            !p.contains(".github") && !p.contains("workflows") && !p.contains("build/") && !p.contains("dist/") &&
            (p.endsWith(".kt") || p.endsWith(".dart") || p.endsWith(".java") || (p.contains("res/layout") && p.endsWith(".xml")))
        }
        val targetFiles = if (sourceFiles.isNotEmpty()) sourceFiles else files
        val allContent = targetFiles.joinToString("\n") { it.content }
        val title = extractAppTitle(files)
        val framework = if (targetFiles.any { it.path.endsWith(".dart", ignoreCase = true) }) "Flutter" else "Kotlin Compose"

        val initialState = extractInitialState(allContent)
        val rootNode = parseRootNode(allContent, initialState)

        return SemanticDocument(
            title = title,
            framework = framework,
            initialState = initialState,
            rootNode = rootNode
        )
    }

    private fun extractInitialState(code: String): MutableMap<String, Any> {
        val state = mutableMapOf<String, Any>()

        // Kotlin Compose mutableStateOf patterns
        // e.g. var isLoading by remember { mutableStateOf(false) }
        val composeStateRegex = Regex("""(?:var|val)\s+([A-Za-z0-9_]+)\s*(?:by|[:=])\s*(?:remember)?\s*\{?\s*(?:mutableStateOf|mutableIntStateOf|mutableFloatStateOf|mutableDoubleStateOf)\s*\(([^)]*)\)""")
        for (m in composeStateRegex.findAll(code)) {
            val name = m.groupValues[1].trim()
            val rawVal = m.groupValues[2].trim()
            state[name] = parseRawValue(rawVal)
        }

        // Flutter state patterns
        // e.g. int _counter = 0; bool isLoading = false; String username = "";
        val flutterStateRegex = Regex("""(?:int|double|bool|String|var)\s+([A-Za-z0-9_]+)\s*=\s*([^;]+);""")
        for (m in flutterStateRegex.findAll(code)) {
            val name = m.groupValues[1].trim()
            val rawVal = m.groupValues[2].trim()
            if (!rawVal.contains("(")) {
                state[name] = parseRawValue(rawVal)
            }
        }

        // GameState enum heuristic
        if (code.contains("GameState", ignoreCase = true) && !state.containsKey("gameState")) {
            state["gameState"] = "START"
        }

        return state
    }

    private fun parseRawValue(raw: String): Any {
        val clean = raw.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'")
        return when {
            clean.equals("true", ignoreCase = true) -> true
            clean.equals("false", ignoreCase = true) -> false
            clean.toIntOrNull() != null -> clean.toInt()
            clean.toDoubleOrNull() != null -> clean.toDouble()
            clean.contains("START", ignoreCase = true) -> "START"
            clean.contains("RUNNING", ignoreCase = true) -> "RUNNING"
            clean.contains("GAMEOVER", ignoreCase = true) || clean.contains("GAME_OVER", ignoreCase = true) -> "GAME_OVER"
            else -> clean
        }
    }

    private fun parseRootNode(code: String, state: MutableMap<String, Any>): SemanticNode {
        val rootNode = SemanticNode(
            id = "root_scaffold",
            component = "Scaffold",
            layout = LayoutProps(type = "column", horizontalAlignment = "stretch", verticalAlignment = "start"),
            size = SizeProps(fillMaxWidth = true, fillMaxHeight = true),
            visual = VisualProps(backgroundColor = extractBackgroundColor(code))
        )

        // Parse conditional blocks: e.g. if (gameState == GameState.START) { ... }
        val conditionalBlocks = Regex("""if\s*\(([^)]+)\)\s*\{([\s\S]*?)\}(?:\s*else\s*\{([\s\S]*?)\})?""").findAll(code).toList()

        if (conditionalBlocks.isNotEmpty()) {
            for (cb in conditionalBlocks) {
                val rawCondition = cb.groupValues[1].trim()
                val ifBody = cb.groupValues[2]
                val elseBody = cb.groupValues.getOrNull(3)

                val jsCondition = convertConditionToJs(rawCondition)

                val ifContainer = SemanticNode(
                    id = "branch_" + UUID.randomUUID().toString().take(6),
                    component = "Box",
                    conditional = jsCondition,
                    layout = LayoutProps(type = "column", horizontalAlignment = "center", verticalAlignment = "center"),
                    size = SizeProps(fillMaxWidth = true, fillMaxHeight = true)
                )
                parseChildrenInto(ifBody, ifContainer, state)
                rootNode.children.add(ifContainer)

                if (!elseBody.isNullOrBlank()) {
                    val elseContainer = SemanticNode(
                        id = "branch_else_" + UUID.randomUUID().toString().take(6),
                        component = "Box",
                        conditional = "!($jsCondition)",
                        layout = LayoutProps(type = "column", horizontalAlignment = "center", verticalAlignment = "center"),
                        size = SizeProps(fillMaxWidth = true, fillMaxHeight = true)
                    )
                    parseChildrenInto(elseBody, elseContainer, state)
                    rootNode.children.add(elseContainer)
                }
            }
        }

        // If no conditional blocks or direct children exist
        if (rootNode.children.isEmpty()) {
            val contentContainer = SemanticNode(
                id = "content_body",
                component = "Column",
                layout = LayoutProps(type = "column", horizontalAlignment = "stretch", verticalAlignment = "start", gap = "12px", scrollable = true),
                size = SizeProps(fillMaxWidth = true, fillMaxHeight = true),
                spacing = SpacingProps(paddingTop = "16px", paddingBottom = "16px", paddingStart = "16px", paddingEnd = "16px")
            )
            parseChildrenInto(code, contentContainer, state)
            rootNode.children.add(contentContainer)
        }

        return rootNode
    }

    private fun parseChildrenInto(codeSnippet: String, parent: SemanticNode, state: MutableMap<String, Any>) {
        // 1. Parse Buttons
        val buttonRegex = Regex("""(?:Button|OutlinedButton|ElevatedButton|TextButton)\s*\([\s\S]*?(?:onClick|onPressed)\s*=\s*\{?([^}()\n]+)\}?[\s\S]*?\)\s*\{?([\s\S]*?)\}?""")
        for (bm in buttonRegex.findAll(codeSnippet)) {
            val actionCode = bm.groupValues[1].trim()
            val buttonBody = bm.groupValues[2].trim()

            val textMatch = Regex("""Text\s*\(\s*(?:text\s*=\s*)?['"]([^'"]+)['"]""").find(buttonBody)
            val buttonText = textMatch?.groupValues?.get(1) ?: "Action"

            val eventAction = parseEventAction(actionCode, state)

            val btnNode = SemanticNode(
                id = "btn_" + UUID.randomUUID().toString().take(5),
                component = "Button",
                text = buttonText,
                size = SizeProps(fillMaxWidth = buttonText.length > 15),
                spacing = SpacingProps(paddingTop = "12px", paddingBottom = "12px", paddingStart = "24px", paddingEnd = "24px", marginTop = "8px", marginBottom = "8px"),
                typography = TypographyProps(fontSize = "15px", fontWeight = "700", textAlign = "center"),
                visual = VisualProps(borderRadius = "24px", elevation = "2px")
            )
            btnNode.events["click"] = eventAction
            parent.children.add(btnNode)
        }

        // 2. Parse Cards
        val cardRegex = Regex("""(?:Card|ElevatedCard|OutlinedCard)\s*\([\s\S]*?\{([\s\S]*?)\}""")
        for (cm in cardRegex.findAll(codeSnippet)) {
            val cardBody = cm.groupValues[1]
            val cardNode = SemanticNode(
                id = "card_" + UUID.randomUUID().toString().take(5),
                component = "Card",
                layout = LayoutProps(type = "column", gap = "8px"),
                size = SizeProps(fillMaxWidth = true),
                spacing = SpacingProps(paddingTop = "16px", paddingBottom = "16px", paddingStart = "16px", paddingEnd = "16px", marginTop = "8px", marginBottom = "8px"),
                visual = VisualProps(borderRadius = "16px", elevation = "2px")
            )
            parseChildrenInto(cardBody, cardNode, state)
            parent.children.add(cardNode)
        }

        // 3. Parse Texts
        val textRegex = Regex("""Text\s*\(\s*(?:text\s*=\s*)?(?:['"]([^'"]*)['"]|([A-Za-z0-9_]+))[\s\S]*?\)""")
        for (tm in textRegex.findAll(codeSnippet)) {
            val literal = tm.groupValues[1]
            val varRef = tm.groupValues[2]
            val fullSnippet = tm.value

            val isLarge = fullSnippet.contains("headline", true) || fullSnippet.contains("title", true) || literal.length < 8 && literal.all { it.isUpperCase() || it.isDigit() }
            val txt = if (literal.isNotBlank()) literal else state[varRef]?.toString() ?: varRef

            if (txt.isNotBlank() && !txt.startsWith("Icons.") && parent.children.none { it.text == txt }) {
                val textNode = SemanticNode(
                    id = "txt_" + UUID.randomUUID().toString().take(5),
                    component = "Text",
                    text = txt,
                    size = SizeProps(fillMaxWidth = isLarge),
                    spacing = SpacingProps(marginTop = if (isLarge) "8px" else "4px", marginBottom = if (isLarge) "8px" else "4px"),
                    typography = TypographyProps(
                        fontSize = if (isLarge) "24px" else "15px",
                        fontWeight = if (isLarge) "800" else "400",
                        textAlign = if (isLarge) "center" else "left"
                    )
                )
                if (varRef.isNotBlank() && state.containsKey(varRef)) {
                    textNode.stateBindings["text"] = varRef
                }
                parent.children.add(textNode)
            }
        }

        // 4. Parse TextFields
        val tfRegex = Regex("""(?:TextField|OutlinedTextField|TextFormField)\s*\([\s\S]*?(?:value\s*=\s*([A-Za-z0-9_]+)|label\s*=\s*\{?\s*Text\(['"]([^'"]+)['"]\)\}?)""")
        for (tf in tfRegex.findAll(codeSnippet)) {
            val varName = tf.groupValues[1]
            val placeholder = tf.groupValues.getOrNull(2) ?: "Enter text..."
            val tfNode = SemanticNode(
                id = "tf_" + UUID.randomUUID().toString().take(5),
                component = "TextField",
                text = placeholder,
                size = SizeProps(fillMaxWidth = true),
                spacing = SpacingProps(marginTop = "8px", marginBottom = "8px"),
                visual = VisualProps(borderRadius = "12px")
            )
            if (varName.isNotBlank()) {
                tfNode.stateBindings["value"] = varName
                tfNode.events["input"] = EventAction(type = "state_update", stateUpdates = mutableMapOf(varName to "__value__"))
            }
            parent.children.add(tfNode)
        }
    }

    private fun parseEventAction(code: String, state: MutableMap<String, Any>): EventAction {
        val clean = code.trim().replace(";", "")

        // Increment: count++, count += 1
        if (clean.contains("++") || clean.contains("+=")) {
            val varName = clean.removeSuffix("++").split("+=")[0].trim()
            return EventAction(type = "state_increment", target = varName, delta = 1)
        }

        // Toggle: isDone = !isDone
        val toggleMatch = Regex("""([A-Za-z0-9_]+)\s*=\s*!\s*\1""").find(clean)
        if (toggleMatch != null) {
            return EventAction(type = "state_toggle", target = toggleMatch.groupValues[1])
        }

        // Assignment: isLoading = true, gameState = GameState.RUNNING
        val assignMatch = Regex("""([A-Za-z0-9_]+)\s*=\s*([^;]+)""").find(clean)
        if (assignMatch != null) {
            val varName = assignMatch.groupValues[1].trim()
            val valStr = assignMatch.groupValues[2].trim()
            val parsedVal = parseRawValue(valStr)
            return EventAction(
                type = "state_update",
                stateUpdates = mutableMapOf(varName to parsedVal)
            )
        }

        // Fallback custom
        return EventAction(type = "custom", codeSnippet = clean)
    }

    private fun convertConditionToJs(condition: String): String {
        return condition
            .replace(Regex("""(?<!!)==(?!=)"""), "===")
            .replace(Regex("""(?<!\!)!=(?!=)"""), "!==")
            .replace("====", "===")
            .replace("GameState.START", "'START'")
            .replace("GameState.RUNNING", "'RUNNING'")
            .replace("GameState.GAME_OVER", "'GAME_OVER'")
            .replace("GameState.GAMEOVER", "'GAME_OVER'")
    }

    private fun extractBackgroundColor(code: String): String {
        return when {
            code.contains("Color(0xFF70C5CE)", true) || code.contains("Color(0xFF4EC0CA)", true) -> "#4EC0CA"
            code.contains("Colors.lightBlue", true) -> "#4EC0CA"
            else -> "#FFFFFF"
        }
    }

    private fun extractAppTitle(files: List<ProjectFileEntity>): String {
        val stringsXml = files.find { it.path.contains("strings.xml") }?.content
        if (stringsXml != null) {
            val m = Regex("""<string\s+name=["']app_name["']>([^<]+)</string>""").find(stringsXml)
            if (m != null) return m.groupValues[1].trim()
        }
        return "App Preview"
    }
}
