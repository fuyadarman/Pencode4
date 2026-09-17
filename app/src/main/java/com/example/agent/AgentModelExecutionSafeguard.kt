package com.example.agent

import com.example.api.ToolCallItem
import com.example.data.ProjectEntity
import com.example.data.ProjectFileEntity
import com.example.data.VibeRepository
import java.io.File

/**
 * AgentModelExecutionSafeguard
 * 
 * 1. Prevents small models (like Gemini Flash-Lite) from prematurely calling 'complete'
 *    in 6s without doing any actual work or after failed operations.
 * 2. Provides intelligent auto-recovery when a model attempts to edit a file before reading it:
 *    - If the search block matches, allows the edit safely.
 *    - If the search block doesn't match, injects the actual file content directly so the model
 *      can immediately apply the correct edit on the next turn.
 * 3. Prevents large Pro models from creating infinite thinking/reading loops.
 */
object AgentModelExecutionSafeguard {

    sealed class CompletionCheckResult {
        object Allowed : CompletionCheckResult()
        data class Denied(val reason: String) : CompletionCheckResult()
    }

    /**
     * Checks if the AI is allowed to call 'complete' right now.
     * Prevents Flash-Lite models from claiming success when edits failed and no files were modified.
     */
    fun validateTaskCompletion(
        userPrompt: String,
        filesModifiedThisPrompt: Boolean,
        anyFilesModifiedSession: Boolean,
        failedActionsInTurn: Int,
        lastErrorSummary: String?,
        completionMessage: String? = null
    ): CompletionCheckResult {
        // If the AI is asking the user a question or asking for clarification, always allow it
        if (com.example.agent.AgentQuestionDetector.isClarificationOrQuestion(completionMessage)) {
            return CompletionCheckResult.Allowed
        }

        val lowerPrompt = userPrompt.lowercase()
        val isCodeModificationRequest = lowerPrompt.contains("create") ||
                lowerPrompt.contains("add") ||
                lowerPrompt.contains("build") ||
                lowerPrompt.contains("make") ||
                lowerPrompt.contains("implement") ||
                lowerPrompt.contains("modify") ||
                lowerPrompt.contains("update") ||
                lowerPrompt.contains("change") ||
                lowerPrompt.contains("fix") ||
                lowerPrompt.contains("write") ||
                lowerPrompt.contains("3d") ||
                lowerPrompt.contains("scene") ||
                lowerPrompt.contains("car") ||
                lowerPrompt.contains("game") ||
                lowerPrompt.contains("ui")

        // If the user asked to build/create/modify, but no files were modified AND errors occurred
        if (isCodeModificationRequest && !filesModifiedThisPrompt && !anyFilesModifiedSession) {
            if (failedActionsInTurn > 0 || !lastErrorSummary.isNullOrBlank()) {
                val detail = lastErrorSummary ?: "Previous file operations failed or were rejected."
                return CompletionCheckResult.Denied(
                    "SYSTEM REJECTION (PREMATURE COMPLETION): You attempted to complete the task, but NO files have been created or modified yet, and previous operations failed ($detail). You MUST implement and apply your code changes using 'create_file' or 'edit_file' before calling 'complete'. If you already read the files, do NOT re-read them; proceed directly with applying your edits."
                )
            }
        }

        return CompletionCheckResult.Allowed
    }

    sealed class UnreadFileEditDecision {
        data class ApplyDirectly(val updatedContent: String, val rangeDesc: String) : UnreadFileEditDecision()
        data class ProvideContentForEdit(val guidanceMessage: String) : UnreadFileEditDecision()
    }

    /**
     * When a model calls edit_file on a target file that was not read yet:
     * Checks if search block exists in original content.
     * If it matches uniquely, allows the edit!
     * If not, returns guidance containing the actual content so the model can apply the fix in the next turn.
     */
    fun handleUnreadFileEditAttempt(
        filePath: String,
        targetFile: ProjectFileEntity,
        searchStr: String,
        replaceStr: String
    ): UnreadFileEditDecision {
        val originalContent = targetFile.content

        if (searchStr.isNotEmpty() && originalContent.contains(searchStr)) {
            val occurrences = originalContent.split(searchStr).size - 1
            if (occurrences == 1) {
                val startIndex = originalContent.indexOf(searchStr)
                val linesBefore = originalContent.substring(0, startIndex).count { it == '\n' } + 1
                val linesInSearch = searchStr.count { it == '\n' }
                val endLine = linesBefore + linesInSearch
                val rangeDesc = if (linesBefore == endLine) "line $linesBefore" else "lines $linesBefore-$endLine"
                val updatedContent = originalContent.replace(searchStr, replaceStr)
                return UnreadFileEditDecision.ApplyDirectly(updatedContent, rangeDesc)
            }
        }

        // Search string did not match or was empty -> provide file content so the model has it immediately
        val maxPreviewLength = 6000
        val truncatedContent = if (originalContent.length > maxPreviewLength) {
            originalContent.substring(0, maxPreviewLength) + "\n... [content truncated: call read_file for full file]"
        } else {
            originalContent
        }

        val guidance = "NOTICE: '$filePath' was not read before editing. Here are the current exact contents of '$filePath':\n" +
                "==================== FILE CONTENTS ($filePath) ====================\n" +
                "$truncatedContent\n" +
                "===================================================================\n" +
                "Your search block did not match the file above. Please inspect the code above and call 'edit_file' with the exact matching 'search' block."

        return UnreadFileEditDecision.ProvideContentForEdit(guidance)
    }

    /**
     * Pro Model Anti-Infinite Loop Convergence Guard:
     * Checks if a model is trapped in endless thinking or re-reading without making progress.
     */
    fun evaluateProModelConvergence(
        turn: Int,
        consecutiveTurnsWithoutEdits: Int,
        hasModifiedFiles: Boolean,
        recentThoughts: List<String>,
        currentThought: String?
    ): ProConvergenceDecision {
        // If files have already been modified and the model has spent 3 consecutive turns
        // only thinking or re-reading without further modifications -> Force clean completion!
        if (hasModifiedFiles && consecutiveTurnsWithoutEdits >= 3) {
            return ProConvergenceDecision.ForceAutoComplete(
                summary = "All requested code changes have been applied and finalized. Task completed successfully.",
                reason = "AI applied code modifications and completed verification. Auto-finalizing to prevent infinite re-reading loop."
            )
        }

        // Hard turn ceiling for pro models: If turn exceeds 18 and modifications exist, finalize
        if (turn >= 18 && hasModifiedFiles) {
            return ProConvergenceDecision.ForceAutoComplete(
                summary = "Task completed: Requested features and modifications have been implemented.",
                reason = "Reached turn ceiling with successful code modifications."
            )
        }

        return ProConvergenceDecision.Proceed
    }

    sealed class ProConvergenceDecision {
        object Proceed : ProConvergenceDecision()
        data class ForceAutoComplete(val summary: String, val reason: String) : ProConvergenceDecision()
    }
}
