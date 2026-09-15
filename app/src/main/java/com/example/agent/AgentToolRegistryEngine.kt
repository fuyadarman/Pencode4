package com.example.agent

/**
 * AgentToolRegistryEngine
 * Canonical registry of ALL available tools in PenCode.
 * When the AI calls 'list_all_tools', this provides the full, unabridged tool catalog
 * with parameters, syntax, and purpose.
 */
object AgentToolRegistryEngine {

    val ALL_TOOLS_DOCUMENTATION: String by lazy {
        """
        === PENCODE AI COMPLETE TOOLS REGISTRY ===
        You can execute ANY of the following tools in your 'tools' array or 'tool' field.

        [FILE & WORKSPACE OPERATIONS]
        • read_file(path: string) -> Reads the entire content of a file with line numbers.
        • read_file_range(path: string, startLine: int, endLine: int) -> Reads a specific line range.
        • multi_read_file(path: string, ranges: [{startLine, endLine}]) -> Reads multiple line ranges.
        • create_file(path: string, content: string) -> Creates a brand new file (rejected if file exists).
        • edit_file(path: string, search: string, replace: string) -> Surgical search and replace edit.
        • multi_edit_file(path: string, chunks: [{search, replace}]) -> Multiple non-contiguous edits.
        • patch_file(path: string, search: string, replace: string) -> Applies patch replacement.
        • append(path: string, content: string) -> Appends text to the end of a file.
        • delete_file(path: string) -> Deletes an existing file.
        • rename_file(oldPath: string, newPath: string) -> Renames a file in the workspace.
        • move_file(sourcePath: string, destinationPath: string) -> Moves a file to another path.
        • copy_file(path: string, destinationPath: string) -> Copies a file to a new location.
        • duplicate_file(path: string, count?: int, targetPaths?: [string]) -> Duplicates a file.
        • scan_dir(path: string) -> Scans and lists all files inside a specific directory.
        • global_search(query: string) -> Fast grep/search across all files in the project.

        [CODE CHUNK & BLOCK MANIPULATION]
        • delete_code_chunk(path: string, codeChunk: string, deleteAllOccurrences?: boolean) -> Deletes a code block from a file without leaving corrupt syntax or blank lines.
        • copy_code_chunk(sourcePath: string, targetPath: string, codeChunk: string, targetAnchor?: string, insertAt?: 'start'|'end'|'before'|'after'|'replace') -> Copies a code block into another file.
        • move_code_chunk(sourcePath: string, targetPath: string, codeChunk: string, targetAnchor?: string, insertAt?: string) -> Moves a code block from source file to target file.
        • transfer_code_chunk(sourcePath: string, targetPath: string, codeChunk: string, targetAnchor?: string, insertAt?: string, isMove?: boolean) -> Generalized code chunk transfer/copy/move.

        [WORKSPACE LOGS & ERROR DIAGNOSTICS]
        • read_preview_errors(query?: string, maxLines?: int) -> Specifically extracts ONLY errors, runtime exceptions, and console.error from the Preview tab.
        • read_build_errors(query?: string, maxLines?: int) -> Specifically extracts ONLY compilation failures, syntax errors, and fatal build errors from the Build tab GitHub Actions.
        • read_console_logs(filter?: 'all'|'error'|'warn'|'info', query?: string, maxLines?: int) -> Reads raw Preview tab web console logs.
        • read_build_logs(filter?: 'all'|'error', query?: string, maxLines?: int) -> Reads raw Build tab GitHub Actions compilation output.

        [CORE AGENT & SYSTEM CONTROL]
        • list_all_tools() -> Displays this complete list of all supported PenCode AI tools and commands.
        • learn_pattern(title: string, category: string, issue: string, solution: string, tags?: [string]) -> Memorizes a newly discovered fix pattern, architectural rule, or convention into persistent self-learning memory.
        • synthesize_skill(name: string, description: string, instructions: string, category?: string) -> Autonomous skill synthesis: registers a new specialized Agent Skill in PenCode.
        • recall_learned_patterns(query: string) -> Queries the self-learning memory for past bug fixes, rules, and architecture solutions.
        • ai_think(message: string) -> Formulates logic or plans steps before making changes.
        • complete(message: string) -> Completes task execution and returns final Markdown summary.
        • ask_user(question: string, options?: [string]) -> Prompts user for clarification or confirmation.
        • skill_check(query?: string) -> Queries active skills and instructions.
        • create_todo_list(query: string) -> Creates a task checklist.
        • complete_todo_task(query: string) -> Marks a checklist task complete.

        [VISUAL ASSETS & DOCUMENTS]
        • generate_image(prompt: string, path?: string, width?: int, height?: int) -> Generates visual assets via Pollinations AI.
        • generate_logo(prompt: string, path?: string, width?: int, height?: int) -> Generates app logo assets.
        • resize_image(path: string, width?: int, height?: int, destinationPath?: string, format?: 'png'|'jpg'|'webp') -> Scales, crops, or compresses image assets.
        • generate_pdf(title: string, content: string, theme?: 'modern'|'elegant'|'minimal'|'cyberpunk'|'dark', path?: string) -> Generates styled PDF documents.
        • generate_document(type: 'pdf'|'html'|'md'|'txt', title: string, content: string, theme?: string, path?: string) -> Generates documents.

        [WEB BROWSING & UI CLONING]
        • browser_search(query: string) -> Searches the web or navigates to a URL.
        • browser_read() -> Reads the current webpage text.
        • browser_snapshot() -> Takes an indexed snapshot of all interactive elements on the page.
        • browser_controller(action: 'click'|'type'|'scroll'|'select', elementIndex?: int, selector?: string, text?: string) -> Interacts with webpage elements.
        • fetch_url(url: string, targetFile?: string) -> Fetches raw webpage content.
        • clone_web_ui(url: string, targetFilePath?: string) -> Scrapes UI design and extracts tokens.
        • deep_clone_web_ui(url: string, targetFilePath?: string) -> Deep clones complete DOM, styles, and layouts.

        [MCP & INTEGRATIONS]
        • mcp_call_tool(mcpServerId: string, toolName: string, mcpArgsJson: string) -> Calls an MCP tool.
        • mcp_list_tools(mcpServerId: string) -> Lists tools available on an MCP server.
        • mcp_read_resource(mcpServerId: string, resourceUri: string) -> Reads an MCP resource.
        """.trimIndent()
    }

    /**
     * Formats concise tool listing for system instruction to save tokens.
     */
    fun getCompactToolList(): List<String> {
        return listOf(
            "'read_file'(path)",
            "'read_file_range'(path, startLine, endLine)",
            "'multi_read_file'(path, ranges:[{startLine,endLine}])",
            "'create_file'(path, content) [New files only]",
            "'edit_file'(path, search, replace)",
            "'multi_edit_file'(path, chunks:[{search,replace}])",
            "'delete_file'(path)",
            "'scan_dir'(path)",
            "'global_search'(query)",
            "'delete_code_chunk'(path, codeChunk)",
            "'copy_code_chunk'(sourcePath, targetPath, codeChunk, targetAnchor?)",
            "'move_code_chunk'(sourcePath, targetPath, codeChunk, targetAnchor?)",
            "'read_preview_errors'(query?, maxLines?) [ONLY preview runtime/console errors]",
            "'read_build_errors'(query?, maxLines?) [ONLY build/GitHub Action errors]",
            "'read_console_logs'(filter?, query?, maxLines?)",
            "'read_build_logs'(filter?, query?, maxLines?)",
            "'list_all_tools'() [View all tools & commands with full docs]",
            "'browser_search'(query)",
            "'browser_read'()",
            "'browser_snapshot'()",
            "'browser_controller'(action, elementIndex?, selector?, text?)",
            "'fetch_url'(url, targetFile?)",
            "'clone_web_ui'(url, targetFilePath?)",
            "'deep_clone_web_ui'(url, targetFilePath?)",
            "'generate_image'(prompt, path?)",
            "'generate_pdf'(title, content, theme?, path?)",
            "'ask_user'(question, options?)",
            "'ai_think'(message)",
            "'complete'(message)"
        )
    }
}
