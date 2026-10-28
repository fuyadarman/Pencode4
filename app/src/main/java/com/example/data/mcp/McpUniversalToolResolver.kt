package com.example.data.mcp

import android.content.Context
import com.example.api.ToolArguments
import com.example.data.McpManager
import com.example.data.McpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * McpUniversalToolResolver
 * Audits, routes, normalizes, and reliably executes tool calls across EVERY connected or preset MCP:
 * 1. GitHub Platform MCP (all official & prefixed GitHub tools)
 * 2. Supabase MCP (PostgreSQL queries, schema migrations, table introspection, storage, auth)
 * 3. Cloudflare MCP (D1 SQL database, R2 storage, KV store, Workers deployment)
 * 4. Vercel MCP (deployments, environment variables)
 * 5. Google Search Console MCP (traffic analytics, sitemaps, URL inspection)
 * 6. Custom Remote MCP servers
 */
object McpUniversalToolResolver {

    /**
     * Determines whether the given tool invocation belongs to any known MCP server or protocol.
     */
    fun isMcpToolInvocation(
        toolName: String,
        mcpManager: McpManager,
        candidateServers: List<McpServer> = emptyList()
    ): Boolean {
        val clean = toolName.trim().lowercase()

        // 1. Explicit MCP command prefixes
        if (clean.startsWith("mcp_") || clean.startsWith("gsc_") || clean.contains("__")) return true

        // 2. GitHub tools (both official and prefixed)
        if (clean.startsWith("github_") || isGitHubTool(clean)) return true

        // 3. Cloudflare tools
        if (clean.startsWith("cloudflare_") || clean.startsWith("d1_") || clean.startsWith("r2_")) return true

        // 4. Supabase tools
        if (clean.startsWith("supabase_") || clean == "sql_query" || clean == "list_tables") return true

        // 5. Vercel tools
        if (clean.startsWith("vercel_")) return true

        // 6. Registered or active tools in any MCP server
        val pool = candidateServers.ifEmpty { mcpManager.servers.value }
        for (server in pool) {
            if (server.availableTools.any {
                it.name.equals(clean, ignoreCase = true) ||
                it.name.equals(clean.removePrefix("${server.platform.lowercase()}_"), ignoreCase = true)
            }) return true
        }

        return false
    }

    /**
     * Executes any MCP tool call with universal normalization and robust error resilience.
     */
    suspend fun executeUniversalMcpCall(
        context: Context,
        mcpManager: McpManager,
        toolName: String,
        args: ToolArguments?,
        projectName: String,
        selectedServerIds: Set<String> = emptySet()
    ): String = withContext(Dispatchers.IO) {
        val cleanToolName = toolName.trim().lowercase()
        val allServers = mcpManager.servers.value
        val enabledServers = mcpManager.getEnabledServersForWorkspace(projectName)

        // 1. Check if tool is a GitHub MCP tool
        if (cleanToolName.startsWith("github_") || isGitHubTool(cleanToolName)) {
            val githubProvider = GitHubMcpServerProvider(context)
            val parsedArgs = extractMapFromArgs(args)
            return@withContext githubProvider.executeTool(cleanToolName, parsedArgs)
        }

        // 2. Check Google Search Console MCP
        if (cleanToolName.startsWith("gsc_")) {
            val queryText = args?.query ?: args?.message ?: args?.search ?: args?.url ?: ""
            val jsonArgs = JSONObject().apply {
                put("siteUrl", args?.url ?: args?.path ?: "")
                put("startDate", args?.category ?: "30daysAgo")
                put("endDate", "today")
                put("inspectionUrl", queryText)
                if (args?.mcpArgsJson != null) {
                    try {
                        val parsed = JSONObject(args.mcpArgsJson)
                        parsed.keys().forEach { k -> put(k, parsed.get(k)) }
                    } catch (_: Exception) {}
                }
            }
            return@withContext mcpManager.gscService.executeTool("builtin-gsc", cleanToolName, jsonArgs.toString())
        }

        // 3. Find matching MCP server (Candidate pool prioritization: prompt selection > workspace enabled > all)
        val targetServer = McpExecutionEngine.resolveTargetServer(
            serverIdOrName = args?.mcpServerId ?: args?.mcpServerName ?: "",
            toolName = cleanToolName,
            allServers = allServers,
            selectedServerIds = selectedServerIds,
            enabledServers = enabledServers
        ) ?: when {
            cleanToolName.startsWith("cloudflare_") -> allServers.find { it.platform == "CLOUDFLARE" }
            cleanToolName.startsWith("supabase_") -> allServers.find { it.platform == "SUPABASE" }
            cleanToolName.startsWith("vercel_") -> allServers.find { it.platform == "VERCEL" }
            else -> allServers.firstOrNull { it.status.startsWith("Connected") } ?: allServers.firstOrNull()
        }

        if (targetServer == null) {
            return@withContext "Error: No matching MCP server found to handle tool '$toolName'. Please configure and connect your MCP server in the MCP tab."
        }

        // 4. Extract standardized payload JSON
        val mcpArgsJson = McpExecutionEngine.extractMcpPayload(args)
        val actualTool = McpExecutionEngine.cleanToolName(cleanToolName)

        mcpManager.executeMcpToolCall(
            serverId = targetServer.id,
            toolName = actualTool,
            argumentsJson = mcpArgsJson
        )
    }

    private fun isGitHubTool(name: String): Boolean {
        val knownGitHubTools = setOf(
            "list_repositories", "search_repositories", "create_repository", "get_file_contents",
            "create_or_update_file", "delete_file", "push_files", "list_branches", "create_branch",
            "list_commits", "list_issues", "get_issue", "create_issue", "add_issue_comment",
            "list_pull_requests", "create_pull_request", "search_code", "list_workflows",
            "trigger_workflow", "get_workflow_runs", "get_workflow_logs", "get_file", "view_file"
        )
        return knownGitHubTools.contains(name) ||
               knownGitHubTools.contains(name.removePrefix("github_")) ||
               knownGitHubTools.contains(name.removePrefix("github__"))
    }

    private fun extractMapFromArgs(args: ToolArguments?): Map<String, Any?> {
        if (args == null) return emptyMap()
        val map = mutableMapOf<String, Any?>()

        if (!args.mcpArgsJson.isNullOrBlank()) {
            try {
                val json = JSONObject(args.mcpArgsJson)
                json.keys().forEach { k -> map[k] = json.opt(k) }
            } catch (_: Exception) {}
        }

        if (!args.path.isNullOrBlank() && !map.containsKey("path")) map["path"] = args.path
        if (!args.targetFile.isNullOrBlank() && !map.containsKey("path")) map["path"] = args.targetFile
        if (!args.content.isNullOrBlank() && !map.containsKey("content")) map["content"] = args.content
        if (!args.code.isNullOrBlank() && !map.containsKey("content")) map["content"] = args.code
        if (!args.message.isNullOrBlank() && !map.containsKey("message")) map["message"] = args.message
        if (!args.query.isNullOrBlank() && !map.containsKey("query")) map["query"] = args.query
        if (!args.name.isNullOrBlank() && !map.containsKey("name")) map["name"] = args.name
        if (!args.title.isNullOrBlank() && !map.containsKey("title")) map["title"] = args.title
        if (!args.url.isNullOrBlank() && !map.containsKey("url")) map["url"] = args.url
        if (!args.branch.isNullOrBlank() && !map.containsKey("branch")) map["branch"] = args.branch

        return map
    }
}
