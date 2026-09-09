package com.example.ui.agent

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.AiActionLog

/**
 * Categorizes tool execution calls to provide dedicated icons, badge labels,
 * target resource extraction, and thematic accent coloring.
 */
enum class ToolCallType(
    val toolName: String,
    val displayName: String,
    val defaultColor: Color,
    val icon: ImageVector
) {
    READ_FILE("read_file", "Read File", Color(0xFF38BDF8), Icons.Default.Description),
    EDIT_FILE("edit_file", "Edit File", Color(0xFF4ADE80), Icons.Default.Edit),
    MULTI_EDIT("multi_edit_file", "Multi Edit", Color(0xFF34D399), Icons.Default.Difference),
    CREATE_FILE("create_file", "Create File", Color(0xFF2DD4BF), Icons.Default.NoteAdd),
    DELETE_FILE("delete_file", "Delete File", Color(0xFFF87171), Icons.Default.DeleteForever),
    MOVE_FILE("move_file", "Move / Rename", Color(0xFFFBBF24), Icons.Default.DriveFileMove),
    SCAN_DIR("list_dir", "List Directory", Color(0xFF60A5FA), Icons.Default.FolderOpen),
    COMMAND_EXEC("run_command", "Run Shell", Color(0xFFF59E0B), Icons.Default.Terminal),
    WEB_SEARCH("search_web", "Web Search", Color(0xFF818CF8), Icons.Default.Language),
    COMPILE_BUILD("compile_applet", "Build & Compile", Color(0xFFA78BFA), Icons.Default.Build),
    LINT_CHECK("lint_applet", "Lint Check", Color(0xFFC084FC), Icons.Default.FactCheck),
    SKILL_LOAD("load_skill", "Skill Engine", Color(0xFFE879F9), Icons.Default.AutoAwesome),
    MCP_TOOL("mcp_tool", "MCP Tool", Color(0xFFFB7185), Icons.Default.Extension),
    MANAGE_TASK("manage_task", "Manage Task", Color(0xFF38BDF8), Icons.Default.Schedule),
    SCHEDULE_TIMER("schedule", "Schedule Timer", Color(0xFFF472B6), Icons.Default.Alarm),
    THINKING("reasoning", "Thinking", Color(0xFF94A3B8), Icons.Default.Psychology),
    GENERIC_TOOL("tool_call", "Tool Call", Color(0xFF94A3B8), Icons.Default.Code)
}

data class ClassifiedToolCall(
    val type: ToolCallType,
    val toolName: String,
    val targetResource: String,
    val lineRange: String?,
    val isThought: Boolean,
    val isRunning: Boolean,
    val isFailed: Boolean,
    val statusLabel: String,
    val durationText: String?
)

object ToolCallTypeClassifier {

    fun classify(log: AiActionLog, isGlobalThinking: Boolean): ClassifiedToolCall {
        val title = log.title.trim()
        val details = log.details ?: ""
        val status = log.status.lowercase()
        val isRunning = status == "thinking" || status == "running" || (isGlobalThinking && status != "success" && status != "failed" && status != "error")
        val isFailed = status == "failed" || status == "error" || title.contains("failed", ignoreCase = true) || title.contains("error", ignoreCase = true)

        val durationMs = log.durationMillis ?: if (log.timestamp > log.startTime && log.startTime > 0) (log.timestamp - log.startTime) else null
        val durationText = durationMs?.let {
            if (it < 1000) "${it}ms" else "${String.format("%.1f", it / 1000.0)}s"
        }

        // 1. Thinking / Reasoning
        if (title.contains("thinking", ignoreCase = true) ||
            title.contains("formulating logic", ignoreCase = true) ||
            title.contains("thought process", ignoreCase = true) ||
            title.contains("analyzing", ignoreCase = true) && !title.contains("file", ignoreCase = true)) {
            return ClassifiedToolCall(
                type = ToolCallType.THINKING,
                toolName = "reasoning",
                targetResource = "",
                lineRange = log.lineRange,
                isThought = true,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Thinking..." else "Reasoned",
                durationText = durationText
            )
        }

        // 2. Read File / View File
        if (title.contains("read_file", ignoreCase = true) ||
            title.contains("view_file", ignoreCase = true) ||
            title.startsWith("Read:", ignoreCase = true) ||
            title.contains("Read file", ignoreCase = true) ||
            title.contains("Viewing file", ignoreCase = true)) {
            val path = extractPath(title, details, "Read:")
            return ClassifiedToolCall(
                type = ToolCallType.READ_FILE,
                toolName = "read_file",
                targetResource = path,
                lineRange = log.lineRange,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Reading..." else if (isFailed) "Failed" else "Read",
                durationText = durationText
            )
        }

        // 3. Multi Edit File
        if (title.contains("multi_edit_file", ignoreCase = true) ||
            title.startsWith("Patch:", ignoreCase = true) ||
            title.contains("Patched file", ignoreCase = true)) {
            val path = extractPath(title, details, "Patch:")
            return ClassifiedToolCall(
                type = ToolCallType.MULTI_EDIT,
                toolName = "multi_edit_file",
                targetResource = path,
                lineRange = log.lineRange,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Patching..." else if (isFailed) "Failed" else "Patched",
                durationText = durationText
            )
        }

        // 4. Edit File
        if (title.contains("edit_file", ignoreCase = true) ||
            title.startsWith("Edit:", ignoreCase = true) ||
            title.contains("Modified file", ignoreCase = true) ||
            title.contains("Editing file", ignoreCase = true)) {
            val path = extractPath(title, details, "Edit:")
            return ClassifiedToolCall(
                type = ToolCallType.EDIT_FILE,
                toolName = "edit_file",
                targetResource = path,
                lineRange = log.lineRange,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Editing..." else if (isFailed) "Failed" else "Edited",
                durationText = durationText
            )
        }

        // 5. Create / Write File
        if (title.contains("create_file", ignoreCase = true) ||
            title.contains("write_file", ignoreCase = true) ||
            title.startsWith("Write:", ignoreCase = true) ||
            title.contains("Created file", ignoreCase = true)) {
            val path = extractPath(title, details, "Write:")
            return ClassifiedToolCall(
                type = ToolCallType.CREATE_FILE,
                toolName = "create_file",
                targetResource = path,
                lineRange = log.lineRange,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Creating..." else if (isFailed) "Failed" else "Created",
                durationText = durationText
            )
        }

        // 6. Delete File / Directory
        if (title.contains("delete_file", ignoreCase = true) ||
            title.contains("delete_dir", ignoreCase = true) ||
            title.contains("Deleted file", ignoreCase = true) ||
            title.contains("Deleted dir", ignoreCase = true)) {
            val path = extractPath(title, details, "Delete:")
            return ClassifiedToolCall(
                type = ToolCallType.DELETE_FILE,
                toolName = "delete_file",
                targetResource = path,
                lineRange = null,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Deleting..." else if (isFailed) "Failed" else "Deleted",
                durationText = durationText
            )
        }

        // 7. Move / Rename File
        if (title.contains("move_file", ignoreCase = true) ||
            title.contains("move", ignoreCase = true) && title.contains("file", ignoreCase = true)) {
            val path = extractPath(title, details, "Move:")
            return ClassifiedToolCall(
                type = ToolCallType.MOVE_FILE,
                toolName = "move",
                targetResource = path,
                lineRange = null,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Moving..." else if (isFailed) "Failed" else "Moved",
                durationText = durationText
            )
        }

        // 8. Scan Directory / List Dir
        if (title.contains("list_dir", ignoreCase = true) ||
            title.contains("scan_dir", ignoreCase = true) ||
            title.contains("Scanned directory", ignoreCase = true) ||
            title.contains("Listing directory", ignoreCase = true)) {
            val path = extractPath(title, details, "Directory:")
            return ClassifiedToolCall(
                type = ToolCallType.SCAN_DIR,
                toolName = "list_dir",
                targetResource = path.ifBlank { "/" },
                lineRange = null,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Scanning..." else if (isFailed) "Failed" else "Listed",
                durationText = durationText
            )
        }

        // 9. Command Execution / Shell / Grep
        if (title.contains("run_command", ignoreCase = true) ||
            title.contains("shell_exec", ignoreCase = true) ||
            title.contains("Running command", ignoreCase = true) ||
            title.contains("Shell:", ignoreCase = true) ||
            title.contains("Command execution", ignoreCase = true) ||
            title.contains("grep", ignoreCase = true)) {
            val cmd = extractCommand(title, details)
            return ClassifiedToolCall(
                type = ToolCallType.COMMAND_EXEC,
                toolName = "run_command",
                targetResource = cmd,
                lineRange = null,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Running..." else if (isFailed) "Failed" else "Executed",
                durationText = durationText
            )
        }

        // 10. Web Search
        if (title.contains("search_web", ignoreCase = true) ||
            title.contains("web_search", ignoreCase = true) ||
            title.contains("Searching the web", ignoreCase = true) ||
            title.startsWith("Search:", ignoreCase = true)) {
            val query = title.removePrefix("Search:").removePrefix("Searching the web:").trim()
            return ClassifiedToolCall(
                type = ToolCallType.WEB_SEARCH,
                toolName = "search_web",
                targetResource = query.ifBlank { details.take(40) },
                lineRange = null,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Searching..." else if (isFailed) "Failed" else "Searched",
                durationText = durationText
            )
        }

        // 11. Compile / Build / Lint
        if (title.contains("compile_applet", ignoreCase = true) ||
            title.contains("Building applet", ignoreCase = true) ||
            title.contains("Build", ignoreCase = true) && title.contains("Gradle", ignoreCase = true)) {
            return ClassifiedToolCall(
                type = ToolCallType.COMPILE_BUILD,
                toolName = "compile_applet",
                targetResource = "assembleDebug",
                lineRange = null,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Compiling..." else if (isFailed) "Failed" else "Success",
                durationText = durationText
            )
        }

        if (title.contains("lint_applet", ignoreCase = true) ||
            title.contains("Lint", ignoreCase = true)) {
            return ClassifiedToolCall(
                type = ToolCallType.LINT_CHECK,
                toolName = "lint_applet",
                targetResource = "npm run lint",
                lineRange = null,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Linting..." else if (isFailed) "Issues" else "Passed",
                durationText = durationText
            )
        }

        // 12. Skills
        if (title.contains("skill", ignoreCase = true) || title.startsWith("Skill:")) {
            val skillName = title.removePrefix("Skill:").trim()
            return ClassifiedToolCall(
                type = ToolCallType.SKILL_LOAD,
                toolName = "load_skill",
                targetResource = skillName,
                lineRange = null,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Loading..." else "Loaded",
                durationText = durationText
            )
        }

        // 13. MCP / Document tools
        if (title.contains("mcp", ignoreCase = true) || title.contains("document", ignoreCase = true)) {
            return ClassifiedToolCall(
                type = ToolCallType.MCP_TOOL,
                toolName = "mcp_tool",
                targetResource = title,
                lineRange = log.lineRange,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Calling..." else if (isFailed) "Failed" else "Done",
                durationText = durationText
            )
        }

        // 14. Manage task / Schedule
        if (title.contains("manage_task", ignoreCase = true) || title.contains("task", ignoreCase = true) && title.contains("background", ignoreCase = true)) {
            return ClassifiedToolCall(
                type = ToolCallType.MANAGE_TASK,
                toolName = "manage_task",
                targetResource = title,
                lineRange = null,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Executing..." else "Completed",
                durationText = durationText
            )
        }

        if (title.contains("schedule", ignoreCase = true) || title.contains("timer", ignoreCase = true)) {
            return ClassifiedToolCall(
                type = ToolCallType.SCHEDULE_TIMER,
                toolName = "schedule",
                targetResource = title,
                lineRange = null,
                isThought = false,
                isRunning = isRunning,
                isFailed = isFailed,
                statusLabel = if (isRunning) "Scheduling..." else "Scheduled",
                durationText = durationText
            )
        }

        // Fallback generic tool
        return ClassifiedToolCall(
            type = ToolCallType.GENERIC_TOOL,
            toolName = "tool_call",
            targetResource = title,
            lineRange = log.lineRange,
            isThought = false,
            isRunning = isRunning,
            isFailed = isFailed,
            statusLabel = if (isRunning) "Running..." else if (isFailed) "Failed" else "Done",
            durationText = durationText
        )
    }

    private fun extractPath(title: String, details: String, prefix: String): String {
        if (title.startsWith(prefix, ignoreCase = true)) {
            return title.removePrefix(prefix).trim()
        }
        val words = title.split(" ")
        val pathCandidate = words.firstOrNull { it.contains("/") || it.endsWith(".kt") || it.endsWith(".xml") || it.endsWith(".json") || it.endsWith(".gradle") }
        if (pathCandidate != null) return pathCandidate
        return title
    }

    private fun extractCommand(title: String, details: String): String {
        if (title.startsWith("Shell:", ignoreCase = true)) {
            return title.removePrefix("Shell:").trim()
        }
        if (title.startsWith("Command execution:", ignoreCase = true)) {
            return title.removePrefix("Command execution:").trim()
        }
        return title
    }
}
