package com.example.agent

/**
 * HybridActionIntentTracker
 *
 * Translates low-level agent tool invocations, shell grep queries, and file inspections
 * into natural, human-readable action summaries (e.g. "Locate ChatBar component",
 * "Find functions in WorkspaceScreen.kt", "Diagnose preview runtime error").
 */
object HybridActionIntentTracker {

    data class ActionIntent(
        val title: String,
        val target: String,
        val humanSummary: String
    )

    /**
     * Resolves human-readable action intent from tool call and arguments.
     */
    fun resolveIntent(
        tool: String,
        args: Map<String, Any?>?,
        thought: String = ""
    ): ActionIntent {
        val query = (args?.get("query") as? String) ?: ""
        val path = (args?.get("path") as? String) ?: (args?.get("sourcePath") as? String) ?: ""
        val fileName = if (path.isNotBlank()) path.substringAfterLast("/") else ""
        val command = (args?.get("command") as? String) ?: ""

        return when (tool) {
            "global_search", "grep" -> {
                val cleanQuery = query.replace("\"", "").replace("'", "").trim()
                val title = when {
                    cleanQuery.endsWith("Component") || cleanQuery.endsWith("Screen") || cleanQuery.endsWith("View") -> "Locate $cleanQuery"
                    cleanQuery.contains("fun ") || cleanQuery.contains("val ") || cleanQuery.contains("class ") -> "Search declaration: $cleanQuery"
                    cleanQuery.isNotBlank() -> "Locate $cleanQuery"
                    else -> "Search codebase"
                }
                ActionIntent(
                    title = title,
                    target = if (cleanQuery.isNotBlank()) "query: '$cleanQuery'" else "All files",
                    humanSummary = "Scanning project files for '$cleanQuery'"
                )
            }
            "run_command" -> {
                val title = when {
                    command.contains("grep") -> {
                        val pattern = command.substringAfter("grep ").substringBefore(" -").trim()
                        if (pattern.isNotBlank()) "Locate $pattern" else "Grep codebase"
                    }
                    command.contains("git") -> "Git repository status"
                    command.contains("gradle") || command.contains("./gradlew") -> "Execute Gradle task"
                    else -> "Execute system command"
                }
                ActionIntent(
                    title = title,
                    target = command.take(60),
                    humanSummary = "> $command"
                )
            }
            "read_file" -> {
                val title = if (thought.contains("function", ignoreCase = true) || thought.contains("find", ignoreCase = true)) {
                    "Find functions in $fileName"
                } else {
                    "Read $fileName"
                }
                ActionIntent(
                    title = title,
                    target = path,
                    humanSummary = "Inspecting $path"
                )
            }
            "read_file_range", "multi_read_file" -> {
                ActionIntent(
                    title = "Inspect lines in $fileName",
                    target = path,
                    humanSummary = "Examining targeted section of $fileName"
                )
            }
            "edit_file", "patch_file" -> {
                ActionIntent(
                    title = "Update $fileName",
                    target = path,
                    humanSummary = "Applying surgical code changes to $path"
                )
            }
            "multi_edit_file" -> {
                ActionIntent(
                    title = "Multi-edit $fileName",
                    target = path,
                    humanSummary = "Performing multi-chunk replacement on $path"
                )
            }
            "create_file" -> {
                ActionIntent(
                    title = "Create new file",
                    target = path,
                    humanSummary = "Generating new module: $path"
                )
            }
            "delete_file" -> {
                ActionIntent(
                    title = "Delete file",
                    target = path,
                    humanSummary = "Removing obsolete file: $path"
                )
            }
            "read_preview_errors" -> {
                ActionIntent(
                    title = "Diagnose preview errors",
                    target = "Preview web console",
                    humanSummary = "Extracting runtime exceptions from Preview tab"
                )
            }
            "read_build_errors" -> {
                ActionIntent(
                    title = "Diagnose build errors",
                    target = "GitHub Actions logs",
                    humanSummary = "Analyzing compilation failures from Build tab"
                )
            }
            "read_console_logs" -> {
                ActionIntent(
                    title = "Inspect console logs",
                    target = "Preview console",
                    humanSummary = "Reading web preview logs"
                )
            }
            "read_build_logs" -> {
                ActionIntent(
                    title = "Inspect build logs",
                    target = "Build workflow",
                    humanSummary = "Reading GitHub Action build output"
                )
            }
            "list_all_tools" -> {
                ActionIntent(
                    title = "List all AI tools",
                    target = "PenCode tools registry",
                    humanSummary = "Querying complete tools documentation"
                )
            }
            "learn_pattern" -> {
                ActionIntent(
                    title = "Memorize learned pattern",
                    target = (args?.get("title") as? String) ?: "Self-learning memory",
                    humanSummary = "Persisting proven fix pattern to vector memory"
                )
            }
            "synthesize_skill" -> {
                ActionIntent(
                    title = "Synthesize custom skill",
                    target = (args?.get("name") as? String) ?: "Agent Skill",
                    humanSummary = "Autonomous agent skill generation"
                )
            }
            "recall_learned_patterns" -> {
                ActionIntent(
                    title = "Recall learned patterns",
                    target = query.ifBlank { "vector memory" },
                    humanSummary = "Retrieving past fixes and architecture rules"
                )
            }
            "generate_image", "generate_logo" -> {
                ActionIntent(
                    title = "Generate visual asset",
                    target = (args?.get("prompt") as? String)?.take(40) ?: "image",
                    humanSummary = "Creating visual graphics with AI"
                )
            }
            "complete" -> {
                ActionIntent(
                    title = "Task completed",
                    target = "Finalized",
                    humanSummary = (args?.get("message") as? String)?.take(80) ?: "Task finished"
                )
            }
            else -> {
                ActionIntent(
                    title = tool.replace("_", " ").replaceFirstChar { it.uppercase() },
                    target = path.ifBlank { query }.ifBlank { tool },
                    humanSummary = "Executing $tool"
                )
            }
        }
    }
}
