package com.example.agent

import com.example.api.ToolCallItem
import com.example.ui.AiActionLog

sealed class SequenceLoopResult {
    object Proceed : SequenceLoopResult()
    data class WarningInjected(val warningText: String, val logTitle: String, val logDetails: String) : SequenceLoopResult()
    data class Abort(val reason: String, val logTitle: String) : SequenceLoopResult()
}

object AgentSequenceLoopChecker {

    fun checkSequenceLoop(
        recentToolCallsHistory: MutableList<ToolCallItem>,
        call: ToolCallItem,
        tool: String,
        argsString: String?,
        recentLogs: List<AiActionLog>,
        isSameWork: (ToolCallItem, ToolCallItem) -> Boolean
    ): SequenceLoopResult {
        recentToolCallsHistory.add(call)
        // Disabled: No intrusive warnings or directives injected.
        return SequenceLoopResult.Proceed
    }
}
