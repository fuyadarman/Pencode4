package com.example.preview.universal

/**
 * PreviewIR (Intermediate Representation)
 * 
 * A unified, framework-agnostic AST for representing Mobile UI trees, 
 * reactive state, navigation graphs, and interactive events.
 * 
 * Converts both Kotlin Jetpack Compose and Flutter Dart into this common IR.
 */
data class PreviewDocument(
    val framework: FrameworkDetector.FrameworkType,
    val appTitle: String,
    val screens: List<PreviewScreen>,
    val globalState: Map<String, Any>,
    val theme: PreviewTheme = PreviewTheme()
)

data class PreviewScreen(
    val id: String,
    val name: String,
    val isInitial: Boolean = false,
    val rootNode: PreviewNode,
    val stateVariables: MutableMap<String, Any> = mutableMapOf(),
    val navigationRoutes: MutableMap<String, String> = mutableMapOf() // trigger -> targetScreenId
)

enum class PreviewNodeType {
    SCAFFOLD,
    APP_BAR,
    BOTTOM_NAV,
    FLOATING_ACTION_BUTTON,
    COLUMN,
    ROW,
    BOX,
    CARD,
    LAZY_COLUMN,
    TEXT,
    BUTTON,
    OUTLINED_BUTTON,
    TEXT_BUTTON,
    TEXT_FIELD,
    OUTLINED_TEXT_FIELD,
    IMAGE,
    ICON,
    SWITCH,
    CHECKBOX,
    SLIDER,
    PROGRESS_INDICATOR,
    SPACER,
    DIVIDER,
    SNACKBAR_HOST,
    DIALOG,
    CONTAINER,
    GRID,
    CUSTOM
}

data class PreviewNode(
    val id: String,
    val type: PreviewNodeType,
    val label: String = "",
    val props: MutableMap<String, Any> = mutableMapOf(),
    val style: PreviewNodeStyle = PreviewNodeStyle(),
    val stateBindings: MutableMap<String, String> = mutableMapOf(), // e.g. "value" -> "emailInput", "visible" -> "isLoading"
    val actions: MutableList<PreviewAction> = mutableListOf(),
    val children: MutableList<PreviewNode> = mutableListOf(),
    val conditionalExpr: String? = null // e.g. "isLoading", "!isLoggedIn"
)

data class PreviewNodeStyle(
    var width: String? = null,
    var height: String? = null,
    var padding: String? = null,
    var margin: String? = null,
    var backgroundColor: String? = null,
    var textColor: String? = null,
    var fontSize: String? = null,
    var fontWeight: String? = null,
    var borderRadius: String? = null,
    var elevation: Int = 0,
    var alignment: String? = null, // center, start, end, space-between
    var fillMaxWidth: Boolean = false,
    var fillMaxHeight: Boolean = false,
    var shape: String? = null // rounded, circle, rectangle
)

data class PreviewAction(
    val trigger: String = "onClick", // onClick, onChange, onToggle, onSubmit
    val actionType: ActionType,
    val target: String, // screenId or state key or API name
    val payload: String = ""
)

enum class ActionType {
    NAVIGATE,
    GO_BACK,
    SET_STATE,
    TOGGLE_STATE,
    INCREMENT_STATE,
    SHOW_TOAST,
    SHOW_SNACKBAR,
    MOCK_API_CALL,
    CALCULATOR_INPUT
}

data class PreviewTheme(
    val primaryColor: String = "#6750A4",
    val onPrimaryColor: String = "#FFFFFF",
    val primaryContainer: String = "#EADDFF",
    val secondaryColor: String = "#625B71",
    val backgroundColor: String = "#FEF7FF",
    val surfaceColor: String = "#FEF7FF",
    val onSurfaceColor: String = "#1D1B20",
    val isDark: Boolean = false
)
