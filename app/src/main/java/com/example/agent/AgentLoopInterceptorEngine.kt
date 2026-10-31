package com.example.agent

import com.example.api.Content
import com.example.api.Part
import com.example.api.ToolArguments
import com.example.ui.AiActionLog

/**
 * AgentLoopInterceptorEngine
 * Consolidates loop detection logic into an isolated engine to avoid JVM bytecode 64KB method limits.
 */
object AgentLoopInterceptorEngine {

    sealed class LoopAction {
        object Continue : LoopAction()
        object SkipTurn : LoopAction()
        data class Abort(val reason: String, val log: AiActionLog) : LoopAction()
        data class BlockTool(val directResult: String, val log: AiActionLog) : LoopAction()
    }

    fun inspectAction(
        tool: String,
        args: ToolArguments?,
        turn: Int,
        hasModifiedFiles: Boolean,
        createAiLog: (String, String, String) -> AiActionLog,
        addHistory: (Content) -> Unit,
        addLog: (AiActionLog) -> Unit
    ): LoopAction {
        when (val gateVerdict = IntelligentRetryController.evaluatePreExecution(tool, args)) {
            is IntelligentRetryController.GateVerdict.Block -> {
                val blockLog = createAiLog(
                    "Action Blocked by Harness",
                    "failed",
                    gateVerdict.reason
                )
                return LoopAction.BlockTool(gateVerdict.simulatedToolOutput, blockLog)
            }
            is IntelligentRetryController.GateVerdict.WarnAndAnalyze -> {
                addHistory(Content(role = "user", parts = listOf(Part(text = "System Notice: ${gateVerdict.message}"))))
            }
            IntelligentRetryController.GateVerdict.Allow -> { /* proceed */ }
        }

        val repCheck = OpenCodeLoopGuardEngine.checkAndRecordRepetition(
            tool = tool,
            args = args,
            turn = turn,
            hasModifiedFiles = hasModifiedFiles
        )

        when (repCheck) {
            is OpenCodeLoopGuardEngine.RepetitionResult.AbortRepetition -> {
                val abortLog = createAiLog(
                    "Repetition Loop Intercepted",
                    "success",
                    repCheck.summary
                )
                return LoopAction.Abort(repCheck.summary, abortLog)
            }
            is OpenCodeLoopGuardEngine.RepetitionResult.WarnAndNudge -> {
                if (!repCheck.silentInUi) {
                    val warnLog = createAiLog(
                        "Repetition Warning",
                        "failed",
                        repCheck.message
                    )
                    addLog(warnLog)
                }
                addHistory(Content(role = "user", parts = listOf(Part(text = "System Notice: ${repCheck.message}"))))
                return LoopAction.SkipTurn
            }
            OpenCodeLoopGuardEngine.RepetitionResult.Proceed -> { /* continue to guard check */ }
        }

        val targetSummary = args?.path ?: args?.targetFile ?: args?.selector ?: args?.query ?: args?.command ?: ""
        val snippetSummary = args?.targetContent ?: args?.targetContentPascal ?: args?.old_string ?: args?.old_str ?: args?.startLine?.toString() ?: ""
        when (val guardVerdict = AgentRepetitionGuardEngine.evaluateAction(tool, targetSummary, snippetSummary)) {
            is AgentRepetitionGuardEngine.LoopVerdict.Halt -> {
                val loopWarnLog = createAiLog(
                    "Loop Alert",
                    "failed",
                    guardVerdict.reason
                )
                addLog(loopWarnLog)
                addHistory(Content(role = "user", parts = listOf(Part(text = "System Alert: ${guardVerdict.reason}"))))
            }
            is AgentRepetitionGuardEngine.LoopVerdict.Warn -> {
                val loopWarnLog = createAiLog(
                    "Loop Warning",
                    "failed",
                    guardVerdict.message
                )
                addLog(loopWarnLog)
                addHistory(Content(role = "user", parts = listOf(Part(text = "System Notice: ${guardVerdict.message}"))))
            }
            AgentRepetitionGuardEngine.LoopVerdict.Proceed -> {}
        }

        return LoopAction.Continue
    }
}
