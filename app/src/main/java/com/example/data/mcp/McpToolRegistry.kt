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
        val tools = _registeredTools.value
        if (tools.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("\n=== EXTERNAL MCP (MODEL CONTEXT PROTOCOL) REMOTE TOOLS ===\n")
        sb.append("(Note: These remote MCP tools are separate from your local system tools like terminal, file_editor, apk_builder).\n")

        for (reg in tools) {
            sb.append("- Tool: ${reg.globalToolName} (Server: ${reg.serverName})\n")
            if (!reg.tool.description.isNullOrBlank()) {
                sb.append("  Description: ${reg.tool.description}\n")
            }
            if (!reg.tool.parametersJsonSchema.isNullOrBlank()) {
                sb.append("  Parameters Schema: ${reg.tool.parametersJsonSchema}\n")
            }
        }
        return sb.toString()
    }
}
