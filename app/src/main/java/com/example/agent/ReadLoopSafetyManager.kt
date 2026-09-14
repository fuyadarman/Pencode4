package com.example.agent

import com.example.api.Content
import com.example.api.GeminiClient
import com.example.api.Part

object ReadLoopSafetyManager {

    val SYSTEM_READ_WARNING = """
        === SAFETY & ANTI-LOOP DIRECTIVES ===
        1. Read existing files AT MOST ONCE before editing. NEVER re-read right after editing/creating.
        2. Execute EXACTLY what user requested. Call 'complete' immediately when done.
        3. Keep files modular (<1000 lines). Create new files for new features/systems.
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
