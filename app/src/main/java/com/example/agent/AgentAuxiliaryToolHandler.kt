package com.example.agent

import com.example.api.ToolArguments
import com.example.api.ToolCallResponse
import com.example.data.ProjectEntity
import com.example.data.VibeRepository
import com.example.ui.AiActionLog

/**
 * AgentAuxiliaryToolHandler
 * Handles auxiliary agent tools (commands, searching, skills, block copying)
 * in an isolated handler to keep VibeViewModel method bytecode within JVM limits.
 */
object AgentAuxiliaryToolHandler {

    fun isAuxiliaryTool(tool: String): Boolean {
        return tool in listOf(
            "run_command", "global_search", "grep", "find_in_files", "find",
            "load_skill", "copy_code"
        )
    }

    suspend fun handleAuxiliaryTool(
        tool: String,
        args: ToolArguments?,
        project: ProjectEntity,
        repository: VibeRepository,
        stepResponse: ToolCallResponse?,
        createAiLog: (String, String, String) -> AiActionLog,
        addLog: (AiActionLog) -> Unit,
        updateLog: (String, String, String) -> Unit,
        appendToTerminal: (String, String) -> Unit,
        refreshFiles: suspend () -> Unit,
        normalizePath: (String) -> String,
        activeSkills: List<com.example.ui.AgentSkill>
    ): String {
        return when (tool) {
            "copy_code" -> {
                val sourcePath = normalizePath(args?.path ?: "")
                val destPath = normalizePath(args?.destinationPath ?: "")
                val copyCodeLog = AiActionLog(
                    title = "Copied code block",
                    status = "thinking",
                    details = "Copying from $sourcePath to $destPath"
                )
                addLog(copyCodeLog)

                val result = AgentCodeBlockOperations.handleCopyCode(
                    args = args,
                    project = project,
                    repository = repository,
                    normalizePath = normalizePath
                )
                if (result.startsWith("Successfully")) {
                    refreshFiles()
                }
                updateLog(copyCodeLog.id, if (result.startsWith("Successfully")) "success" else "failed", result)
                result
            }
            "run_command" -> {
                val shellCmd = args?.command ?: ""
                val intent = HybridActionIntentTracker.resolveIntent(
                    tool = "run_command",
                    args = mapOf("command" to shellCmd),
                    thought = stepResponse?.thought ?: ""
                )
                val shellLog = createAiLog(
                    intent.title,
                    "thinking",
                    if (shellCmd.isNotEmpty()) "> $shellCmd" else ""
                )
                addLog(shellLog)

                val result = repository.executeCommand(project.name, shellCmd)
                appendToTerminal(shellCmd, result)

                val finalDetails = if (shellCmd.isNotEmpty()) {
                    "> $shellCmd\n\n" + if (result.isEmpty()) "(no output)" else result
                } else {
                    if (result.isEmpty()) "(no output)" else result
                }
                updateLog(shellLog.id, "success", finalDetails)
                result
            }
            "global_search", "grep", "find_in_files", "find" -> {
                val query = args?.query ?: args?.search ?: args?.pattern ?: args?.message ?: ""
                val toolName = stepResponse?.tool ?: "global_search"
                val intent = HybridActionIntentTracker.resolveIntent(
                    tool = toolName,
                    args = mapOf("query" to query),
                    thought = stepResponse?.thought ?: ""
                )
                val searchLog = AiActionLog(
                    title = intent.title,
                    status = "thinking",
                    details = "> grep -r '$query' ."
                )
                addLog(searchLog)

                val files = repository.getFilesForProject(project.name)
                val matches = mutableListOf<String>()
                files.forEach { file ->
                    val lines = file.content.lines()
                    lines.forEachIndexed { idx, line ->
                        if (line.contains(query, ignoreCase = true)) {
                            matches.add("${file.path}:${idx + 1}: $line")
                        }
                    }
                }

                val result = if (matches.isEmpty()) {
                    "No occurrences of '$query' found."
                } else {
                    "Matches found for '$query':\n" + matches.joinToString("\n")
                }
                appendToTerminal("grep -rn \"$query\" .", result)
                updateLog(searchLog.id, "success", "> grep -r '$query' .\n\n$result")
                result
            }
            "load_skill" -> {
                val skillQuery = args?.path ?: args?.query ?: args?.message ?: args?.content ?: ""
                val enabledSkills = activeSkills.filter { it.isInstalled && it.isEnabled }
                val matchedSkill = enabledSkills.find {
                    it.id.equals(skillQuery, ignoreCase = true) ||
                            it.name.contains(skillQuery, ignoreCase = true) ||
                            skillQuery.contains(it.id, ignoreCase = true)
                }

                if (matchedSkill != null) {
                    val skillLog = createAiLog(
                        "Loaded Skill: ${matchedSkill.name}",
                        "success",
                        "Agent analyzed prompt and loaded skill '${matchedSkill.name}' by ${matchedSkill.author}"
                    )
                    addLog(skillLog)
                    "Loaded Skill: ${matchedSkill.name} (${matchedSkill.author})\nDescription: ${matchedSkill.description}\nInstructions:\n${matchedSkill.skillPrompt}"
                } else {
                    val skillLog = createAiLog(
                        "Load Skill Failed",
                        "failed",
                        "Skill '$skillQuery' not active or not installed."
                    )
                    addLog(skillLog)
                    val availableList = enabledSkills.joinToString(", ") { "${it.id} (${it.name})" }
                    "Skill '$skillQuery' not found or not active. Active installed skills: $availableList"
                }
            }
            else -> "Unsupported auxiliary tool: $tool"
        }
    }
}
