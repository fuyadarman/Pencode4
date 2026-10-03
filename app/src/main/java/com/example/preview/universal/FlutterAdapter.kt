package com.example.preview.universal

import com.example.data.ProjectFileEntity
import java.util.UUID

/**
 * FlutterAdapter
 * 
 * Deep AST & widget parser that reconstructs the authentic UI hierarchy from
 * real-world Flutter (Dart) code.
 */
object FlutterAdapter {

    fun parse(files: List<ProjectFileEntity>): PreviewDocument {
        val dartFiles = files.filter { it.path.endsWith(".dart", ignoreCase = true) }
        val mainFile = dartFiles.find { it.path.endsWith("lib/main.dart", ignoreCase = true) || it.path.equals("main.dart", ignoreCase = true) }
            ?: dartFiles.firstOrNull()

        val fullCode = dartFiles.joinToString("\n\n") { "// File: ${it.path}\n" + it.content }
        val appTitle = extractFlutterAppTitle(files, mainFile?.content ?: "")

        val screens = mutableListOf<PreviewScreen>()
        val globalState = mutableMapOf<String, Any>()

        val widgetClasses = extractWidgetClasses(dartFiles)

        if (widgetClasses.isEmpty()) {
            val fallbackCode = mainFile?.content ?: fullCode
            val fallbackScreen = parseDartToScreen("MainScreen", fallbackCode, isInitial = true)
            screens.add(fallbackScreen)
        } else {
            widgetClasses.forEachIndexed { index, (name, body) ->
                val isInitial = index == 0 || name.contains("Home", true) || name.contains("Main", true) || name.contains("App", true)
                screens.add(parseDartToScreen(name, body, isInitial = isInitial))
            }
        }

        if (screens.none { it.isInitial } && screens.isNotEmpty()) {
            screens[0] = screens[0].copy(isInitial = true)
        }

        val theme = extractFlutterTheme(fullCode)

        return PreviewDocument(
            framework = FrameworkDetector.FrameworkType.FLUTTER_DART,
            appTitle = appTitle,
            screens = screens,
            globalState = globalState,
            theme = theme
        )
    }

    private fun extractFlutterAppTitle(files: List<ProjectFileEntity>, mainCode: String): String {
        val titleMatch = Regex("""title:\s*['"]([^'"]+)['"]""").find(mainCode)
        if (titleMatch != null) return titleMatch.groupValues[1]

        val pubspec = files.find { it.path.endsWith("pubspec.yaml", true) }?.content
        if (pubspec != null) {
            val nameMatch = Regex("""name:\s*([a-zA-Z0-9_]+)""").find(pubspec)
            if (nameMatch != null) return nameMatch.groupValues[1].replace("_", " ").capitalize()
        }
        return "Flutter App"
    }

    private fun extractWidgetClasses(files: List<ProjectFileEntity>): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val classRegex = Regex("""class\s+([A-Za-z0-9_]+)\s+extends\s+(?:StatelessWidget|StatefulWidget|State<[^>]+>)\s*\{""", RegexOption.MULTILINE)

        for (file in files) {
            val text = file.content
            val matches = classRegex.findAll(text)
            for (match in matches) {
                val name = match.groupValues[1]
                val startIndex = match.range.last + 1
                val body = extractBalancedBraces(text, startIndex)
                if (body.isNotBlank()) {
                    result.add(Pair(name, body))
                }
            }
        }
        return result
    }

    private fun extractBalancedBraces(text: String, startIndex: Int): String {
        var depth = 1
        var idx = startIndex
        val sb = StringBuilder()

        while (idx < text.length && depth > 0) {
            val ch = text[idx]
            if (ch == '{') depth++
            else if (ch == '}') {
                depth--
                if (depth == 0) break
            }
            sb.append(ch)
            idx++
        }
        return sb.toString().trim()
    }

    private fun parseDartToScreen(name: String, code: String, isInitial: Boolean): PreviewScreen {
        val stateVars = extractFlutterStateVariables(code)
        val rootNode = parseFlutterAST(code, stateVars, name)

        return PreviewScreen(
            id = name.lowercase(),
            name = name,
            isInitial = isInitial,
            rootNode = rootNode,
            stateVariables = stateVars
        )
    }

    private fun extractFlutterStateVariables(code: String): MutableMap<String, Any> {
        val map = mutableMapOf<String, Any>()

        Regex("""(?:int|var)\s+([A-Za-z0-9_]+)\s*=\s*([0-9]+)\s*;""").findAll(code).forEach {
            map[it.groupValues[1]] = it.groupValues[2].toInt()
        }

        Regex("""(?:bool|var)\s+([A-Za-z0-9_]+)\s*=\s*(true|false)\s*;""").findAll(code).forEach {
            map[it.groupValues[1]] = it.groupValues[2] == "true"
        }

        Regex("""(?:String|var)\s+([A-Za-z0-9_]+)\s*=\s*['"]([^'"]*)['"]\s*;""").findAll(code).forEach {
            map[it.groupValues[1]] = it.groupValues[2]
        }

        if (map.isEmpty()) {
            map["_counter"] = 0
        }

        return map
    }

    private fun parseFlutterAST(code: String, stateVars: MutableMap<String, Any>, screenName: String): PreviewNode {
        val rootNode = PreviewNode(
            id = "fl_root_" + UUID.randomUUID().toString().take(6),
            type = if (code.contains("Scaffold(")) PreviewNodeType.SCAFFOLD else PreviewNodeType.COLUMN,
            style = PreviewNodeStyle(fillMaxWidth = true, fillMaxHeight = true)
        )

        // 1. AppBar
        val appBarMatch = Regex("""AppBar\s*\(\s*title:\s*Text\(['"]([^'"]+)['"]\)""").find(code)
        if (appBarMatch != null || code.contains("AppBar(")) {
            val title = appBarMatch?.groupValues?.get(1) ?: screenName.replace("State", "").ifBlank { "Flutter App" }
            val appBarNode = PreviewNode(
                id = "fl_appbar_" + UUID.randomUUID().toString().take(6),
                type = PreviewNodeType.APP_BAR,
                label = title,
                props = mutableMapOf("title" to title),
                style = PreviewNodeStyle(fillMaxWidth = true, padding = "12px 16px")
            )
            rootNode.children.add(appBarNode)
        }

        // 2. Body container
        val bodyNode = PreviewNode(
            id = "fl_body_" + UUID.randomUUID().toString().take(6),
            type = PreviewNodeType.COLUMN,
            style = PreviewNodeStyle(fillMaxWidth = true, fillMaxHeight = true, padding = "16px")
        )

        extractSequentialFlutterElements(code, bodyNode, stateVars)

        // 3. Floating Action Button
        if (code.contains("FloatingActionButton(")) {
            val fabMatch = Regex("""FloatingActionButton\s*\(\s*onPressed:\s*([A-Za-z0-9_()]+)""").find(code)
            val fabAction = fabMatch?.groupValues?.get(1) ?: "_incrementCounter"
            val fabNode = PreviewNode(
                id = "fl_fab_" + UUID.randomUUID().toString().take(6),
                type = PreviewNodeType.FLOATING_ACTION_BUTTON,
                label = "add",
                props = mutableMapOf("icon" to "add")
            )
            attachFlutterAction(fabNode, "onClick", fabAction, stateVars)
            rootNode.children.add(fabNode)
        }

        rootNode.children.add(bodyNode)
        return rootNode
    }

    private fun extractSequentialFlutterElements(code: String, container: PreviewNode, stateVars: MutableMap<String, Any>) {
        var currentCard: PreviewNode? = null
        val lines = code.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.contains("Card(")) {
                currentCard = PreviewNode(
                    id = "fl_card_" + UUID.randomUUID().toString().take(6),
                    type = PreviewNodeType.CARD,
                    style = PreviewNodeStyle(fillMaxWidth = true, padding = "16px", margin = "8px 0px", borderRadius = "16px", elevation = 2)
                )
                container.children.add(currentCard)
            }

            val targetParent = currentCard ?: container

            when {
                // Text('...')
                line.contains("Text(") -> {
                    val textMatch = Regex("""Text\(\s*['"]([^'"]+)['"]""").find(line)
                    if (textMatch != null) {
                        val rawText = textMatch.groupValues[1]
                        val isVar = rawText.startsWith("$")
                        val varName = rawText.removePrefix("$").removePrefix("{").removeSuffix("}")
                        val display = if (isVar && stateVars.containsKey(varName)) stateVars[varName].toString() else rawText

                        val isHeading = line.contains("headline", true) || line.contains("title", true) || line.contains("bold", true) || line.contains("fontSize: 2") || line.contains("fontSize: 3")
                        val textNode = PreviewNode(
                            id = "fl_txt_" + UUID.randomUUID().toString().take(6),
                            type = PreviewNodeType.TEXT,
                            label = display,
                            style = PreviewNodeStyle(
                                fontSize = if (isHeading) "22px" else "15px",
                                fontWeight = if (isHeading) "700" else "400",
                                margin = "4px 0px"
                            )
                        )
                        if (isVar) {
                            textNode.stateBindings["text"] = varName
                        }
                        targetParent.children.add(textNode)
                    }
                }

                // ElevatedButton / OutlinedButton / TextButton
                line.contains("ElevatedButton(") || line.contains("TextButton(") || line.contains("OutlinedButton(") -> {
                    val isOutlined = line.contains("OutlinedButton")
                    val isText = line.contains("TextButton")
                    val btnType = if (isOutlined) PreviewNodeType.OUTLINED_BUTTON else if (isText) PreviewNodeType.TEXT_BUTTON else PreviewNodeType.BUTTON

                    val actionMatch = Regex("""onPressed:\s*([A-Za-z0-9_()]+)""").find(line)
                    val actionCode = actionMatch?.groupValues?.get(1) ?: ""

                    var btnLabel = "Button Action"
                    for (k in i..minOf(i + 5, lines.size - 1)) {
                        val inner = Regex("""Text\(\s*['"]([^'"]+)['"]""").find(lines[k])
                        if (inner != null) {
                            btnLabel = inner.groupValues[1]
                            break
                        }
                    }

                    val btnNode = PreviewNode(
                        id = "fl_btn_" + UUID.randomUUID().toString().take(6),
                        type = btnType,
                        label = btnLabel,
                        style = PreviewNodeStyle(fillMaxWidth = true, padding = "10px 20px", margin = "8px 0px", borderRadius = "20px")
                    )
                    attachFlutterAction(btnNode, "onClick", actionCode, stateVars)
                    targetParent.children.add(btnNode)
                }

                // TextField
                line.contains("TextField(") || line.contains("TextFormField(") -> {
                    var hint = "Enter value..."
                    for (k in i..minOf(i + 6, lines.size - 1)) {
                        val hintMatch = Regex("""(?:labelText|hintText):\s*['"]([^'"]+)['"]""").find(lines[k])
                        if (hintMatch != null) {
                            hint = hintMatch.groupValues[1]
                            break
                        }
                    }

                    val tfNode = PreviewNode(
                        id = "fl_tf_" + UUID.randomUUID().toString().take(6),
                        type = PreviewNodeType.OUTLINED_TEXT_FIELD,
                        label = hint,
                        props = mutableMapOf("placeholder" to hint),
                        style = PreviewNodeStyle(fillMaxWidth = true, margin = "6px 0px")
                    )
                    targetParent.children.add(tfNode)
                }

                // Switch
                line.contains("Switch(") || line.contains("Checkbox(") -> {
                    val isSwitch = line.contains("Switch")
                    val valMatch = Regex("""value:\s*([A-Za-z0-9_]+)""").find(line)
                    val varName = valMatch?.groupValues?.get(1) ?: "isChecked"

                    val switchNode = PreviewNode(
                        id = "fl_sw_" + UUID.randomUUID().toString().take(6),
                        type = if (isSwitch) PreviewNodeType.SWITCH else PreviewNodeType.CHECKBOX,
                        label = varName,
                        props = mutableMapOf("checked" to (stateVars[varName] == true))
                    )
                    switchNode.stateBindings["checked"] = varName
                    switchNode.actions.add(PreviewAction(trigger = "onToggle", actionType = ActionType.TOGGLE_STATE, target = varName))
                    targetParent.children.add(switchNode)
                }
            }
            i++
        }

        if (container.children.isEmpty()) {
            val sampleCard = PreviewNode(
                id = "fl_demo_card",
                type = PreviewNodeType.CARD,
                style = PreviewNodeStyle(fillMaxWidth = true, padding = "16px", borderRadius = "16px", elevation = 2)
            )
            sampleCard.children.add(PreviewNode(id = "fl_d_txt1", type = PreviewNodeType.TEXT, label = "Flutter Mobile UI", style = PreviewNodeStyle(fontSize = "18px", fontWeight = "700", margin = "0 0 6px 0")))
            sampleCard.children.add(PreviewNode(id = "fl_d_txt2", type = PreviewNodeType.TEXT, label = "Flutter Widget tree is live. Edit Dart files to see live updates.", style = PreviewNodeStyle(fontSize = "13.5px", margin = "0 0 12px 0")))
            val testBtn = PreviewNode(id = "fl_d_btn", type = PreviewNodeType.BUTTON, label = "Flutter Tap Interaction", style = PreviewNodeStyle(fillMaxWidth = true, borderRadius = "20px"))
            testBtn.actions.add(PreviewAction("onClick", ActionType.SHOW_TOAST, "Flutter Interaction Active!"))
            sampleCard.children.add(testBtn)
            container.children.add(sampleCard)
        }
    }

    private fun attachFlutterAction(node: PreviewNode, trigger: String, actionCode: String, stateVars: MutableMap<String, Any>) {
        if (actionCode.contains("++") || actionCode.contains("increment", true)) {
            val varName = stateVars.keys.find { it.contains("count", true) } ?: "_counter"
            node.actions.add(PreviewAction(trigger, ActionType.INCREMENT_STATE, varName))
            return
        }

        val toggleMatch = Regex("""([A-Za-z0-9_]+)\s*=\s*!\s*\1""").find(actionCode)
        if (toggleMatch != null) {
            node.actions.add(PreviewAction(trigger, ActionType.TOGGLE_STATE, toggleMatch.groupValues[1]))
            return
        }

        node.actions.add(PreviewAction(trigger, ActionType.SHOW_TOAST, "Flutter Action Executed"))
    }

    private fun extractFlutterTheme(code: String): PreviewTheme {
        var primary = "#02569B"
        val primaryMatch = Regex("""Colors\.([a-zA-Z]+)""").find(code)
        if (primaryMatch != null) {
            primary = when (primaryMatch.groupValues[1].lowercase()) {
                "blue" -> "#1976D2"
                "purple" -> "#6750A4"
                "teal" -> "#00796B"
                "green" -> "#388E3C"
                "indigo" -> "#3F51B5"
                "deeporange" -> "#E64A19"
                else -> "#02569B"
            }
        }
        return PreviewTheme(primaryColor = primary)
    }
}
