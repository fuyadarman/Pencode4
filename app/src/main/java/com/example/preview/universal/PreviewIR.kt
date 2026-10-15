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
    LAZY_ROW,
    GRID,
    TEXT,
    BUTTON,
    ELEVATED_BUTTON,
    FILLED_TONAL_BUTTON,
    OUTLINED_BUTTON,
    TEXT_BUTTON,
    TEXT_FIELD,
    OUTLINED_TEXT_FIELD,
    IMAGE,
    ICON,
    SWITCH,
    CHECKBOX,
    RADIO_BUTTON,
    SLIDER,
    PROGRESS_INDICATOR,
    CIRCULAR_PROGRESS_INDICATOR,
    LINEAR_PROGRESS_INDICATOR,
    CHIP,
    BADGE,
    TAB_ROW,
    TAB,
    AVATAR,
    CANVAS_2D,
    CANVAS_3D,
    ANIMATED_VISIBILITY,
    SPACER,
    DIVIDER,
    SNACKBAR_HOST,
    DIALOG,
    CONTAINER,
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
    var gradientBackground: String? = null,
    var textColor: String? = null,
    var fontSize: String? = null,
    var fontWeight: String? = null,
    var fontFamily: String? = null,
    var borderRadius: String? = null,
    var elevation: Int = 0,
    var shadowElevation: Int = 0,
    var tonalElevation: Int = 0,
    var alignment: String? = null, // center, start, end, space-between, space-around, space-evenly
    var horizontalAlignment: String? = null, // start, center, end
    var verticalAlignment: String? = null, // top, center, bottom
    var fillMaxWidth: Boolean = false,
    var fillMaxHeight: Boolean = false,
    var shape: String? = null, // rounded, circle, rectangle, cut, stadium
    var clipShape: String? = null,
    var isScrollable: Boolean = false,
    var scrollDirection: String = "vertical", // vertical, horizontal, both
    var scrollPhysics: String? = null, // bouncing, clamping
    var hasAnimation: Boolean = false,
    var animationType: String? = null, // fadeIn, slideIn, scaleIn, ripple
    var letterSpacing: String? = null,
    var lineHeight: String? = null,
    var textAlign: String? = null,
    var maxLines: Int? = null,
    var borderColor: String? = null,
    var borderWidth: String? = null,
    var opacity: Float? = null,
    var scale: Float? = null,
    var rotation: Float? = null,
    var zIndex: Int? = null,
    var aspectRatio: Float? = null,
    var flexWeight: Float? = null,
    var transform3d: String? = null // perspective(800px) rotateX(15deg) rotateY(10deg)
)

data class PreviewAction(
    val trigger: String = "onClick", // onClick, onChange, onToggle, onSubmit, onLongClick, onSwipe
    val actionType: ActionType,
    val target: String, // screenId or state key or API name
    val payload: String = ""
)

enum class ActionType {
    NAVIGATE,
    GO_BACK,
    SET_STATE,
    STATE_UPDATE,
    TOGGLE_STATE,
    INCREMENT_STATE,
    DECREMENT_STATE,
    RESET_STATE,
    SHOW_TOAST,
    SHOW_SNACKBAR,
    SHOW_DIALOG,
    DISMISS_DIALOG,
    SELECT_TAB,
    MOCK_API_CALL,
    CALCULATOR_INPUT,
    FORM_SUBMIT
}

data class PreviewTheme(
    val primaryColor: String = "#6750A4",
    val onPrimaryColor: String = "#FFFFFF",
    val primaryContainer: String = "#EADDFF",
    val secondaryColor: String = "#625B71",
    val backgroundColor: String = "#FEF7FF",
    val surfaceColor: String = "#FEF7FF",
    val onSurfaceColor: String = "#1D1B20",
    val inversePrimary: String = "#D0BCFF",
    val isDark: Boolean = false
)
