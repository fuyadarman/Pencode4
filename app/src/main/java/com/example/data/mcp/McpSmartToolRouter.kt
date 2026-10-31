package com.example.data.mcp

import android.util.Log
import com.example.data.McpServer
import com.example.data.McpToolInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * McpSmartToolRouter
 *
 * Implements modern Claude / Cursor / Devin style MCP architecture:
 * 1. Dynamic Discovery: Discovers tools on server connect via tools/list
 * 2. Tool Refresh: Refreshes registry on notifications/tools/list_changed or manual triggers
 * 3. Lazy Tool Loading: Filters out of 100+ tools down to the most relevant active subset for the prompt,
 *    and provides `search_mcp_tools` meta-tool for runtime dynamic on-demand tool discovery
 * 4. Tool Execution Validation: Validates incoming AI tool call arguments against inputSchema before execution
 */
object McpSmartToolRouter {

    private const val TAG = "McpSmartToolRouter"
    const val MAX_LAZY_EXPOSED_TOOLS = 25

    /**
     * Lazy Tool Loading: Selects the most relevant MCP tools for the given user prompt.
     * Prevents context exhaustion when 100+ tools are registered across multiple MCP servers.
     */
    fun selectRelevantTools(
        allRegisteredTools: List<RegisteredMcpTool>,
        prompt: String,
        maxTools: Int = MAX_LAZY_EXPOSED_TOOLS
    ): List<RegisteredMcpTool> {
        if (allRegisteredTools.size <= maxTools) {
            return allRegisteredTools
        }

        val promptLower = prompt.lowercase(Locale.ROOT)
        val tokens = promptLower.split(Regex("[^a-z0-9_]")).filter { it.length > 2 }.toSet()

        // Score each tool by relevance to user prompt
        val scored = allRegisteredTools.map { registered ->
            var score = 0
            val toolName = registered.tool.name.lowercase(Locale.ROOT)
            val serverName = registered.serverName.lowercase(Locale.ROOT)
            val description = (registered.tool.description ?: "").lowercase(Locale.ROOT)

            // Direct keywords in tool name or server name
            for (token in tokens) {
                if (toolName.contains(token)) score += 5
                if (serverName.contains(token)) score += 4
                if (description.contains(token)) score += 2
            }

            // Domain-specific boosts
            if (tokens.any { it in setOf("git", "github", "commit", "push", "pr", "repo", "workflow", "action", "branch") } &&
                serverName.contains("github")
            ) score += 10

            if (tokens.any { it in setOf("database", "db", "sql", "postgres", "table", "supabase", "auth", "storage") } &&
                serverName.contains("supabase")
            ) score += 10

            if (tokens.any { it in setOf("deploy", "worker", "dns", "kv", "r2", "cloudflare", "domain") } &&
                serverName.contains("cloudflare")
            ) score += 10

            if (tokens.any { it in setOf("deploy", "vercel", "project", "domain", "hosting", "production") } &&
                serverName.contains("vercel")
            ) score += 10

            if (tokens.any { it in setOf("search", "google", "sitemap", "analytics", "ranking", "seo", "gsc") } &&
                serverName.contains("search")
            ) score += 10

            // Always keep staple tools with high baseline priority
            if (toolName in setOf("push_files", "trigger_workflow", "execute_sql", "deploy_worker", "deploy_project", "search_mcp_tools")) {
                score += 3
            }

            registered to score
        }

        // Return top scoring tools, sorted by relevance
        val sorted = scored.sortedByDescending { it.second }.map { it.first }
        val topList = sorted.take(maxTools).toMutableList()

        // Always include a virtual search_mcp_tools tool so AI can lazily search all tools
        val searchTool = RegisteredMcpTool(
            serverId = "mcp_router_core",
            serverName = "MCP Router",
            tool = McpToolInfo(
                name = "search_mcp_tools",
                description = "Dynamically discover and search all available tools across all connected MCP servers by keyword.",
                parametersJsonSchema = """{"type":"object","properties":{"query":{"type":"string","description":"Keyword to search tools by"}},"required":["query"]}"""
            )
        )
        if (topList.none { it.tool.name == "search_mcp_tools" }) {
            topList.add(0, searchTool)
        }

        return topList
    }

    /**
     * Executes the `search_mcp_tools` lazy search query.
     */
    fun searchTools(query: String, allTools: List<RegisteredMcpTool>): String {
        val q = query.trim().lowercase(Locale.ROOT)
        val matched = allTools.filter {
            it.tool.name.contains(q, ignoreCase = true) ||
            it.serverName.contains(q, ignoreCase = true) ||
            (it.tool.description ?: "").contains(q, ignoreCase = true)
        }

        if (matched.isEmpty()) {
            return "No MCP tools found matching '$query'. Available servers: ${allTools.map { it.serverName }.distinct().joinToString()}"
        }

        val sb = StringBuilder("Found ${matched.size} matching MCP tools for '$query':\n")
        matched.take(15).forEach { reg ->
            sb.append("• Tool: '${reg.globalToolName}' (Alias: '${reg.tool.name}')\n")
            sb.append("  Server: ${reg.serverName}\n")
            if (!reg.tool.description.isNullOrBlank()) {
                sb.append("  Description: ${reg.tool.description}\n")
            }
            if (!reg.tool.parametersJsonSchema.isNullOrBlank()) {
                sb.append("  Schema: ${reg.tool.parametersJsonSchema}\n")
            }
            sb.append("\n")
        }
        return sb.toString().trim()
    }

    /**
     * Tool Execution Validation: Validates tool call arguments against the tool's inputSchema
     * before execution to prevent runtime crashes and provide actionable correction guidance.
     */
    fun validateToolExecution(
        toolName: String,
        argsJson: String?,
        schemaJson: String?
    ): ValidationResult {
        if (schemaJson.isNullOrBlank()) return ValidationResult.Valid

        try {
            val schema = JSONObject(schemaJson)
            val requiredProps = schema.optJSONArray("required") ?: JSONArray()
            val properties = schema.optJSONObject("properties") ?: JSONObject()

            val args = if (!argsJson.isNullOrBlank()) {
                try { JSONObject(argsJson) } catch (_: Exception) { JSONObject() }
            } else JSONObject()

            val missing = mutableListOf<String>()
            for (i in 0 until requiredProps.length()) {
                val reqKey = requiredProps.getString(i)
                // Relaxed workflow requirement for trigger_workflow
                if (toolName.contains("trigger_workflow") && reqKey == "workflow") {
                    continue
                }
                if (!args.has(reqKey) || args.optString(reqKey).isBlank()) {
                    missing.add(reqKey)
                }
            }

            if (missing.isNotEmpty()) {
                return ValidationResult.Invalid(
                    "Missing required parameter(s): ${missing.joinToString(", ")}. Please provide them and call '$toolName' again."
                )
            }

            return ValidationResult.Valid
        } catch (e: Exception) {
            Log.w(TAG, "Schema validation skipped due to schema parse error: ${e.message}")
            return ValidationResult.Valid
        }
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errorExplanation: String) : ValidationResult()
    }
}
