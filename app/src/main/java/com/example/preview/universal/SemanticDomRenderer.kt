package com.example.preview.universal

import org.json.JSONArray
import org.json.JSONObject

/**
 * SemanticDomRenderer
 *
 * Transforms Semantic UI IR into responsive HTML/CSS DOM with full styling,
 * layout preservation, and Action Runtime binding.
 */
object SemanticDomRenderer {

    fun render(document: SemanticDocument): String {
        val rootHtml = renderNode(document.rootNode)
        val nodesJson = serializeNodes(document.rootNode).toString()
        val runtimeScript = ActionRuntimeEngine.generateRuntimeScript(document.initialState, nodesJson)

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <title>${document.title}</title>
            <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=Fredoka+One&display=swap" rel="stylesheet">
            <style>
                * {
                    box-sizing: border-box;
                    margin: 0;
                    padding: 0;
                    -webkit-tap-highlight-color: transparent;
                }
                html, body {
                    width: 100%;
                    height: 100%;
                    background: ${document.rootNode.visual.backgroundColor ?: "#FFFFFF"};
                    font-family: 'Plus Jakarta Sans', sans-serif;
                    overflow: hidden;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                .mobile-viewport {
                    width: 100%;
                    height: 100%;
                    background: ${document.rootNode.visual.backgroundColor ?: "#FFFFFF"};
                    display: flex;
                    flex-direction: column;
                    overflow-y: auto;
                    overflow-x: hidden;
                    position: relative;
                }
                .semantic-box {
                    display: flex;
                    position: relative;
                }
                .semantic-column {
                    display: flex;
                    flex-direction: column;
                }
                .semantic-row {
                    display: flex;
                    flex-direction: row;
                    align-items: center;
                }
                .semantic-card {
                    background: #FFFFFF;
                    border: 1px solid rgba(0,0,0,0.08);
                    box-shadow: 0 4px 12px rgba(0,0,0,0.05);
                }
                .semantic-text {
                    color: #1C1B1F;
                    font-size: 15px;
                    line-height: 1.4;
                    word-break: break-word;
                }
                .semantic-button {
                    background: #6750A4;
                    color: #FFFFFF;
                    border: none;
                    cursor: pointer;
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    font-family: inherit;
                    transition: transform 0.1s ease, filter 0.1s ease;
                }
                .semantic-button:active {
                    transform: scale(0.96);
                    filter: brightness(0.9);
                }
                .semantic-text-field {
                    width: 100%;
                    padding: 12px 16px;
                    border: 1.5px solid #79747E;
                    background: transparent;
                    font-size: 15px;
                    font-family: inherit;
                    outline: none;
                }
                .semantic-text-field:focus {
                    border-color: #6750A4;
                }
            </style>
        </head>
        <body>
            <div class="mobile-viewport">
                $rootHtml
            </div>
            $runtimeScript
        </body>
        </html>
        """.trimIndent()
    }

    private fun renderNode(node: SemanticNode): String {
        val styleAttr = buildStyleAttribute(node)
        val dataAttrs = buildDataAttributes(node)
        val eventHandlers = buildEventHandlers(node)

        val childrenHtml = node.children.joinToString("\n") { renderNode(it) }

        return when (node.component.lowercase()) {
            "scaffold" -> {
                "<div id=\"${node.id}\" class=\"semantic-column\" $styleAttr $dataAttrs $eventHandlers>$childrenHtml</div>"
            }
            "box" -> {
                val display = if (node.layout.horizontalAlignment == "center") "flex" else "block"
                "<div id=\"${node.id}\" class=\"semantic-box\" data-display-style=\"$display\" $styleAttr $dataAttrs $eventHandlers>$childrenHtml</div>"
            }
            "column" -> {
                "<div id=\"${node.id}\" class=\"semantic-column\" data-display-style=\"flex\" $styleAttr $dataAttrs $eventHandlers>$childrenHtml</div>"
            }
            "row" -> {
                "<div id=\"${node.id}\" class=\"semantic-row\" data-display-style=\"flex\" $styleAttr $dataAttrs $eventHandlers>$childrenHtml</div>"
            }
            "card" -> {
                "<div id=\"${node.id}\" class=\"semantic-card\" data-display-style=\"flex\" $styleAttr $dataAttrs $eventHandlers>$childrenHtml</div>"
            }
            "text" -> {
                val bindAttr = node.stateBindings["text"]?.let { "data-bind-text=\"$it\"" } ?: ""
                "<div id=\"${node.id}\" class=\"semantic-text\" $bindAttr $styleAttr $dataAttrs>$childrenHtml${node.text}</div>"
            }
            "button" -> {
                "<button id=\"${node.id}\" class=\"semantic-button\" $styleAttr $dataAttrs $eventHandlers>${node.text}</button>"
            }
            "textfield" -> {
                val bindVal = node.stateBindings["value"]?.let { "data-bind-value=\"$it\" oninput=\"window.dispatchSemanticAction('${node.id}', 'input', this.value)\"" } ?: ""
                "<input type=\"text\" id=\"${node.id}\" class=\"semantic-text-field\" placeholder=\"${node.text}\" $bindVal $styleAttr $dataAttrs />"
            }
            else -> {
                "<div id=\"${node.id}\" $styleAttr $dataAttrs $eventHandlers>$childrenHtml</div>"
            }
        }
    }

    private fun buildStyleAttribute(node: SemanticNode): String {
        val styles = mutableListOf<String>()

        // Layout
        if (node.layout.horizontalAlignment == "center") {
            styles.add("align-items: center")
            styles.add("justify-content: center")
        }
        if (node.layout.gap != null) {
            styles.add("gap: ${node.layout.gap}")
        }
        if (node.layout.scrollable) {
            styles.add("overflow-y: auto")
        }

        // Size
        if (node.size.fillMaxWidth) styles.add("width: 100%")
        if (node.size.fillMaxHeight) styles.add("height: 100%")
        if (node.size.width != null) styles.add("width: ${node.size.width}")
        if (node.size.height != null) styles.add("height: ${node.size.height}")

        // Spacing
        node.spacing.paddingTop?.let { styles.add("padding-top: $it") }
        node.spacing.paddingBottom?.let { styles.add("padding-bottom: $it") }
        node.spacing.paddingStart?.let { styles.add("padding-left: $it") }
        node.spacing.paddingEnd?.let { styles.add("padding-right: $it") }
        node.spacing.marginTop?.let { styles.add("margin-top: $it") }
        node.spacing.marginBottom?.let { styles.add("margin-bottom: $it") }

        // Typography
        node.typography.fontSize?.let { styles.add("font-size: $it") }
        node.typography.fontWeight?.let { styles.add("font-weight: $it") }
        node.typography.textColor?.let { styles.add("color: $it") }
        if (node.typography.textAlign != "left") styles.add("text-align: ${node.typography.textAlign}")

        // Visual
        node.visual.backgroundColor?.let { styles.add("background-color: $it") }
        node.visual.borderRadius?.let { styles.add("border-radius: $it") }
        node.visual.borderWidth?.let { styles.add("border: $it solid ${node.visual.borderColor ?: "#E0E0E0"}") }

        return if (styles.isNotEmpty()) "style=\"${styles.joinToString("; ")}\"" else ""
    }

    private fun buildDataAttributes(node: SemanticNode): String {
        val attrs = mutableListOf<String>()
        if (node.conditional != null) {
            attrs.add("data-conditional=\"${node.conditional.replace("\"", "&quot;")}\"")
        }
        return attrs.joinToString(" ")
    }

    private fun buildEventHandlers(node: SemanticNode): String {
        val handlers = mutableListOf<String>()
        if (node.events.containsKey("click")) {
            handlers.add("onclick=\"window.dispatchSemanticAction('${node.id}', 'click')\"")
        }
        return handlers.joinToString(" ")
    }

    private fun serializeNodes(node: SemanticNode): JSONObject {
        val obj = JSONObject()
        obj.put("id", node.id)
        obj.put("component", node.component)

        val eventsObj = JSONObject()
        node.events.forEach { (event, action) ->
            val actionObj = JSONObject()
            actionObj.put("type", action.type)
            action.target?.let { actionObj.put("target", it) }
            action.delta.let { actionObj.put("delta", it) }
            val updatesObj = JSONObject()
            action.stateUpdates.forEach { (k, v) -> updatesObj.put(k, v) }
            actionObj.put("stateUpdates", updatesObj)
            eventsObj.put(event, actionObj)
        }
        obj.put("events", eventsObj)

        val childrenArr = JSONArray()
        node.children.forEach { childrenArr.put(serializeNodes(it)) }
        obj.put("children", childrenArr)

        return obj
    }
}
