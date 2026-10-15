package com.example.preview.universal

/**
 * Semantic UI IR
 *
 * Full intermediate representation for mobile UIs preserving:
 * - Component type
 * - Layout (type, alignment, arrangement, scroll)
 * - Size (width, height, min/max, fill constraints)
 * - Spacing (padding, margin)
 * - Typography (font size, weight, color, alignment, line-height)
 * - Visual/Shape (background, border, corner-radius, elevation, shadows)
 * - State and Reactive bindings
 * - Events & Actions (state_update, toggle, increment, navigate, compute)
 */

data class SemanticDocument(
    val title: String,
    val framework: String,
    val initialState: MutableMap<String, Any> = mutableMapOf(),
    val rootNode: SemanticNode
)

data class SemanticNode(
    val id: String,
    val component: String, // Box, Column, Row, Text, Button, Card, TextField, Checkbox, Switch, Image, Icon, Scaffold, Canvas, Spacer
    val text: String = "",
    val layout: LayoutProps = LayoutProps(),
    val size: SizeProps = SizeProps(),
    val spacing: SpacingProps = SpacingProps(),
    val typography: TypographyProps = TypographyProps(),
    val visual: VisualProps = VisualProps(),
    val events: MutableMap<String, EventAction> = mutableMapOf(),
    val stateBindings: MutableMap<String, String> = mutableMapOf(), // e.g. "text" -> "counter", "visible" -> "!isLoading", "value" -> "username"
    val conditional: String? = null, // e.g. "gameState === 'START'", "isLoading === true"
    val children: MutableList<SemanticNode> = mutableListOf(),
    val props: MutableMap<String, Any> = mutableMapOf()
)

data class LayoutProps(
    val type: String = "column", // column, row, box, grid, scaffold
    val horizontalAlignment: String = "stretch", // start, center, end, stretch, space-between
    val verticalAlignment: String = "start", // top, center, bottom, space-between
    val scrollable: Boolean = false,
    val scrollDirection: String = "vertical", // vertical, horizontal
    val gap: String? = null
)

data class SizeProps(
    val width: String? = null,
    val height: String? = null,
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    val aspectRatio: Float? = null
)

data class SpacingProps(
    val paddingTop: String? = null,
    val paddingBottom: String? = null,
    val paddingStart: String? = null,
    val paddingEnd: String? = null,
    val marginTop: String? = null,
    val marginBottom: String? = null,
    val marginStart: String? = null,
    val marginEnd: String? = null
)

data class TypographyProps(
    val fontSize: String? = null,
    val fontWeight: String? = null,
    val textColor: String? = null,
    val textAlign: String = "left",
    val letterSpacing: String? = null,
    val lineHeight: String? = null
)

data class VisualProps(
    val backgroundColor: String? = null,
    val borderColor: String? = null,
    val borderWidth: String? = null,
    val borderRadius: String? = null,
    val elevation: String? = null,
    val opacity: Float = 1.0f,
    val shadow: String? = null
)

data class EventAction(
    val type: String, // state_update, state_toggle, state_increment, navigate, custom
    val stateUpdates: MutableMap<String, Any> = mutableMapOf(), // e.g. {"isLoading": true, "gameState": "RUNNING"}
    val target: String? = null, // variable or destination name
    val delta: Number = 1,
    val codeSnippet: String? = null
)
