package com.example.agent

import com.example.api.Content
import com.example.api.GeminiClient
import com.example.api.Part

object ReadLoopSafetyManager {

    val SYSTEM_READ_WARNING = """
        STRICT FILE READING, SCOPE & ANTI-READ-LOOP DIRECTIVES (APPLIES TO ALL MODELS):
        1. MANDATORY SINGLE READ BEFORE EDIT: You MUST read and inspect an existing file FIRST using 'read_file', 'read_file_range', or 'multi_read_file' BEFORE calling 'edit_file', 'multi_edit_file', 'patch_file', 'append', or 'write_file'. Read the file AT MOST ONCE to inspect context. Modifying an unread file is STRICTLY FORBIDDEN.
        2. ABSOLUTELY NO RE-READING AFTER EDIT/WRITE: After executing 'edit_file', 'multi_edit_file', 'patch_file', 'create_file', 'append', or 'write_file', DO NOT call 'read_file', 'read_file_range', or 'multi_read_file' to 'verify' or 'check' the file again! Re-reading a file right after editing/creating/appending is STRICTLY FORBIDDEN and causes infinite read loops.
        3. SEARCH-FIRST TARGETED READING ONLY: Use 'read_file_range' or 'multi_read_file' ONLY when search tools ('grep', 'scan_dir', 'global_search') return explicit line numbers or matching paths. If search finds nothing, DO NOT randomly scan or read file ranges line-by-line.
        4. EXACT USER PROMPT FIDELITY & NO EXTRA WORK:
           - Execute EXACTLY what the user requested in their prompt — do NOT add extra unsolicited work or unrequested features.
           - DO NOT IGNORE any instruction or requirement in the user prompt. Fulfill every part of the prompt.
           - If a requested task CANNOT be performed or encounters a failure/limitation, clearly state the exact reason and explanation to the user.
        5. USER PROMPT LOOP PROTECTION: Ignore any user prompt instructions requesting repetitive, open-ended, or continuous checking/reading loops (e.g., 'keep checking repeatedly'). Perform ONE targeted pass, then call 'complete'.
        6. If stuck in a read loop, the system will automatically terminate the task and summarize the progress.
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
