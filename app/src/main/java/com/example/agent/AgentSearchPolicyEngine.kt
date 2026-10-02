package com.example.agent

/**
 * AgentSearchPolicyEngine
 * 
 * Regulates search tool usage (grep, find, global_search) based on project size.
 * Prevents useless search calls and token waste on small projects (3-15 files)
 * while enabling deep symbol discovery on large enterprise codebases.
 */
object AgentSearchPolicyEngine {

    const val SMALL_PROJECT_FILE_THRESHOLD = 15

    fun isSmallProject(fileCount: Int): Boolean {
        return fileCount <= SMALL_PROJECT_FILE_THRESHOLD
    }

    /**
     * Builds targeted search directive for the agent system prompt.
     */
    fun buildSearchDirective(fileCount: Int): String {
        return if (isSmallProject(fileCount)) {
            "4. DIRECT TARGETING (Small Project - $fileCount files total): All project files are already visible in your workspace file tree above. Do NOT waste tokens running 'grep', 'global_search', or 'find'. Open the target file directly with 'read_file' or edit it immediately with 'edit_file'."
        } else {
            "4. CODE INSPECTION (Large Codebase - $fileCount files): Use 'global_search' or 'grep' to pinpoint relevant symbols or classes across directories before reading entire files."
        }
    }

    /**
     * Builds concise tool documentation for search tools tailored to project size.
     */
    fun formatSearchToolDoc(fileCount: Int): String {
        return if (isSmallProject(fileCount)) {
            "'global_search'(query) [or 'grep'(query) - Optional. Do NOT use on this small project as all files are already visible above]"
        } else {
            "'global_search'(query) [or 'grep'(query) - Fast grep across codebase to locate symbols in large projects]"
        }
    }
}
