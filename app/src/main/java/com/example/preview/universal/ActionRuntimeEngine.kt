package com.example.preview.universal

/**
 * ActionRuntimeEngine
 * 
 * Deep event, action, and state mutation engine for Mobile Previews.
 * Reconstructs authentic runtime behavior:
 * User Clicks -> Action Runtime -> State Update -> Re-render
 * 
 * Parses Compose / Flutter onClick blocks:
 * - Direct state mutations (e.g. `isLoading = true`, `count++`, `selectedTab = 1`)
 * - Inversions / Toggles (e.g. `isDarkMode = !isDarkMode`, `isChecked = !isChecked`)
 * - Screen Navigation (e.g. `navController.navigate("detail")`, `Navigator.push(...)`)
 * - Delayed Async Simulation (e.g. login API loading state -> success transition)
 */
object ActionRuntimeEngine {

    /**
     * Extracts reactive state variable declarations from Compose code.
     * e.g. `var isLoading by remember { mutableStateOf(false) }`
     * e.g. `val count = remember { mutableStateOf(0) }`
     */
    fun extractComposeStates(code: String): MutableMap<String, Any> {
        val states = mutableMapOf<String, Any>()

        // 1. mutableStateOf(initialValue)
        val stateRegex = Regex("""(?:var|val)\s+([A-Za-z0-9_]+)\s*(?:by|=)\s*remember\s*\{(?:\s*mutableStateOf|\s*mutableIntStateOf|\s*mutableDoubleStateOf|\s*mutableLongStateOf|\s*mutableFloatStateOf)?\s*\(([\s\S]*?)\)\}""")
        for (m in stateRegex.findAll(code)) {
            val name = m.groupValues[1]
            val initRaw = m.groupValues[2].trim().removeSuffix(")")
            val parsedValue: Any = when {
                initRaw == "true" -> true
                initRaw == "false" -> false
                initRaw.toIntOrNull() != null -> initRaw.toInt()
                initRaw.toDoubleOrNull() != null -> initRaw.toDouble()
                initRaw.startsWith("\"") && initRaw.endsWith("\"") -> initRaw.removeSurrounding("\"")
                initRaw.startsWith("'") && initRaw.endsWith("'") -> initRaw.removeSurrounding("'")
                else -> initRaw.ifBlank { "" }
            }
            states[name] = parsedValue
        }

        // 2. Fallback common state variables if found referenced
        if (code.contains("count", true) && !states.containsKey("count") && !states.containsKey("_counter")) {
            states["_counter"] = 0
        }
        if (code.contains("isLoading", true) && !states.containsKey("isLoading")) {
            states["isLoading"] = false
        }
        if (code.contains("isLoggedIn", true) && !states.containsKey("isLoggedIn")) {
            states["isLoggedIn"] = false
        }

        return states
    }

    /**
     * Parses the onClick code block into structured PreviewAction objects.
     */
    fun parseComposeClickAction(
        actionCode: String,
        stateVars: Map<String, Any>,
        buttonLabel: String = "Action"
    ): List<PreviewAction> {
        val actions = mutableListOf<PreviewAction>()
        val trimmed = actionCode.trim()

        if (trimmed.isBlank()) {
            actions.add(PreviewAction(
                trigger = "onClick",
                actionType = ActionType.SET_STATE,
                target = "_lastAction",
                payload = "Clicked $buttonLabel"
            ))
            return actions
        }

        // 1. Navigation: navController.navigate("screen_name") or onNavigate(...)
        val navMatch = Regex("""(?:navController\.navigate|navigate|pushRoute)\s*\(\s*["']([^"']+)["']\s*\)""").find(trimmed)
        if (navMatch != null) {
            val dest = navMatch.groupValues[1].lowercase()
            actions.add(PreviewAction(trigger = "onClick", actionType = ActionType.NAVIGATE, target = dest))
            return actions
        }

        // Back navigation: popBackStack()
        if (trimmed.contains("popBackStack()") || trimmed.contains("navigateUp()") || trimmed.contains("pop(")) {
            actions.add(PreviewAction(trigger = "onClick", actionType = ActionType.GO_BACK, target = ""))
            return actions
        }

        // 2. Increments: count++ or count += 1
        val incMatch = Regex("""([A-Za-z0-9_]+)\s*(?:\+\+|\+=\s*1)""").find(trimmed)
        if (incMatch != null) {
            val varName = incMatch.groupValues[1]
            actions.add(PreviewAction(trigger = "onClick", actionType = ActionType.INCREMENT_STATE, target = varName))
            return actions
        }

        // Decrements: count-- or count -= 1
        val decMatch = Regex("""([A-Za-z0-9_]+)\s*(?:--|-=\s*1)""").find(trimmed)
        if (decMatch != null) {
            val varName = decMatch.groupValues[1]
            actions.add(PreviewAction(trigger = "onClick", actionType = ActionType.DECREMENT_STATE, target = varName))
            return actions
        }

        // 3. Toggle: isSomething = !isSomething
        val toggleMatch = Regex("""([A-Za-z0-9_]+)\s*=\s*!\s*\1""").find(trimmed)
        if (toggleMatch != null) {
            val varName = toggleMatch.groupValues[1]
            actions.add(PreviewAction(trigger = "onClick", actionType = ActionType.TOGGLE_STATE, target = varName))
            return actions
        }

        // 4. State Assignment: variable = value (e.g. isLoading = true)
        val setMatches = Regex("""([A-Za-z0-9_]+)\s*=\s*([^;\n}]+)""").findAll(trimmed)
        var foundSet = false
        for (sm in setMatches) {
            val varName = sm.groupValues[1].trim()
            val rawVal = sm.groupValues[2].trim()

            if (varName != "modifier" && varName != "color" && varName != "onClick") {
                actions.add(PreviewAction(
                    trigger = "onClick",
                    actionType = ActionType.SET_STATE,
                    target = varName,
                    payload = rawVal
                ))
                foundSet = true
            }
        }
        if (foundSet) return actions

        // 5. Calculator input buttons
        if (buttonLabel.length in 1..4 && (buttonLabel.all { it.isDigit() } || "+-*/=C%.".contains(buttonLabel))) {
            actions.add(PreviewAction(
                trigger = "onClick",
                actionType = ActionType.CALCULATOR_INPUT,
                target = buttonLabel,
                payload = buttonLabel
            ))
            return actions
        }

        // 6. Generic interactive feedback
        actions.add(PreviewAction(
            trigger = "onClick",
            actionType = ActionType.SHOW_SNACKBAR,
            target = "$buttonLabel tapped"
        ))

        return actions
    }

    /**
     * Generates responsive Semantic DOM Action Runtime JavaScript.
     */
    fun generateRuntimeScript(initialState: Map<String, Any>, nodesJson: String): String {
        val stateJson = org.json.JSONObject(initialState).toString()
        return """
        <script>
            (function() {
                let state = $stateJson;
                const nodeHierarchy = $nodesJson;

                function findNodeById(root, id) {
                    if (!root) return null;
                    if (root.id === id) return root;
                    if (root.children) {
                        for (let child of root.children) {
                            const found = findNodeById(child, id);
                            if (found) return found;
                        }
                    }
                    return null;
                }

                function updateBindings() {
                    document.querySelectorAll('[data-bind-text]').forEach(function(el) {
                        const key = el.getAttribute('data-bind-text');
                        if (state[key] !== undefined) {
                            el.textContent = state[key];
                        }
                    });

                    document.querySelectorAll('[data-bind-value]').forEach(function(el) {
                        const key = el.getAttribute('data-bind-value');
                        if (state[key] !== undefined && el.value !== state[key]) {
                            el.value = state[key];
                        }
                    });

                    document.querySelectorAll('[data-conditional]').forEach(function(el) {
                        const cond = el.getAttribute('data-conditional');
                        try {
                            const func = new Function(...Object.keys(state), 'return ' + cond);
                            const isVisible = func(...Object.values(state));
                            el.style.display = isVisible ? (el.getAttribute('data-display-style') || 'flex') : 'none';
                        } catch (e) {
                            // ignore evaluation issues
                        }
                    });
                }

                window.dispatchSemanticAction = function(nodeId, eventType, extraPayload) {
                    const node = findNodeById(nodeHierarchy, nodeId);
                    if (!node || !node.events || !node.events[eventType]) return;

                    const action = node.events[eventType];
                    switch (action.type) {
                        case 'state_update':
                            if (action.stateUpdates) {
                                Object.assign(state, action.stateUpdates);
                            }
                            if (action.target && extraPayload !== undefined) {
                                state[action.target] = extraPayload;
                            }
                            break;
                        case 'state_toggle':
                            if (action.target && state[action.target] !== undefined) {
                                state[action.target] = !state[action.target];
                            }
                            break;
                        case 'state_increment':
                            if (action.target) {
                                const cur = Number(state[action.target]) || 0;
                                state[action.target] = cur + (Number(action.delta) || 1);
                            }
                            break;
                    }
                    updateBindings();
                };

                updateBindings();
            })();
        </script>
        """.trimIndent()
    }
}
