package com.example.agent.harness

import android.content.Context
import com.example.data.McpServer
import com.example.data.ProjectEntity
import com.example.data.ProjectFileEntity
import com.example.ui.AgentSkill

/**
 * Jcode Harness Manager & Runtime Coordinator.
 * Implements the "Harness matters as much as the model" architecture:
 *
 * System Architecture Pipeline:
 * User Request
 *   ↓
 * Agent Runtime / Harness
 *   ↓
 * Context Assembly + Semantic Memory Retrieval + Tool Orchestration
 *   ↓
 * LLM Inference (Ultra-lean ~670 token core prompt)
 *   ↓
 * Tool Calls
 *   ↓
 * Tool Results
 *   ↓
 * Harness Feedback Loop & Context Pruning
 *   ↓
 * LLM Decision / Completion
 */
object JcodeHarnessManager {

    /**
     * Builds the streamlined, high-efficiency Jcode harness prompt (~600-700 tokens base).
     */
    fun buildHarnessSystemPrompt(
        userPrompt: String,
        project: ProjectEntity,
        allFiles: List<ProjectFileEntity>,
        fileTreeSummary: String,
        activeSkills: List<AgentSkill> = emptyList(),
        effectiveMcpServers: List<McpServer> = emptyList(),
        mcpToolsPrompt: String = "",
        activeTemplateInfo: String = "",
        maxActionSteps: Int = 30,
        allowBuildPush: Boolean = true,
        context: Context? = null
    ): String {
        val sb = StringBuilder()

        // 1. Core Agent Rules (Lean, declarative, high precision)
        sb.append(
            """
            You are Jcode AI Agent, an autonomous, production-grade Android vibe-coding engine.
            You operate inside a real Android runtime with full filesystem and tool execution capabilities.

            CORE AGENT DIRECTIVES:
            1. ACTION OVER TALK: Never give conversational fluff or pretend code. Use provided tools to directly inspect and edit files.
            2. SURGICAL EDITS: Read files with 'read_file' or 'grep' before modifying. Use 'edit_file' for surgical replacements.
            3. PERSISTENCE & COMPLETION: Keep executing tool actions step-by-step until the requested feature is fully implemented, then call 'complete'.
            4. ACCURACY: Strictly adhere to Kotlin & Jetpack Compose idioms. Ensure every Composable compiles without missing imports.
            
            """.trimIndent()
        )
        sb.append("\n\n")

        // 2. Dynamic Semantic Memory Retrieval (Cosine Similarity Vector Search)
        val relevantMemories = SemanticMemoryStore.retrieveRelevantMemories(
            query = userPrompt,
            projectName = project.name,
            topK = 3,
            threshold = 0.20f
        )
        if (relevantMemories.isNotEmpty()) {
            sb.append(SemanticMemoryStore.formatMemoriesForPrompt(relevantMemories))
        }

        // 3. Project Context & Environment
        sb.append("=== PROJECT CONTEXT ===\n")
        sb.append("Project: ${project.name}\n")
        sb.append("Template: ${activeTemplateInfo.ifBlank { "Android Jetpack Compose" }}\n")
        sb.append("File Tree Structure:\n$fileTreeSummary\n")
        sb.append("=== END PROJECT CONTEXT ===\n\n")

        // 4. MCP Tools (if connected)
        if (mcpToolsPrompt.isNotBlank()) {
            sb.append("=== MODEL CONTEXT PROTOCOL (MCP) TOOLS ===\n")
            sb.append(mcpToolsPrompt.trim())
            sb.append("\n=== END MCP TOOLS ===\n\n")
        }

        // 5. Active Custom Skills
        if (activeSkills.isNotEmpty()) {
            sb.append("=== ACTIVE SPECIALIZED SKILLS ===\n")
            activeSkills.forEach { skill ->
                sb.append("• Skill: ${skill.name} - ${skill.description}\n")
                if (skill.skillPrompt.isNotBlank()) {
                    sb.append("  Instructions: ${skill.skillPrompt}\n")
                }
            }
            sb.append("=== END SPECIALIZED SKILLS ===\n\n")
        }

        // 6. Tool Schema & Response Format
        sb.append(
            """
            === AVAILABLE CORE TOOLS ===
            • read_file: {"path": "relative/path/to/file"} - Read file contents.
            • edit_file: {"path": "...", "search": "exact string to replace", "replace": "new content"} - Surgical replacement.
            • create_file: {"path": "...", "content": "..."} - Create a new file with parent directories.
            • delete_file: {"path": "..."} - Delete a file.
            • list_files: {"path": "..."} - List files in workspace.
            • grep: {"query": "text to search", "path": "optional subpath"} - Fast regex/text search.
            • complete: {"message": "Final completion summary"} - Mark task finished.

            === OUTPUT PROTOCOL ===
            You MUST respond with valid JSON tool invocations matching:
            {
              "thought": "Brief reasoning of what step to do next",
              "tool": "tool_name",
              "arguments": { ... }
            }
            """.trimIndent()
        )

        return sb.toString()
    }

    /**
     * Evaluates tool output and generates feedback guidance for the harness feedback loop.
     */
    fun evaluateHarnessFeedback(
        toolName: String,
        argumentsString: String,
        resultOutput: String,
        isError: Boolean
    ): String? {
        if (isError) {
            if (resultOutput.contains("Target content not found") || resultOutput.contains("search string not found")) {
                return "HARNESS FEEDBACK: The target search string was not found in the file. Call 'read_file' or 'grep' to inspect the exact lines before attempting 'edit_file' again."
            }
            if (resultOutput.contains("File not found") || resultOutput.contains("No such file")) {
                return "HARNESS FEEDBACK: The specified file path does not exist. Call 'list_files' or verify the project file tree to locate the correct path."
            }
        }
        return null
    }

    /**
     * Ingests a completed conversation turn or milestone into the semantic memory vector store.
     */
    fun recordTurnMemory(
        projectName: String,
        userPrompt: String,
        assistantSummary: String,
        filesModified: List<String>,
        context: Context? = null
    ) {
        val topic = if (filesModified.isNotEmpty()) {
            "Modified files: ${filesModified.joinToString(", ")}"
        } else {
            "Task: ${userPrompt.take(50)}"
        }

        val content = "User requested: $userPrompt\nResult: $assistantSummary\nAffected files: ${filesModified.joinToString(", ")}"
        val tags = mutableListOf("turn", "completed")
        tags.addAll(filesModified.map { it.substringAfterLast("/") })

        SemanticMemoryStore.recordMemory(
            projectName = projectName,
            topic = topic,
            content = content,
            tags = tags,
            context = context
        )
    }
}
