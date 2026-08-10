package com.example.agent

import com.example.api.Content
import com.example.api.GeminiClient
import com.example.api.Part

object ReadLoopSafetyManager {

    val SYSTEM_READ_WARNING = """
        STRICT FILE READING RULES & ANTI-READ-LOOP DIRECTIVES:
        - MANDATORY READ BEFORE EDIT/WRITE: You MUST ALWAYS read and inspect an existing file FIRST using 'read_file', 'read_file_range', or 'multi_read_file' BEFORE calling 'edit_file', 'multi_edit_file', 'patch_file', or 'write_file'. Editing or modifying a file without inspecting/reading it first is STRICTLY FORBIDDEN!
        - NO RE-READING AFTER EDIT, CREATE, OR OVERWRITE: After calling 'edit_file', 'multi_edit_file', 'patch_file', 'create_file', or 'write_file', DO NOT re-read or inspect the same file again immediately! Re-reading a file right after editing, creating, or overwriting it creates redundant read loops and is STRICTLY FORBIDDEN.
        - NO REDUNDANT READ LOOPS: Always inspect existing tool outputs in the conversation history before calling read tools. DO NOT repeatedly call read tools on the same file if you already have its contents in context.
        - Once you have inspected and edited a file, IMMEDIATELY proceed to 'complete' or your next constructive action.
        - If stuck in a read loop, the system will automatically complete the task and report short details.
    """.trimIndent()

    fun isReadTool(toolName: String): Boolean {
        val t = toolName.trim().lowercase()
        return t == "read_file" ||
                t == "read_file_range" ||
                t == "multi_read_file" ||
                t == "multi_read" ||
                t == "view_file"
    }

    suspend fun generateAiCompletionSummary(
        userPrompt: String,
        activeApiKey: String,
        systemInstruction: String,
        history: List<Content>,
        provider: String,
        modelId: String,
        baseUrl: String?,
        useCustom: Boolean,
        lastTool: String,
        path: String?
    ): String {
        return try {
            val forceCompletePrompt = """
                SYSTEM DIRECTIVE (ANTI-READ-LOOP FINALIZATION):
                The anti-read-loop safety system has triggered because files have been sufficiently inspected.
                User Request: "$userPrompt"
                
                You MUST finalize the task now. Provide a concise, professional summary of the codebase inspection, key findings, and completed status.
                Call the 'complete' tool or provide a clear short explanation of what was inspected.
            """.trimIndent()
            
            val tempHistory = history.toMutableList()
            tempHistory.add(Content(role = "user", parts = listOf(Part(text = forceCompletePrompt))))

            val stepResponse = GeminiClient.generateAgentStep(
                apiKey = activeApiKey,
                systemInstruction = systemInstruction,
                conversationHistory = tempHistory,
                provider = provider,
                modelId = modelId,
                customBaseUrl = baseUrl,
                useCustom = useCustom
            )

            val args = stepResponse?.arguments
            val aiAnswer = args?.message
                ?: args?.content
                ?: stepResponse?.thought?.takeIf { it.isNotBlank() }

            if (!aiAnswer.isNullOrBlank()) {
                aiAnswer.trim()
            } else {
                generateReadLoopAutoCompleteSummary(lastTool, path)
            }
        } catch (e: Exception) {
            generateReadLoopAutoCompleteSummary(lastTool, path)
        }
    }

    fun generateReadLoopAutoCompleteSummary(toolName: String, path: String?): String {
        val targetPath = path?.takeIf { it.isNotBlank() } ?: "the target codebase files"
        return "Task completed: Successfully inspected $targetPath and gathered required context."
    }
}
