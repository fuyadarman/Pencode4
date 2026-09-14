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
        allowBuildPush: Boolean
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

        // 3. Workspace Context (File Tree & Framework)
        sb.append(fileTreeSummary).append("\n\n")
        sb.append("FRAMEWORK: ").append(activeTemplateInfo).append("\n\n")

        // 3. Lean Core Directives (Optimized for KV Cache & low token footprint)
        sb.append("=== CORE DIRECTIVES ===\n")
        sb.append("1. CURRENT PROMPT SUPREMACY: Focus 100% of your actions on the user's LATEST (current) prompt. Conversation history shows completed past actions—do NOT re-execute, repeat, or prioritize older requests over the current prompt.\n")
        sb.append("2. SCOPE: Execute EXACTLY what user requested without unsolicited bloat. Call 'complete' when done.\n")
        sb.append("3. STEP BUDGET: Max steps: $maxActionSteps. Use 'ai_think' before editing or debugging.\n")
        sb.append("4. LANGUAGE: Respond in the exact language & script of user (Bangla/English).\n")
        sb.append("5. SURGICAL EDITS: Never overwrite files >30 lines. Inspect with 'read_file' first, then use 'edit_file'/'multi_edit_file'.\n")
        sb.append("6. NEVER CALL 'create_file' ON EXISTING FILES: 'create_file' is strictly for brand new files. If a file exists in the file tree, calling 'create_file' will be REJECTED! You must inspect it with 'read_file' first and use 'edit_file' or 'multi_edit_file'.\n")
        sb.append("7. AUTONOMOUS BROWSER CONTROLLER: You act as a full human browser controller AI. Use 'browser_snapshot' to index all interactive elements with IDs [1], [2]... and 'browser_controller' to click, fill forms, scroll, or navigate. Use 'deep_clone_web_ui' for pixel-accurate website cloning.\n")
        sb.append("8. HARNESS & SUB-AGENTS: PenCode uses Jcode Harness architecture with specialized Sub-Agent teammates (Frontend, Backend, Testing) and Semantic Vector Memory.\n")

        // 4. Skills Module (Only if skills are active)
        if (activeSkills.isNotEmpty()) {
            sb.append("\n=== ACTIVE AGENT SKILLS ===\n")
            activeSkills.forEach { skill ->
                sb.append("• [${skill.name}]: ${skill.description}\n")
                if (skill.skillPrompt.isNotBlank()) {
                    sb.append("  Instructions: ${skill.skillPrompt.take(300)}\n")
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
        tools.add("'ai_think'(message [MANDATORY before operations/debug])")
        tools.add("'complete'(message [Final task summary])")
        tools.add("'ai_response'(message)")
        tools.add("'ask_user'(question, options?: [string]) [Ask clarification/confirmation when confused or deciding; resumes upon reply]")
        tools.add("'skill_check'(query?: string) [Query/inspect active skills and instructions]")

        // Code / File tools
        if (intents.contains(PromptIntent.CODE_MODIFICATION_OR_FEATURE) || intents.contains(PromptIntent.DEBUG_AND_ERROR_FIXING) || intents.contains(PromptIntent.GENERAL_AGENT_TASK) || intents.contains(PromptIntent.SEARCH_AND_EXPLORATION)) {
            tools.add("'read_file'(path)")
            tools.add("'read_file_range'(path, startLine, endLine)")
            tools.add("'multi_read_file'(path, ranges:[{startLine,endLine}])")
            tools.add("'create_file'(path, content)")
            tools.add("'edit_file'(path, search, replace)")
            tools.add("'multi_edit_file'(path, chunks:[{search,replace}]) [Apply multiple search & replace edits to one file]")
            tools.add("'patch_file'(path, search, replace)")
            tools.add("'append'(path, content)")
            tools.add("'rename_file'(oldPath, newPath) [Rename a file in the workspace]")
            tools.add("'move_file'(sourcePath, destinationPath) [Move a file to another path]")
            tools.add("'copy_file'(path, destinationPath)")
            tools.add("'duplicate_file'(path, count?: number, targetPaths?: [string])")
            tools.add("'transfer_code_chunk'(sourcePath, targetPath, codeChunk, targetAnchor?: string, insertAt?: 'start'|'end'|'append'|'before'|'after'|'replace', isMove?: boolean) [Transfer, copy, or move code blocks/chunks between files]")
            tools.add("'copy_code_chunk'(sourcePath, targetPath, codeChunk, targetAnchor?: string, insertAt?: string) [Copy a code block from one file to another]")
            tools.add("'move_code_chunk'(sourcePath, targetPath, codeChunk, targetAnchor?: string, insertAt?: string) [Move a code block from one file to another, removing it from source]")
            tools.add("'delete_code_chunk'(path, codeChunk, deleteAllOccurrences?: boolean) [Delete a specific code block or chunk from a file without leaving corrupt syntax or excessive empty lines]")
            tools.add("'delete_file'(path)")
            tools.add("'scan_dir'(path: folder name)")
            tools.add("'global_search'(query)")
            tools.add("'generate_image'(prompt, path?: 'assets/image.png', width?: 1024, height?: 1024, isLogo?: boolean)")
            tools.add("'generate_logo'(prompt, path?: 'assets/logo.png', width?: 512, height?: 512)")
            tools.add("'resize_image'(path, width?: number, height?: number, destinationPath?: string, format?: 'png'|'jpg'|'webp') [Resize, scale, convert, or compress an image]")
            tools.add("'create_todo_list'(query)")
            tools.add("'complete_todo_task'(query)")
            tools.add("'generate_pdf'(title, content, theme: 'modern'|'elegant'|'minimal'|'cyberpunk'|'dark', path?: optional filename)")
            tools.add("'generate_document'(type: 'pdf'|'html'|'md'|'txt', title, content, theme?: string, path?: string)")
            tools.add("'read_console_logs'(filter?: 'all'|'error'|'warn'|'info', query?: string, maxLines?: number) [Read Preview tab web console logs to diagnose runtime errors and logs]")
            tools.add("'read_build_logs'(filter?: 'all'|'error', query?: string, maxLines?: number) [Read Build tab GitHub Actions compilation and build logs to diagnose build failures]")
        }

        // Web / Internet / Browser inspection & cloning tools
        tools.add("'browser_search'(query [Search web or navigate URL])")
        tools.add("'browser_read'() [Read current webpage/article]")
        tools.add("'browser_snapshot'() [Scan all clickable, typable, and form elements on page with numeric tags [1], [2]...]")
        tools.add("'browser_controller'(action: 'click'|'type'|'scroll'|'select', elementIndex?: number, selector?: string, text?: string, pressEnter?: boolean, clearBefore?: boolean) [Control webpage like a human: click buttons, type in search/form fields, submit]")
        tools.add("'fetch_url'(url, targetFile?: string) [Fetch raw web page / article text]")
        tools.add("'clone_web_ui'(url, targetFilePath?: string) [Scrape website UI, extract design tokens and clone layout]")
        tools.add("'deep_clone_web_ui'(url, targetFilePath?: string) [Deeply clone website UI: extract complete DOM structure, color palette, typography tokens, and layout]")

        if (intents.contains(PromptIntent.WEB_AND_UI_INSPECTION)) {
            tools.add("'open_url'(url)")
            tools.add("'inspect_dom'(selector)")
            tools.add("'inspect_css'(selector)")
            tools.add("'get_computed_styles'(selector, properties)")
            tools.add("'take_screenshot'(path)")
            tools.add("'click'(selector)")
            tools.add("'type'(selector, text)")
            tools.add("'scroll'(direction, amount)")
            tools.add("'get_links'()")
            tools.add("'get_images'()")
            tools.add("'get_fonts'()")
            tools.add("'run_javascript'(script)")
            tools.add("'compare_screenshot'(targetImage)")
        }

        // MCP Tools
        if (intents.contains(PromptIntent.DATABASE_AND_MCP) || effectiveMcpServers.isNotEmpty()) {
            tools.add("'mcp_call_tool'(mcpServerId/mcpServerName, toolName, mcpArgsJson)")
            tools.add("'mcp_list_tools'(mcpServerId)")
            tools.add("'mcp_read_resource'(mcpServerId, resourceUri)")
        }

        tools.forEach { t -> sb.append("- ").append(t).append("\n") }

        // 7. Batch Operations & Formulating Logic Guideline
        sb.append("\n").append(AgentBatchExecutionManager.SYSTEM_BATCH_LOGIC_INSTRUCTION).append("\n")

        // 8. Output Format
        sb.append("""

=== MANDATORY FORMAT ===
Return ONLY raw JSON object.

Standard & Required Format (ALWAYS BATCH 2+ OPERATIONS):
{"thought":"Your formulated master plan for multiple upcoming operations","tools":[{"tool":"tool_1","arguments":{...}},{"tool":"tool_2","arguments":{...}}]}

Single Operation Format (ONLY for genuinely complex/unpredictable investigations):
{"thought":"Detailed reasoning explaining why this single step must be explored first","tool":"tool_name","arguments":{"path":"...","search":"...","replace":"...","message":"..."}}

- Call 'complete' with Markdown summary when all tasks are finished.
        """.trimIndent())

        return sb.toString()
    }
}
