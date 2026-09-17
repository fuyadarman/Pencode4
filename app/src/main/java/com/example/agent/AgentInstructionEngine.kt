package com.example.agent

import com.example.data.McpServer
import com.example.data.ProjectEntity
import com.example.data.ProjectFileEntity
import com.example.ui.AgentSkill

/**
 * Dynamic Intent-Aware Instruction Engine.
 * Inspired by OpenCode, Claude Code, and Cline.
 * Dynamically loads only relevant instruction modules and tools based on prompt intent and active context.
 */
object AgentInstructionEngine {

    enum class PromptIntent {
        CONVERSATIONAL_OR_EXPLANATION,
        TASK_CONTINUATION,
        CODE_MODIFICATION_OR_FEATURE,
        SEARCH_AND_EXPLORATION,
        WEB_AND_UI_INSPECTION,
        DATABASE_AND_MCP,
        DEBUG_AND_ERROR_FIXING,
        GENERAL_AGENT_TASK
    }

    /**
     * Classifies user prompt to determine which instruction modules to load dynamically.
     */
    fun classifyPromptIntent(
        userPrompt: String,
        hasSelectedMcp: Boolean,
        hasTaggedFiles: Boolean,
        hasBrowserUrls: Boolean
    ): Set<PromptIntent> {
        val p = userPrompt.lowercase().trim()
        val intents = mutableSetOf<PromptIntent>()

        // Check for continuation trigger (e.g. "continue", "Continue", "CONTINUE", "cont", "kaj caliye jao", etc.)
        val isContinuation = p == "continue" || p == "cont" || p == "continue task" || p == "continue please" ||
                p == "chalate thako" || p == "caliye jao" || p == "কাজ চালিয়ে যান" || p == "চালিয়ে যাও" ||
                p.startsWith("[task continuation") || p.startsWith("please continue the previous")

        if (isContinuation) {
            intents.add(PromptIntent.TASK_CONTINUATION)
            intents.add(PromptIntent.CODE_MODIFICATION_OR_FEATURE)
            return intents
        }

        val isGreetingOrChat = p.matches(Regex("^(hi|hello|hey|hola|kemon|kemn|kemon acho|assalamu alaikum|salam|sup|yo|good morning|good evening|thanks|thank you|dhonnobad)[.!?\\s]*$")) ||
                (p.length < 35 && (p.contains("explain") || p.contains("what is") || p.contains("how does") || p.contains("ki eta") || p.contains("bujhiye dao") || p.contains("meaning")) && !p.contains("code") && !p.contains("create") && !p.contains("make") && !p.contains("build") && !p.contains("add") && !p.contains("fix"))

        if (isGreetingOrChat && !hasSelectedMcp && !hasTaggedFiles && !hasBrowserUrls) {
            return setOf(PromptIntent.CONVERSATIONAL_OR_EXPLANATION)
        }

        // Web / Browser / UI Clone
        if (hasBrowserUrls || p.contains("http://") || p.contains("https://") || p.contains("clone") || p.contains("website") || p.contains("inspect") || p.contains("screenshot") || p.contains("scrape") || p.contains("dom") || p.contains("css")) {
            intents.add(PromptIntent.WEB_AND_UI_INSPECTION)
        }

        // Database / MCP / Cloudflare / Supabase
        if (hasSelectedMcp || p.contains("database") || p.contains("sql") || p.contains("table") || p.contains("query") || p.contains("d1") || p.contains("supabase") || p.contains("mcp") || p.contains("r2") || p.contains("kv") || p.contains("schema") || p.contains("migration") || p.contains("crud")) {
            intents.add(PromptIntent.DATABASE_AND_MCP)
        }

        // Debug / Error fixing
        if (p.contains("error") || p.contains("bug") || p.contains("fix") || p.contains("exception") || p.contains("failed") || p.contains("crash") || p.contains("not working") || p.contains("issue") || p.contains("build failed") || p.contains("console") || p.contains("log") || p.contains("action") || p.contains("workflow") || p.contains("preview")) {
            intents.add(PromptIntent.DEBUG_AND_ERROR_FIXING)
        }

        // Search / Exploration
        if (p.contains("search") || p.contains("find") || p.contains("where is") || p.contains("locate") || p.contains("list files") || p.contains("structure")) {
            intents.add(PromptIntent.SEARCH_AND_EXPLORATION)
        }

        // Document & PDF Generation
        if (p.contains("pdf") || p.contains("document") || p.contains("report") || p.contains("doc") || p.contains("export pdf") || p.contains("print")) {
            intents.add(PromptIntent.CODE_MODIFICATION_OR_FEATURE)
        }

        // Image generation, resizing & manipulation
        if (p.contains("resize") || p.contains("scale") || p.contains("image") || p.contains("crop") || p.contains("ছবি") || p.contains("রিসাইজ") || p.contains("compress")) {
            intents.add(PromptIntent.CODE_MODIFICATION_OR_FEATURE)
        }

        // Code modification / New features
        if (hasTaggedFiles || p.contains("create") || p.contains("add") || p.contains("build") || p.contains("implement") || p.contains("modify") || p.contains("update") || p.contains("change") || p.contains("write") || p.contains("edit") || p.contains("screen") || p.contains("ui") || p.contains("button") || p.contains("feature") || p.contains("design") || p.contains("code") || p.contains("refactor") || p.contains("make")) {
            intents.add(PromptIntent.CODE_MODIFICATION_OR_FEATURE)
        }

        if (intents.isEmpty()) {
            intents.add(PromptIntent.GENERAL_AGENT_TASK)
            intents.add(PromptIntent.CODE_MODIFICATION_OR_FEATURE)
        }

        return intents
    }

    /**
     * Builds dynamic, ultra-lean System Instruction matching the classified intents.
     */
    fun buildDynamicSystemInstruction(
        userPrompt: String,
        project: ProjectEntity,
        allFiles: List<ProjectFileEntity>,
        fileTreeSummary: String,
        activeSkills: List<AgentSkill>,
        effectiveMcpServers: List<McpServer>,
        mcpToolsPrompt: String,
        activeTemplateInfo: String,
        maxActionSteps: Int,
        allowBuildPush: Boolean,
        reasoningEffort: ReasoningEffort = ReasoningEffort.NORMAL
    ): String {
        val intents = classifyPromptIntent(
            userPrompt = userPrompt,
            hasSelectedMcp = effectiveMcpServers.isNotEmpty(),
            hasTaggedFiles = false,
            hasBrowserUrls = userPrompt.contains("http://") || userPrompt.contains("https://")
        )

        val isPureChat = intents.contains(PromptIntent.CONVERSATIONAL_OR_EXPLANATION) && intents.size == 1

        val sb = StringBuilder()

        // 1. Core Agent Identity
        sb.append("You are PenCode AI, an elite Autonomous Development Agent.\n\n")

        // Pure Chat Mode: Ultra-lightweight ~200 tokens
        if (isPureChat) {
            sb.append("""
                === CONVERSATION MODE ===
                - Respond directly, helpfully, and conversationally to the user in their language.
                - If no workspace tool actions are needed, return your final response or invoke 'complete'.
                
                === TOOLS ===
                - 'ai_think'(message) [Optional: analyze concept], 'complete'(message) [Finish response]
                
                === MANDATORY FORMAT ===
                {"thought":"Short reasoning","tool":"complete","arguments":{"message":"Your helpful response"}}
            """.trimIndent())
            return sb.toString()
        }

        // 2. Dynamic Semantic Memory (Jcode Vector Cosine Similarity Retrieval)
        val relevantMemories = com.example.agent.harness.SemanticMemoryStore.retrieveRelevantMemories(
            query = userPrompt,
            projectName = project.name,
            topK = 3,
            threshold = 0.18f
        )
        if (relevantMemories.isNotEmpty()) {
            sb.append(com.example.agent.harness.SemanticMemoryStore.formatMemoriesForPrompt(relevantMemories))
        }

        // 2b. Autonomous Self-Learned Rules & Fix Memory
        val learnedRules = HybridSelfLearningEngine.formatLearnedRulesForPrompt(userPrompt)
        if (learnedRules.isNotBlank()) {
            sb.append(learnedRules)
        }

        // 3. Workspace Context (File Tree & Framework)
        sb.append(fileTreeSummary).append("\n\n")
        sb.append("FRAMEWORK: ").append(activeTemplateInfo).append("\n\n")

        // 3. Lean Core Directives (Optimized for KV Cache & low token footprint)
        sb.append("=== CORE DIRECTIVES ===\n")
        sb.append("1. LATEST PROMPT: Execute current user prompt faithfully without unsolicited bloat. Call 'complete' when finished.\n")
        sb.append("2. BUDGET: Max steps: $maxActionSteps. Language: match user (Bangla/English).\n")
        sb.append("3. SEARCH FIRST (NO BLIND READ): STRICTLY FORBIDDEN from casually dumping files with 'read_file_range' or 'read_file'. If looking for code, functions, or text, you MUST use 'global_search' or 'grep' FIRST to locate the exact file and lines before reading!\n")
        sb.append("4. NO VERIFICATION READS: STRICTLY FORBIDDEN from calling 'read_file' or 'read_file_range' to 'verify', 'check', or 'confirm' code edits. Once 'edit_file', 'multi_edit_file', or 'create_file' executes, your changes are already applied. Move directly to the next task or call 'complete'!\n")
        sb.append("5. SURGICAL EDITS: Never overwrite files >30 lines. Read once before editing with 'edit_file'/'multi_edit_file'.\n")
        sb.append("6. NEW FILES: Use 'create_file' ONLY for new files. Existing files must be edited.\n")
        sb.append("7. DIAGNOSTICS: Use 'read_preview_errors' for preview bugs and 'read_build_errors' for build failures.\n")
        sb.append("8. TOOL CATALOG: If any tool command is omitted or you need full documentation, invoke 'list_all_tools' to see all available tools and usage.\n")
        sb.append("9. ${reasoningEffort.directive}\n")
        sb.append("10. BUILD & PUSH: You can press the Build tab Build button to push code to GitHub and trigger compilation by invoking 'trigger_build'.\n")

        // 4. Skills Module (Only if skills are active)
        if (activeSkills.isNotEmpty()) {
            sb.append("\n=== ACTIVE AGENT SKILLS ===\n")
            activeSkills.forEach { skill ->
                sb.append("• [${skill.name}]: ${skill.description}\n")
                if (skill.skillPrompt.isNotBlank()) {
                    sb.append("  Instructions: ${skill.skillPrompt.take(200)}\n")
                }
            }
            sb.append("\n")
        }

        // 5. MCP & Remote Backend Module (Only if MCP active)
        if (mcpToolsPrompt.isNotBlank()) {
            sb.append("\n=== ATTACHED MCP TOOLS ===\n")
            sb.append(mcpToolsPrompt).append("\n\n")
        }

        // 6. Dynamic Tools Selection
        sb.append("=== AVAILABLE TOOLS ===\n")
        val tools = mutableListOf<String>()
        
        // Base / Universal tools
        tools.add("'ai_think'(message)")
        tools.add("'complete'(message)")
        tools.add("'ask_user'(question, options?: [string])")
        tools.add("'list_all_tools'() [View all tools with full documentation]")
        tools.add("'read_preview_errors'(query?: string, maxLines?: number) [Read ONLY Preview tab errors & exceptions]")
        tools.add("'read_build_errors'(query?: string, maxLines?: number) [Read ONLY Build tab compilation & GitHub Action errors]")
        tools.add("'read_console_logs'(filter?, query?, maxLines?)")
        tools.add("'read_build_logs'(filter?, query?, maxLines?)")
        tools.add("'trigger_build'(message?) [Press Build button & push code to GitHub]")

        // Code / File tools
        if (intents.contains(PromptIntent.CODE_MODIFICATION_OR_FEATURE) || intents.contains(PromptIntent.DEBUG_AND_ERROR_FIXING) || intents.contains(PromptIntent.GENERAL_AGENT_TASK) || intents.contains(PromptIntent.SEARCH_AND_EXPLORATION)) {
            tools.add("'global_search'(query) [or 'grep'(query) - Fast grep across all files. ALWAYS USE FIRST TO FIND CODE]")
            tools.add("'read_file'(path)")
            tools.add("'read_file_range'(path, startLine, endLine) [Use only when line numbers are already pinpointed]")
            tools.add("'multi_read_file'(path, ranges:[{startLine,endLine}])")
            tools.add("'create_file'(path, content) [Brand new files only]")
            tools.add("'edit_file'(path, search, replace)")
            tools.add("'multi_edit_file'(path, chunks:[{search,replace}])")
            tools.add("'delete_file'(path)")
            tools.add("'scan_dir'(path)")
            tools.add("'delete_code_chunk'(path, codeChunk)")
            tools.add("'copy_code_chunk'(sourcePath, targetPath, codeChunk, targetAnchor?)")
            tools.add("'move_code_chunk'(sourcePath, targetPath, codeChunk, targetAnchor?)")
            tools.add("'generate_image'(prompt, path?)")
            tools.add("'generate_pdf'(title, content, theme?, path?)")
        }

        // Web / Internet / Browser inspection & cloning tools
        tools.add("'browser_search'(query)")
        tools.add("'browser_read'()")
        tools.add("'browser_snapshot'()")
        tools.add("'browser_controller'(action: 'click'|'type'|'scroll', elementIndex?, selector?, text?)")
        tools.add("'fetch_url'(url, targetFile?)")
        tools.add("'clone_web_ui'(url, targetFilePath?)")
        tools.add("'deep_clone_web_ui'(url, targetFilePath?)")

        if (intents.contains(PromptIntent.WEB_AND_UI_INSPECTION)) {
            tools.add("'open_url'(url)")
            tools.add("'inspect_dom'(selector)")
            tools.add("'inspect_css'(selector)")
            tools.add("'take_screenshot'(path)")
            tools.add("'run_javascript'(script)")
        }

        // MCP Tools
        if (intents.contains(PromptIntent.DATABASE_AND_MCP) || effectiveMcpServers.isNotEmpty()) {
            tools.add("'mcp_call_tool'(mcpServerId, toolName, mcpArgsJson)")
            tools.add("'mcp_list_tools'(mcpServerId)")
        }

        tools.forEach { t -> sb.append("- ").append(t).append("\n") }

        // 7. Batch Operations & Formulating Logic Guideline
        sb.append("\n").append(AgentBatchExecutionManager.SYSTEM_BATCH_LOGIC_INSTRUCTION).append("\n")

        // 8. Output Format
        sb.append("""

=== MANDATORY FORMAT ===
Return ONLY raw JSON object.
Batch format (Standard): {"thought":"...","tools":[{"tool":"read_file","arguments":{"path":"..."}},{"tool":"edit_file","arguments":{...}}]}
Single format (Exploratory only): {"thought":"...","tool":"global_search","arguments":{"query":"..."}}
CRITICAL: Never return only a {"thought":"..."} block without tools when coding. Always bundle your tool calls in 'tools' within the same JSON response.
Call 'complete' with Markdown summary when finished.
        """.trimIndent())

        return sb.toString()
    }
}
