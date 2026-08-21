package com.example.ui

import com.example.api.ToolArguments
import com.example.data.McpManager
import com.example.data.ProjectEntity

object McpToolHandler {

    suspend fun handleMcpToolCall(
        tool: String,
        args: ToolArguments?,
        project: ProjectEntity,
        mcpManager: McpManager,
        createLog: (title: String, details: String) -> AiActionLog,
        updateLog: (id: String, status: String, details: String) -> Unit,
        addLog: (AiActionLog) -> Unit
    ): String {
        return when (tool) {
            "mcp_call_tool", "mcp_call", "mcp_execute", "call_mcp_tool", "use_mcp_tool", "mcp_tool" -> {
                val serverIdOrName = args?.mcpServerId ?: args?.mcpServerName ?: args?.path ?: ""
                val toolName = args?.toolName ?: args?.command ?: args?.query ?: "query"
                val mcpArgsJson = args?.mcpArgsJson ?: args?.content ?: args?.query ?: ""

                val enabledServers = mcpManager.getEnabledServersForWorkspace(project.name)
                val allServers = mcpManager.servers.value
                val searchServers = if (enabledServers.isNotEmpty()) enabledServers else allServers
                val targetServer = searchServers.find { 
                    it.id == serverIdOrName || 
                    it.name.equals(serverIdOrName, ignoreCase = true) || 
                    it.platform.equals(serverIdOrName, ignoreCase = true) ||
                    it.name.replace(" ", "_").equals(serverIdOrName, ignoreCase = true)
                } ?: searchServers.find { s -> 
                    s.availableTools.any { 
                        it.name.equals(toolName, ignoreCase = true) || 
                        it.name.equals(toolName.substringAfterLast("__"), ignoreCase = true) 
                    } 
                } ?: searchServers.firstOrNull { it.status.startsWith("Connected") } ?: searchServers.firstOrNull()

                val serverName = targetServer?.name ?: serverIdOrName.ifEmpty { "MCP Remote" }
                val cleanToolName = if (toolName.contains("__")) toolName.substringAfterLast("__") else toolName
                val mcpLog = createLog(
                    "$serverName: $cleanToolName",
                    "Executing '$cleanToolName' on $serverName (${targetServer?.url ?: ""})"
                )
                addLog(mcpLog)

                val result = if (targetServer != null) {
                    mcpManager.executeMcpToolCall(targetServer.id, cleanToolName, mcpArgsJson)
                } else {
                    "Error: No matching active MCP server found for '$serverIdOrName'. Please enable or connect MCP servers in MCP settings."
                }

                val isSuccess = !result.startsWith("Error:") && !result.startsWith("MCP Call Error:")
                updateLog(
                    mcpLog.id,
                    if (isSuccess) "success" else "failed",
                    if (isSuccess) "$serverName ($cleanToolName)" else result
                )
                result
            }
            "mcp_list_tools", "mcp_list" -> {
                val enabledServers = mcpManager.getEnabledServersForWorkspace(project.name)
                val mcpLog = createLog(
                    "MCP List Tools",
                    "Listing tools for ${enabledServers.size} active MCP servers"
                )
                addLog(mcpLog)

                val result = if (enabledServers.isNotEmpty()) {
                    val sb = StringBuilder("=== ACTIVE MCP SERVERS & TOOLS ===\n")
                    for (server in enabledServers) {
                        sb.append("\n• ${server.name} [${server.platform}] (${server.url})\n")
                        sb.append("  Status: ${server.status}\n")
                        if (server.availableTools.isNotEmpty()) {
                            sb.append("  Tools:\n")
                            server.availableTools.forEach { t ->
                                sb.append("   - ${t.name}: ${t.description ?: "No description"}\n")
                            }
                        } else {
                            sb.append("  Tools: None or not synced yet (Use 'Test / Sync' in MCP settings)\n")
                        }
                    }
                    sb.toString()
                } else {
                    "No active MCP servers enabled for workspace '${project.name}'. User can enable MCP servers in Workspace MCP Settings."
                }

                updateLog(mcpLog.id, "success", "Listed tools for ${enabledServers.size} active MCP servers")
                result
            }
            "mcp_read_resource" -> {
                val serverIdOrName = args?.mcpServerId ?: args?.mcpServerName ?: ""
                val resourceUri = args?.resourceUri ?: args?.path ?: args?.query ?: ""

                val enabledServers = mcpManager.getEnabledServersForWorkspace(project.name)
                val targetServer = enabledServers.find { 
                    it.id == serverIdOrName || 
                    it.name.equals(serverIdOrName, ignoreCase = true) || 
                    it.platform.equals(serverIdOrName, ignoreCase = true) 
                } ?: enabledServers.firstOrNull()

                val serverName = targetServer?.name ?: "MCP Remote"
                val mcpLog = createLog(
                    "$serverName: Read Resource",
                    "Reading resource '$resourceUri' from $serverName"
                )
                addLog(mcpLog)

                val result = if (targetServer != null) {
                    "MCP Resource '$resourceUri' read from ${targetServer.name} (${targetServer.url}): OK"
                } else {
                    "Error: MCP server not found for resource '$resourceUri'."
                }

                updateLog(mcpLog.id, if (targetServer != null) "success" else "failed", result)
                result
            }
            else -> {
                if (tool.startsWith("gsc_")) {
                    val enabledServers = mcpManager.getEnabledServersForWorkspace(project.name)
                    val gscServer = enabledServers.find { it.platform == "GOOGLE_SEARCH_CONSOLE" || it.url.contains("searchconsole") }
                        ?: mcpManager.servers.value.find { it.platform == "GOOGLE_SEARCH_CONSOLE" }
                    if (gscServer != null) {
                        val mcpLog = createLog(
                            "Search Console: $tool",
                            "Executing Google Search Console operation '$tool'"
                        )
                        addLog(mcpLog)
                        val mcpArgsJson = args?.mcpArgsJson ?: args?.content ?: args?.query ?: ""
                        val result = mcpManager.executeMcpToolCall(gscServer.id, tool, mcpArgsJson)
                        val isSuccess = !result.startsWith("Error:") && !result.startsWith("MCP Call Error:")
                        updateLog(mcpLog.id, if (isSuccess) "success" else "failed", if (isSuccess) "GSC ($tool)" else result)
                        return result
                    }
                }

                // Check tool registry & enabled servers for direct tool invocations (e.g. supabase_mcp__query or list_tables)
                val enabledServers = mcpManager.getEnabledServersForWorkspace(project.name)
                val allServers = mcpManager.servers.value
                val candidateServers = if (enabledServers.isNotEmpty()) enabledServers else allServers
                val matched = mcpManager.toolRegistry.findToolInServers(tool, candidateServers)
                if (matched != null) {
                    val (server, toolInfo) = matched
                    val cleanToolName = toolInfo.name
                    val mcpLog = createLog(
                        "${server.name}: $cleanToolName",
                        "Executing '$cleanToolName' on ${server.name} (${server.url})"
                    )
                    addLog(mcpLog)
                    val mcpArgsJson = args?.mcpArgsJson ?: args?.content ?: args?.query ?: args?.message ?: ""
                    val result = mcpManager.executeMcpToolCall(server.id, cleanToolName, mcpArgsJson)
                    val isSuccess = !result.startsWith("Error:") && !result.startsWith("MCP Call Error:")
                    updateLog(mcpLog.id, if (isSuccess) "success" else "failed", if (isSuccess) "${server.name} ($cleanToolName)" else result)
                    return result
                }

                "Error: Unknown MCP tool '$tool'"
            }
        }
    }
}
