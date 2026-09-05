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
        === STRICT MANDATE: MULTI-OPERATION BATCHING & FORMULATING LOGIC ===
        [CRITICAL DIRECTIVE ON FORMULATING LOGIC & TOOL BATCHING]
        1. MANDATORY MULTI-OPERATION BATCHING (ALWAYS 2+ OPERATIONS):
           You MUST formulate logic for MULTIPLE upcoming operations at once (2, 3, or more operations in a single turn).
           Never formulate logic for just 1 minor operation if you can plan subsequent steps together.
           Provide your comprehensive reasoning ONCE in the 'thought' field and return all planned operations together in the 'tools' array:
           {
             "thought": "Master Plan: 1. Read existing config in App.js, 2. Add 3D scene container in styles.css, 3. Create 3D car logic in CarScene.js",
             "tools": [
               { "tool": "read_file", "arguments": { "path": "App.js" } },
               { "tool": "edit_file", "arguments": { "path": "styles.css", "search": "oldStyle", "replace": "newStyle" } },
               { "tool": "create_file", "arguments": { "path": "CarScene.js", "content": "..." } }
             ]
           }

        2. STRICT EXCEPTION (ONLY FOR HIGHLY COMPLEX / UNPREDICTABLE TASKS):
           You may ONLY formulate logic for a SINGLE operation if the task is genuinely complex, unpredictable, or an exploratory investigation where subsequent edits cannot possibly be known without inspecting the tool's result first (e.g. an exploratory global_search or investigating an obscure crash log):
           {
             "thought": "Complex investigation: searching for obscure symbol definition before determining architecture",
             "tool": "global_search",
             "arguments": { "query": "obscureMethodName" }
           }
           Otherwise, for standard coding, modifications, and creation tasks, ALWAYS batch multiple (2+) operations under one formulating logic turn!
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
