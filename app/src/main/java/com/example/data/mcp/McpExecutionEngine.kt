package com.example.data.mcp

import com.example.api.ToolArguments
import com.example.data.McpServer
import com.example.data.McpToolInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * High-reliability MCP Execution & Resolution Engine.
 * Normalizes tool invocation arguments, server matching, and payload formatting.
 */
object McpExecutionEngine {

    /**
     * Resolves the best-matching MCP Server for a tool call.
     * Prioritizes:
     * 1. Prompt-selected servers
     * 2. Server ID / Name match
     * 3. Server containing the tool
     * 4. Connected servers
     * 5. Fallback first server
     */
    fun resolveTargetServer(
        serverIdOrName: String,
        toolName: String,
        allServers: List<McpServer>,
        selectedServerIds: Set<String> = emptySet(),
        enabledServers: List<McpServer> = emptyList()
    ): McpServer? {
        val cleanToolName = cleanToolName(toolName)

        // 1. If explicit serverId or Name provided
        if (serverIdOrName.isNotBlank()) {
            val direct = allServers.find { s ->
                s.id.equals(serverIdOrName, ignoreCase = true) ||
                s.name.equals(serverIdOrName, ignoreCase = true) ||
                s.platform.equals(serverIdOrName, ignoreCase = true) ||
                s.name.replace(" ", "_").equals(serverIdOrName, ignoreCase = true) ||
                s.name.replace("-", "_").equals(serverIdOrName, ignoreCase = true)
            }
            if (direct != null) return direct
        }

        // 2. Search prioritized candidate servers (selected in prompt > enabled in workspace > all)
        val candidatePools = listOf(
            allServers.filter { selectedServerIds.contains(it.id) },
            enabledServers,
            allServers
        )

        for (pool in candidatePools) {
            if (pool.isEmpty()) continue
            val matchedByTool = pool.find { s ->
                s.availableTools.any { t ->
                    t.name.equals(cleanToolName, ignoreCase = true) ||
                    t.name.equals(toolName, ignoreCase = true)
                }
            }
            if (matchedByTool != null) return matchedByTool
        }

        // 3. Platform heuristic from tool name prefix
        val platformPrefixMatch = when {
            cleanToolName.startsWith("supabase_") -> allServers.find { it.platform == "SUPABASE" }
            cleanToolName.startsWith("cloudflare_") -> allServers.find { it.platform == "CLOUDFLARE" }
            cleanToolName.startsWith("vercel_") -> allServers.find { it.platform == "VERCEL" }
            else -> null
        }
        if (platformPrefixMatch != null) return platformPrefixMatch

        // 4. Return first active/connected or available server in selection
        return candidatePools.firstOrNull { it.isNotEmpty() }?.firstOrNull { it.status.startsWith("Connected") || it.status == "Ready" }
            ?: candidatePools.firstOrNull { it.isNotEmpty() }?.firstOrNull()
    }

    /**
     * Cleans tool name from namespace prefixes (e.g., 'supabase_mcp__query' -> 'query')
     */
    fun cleanToolName(rawName: String): String {
        return if (rawName.contains("__")) rawName.substringAfterLast("__") else rawName
    }

    /**
     * Extracts and preserves complete JSON arguments for an MCP tool call.
     */
    fun extractMcpPayload(args: ToolArguments?): String {
        if (args == null) return "{}"

        val json = JSONObject()

        // 1. If explicit mcpArgsJson string exists, populate base object
        if (!args.mcpArgsJson.isNullOrBlank()) {
            try {
                val parsed = JSONObject(args.mcpArgsJson.trim())
                parsed.keys().forEach { k -> json.put(k, parsed.get(k)) }
            } catch (_: Exception) {
                json.put("query", args.mcpArgsJson.trim())
            }
        }

        // 2. Explicitly transfer all known and critical fields from ToolArguments
        if (!args.code.isNullOrBlank() && !json.has("code")) {
            json.put("code", args.code)
        }
        if (!args.script.isNullOrBlank() && !json.has("script")) {
            json.put("script", args.script)
        }
        if (!args.command.isNullOrBlank() && !json.has("command")) {
            json.put("command", args.command)
        }
        if (!args.query.isNullOrBlank() && !json.has("query")) {
            json.put("query", args.query)
        }
        if (!args.content.isNullOrBlank() && !json.has("content")) {
            json.put("content", args.content)
        }
        if (!args.path.isNullOrBlank() && !json.has("path")) {
            json.put("path", args.path)
        }
        if (!args.targetFile.isNullOrBlank() && !json.has("targetFile")) {
            json.put("targetFile", args.targetFile)
        }
        if (!args.url.isNullOrBlank() && !json.has("url")) {
            json.put("url", args.url)
        }
        if (!args.name.isNullOrBlank() && !json.has("name")) {
            json.put("name", args.name)
        }
        if (!args.text.isNullOrBlank() && !json.has("text")) {
            json.put("text", args.text)
        }
        if (!args.message.isNullOrBlank() && !json.has("message")) {
            json.put("message", args.message)
        }
        if (!args.prompt.isNullOrBlank() && !json.has("prompt")) {
            json.put("prompt", args.prompt)
        }

        // 3. Fallback for 'execute' tool calls: if code is still empty, map from script/command/content/query
        if (!json.has("code")) {
            val fallbackCode = args.code
                ?: args.script
                ?: args.command
                ?: args.content
                ?: args.query
            if (!fallbackCode.isNullOrBlank()) {
                json.put("code", fallbackCode)
            }
        }

        if (json.length() > 0) {
            return json.toString()
        }

        return "{}"
    }

    private fun normalizeJsonString(jsonStr: String): String {
        val trimmed = jsonStr.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        return try {
            JSONObject(trimmed).toString()
        } catch (_: Exception) {
            JSONObject().apply { put("query", trimmed) }.toString()
        }
    }
}
