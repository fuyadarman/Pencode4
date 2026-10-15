package com.example.preview.universal

import java.util.UUID

/**
 * HierarchicalUiTreeParser
 *
 * True recursive AST and layout tree parser for Kotlin Jetpack Compose and Flutter Dart.
 * Uses robust character-level balanced paren and brace matching.
 * Preserves full nested parent-child hierarchies with authentic Material 3 properties.
 */
object HierarchicalUiTreeParser {

    fun parseComposeTree(code: String, stateVars: MutableMap<String, Any>): PreviewNode {
        val root = PreviewNode(
            id = "root_" + UUID.randomUUID().toString().take(6),
            type = if (code.contains("Scaffold")) PreviewNodeType.SCAFFOLD else PreviewNodeType.COLUMN,
            style = ComposeModifierParser.parseStyle(code, PreviewNodeStyle(fillMaxWidth = true, fillMaxHeight = true))
        )

        parseRecursive(code, root, stateVars)
        return root
    }

    private fun parseRecursive(code: String, parent: PreviewNode, stateVars: MutableMap<String, Any>) {
        var cursor = 0
        val length = code.length

        while (cursor < length) {
            val componentMatch = findNextComponent(code, cursor) ?: break
            val (compType, compName, header, body, nextPos) = componentMatch

            val node = createNode(compType, compName, header, body, stateVars)

            if (body.isNotBlank() && isContainer(compType)) {
                parseRecursive(body, node, stateVars)
            }

            parent.children.add(node)
            cursor = nextPos
        }
    }

    private data class ComponentMatch(
        val type: PreviewNodeType,
        val name: String,
        val header: String,
        val body: String,
        val nextPos: Int
    )

    private val COMPONENT_TYPES = listOf(
        "TopAppBar" to PreviewNodeType.APP_BAR,
        "BottomNavigation" to PreviewNodeType.BOTTOM_NAV,
        "NavigationBar" to PreviewNodeType.BOTTOM_NAV,
        "FloatingActionButton" to PreviewNodeType.FLOATING_ACTION_BUTTON,
        "ElevatedCard" to PreviewNodeType.CARD,
        "OutlinedCard" to PreviewNodeType.CARD,
        "Card" to PreviewNodeType.CARD,
        "Surface" to PreviewNodeType.CONTAINER,
        "LazyColumn" to PreviewNodeType.LAZY_COLUMN,
        "LazyRow" to PreviewNodeType.LAZY_ROW,
        "Column" to PreviewNodeType.COLUMN,
        "Row" to PreviewNodeType.ROW,
        "Box" to PreviewNodeType.BOX,
        "TabRow" to PreviewNodeType.TAB_ROW,
        "Tab" to PreviewNodeType.TAB,
        "FilledTonalButton" to PreviewNodeType.FILLED_TONAL_BUTTON,
        "ElevatedButton" to PreviewNodeType.ELEVATED_BUTTON,
        "OutlinedButton" to PreviewNodeType.OUTLINED_BUTTON,
        "TextButton" to PreviewNodeType.TEXT_BUTTON,
        "IconButton" to PreviewNodeType.BUTTON,
        "Button" to PreviewNodeType.BUTTON,
        "OutlinedTextField" to PreviewNodeType.OUTLINED_TEXT_FIELD,
        "TextField" to PreviewNodeType.TEXT_FIELD,
        "Text" to PreviewNodeType.TEXT,
        "Icon" to PreviewNodeType.ICON,
        "Image" to PreviewNodeType.IMAGE,
        "Switch" to PreviewNodeType.SWITCH,
        "Checkbox" to PreviewNodeType.CHECKBOX,
        "RadioButton" to PreviewNodeType.RADIO_BUTTON,
        "Slider" to PreviewNodeType.SLIDER,
        "Spacer" to PreviewNodeType.SPACER,
        "Divider" to PreviewNodeType.DIVIDER,
        "HorizontalDivider" to PreviewNodeType.DIVIDER,
        "CircularProgressIndicator" to PreviewNodeType.CIRCULAR_PROGRESS_INDICATOR,
        "LinearProgressIndicator" to PreviewNodeType.LINEAR_PROGRESS_INDICATOR,
        "Badge" to PreviewNodeType.BADGE,
        "AssistChip" to PreviewNodeType.CHIP,
        "FilterChip" to PreviewNodeType.CHIP,
        "SuggestionChip" to PreviewNodeType.CHIP
    )

    private fun findNextComponent(code: String, startIdx: Int): ComponentMatch? {
        var earliestMatch: ComponentMatch? = null
        var earliestPos = Int.MAX_VALUE

        for ((name, type) in COMPONENT_TYPES) {
            val regex = Regex("""\b$name\b""")
            val m = regex.find(code, startIdx) ?: continue
            val pos = m.range.first

            if (pos < earliestPos) {
                // Parse balanced parentheses after component name if any
                var cursor = m.range.last + 1
                while (cursor < code.length && code[cursor].isWhitespace()) cursor++

                var header = ""
                if (cursor < code.length && code[cursor] == '(') {
                    val parenEnd = findClosingDelimiter(code, cursor, '(', ')')
                    header = code.substring(cursor, parenEnd)
                    cursor = parenEnd
                }

                while (cursor < code.length && code[cursor].isWhitespace()) cursor++

                var body = ""
                if (cursor < code.length && code[cursor] == '{') {
                    val braceEnd = findClosingDelimiter(code, cursor, '{', '}')
                    body = if (braceEnd > cursor + 1) code.substring(cursor + 1, braceEnd - 1) else ""
                    cursor = braceEnd
                }

                earliestPos = pos
                earliestMatch = ComponentMatch(type, name, header, body, cursor)
            }
        }

        return earliestMatch
    }

    private fun findClosingDelimiter(code: String, openIdx: Int, openChar: Char, closeChar: Char): Int {
        var depth = 0
        var inString = false
        var stringChar = ' '
        var idx = openIdx

        while (idx < code.length) {
            val ch = code[idx]

            if (inString) {
                if (ch == '\\' && idx + 1 < code.length) {
                    idx += 2
                    continue
                }
                if (ch == stringChar) {
                    inString = false
                }
            } else {
                if (ch == '"' || ch == '\'') {
                    inString = true
                    stringChar = ch
                } else if (ch == openChar) {
                    depth++
                } else if (ch == closeChar) {
                    depth--
                    if (depth == 0) return idx + 1
                }
            }
            idx++
        }
        return code.length
    }

    private fun isContainer(type: PreviewNodeType): Boolean {
        return when (type) {
            PreviewNodeType.SCAFFOLD,
            PreviewNodeType.COLUMN,
            PreviewNodeType.ROW,
            PreviewNodeType.BOX,
            PreviewNodeType.CARD,
            PreviewNodeType.CONTAINER,
            PreviewNodeType.LAZY_COLUMN,
            PreviewNodeType.LAZY_ROW,
            PreviewNodeType.GRID,
            PreviewNodeType.TAB_ROW,
            PreviewNodeType.ANIMATED_VISIBILITY -> true
            else -> false
        }
    }

    private fun createNode(
        type: PreviewNodeType,
        name: String,
        header: String,
        body: String,
        stateVars: MutableMap<String, Any>
    ): PreviewNode {
        val id = name.lowercase().take(4) + "_" + UUID.randomUUID().toString().take(4)
        val style = ComposeModifierParser.parseStyle(header, PreviewNodeStyle())

        var label = ""
        val stateBindings = mutableMapOf<String, String>()

        when (type) {
            PreviewNodeType.TEXT -> {
                val strLiteralMatch = Regex("""["']([^"']*)["']""").find(header)
                if (strLiteralMatch != null) {
                    label = strLiteralMatch.groupValues[1]
                } else {
                    val varMatch = Regex("""(?:text\s*=\s*)?([A-Za-z0-9_]+)""").find(header)
                    val varName = varMatch?.groupValues?.get(1) ?: ""
                    if (varName.isNotBlank()) {
                        stateBindings["text"] = varName
                        label = stateVars[varName]?.toString() ?: ""
                        if (label.isBlank()) {
                            label = if (varName.contains("result", true) || varName.contains("count", true) || varName.contains("display", true)) "0" else ""
                        }
                    }
                }
            }
            PreviewNodeType.APP_BAR -> {
                val titleMatch = Regex("""title\s*=\s*\{\s*Text\([\s\S]*?["']([^"']+)["']""").find(header + body)
                    ?: Regex("""["']([^"']+)["']""").find(header + body)
                label = titleMatch?.groupValues?.get(1) ?: "App"
            }
            PreviewNodeType.BUTTON,
            PreviewNodeType.ELEVATED_BUTTON,
            PreviewNodeType.FILLED_TONAL_BUTTON,
            PreviewNodeType.OUTLINED_BUTTON,
            PreviewNodeType.TEXT_BUTTON -> {
                val innerTextMatch = Regex("""Text\s*\([\s\S]*?["']([^"']+)["']""").find(body)
                    ?: Regex("""["']([^"']+)["']""").find(header + body)
                label = innerTextMatch?.groupValues?.get(1) ?: ""
                
                if (label.isBlank()) {
                    val iconMatch = Regex("""Icons\.[A-Za-z]+\.([A-Za-z0-9_]+)""").find(body + header)
                    if (iconMatch != null) {
                        label = iconMatch.groupValues[1]
                    }
                }
                if (label.isBlank()) {
                    label = "Action"
                }
            }
            PreviewNodeType.ICON -> {
                val ic = Regex("""Icons\.[A-Za-z]+\.([A-Za-z0-9_]+)""").find(header)
                label = ic?.groupValues?.get(1)?.lowercase() ?: "star"
            }
            PreviewNodeType.OUTLINED_TEXT_FIELD,
            PreviewNodeType.TEXT_FIELD -> {
                val labelMatch = Regex("""label\s*=\s*\{\s*Text\([\s\S]*?["']([^"']+)["']""").find(header)
                    ?: Regex("""placeholder\s*=\s*\{\s*Text\([\s\S]*?["']([^"']+)["']""").find(header)
                label = labelMatch?.groupValues?.get(1) ?: "Enter text..."
            }
            else -> {}
        }

        val node = PreviewNode(
            id = id,
            type = type,
            label = label,
            style = style,
            stateBindings = stateBindings
        )

        // Parse onClick actions
        val onClickMatch = Regex("""onClick\s*=\s*\{([^}]*)\}""").find(header)
        if (onClickMatch != null) {
            val actionCode = onClickMatch.groupValues[1].trim()
            val parsedActions = ActionRuntimeEngine.parseComposeClickAction(actionCode, stateVars, label)
            node.actions.addAll(parsedActions)
        }

        // Parse State Bindings for input elements
        if (type == PreviewNodeType.TEXT_FIELD || type == PreviewNodeType.OUTLINED_TEXT_FIELD) {
            val valMatch = Regex("""value\s*=\s*([A-Za-z0-9_]+)""").find(header)
            if (valMatch != null) {
                val varName = valMatch.groupValues[1]
                node.stateBindings["value"] = varName
                node.actions.add(PreviewAction("onChange", ActionType.SET_STATE, varName))
            }
        } else if (type == PreviewNodeType.SWITCH || type == PreviewNodeType.CHECKBOX) {
            val checkedMatch = Regex("""checked\s*=\s*([A-Za-z0-9_]+)""").find(header)
            if (checkedMatch != null) {
                val varName = checkedMatch.groupValues[1]
                node.stateBindings["checked"] = varName
                node.actions.add(PreviewAction("onChange", ActionType.TOGGLE_STATE, varName))
            }
        }

        return node
    }
}
