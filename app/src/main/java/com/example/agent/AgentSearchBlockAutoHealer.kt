package com.example.agent

import android.util.Log
import com.example.data.VibeRepository
import com.example.ui.EditRecord
import java.util.concurrent.ConcurrentHashMap

/**
 * AgentSearchBlockAutoHealer
 *
 * Dedicated engine to resolve empty search block issues ('search' block cannot be empty).
 * Prevents AI models from falling into continuous empty search loops by:
 * 1. Line-by-line Diff Synthesis: Auto-detects modified sections between originalContent and replaceStr,
 *    deriving exact surgical search and replace blocks.
 * 2. Starter & Near-threshold Auto-healing: Safely handles files around the 30-line threshold (up to 65 lines like index.html).
 * 3. Repetition Circuit Breaker: If an AI model repeatedly attempts an empty search on the same file,
 *    it auto-resolves the mutation instead of repeating the rejection error endlessly.
 * 4. Actionable Concrete Prompting: If user must be prompted, provides exact copy-pasteable JSON syntax.
 */
object AgentSearchBlockAutoHealer {

    private const val TAG = "SearchBlockAutoHealer"
    private val emptySearchAttemptCount = ConcurrentHashMap<String, Int>()

    fun resetSession() {
        emptySearchAttemptCount.clear()
    }

    private fun normalizePathKey(filePath: String): String {
        return filePath.trim().trimStart('/', '.').replace('\\', '/').lowercase()
    }

    fun getEmptySearchAttempts(filePath: String): Int {
        val key = normalizePathKey(filePath)
        return emptySearchAttemptCount[key] ?: 0
    }

    fun recordEmptySearchAttempt(filePath: String): Int {
        val key = normalizePathKey(filePath)
        val count = (emptySearchAttemptCount[key] ?: 0) + 1
        emptySearchAttemptCount[key] = count
        return count
    }

    fun clearEmptySearchAttempts(filePath: String) {
        val key = normalizePathKey(filePath)
        emptySearchAttemptCount.remove(key)
    }

    /**
     * Attempts to auto-heal an edit call where searchStr is empty.
     * Returns EditExecutionResult if successfully auto-healed or loop-broken, or null if cannot be auto-healed.
     */
    suspend fun attemptAutoHeal(
        projectName: String,
        filePath: String,
        originalContent: String,
        replaceStr: String,
        tool: String,
        repository: VibeRepository
    ): AgentEditToolExecutor.EditExecutionResult? {
        val lineCount = if (originalContent.isBlank()) 0 else originalContent.lines().size
        val attempts = recordEmptySearchAttempt(filePath)

        Log.d(TAG, "Attempting auto-heal for '$filePath' ($lineCount lines), attempt #$attempts, replaceStr length: ${replaceStr.length}")

        if (replaceStr.isBlank()) {
            return null
        }

        // Strategy 1: Smart Line-by-Line Diff Synthesis
        // If replaceStr contains the updated file or a modified block, extract common prefix and suffix
        val diffResult = synthesizeSurgicalDiff(originalContent, replaceStr)
        if (diffResult != null) {
            val (synthesizedSearch, synthesizedReplace) = diffResult
            if (synthesizedSearch.isNotEmpty() && originalContent.contains(synthesizedSearch)) {
                val occurrences = originalContent.split(synthesizedSearch).size - 1
                if (occurrences == 1) {
                    val updated = originalContent.replace(synthesizedSearch, synthesizedReplace)
                    return try {
                        repository.saveFile(projectName, filePath, updated)
                        clearEmptySearchAttempts(filePath)
                        val startIndex = originalContent.indexOf(synthesizedSearch)
                        val linesBefore = originalContent.substring(0, startIndex).count { it == '\n' } + 1
                        val linesInSearch = synthesizedSearch.count { it == '\n' }
                        val endLine = linesBefore + linesInSearch
                        val range = if (linesBefore == endLine) "line $linesBefore" else "lines $linesBefore-$endLine"
                        val toolType = if (tool.contains("patch")) "patch" else "edit"
                        Log.i(TAG, "Successfully auto-synthesized surgical edit for '$filePath' at $range")
                        AgentEditToolExecutor.EditExecutionResult.Success(
                            message = "Successfully updated '$filePath' ($range). Auto-resolved empty search block through intelligent diff synthesis. Changes are saved. DO NOT re-read this file. If all requested changes are done, call 'complete'.",
                            filePath = filePath,
                            rangeDesc = range,
                            editRecord = EditRecord(tool = toolType, path = filePath, lines = range)
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing diff-synthesized file '$filePath': ${e.message}")
                        null
                    }
                }
            }
        }

        // Strategy 2: Near-threshold / Starter file auto-healing (e.g. index.html has 32 lines, starter configs)
        // Up to 65 lines: small starter templates can be safely saved directly if replaceStr is a full valid document
        if (lineCount <= 65) {
            return try {
                repository.saveFile(projectName, filePath, replaceStr)
                clearEmptySearchAttempts(filePath)
                val newLines = replaceStr.lines().size
                val range = if (newLines <= 1) "all" else "lines 1-$newLines"
                Log.i(TAG, "Auto-healed starter file '$filePath' ($lineCount lines <= 65)")
                AgentEditToolExecutor.EditExecutionResult.Success(
                    message = "Successfully updated '$filePath' ($lineCount lines). Content is saved. DO NOT re-read this file. If all requested changes are done, call 'complete'.",
                    filePath = filePath,
                    rangeDesc = range,
                    editRecord = EditRecord(tool = "edit", path = filePath, lines = range)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error saving near-threshold file '$filePath': ${e.message}")
                AgentEditToolExecutor.EditExecutionResult.Failure("Error writing updated file: ${e.localizedMessage}")
            }
        }

        // Strategy 3: Circuit Breaker for Repetitive Empty Search Loops
        // If the AI has tried empty search on this file 2 or more times, force-apply replaceStr
        // to permanently break the infinite failure loop that annoys the user!
        if (attempts >= 2) {
            return try {
                repository.saveFile(projectName, filePath, replaceStr)
                clearEmptySearchAttempts(filePath)
                val newLines = replaceStr.lines().size
                val range = if (newLines <= 1) "all" else "lines 1-$newLines"
                Log.w(TAG, "Loop breaker triggered on attempt #$attempts for '$filePath'. Applied replaceStr directly.")
                AgentEditToolExecutor.EditExecutionResult.Success(
                    message = "Successfully updated '$filePath'. Circuit-breaker resolved repeating empty search block by applying new content directly. Changes are saved. DO NOT re-read this file. If all requested changes are done, call 'complete'.",
                    filePath = filePath,
                    rangeDesc = range,
                    editRecord = EditRecord(tool = "edit", path = filePath, lines = range)
                )
            } catch (e: Exception) {
                AgentEditToolExecutor.EditExecutionResult.Failure("Error writing updated file: ${e.localizedMessage}")
            }
        }

        return null
    }

    /**
     * Computes the common line prefix and suffix between original and replacement,
     * isolating the exact surgical change.
     */
    private fun synthesizeSurgicalDiff(original: String, replacement: String): Pair<String, String>? {
        if (original == replacement) return null

        val origLines = original.lines()
        val replLines = replacement.lines()

        var start = 0
        while (start < origLines.size && start < replLines.size && origLines[start] == replLines[start]) {
            start++
        }

        var origEnd = origLines.size - 1
        var replEnd = replLines.size - 1
        while (origEnd >= start && replEnd >= start && origLines[origEnd] == replLines[replEnd]) {
            origEnd--
            replEnd--
        }

        val searchLines = origLines.subList(start, origEnd + 1)
        val replaceLines = replLines.subList(start, replEnd + 1)

        val searchBlock = searchLines.joinToString("\n")
        val replaceBlock = replaceLines.joinToString("\n")

        if (searchBlock.isNotEmpty() && original.contains(searchBlock)) {
            return Pair(searchBlock, replaceBlock)
        }

        return null
    }

    /**
     * Builds a clear, actionable guidance message for the model when an empty search block cannot be healed.
     */
    fun buildActionableEmptySearchMessage(filePath: String, lineCount: Int, snippet: String): String {
        return "Error: 'search' block cannot be empty. You must specify the exact, unique block of code to search and replace.\n" +
                "File '$filePath' has $lineCount lines. Current content snippet:\n```\n$snippet\n```\n" +
                "CRITICAL INSTRUCTION: Do NOT pass an empty 'search' argument! Choose a 3-5 line section from above and call:\n" +
                "{\"tool\": \"edit_file\", \"arguments\": {\"path\": \"$filePath\", \"search\": \"<exact code from snippet above>\", \"replace\": \"<your new code>\"}}"
    }
}
