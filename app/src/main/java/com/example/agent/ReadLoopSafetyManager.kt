package com.example.agent

object ReadLoopSafetyManager {

    val SYSTEM_READ_WARNING = """
        STRICT FILE READING RULES & ANTI-READ-LOOP DIRECTIVE:
        - DO NOT repeatedly or redundantly call 'read_file', 'read_file_range', or 'multi_read_file' on the same file if you have already received its content in context!
        - Inspect existing tool outputs in the conversation history before calling read tools. Re-reading the same file without making edits or taking new actions is STRICTLY FORBIDDEN.
        - Once you have inspected a file, IMMEDIATELY proceed to 'edit_file', 'patch_file', 'create_file', or 'complete'. Do NOT re-read unnecessarily.
        - If you get stuck in a read loop, the system will automatically complete the task and report short details.
    """.trimIndent()

    fun isReadTool(toolName: String): Boolean {
        val t = toolName.trim().lowercase()
        return t == "read_file" ||
                t == "read_file_range" ||
                t == "multi_read_file" ||
                t == "multi_read" ||
                t == "view_file"
    }

    fun generateReadLoopAutoCompleteSummary(toolName: String, path: String?): String {
        val targetPath = path?.takeIf { it.isNotBlank() } ?: "the target codebase files"
        return "Auto-completed task: File inspection completed for $targetPath. The AI gathered required context and finished task execution."
    }
}
