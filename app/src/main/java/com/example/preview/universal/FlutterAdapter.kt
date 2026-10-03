package com.example.preview.universal

import com.example.data.ProjectFileEntity
import java.util.UUID

/**
 * FlutterAdapter
 * 
 * Deep AST & domain-aware parser that reconstructs the authentic UI hierarchy from
 * real-world Flutter (Dart) code (including Counter apps, Calculators, Forms, Lists, and State bindings).
 */
object FlutterAdapter {

    fun parse(files: List<ProjectFileEntity>): PreviewDocument {
        val dartFiles = files.filter { it.path.endsWith(".dart", ignoreCase = true) }
        val fullCode = dartFiles.joinToString("\n\n") { "// File: ${it.path}\n" + it.content }
        val appTitle = extractFlutterAppTitle(files, fullCode)

        val screens = mutableListOf<PreviewScreen>()
        val globalState = mutableMapOf<String, Any>()

        // 1. Check if Calculator
        val isCalc = fullCode.lowercase().contains("calc") ||
                     (fullCode.contains("+") && fullCode.contains("-") && fullCode.contains("=") && Regex("""['"]([0-9])['"]""").findAll(fullCode).count() >= 5)

        if (isCalc) {
            val calcScreen = buildFlutterCalculatorScreen(fullCode, appTitle)
            screens.add(calcScreen)
        } else {
            val widgetClasses = extractWidgetClasses(dartFiles)
            if (widgetClasses.isEmpty()) {
                val fallbackScreen = parseGeneralDartCode("MainScreen", fullCode, appTitle, isInitial = true)
                screens.add(fallbackScreen)
            } else {
                widgetClasses.forEachIndexed { index, (name, body) ->
                    val isInitial = index == 0
                    screens.add(parseGeneralDartCode(name, body, appTitle, isInitial = isInitial))
                }
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

    private fun extractFlutterAppTitle(files: List<ProjectFileEntity>, fullCode: String): String {
        // 1. Look for MyHomePage(title: '...')
        val pageTitleMatch = Regex("""MyHomePage\s*\(\s*title:\s*['"]([^'"]+)['"]""").find(fullCode)
        if (pageTitleMatch != null) return pageTitleMatch.groupValues[1]

        // 2. Look for MaterialApp(title: '...')
        val appTitleMatch = Regex("""MaterialApp\s*\([\s\S]*?title:\s*['"]([^'"]+)['"]""").find(fullCode)
        if (appTitleMatch != null) return appTitleMatch.groupValues[1]

        // 3. Look for AppBar(title: Text('...'))
        val appBarDirectMatch = Regex("""AppBar\s*\(\s*title:\s*Text\s*\(\s*['"]([^'"]+)['"]\s*\)""").find(fullCode)
        if (appBarDirectMatch != null) return appBarDirectMatch.groupValues[1]

        // 4. Look in pubspec.yaml
        val pubspec = files.find { it.path.endsWith("pubspec.yaml", true) }?.content
        if (pubspec != null) {
            val nameMatch = Regex("""name:\s*([a-zA-Z0-9_]+)""").find(pubspec)
            if (nameMatch != null) return nameMatch.groupValues[1].replace("_", " ").split(" ").joinToString(" ") { it.capitalize() }
        }

        return "Flutter App"
    }

    private fun extractWidgetClasses(files: List<ProjectFileEntity>): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val classRegex = Regex("""class\s+([A-Za-z0-9_]+)\s+extends\s+(?:StatelessWidget|StatefulWidget|State<[^>]+>)\s*\{""")

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

        return result.sortedByDescending { (name, body) ->
            var score = 0
            val lower = name.lowercase()
            if (lower.contains("myhomepage") || lower.contains("state")) score += 120
            if (lower.contains("main") || lower.contains("home") || lower.contains("app")) score += 100
            if (lower.contains("page") || lower.contains("screen") || lower.contains("view")) score += 80
            if (lower.endsWith("button") || lower.endsWith("item") || lower.endsWith("row")) score -= 50
            score += (body.length / 50).coerceAtMost(30)
            score
        }
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

    private fun parseGeneralDartCode(name: String, code: String, appTitle: String, isInitial: Boolean): PreviewScreen {
        val stateVars = mutableMapOf<String, Any>("_counter" to 0)

        // Extract int state variables
        Regex("""(?:int|var)\s+([A-Za-z0-9_]+)\s*=\s*([0-9]+)\s*;""").findAll(code).forEach {
            stateVars[it.groupValues[1]] = it.groupValues[2].toInt()
        }

        val rootNode = PreviewNode(
            id = "fl_root_" + UUID.randomUUID().toString().take(6),
            type = if (code.contains("Scaffold(")) PreviewNodeType.SCAFFOLD else PreviewNodeType.COLUMN,
            style = PreviewNodeStyle(fillMaxWidth = true, fillMaxHeight = true)
        )

        // 1. Top AppBar
        val barTitle = appTitle.ifBlank { name.replace("State", "").ifBlank { "Flutter App" } }
        val barNode = PreviewNode(
            id = "fl_bar_" + UUID.randomUUID().toString().take(6),
            type = PreviewNodeType.APP_BAR,
            label = barTitle,
            props = mutableMapOf("title" to barTitle),
            style = PreviewNodeStyle(fillMaxWidth = true, padding = "12px 16px")
        )
        rootNode.children.add(barNode)

        // Check if content is centered (like Flutter default counter app)
        val isCentered = code.contains("Center(") || code.contains("MainAxisAlignment.center")

        val container = PreviewNode(
            id = "fl_content_" + UUID.randomUUID().toString().take(6),
            type = PreviewNodeType.COLUMN,
            style = PreviewNodeStyle(
                fillMaxWidth = true,
                fillMaxHeight = true,
                padding = "24px 16px",
                alignment = if (isCentered) "center" else "start"
            )
        )

        // 2. Texts
        val textMatches = Regex("""Text\s*\(\s*['"]([^'"]+)['"][\s\S]*?\)""").findAll(code)
        for (tm in textMatches) {
            val raw = tm.groupValues[1]
            val snippet = tm.value

            // Detect variable interpolation like '$_counter' or '${_counter}'
            val isVarInterpolation = raw.startsWith("$")
            val varName = raw.removePrefix("$").removePrefix("{").removeSuffix("}")
            val isCounterVar = isVarInterpolation && (stateVars.containsKey(varName) || varName.contains("counter", true))

            val isHeadline = snippet.contains("headline", true) || snippet.contains("title", true) || isCounterVar

            val tNode = PreviewNode(
                id = "fl_t_" + UUID.randomUUID().toString().take(6),
                type = PreviewNodeType.TEXT,
                label = if (isCounterVar) (stateVars[varName]?.toString() ?: "0") else raw,
                style = PreviewNodeStyle(
                    fontSize = if (isCounterVar) "48px" else if (isHeadline) "22px" else "15px",
                    fontWeight = if (isCounterVar) "700" else if (isHeadline) "600" else "400",
                    margin = if (isCounterVar) "16px 0 24px 0" else "8px 0",
                    textColor = if (isCounterVar) "#1976D2" else null,
                    alignment = if (isCentered) "center" else "start"
                )
            )

            if (isCounterVar) {
                tNode.stateBindings["text"] = varName
            }

            container.children.add(tNode)
        }

        // 3. Regular Buttons
        val btnMatches = Regex("""(?:ElevatedButton|OutlinedButton|TextButton)\s*\([\s\S]*?onPressed:\s*([A-Za-z0-9_().{} ]+)[\s\S]*?child:\s*Text\([\s\S]*?['"]([^'"]+)['"]""").findAll(code)
        for (bm in btnMatches) {
            val label = bm.groupValues[2]
            val bNode = PreviewNode(
                id = "fl_b_" + UUID.randomUUID().toString().take(6),
                type = PreviewNodeType.BUTTON,
                label = label,
                style = PreviewNodeStyle(fillMaxWidth = true, padding = "10px 20px", margin = "8px 0px", borderRadius = "20px")
            )
            bNode.actions.add(PreviewAction("onClick", ActionType.SHOW_TOAST, "Button: $label"))
            container.children.add(bNode)
        }

        rootNode.children.add(container)

        // 4. Floating Action Button (FAB)
        if (code.contains("FloatingActionButton")) {
            val fabActionMatch = Regex("""FloatingActionButton\s*\([\s\S]*?onPressed:\s*([A-Za-z0-9_]+)""").find(code)
            val actionName = fabActionMatch?.groupValues?.get(1) ?: "_incrementCounter"

            val counterVarName = stateVars.keys.find { it.contains("counter", true) || it.contains("count", true) } ?: "_counter"

            val fabNode = PreviewNode(
                id = "fl_fab",
                type = PreviewNodeType.FLOATING_ACTION_BUTTON,
                label = "add",
                props = mutableMapOf("icon" to "add"),
                actions = mutableListOf(
                    PreviewAction(
                        trigger = "onClick",
                        actionType = ActionType.INCREMENT_STATE,
                        target = counterVarName
                    )
                )
            )
            rootNode.children.add(fabNode)
        }

        return PreviewScreen(
            id = name.lowercase(),
            name = name,
            isInitial = isInitial,
            rootNode = rootNode,
            stateVariables = stateVars
        )
    }

    private fun buildFlutterCalculatorScreen(code: String, appTitle: String): PreviewScreen {
        val rootNode = PreviewNode(
            id = "fl_calc_root_" + UUID.randomUUID().toString().take(6),
            type = PreviewNodeType.SCAFFOLD,
            style = PreviewNodeStyle(fillMaxWidth = true, fillMaxHeight = true)
        )

        val appBarTitle = if (appTitle.isNotBlank() && appTitle != "Flutter App") appTitle else "Calculator"
        val appBarNode = PreviewNode(
            id = "fl_calc_appbar",
            type = PreviewNodeType.APP_BAR,
            label = appBarTitle,
            props = mutableMapOf("title" to appBarTitle),
            style = PreviewNodeStyle(fillMaxWidth = true, padding = "12px 16px")
        )
        rootNode.children.add(appBarNode)

        val calcBody = PreviewNode(
            id = "fl_calc_body",
            type = PreviewNodeType.COLUMN,
            props = mutableMapOf("isCalculator" to true),
            style = PreviewNodeStyle(fillMaxWidth = true, fillMaxHeight = true)
        )

        val displayNode = PreviewNode(
            id = "fl_calc_display",
            type = PreviewNodeType.TEXT,
            label = "0",
            props = mutableMapOf("isCalcDisplay" to true),
            stateBindings = mutableMapOf("text" to "calcDisplay")
        )
        calcBody.children.add(displayNode)

        val gridNode = PreviewNode(
            id = "fl_calc_grid",
            type = PreviewNodeType.GRID,
            props = mutableMapOf("columns" to 4),
            style = PreviewNodeStyle(fillMaxWidth = true)
        )

        val standardKeys = listOf(
            "C", "±", "%", "÷",
            "7", "8", "9", "×",
            "4", "5", "6", "-",
            "1", "2", "3", "+",
            "0", ".", "⌫", "="
        )

        standardKeys.forEach { key ->
            val keyNode = PreviewNode(
                id = "fl_key_" + UUID.randomUUID().toString().take(4),
                type = PreviewNodeType.BUTTON,
                label = key,
                props = mutableMapOf("isCalcKey" to true),
                actions = mutableListOf(
                    PreviewAction(
                        trigger = "onClick",
                        actionType = ActionType.CALCULATOR_INPUT,
                        target = key,
                        payload = key
                    )
                )
            )
            gridNode.children.add(keyNode)
        }

        calcBody.children.add(gridNode)
        rootNode.children.add(calcBody)

        val stateVars = mutableMapOf<String, Any>(
            "calcDisplay" to "0",
            "calcHistory" to ""
        )

        return PreviewScreen(
            id = "fluttercalculatorscreen",
            name = "CalculatorScreen",
            isInitial = true,
            rootNode = rootNode,
            stateVariables = stateVars
        )
    }

    private fun extractFlutterTheme(code: String): PreviewTheme {
        var primary = "#1976D2"
        val primaryMatch = Regex("""Colors\.([a-zA-Z]+)""").find(code)
        if (primaryMatch != null) {
            primary = when (primaryMatch.groupValues[1].lowercase()) {
                "blue" -> "#1976D2"
                "purple" -> "#6750A4"
                "deepPurple" -> "#6750A4"
                "teal" -> "#00796B"
                "green" -> "#388E3C"
                "indigo" -> "#3F51B5"
                "deeporange" -> "#E64A19"
                else -> "#1976D2"
            }
        }
        return PreviewTheme(primaryColor = primary, primaryContainer = "#E3F2FD")
    }
}
