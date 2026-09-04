package com.example.agent

import com.example.api.Content
import com.example.api.Part
import com.example.api.ToolArguments
import com.example.api.ToolCallItem

/**
 * AgentBatchExecutionManager
 * Enables AI models to formulate logic once for one or multiple operations at once.
 * When multiple operations are planned, they execute sequentially under that single formulating logic
 * without repeatedly triggering redundant reasoning cycles.
 */
object AgentBatchExecutionManager {

    val SYSTEM_BATCH_LOGIC_INSTRUCTION = """
        === FORMULATING LOGIC & BATCH OPERATIONS DIRECTIVE ===
        - BATCHED PLANNING SUPPORTED: You can formulate logic for ONE or MULTIPLE upcoming operations at once!
        - MULTIPLE OPERATIONS PREFERRED: When you have a clear multi-step plan (e.g., reading a file then editing, creating multiple files, or updating related code), formulate your overall logic ONCE in the 'thought' field and return all planned operations together in the 'tools' array:
          {
            "thought": "Plan: 1. Read App.js to check state, 2. Update styles in styles.css, 3. Create helper in utils.js",
            "tools": [
              { "tool": "read_file", "arguments": { "path": "App.js" } },
              { "tool": "edit_file", "arguments": { "path": "styles.css", "search": "oldStyle", "replace": "newStyle" } },
              { "tool": "create_file", "arguments": { "path": "utils.js", "content": "export function helper() {}" } }
            ]
          }
        - SINGLE OPERATION ALLOWED: If the next action strictly depends on dynamic exploration (e.g. searching or reading an unfamiliar file first), you can formulate logic for just that single operation:
          {
            "thought": "Searching for button handler implementation in codebase",
            "tool": "global_search",
            "arguments": { "query": "handleButtonClick" }
          }
        - EFFICIENCY: Formulating logic once for multiple operations is strongly preferred over single-step turns whenever feasible, as it executes significantly faster without repetitive formulating logic pauses.
    """.trimIndent()

    /**
     * Formats the log title for formulating logic based on turn and planned tool count.
     */
    fun formatFormulatingLogicTitle(turn: Int, plannedToolsCount: Int): String {
        return when {
            plannedToolsCount > 1 -> "AI formulating logic ($plannedToolsCount operations planned)"
            turn > 1 -> "AI formulating logic (Step $turn)"
            else -> "AI formulating logic"
        }
    }

    /**
     * Ensures the model's message is recorded in conversation history only once per turn,
     * even if multiple tool calls are executed in that turn.
     */
    fun ensureModelTurnRecorded(
        history: MutableList<Content>,
        stepResponseJson: String,
        isRecorded: Boolean
    ): Boolean {
        if (!isRecorded) {
            history.add(Content(role = "model", parts = listOf(Part(text = stepResponseJson))))
            return true
        }
        return true
    }

    /**
     * Normalizes tool calls list. If multiple tool calls are provided in `tools`, returns them.
     * Otherwise falls back to single `tool` if present.
     */
    fun resolveToolCalls(
        tools: List<ToolCallItem>?,
        singleTool: String?,
        singleArgs: ToolArguments?
    ): List<ToolCallItem> {
        val result = mutableListOf<ToolCallItem>()
        if (!tools.isNullOrEmpty()) {
            result.addAll(tools.filter { it.tool.isNotBlank() })
        } else if (!singleTool.isNullOrBlank()) {
            result.add(ToolCallItem(singleTool.trim(), singleArgs))
        }
        return result
    }
}
