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
        === MULTI-OPERATION BATCHING ===
        - Formulate logic in 'thought' and batch 2+ operations in 'tools': [{"tool":"read_file","arguments":{"path":"..."}},{"tool":"edit_file","arguments":{...}}]
        - Single tool format ONLY for unpredictable exploratory search. Otherwise ALWAYS batch 2+ operations.
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
