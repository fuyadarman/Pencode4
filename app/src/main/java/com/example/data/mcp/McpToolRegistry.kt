package com.example.data.mcp

import com.example.data.McpServer
import com.example.data.McpToolInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RegisteredMcpTool(
    val serverId: String,
    val serverName: String,
    val tool: McpToolInfo
) {
    val globalToolName: String
        get() = "${serverName.lowercase().replace(" ", "_").replace("-", "_")}__${tool.name}"
}

class McpToolRegistry {

    private val _registeredTools = MutableStateFlow<List<RegisteredMcpTool>>(emptyList())
    val registeredTools: StateFlow<List<RegisteredMcpTool>> = _registeredTools.asStateFlow()

    /**
     * Register discovered tools from an MCP server
     */
    fun registerToolsForServer(server: McpServer, tools: List<McpToolInfo>) {
        val filtered = _registeredTools.value.filter { it.serverId != server.id }.toMutableList()
        for (tool in tools) {
            filtered.add(RegisteredMcpTool(serverId = server.id, serverName = server.name, tool = tool))
        }
        _registeredTools.value = filtered
    }

    /**
     * Unregister tools when server is disconnected or removed
     */
    fun unregisterServerTools(serverId: String) {
        _registeredTools.value = _registeredTools.value.filter { it.serverId != serverId }
    }

    /**
     * Find target server & tool by name or global tool name
     */
    fun findTool(toolNameOrGlobalName: String): RegisteredMcpTool? {
        return _registeredTools.value.find {
            it.globalToolName.equals(toolNameOrGlobalName, ignoreCase = true) ||
            it.tool.name.equals(toolNameOrGlobalName, ignoreCase = true)
        }
    }

    /**
     * Build system prompt documentation string for registered MCP tools
     */
    fun buildMcpToolsSystemPrompt(): String {
        return buildMcpToolsPromptForServers(emptyList())
    }

    /**
     * Build system prompt documentation string for specific enabled MCP servers
     */
    fun buildMcpToolsPromptForServers(servers: List<McpServer>): String {
        val toolsFromRegistered = _registeredTools.value
        val allTools = mutableListOf<RegisteredMcpTool>()
        allTools.addAll(toolsFromRegistered)

        for (s in servers) {
            for (t in s.availableTools) {
                if (allTools.none { it.serverId == s.id && it.tool.name == t.name }) {
                    allTools.add(RegisteredMcpTool(serverId = s.id, serverName = s.name, tool = t))
                }
            }
        }

        if (allTools.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("\n\n=== CONNECTED REMOTE MCP (MODEL CONTEXT PROTOCOL) TOOLS ===\n")
        sb.append("You have active access to the following live Remote MCP servers and tools. You can invoke them either using 'mcp_call_tool' (with mcpServerName, toolName, mcpArgsJson) or by directly specifying the tool name as the 'tool' in your JSON action:\n\n")

        for (reg in allTools) {
            sb.append("• Tool: '${reg.globalToolName}' (Alias: '${reg.tool.name}')\n")
            sb.append("  Server: ${reg.serverName}\n")
            if (!reg.tool.description.isNullOrBlank()) {
                sb.append("  Description: ${reg.tool.description}\n")
            }
            if (!reg.tool.parametersJsonSchema.isNullOrBlank()) {
                sb.append("  Input Schema: ${reg.tool.parametersJsonSchema}\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    /**
     * Find target server & tool across registered and enabled server instances
     */
    fun findToolInServers(toolNameOrGlobalName: String, servers: List<McpServer>): Pair<McpServer, McpToolInfo>? {
        val reg = findTool(toolNameOrGlobalName)
        if (reg != null) {
            val server = servers.find { it.id == reg.serverId } ?: McpServer(id = reg.serverId, name = reg.serverName, url = "")
            return Pair(server, reg.tool)
        }

        val cleanName = if (toolNameOrGlobalName.contains("__")) toolNameOrGlobalName.substringAfterLast("__") else toolNameOrGlobalName
        for (server in servers) {
            val tool = server.availableTools.find { 
                it.name.equals(toolNameOrGlobalName, ignoreCase = true) ||
                it.name.equals(cleanName, ignoreCase = true)
            }
            if (tool != null) {
                return Pair(server, tool)
            }
        }
        return null
    }
}
