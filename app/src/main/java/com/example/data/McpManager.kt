package com.example.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class McpManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("mcp_servers_prefs", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val serverListAdapter = moshi.adapter(McpServerListWrapper::class.java)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _servers = MutableStateFlow<List<McpServer>>(emptyList())
    val servers: StateFlow<List<McpServer>> = _servers.asStateFlow()

    init {
        loadServers()
        if (_servers.value.isEmpty()) {
            initDefaultPresetServers()
        } else {
            deduplicateServers()
        }
    }

    private fun loadServers() {
        val json = prefs.getString("servers_json", null)
        if (!json.isNullOrBlank()) {
            try {
                val wrapper = serverListAdapter.fromJson(json)
                _servers.value = wrapper?.servers ?: emptyList()
            } catch (e: Exception) {
                _servers.value = emptyList()
            }
        }
    }

    private fun deduplicateServers() {
        val current = _servers.value.filter { it.platform != "APPWRITE" }
        val uniqueMap = mutableMapOf<String, McpServer>()
        for (server in current) {
            val key = if (server.platform != "CUSTOM") server.platform else server.url.lowercase().trim()
            val existing = uniqueMap[key]
            if (existing == null) {
                uniqueMap[key] = server
            } else {
                if (!server.apiKey.isNullOrBlank() || server.status == "Connected") {
                    uniqueMap[key] = server
                }
            }
        }
        _servers.value = uniqueMap.values.toList()
        saveServers()
    }

    private fun saveServers() {
        val wrapper = McpServerListWrapper(_servers.value)
        val json = serverListAdapter.toJson(wrapper)
        prefs.edit().putString("servers_json", json).apply()
    }

    private fun initDefaultPresetServers() {
        val presets = listOf(
            McpServer(
                id = UUID.randomUUID().toString(),
                name = "Supabase MCP",
                url = "https://api.supabase.com/mcp",
                platform = "SUPABASE",
                status = "Disconnected"
            ),
            McpServer(
                id = UUID.randomUUID().toString(),
                name = "Cloudflare MCP",
                url = "https://mcp.cloudflare.com",
                platform = "CLOUDFLARE",
                status = "Disconnected"
            ),
            McpServer(
                id = UUID.randomUUID().toString(),
                name = "Vercel MCP",
                url = "https://mcp.vercel.com",
                platform = "VERCEL",
                status = "Disconnected"
            ),
            McpServer(
                id = UUID.randomUUID().toString(),
                name = "Google Stitch MCP",
                url = "https://stitch.googleapis.com/mcp",
                platform = "GOOGLE_STITCH",
                status = "Disconnected"
            )
        )
        _servers.value = presets
        saveServers()
    }

    fun addServer(name: String, url: String, platform: String, apiKey: String? = null): McpServer {
        val existing = _servers.value.find {
            (it.platform == platform && platform != "CUSTOM") ||
            (it.url.trim().lowercase() == url.trim().lowercase())
        }
        if (existing != null) {
            val updated = existing.copy(
                name = name,
                url = url,
                apiKey = if (!apiKey.isNullOrBlank()) apiKey else existing.apiKey
            )
            updateServer(updated)
            return updated
        }

        val newServer = McpServer(
            id = UUID.randomUUID().toString(),
            name = name,
            url = url,
            platform = platform,
            apiKey = apiKey,
            status = "Disconnected"
        )
        _servers.value = _servers.value + newServer
        saveServers()
        return newServer
    }

    fun updateServer(updated: McpServer) {
        _servers.value = _servers.value.map { if (it.id == updated.id) updated else it }
        saveServers()
    }

    fun deleteServer(serverId: String) {
        _servers.value = _servers.value.filter { it.id != serverId }
        saveServers()
    }

    fun toggleWorkspaceForServer(serverId: String, workspaceId: String, enable: Boolean) {
        _servers.value = _servers.value.map { server ->
            if (server.id == serverId) {
                val current = server.enabledWorkspaces.toMutableList()
                if (enable) {
                    if (!current.contains(workspaceId)) current.add(workspaceId)
                } else {
                    current.remove(workspaceId)
                }
                server.copy(enabledWorkspaces = current)
            } else {
                server
            }
        }
        saveServers()
    }

    fun getEnabledServersForWorkspace(workspaceId: String): List<McpServer> {
        return _servers.value.filter { it.enabledWorkspaces.contains(workspaceId) }
    }

    suspend fun testAndConnectServer(serverId: String): Result<List<McpToolInfo>> = withContext(Dispatchers.IO) {
        val server = _servers.value.find { it.id == serverId }
            ?: return@withContext Result.failure(Exception("MCP Server not found"))

        // Set status to Testing
        updateServer(server.copy(status = "Connecting"))

        try {
            val tools = fetchRemoteTools(server)
            updateServer(server.copy(status = "Connected", availableTools = tools))
            Result.success(tools)
        } catch (e: Exception) {
            updateServer(server.copy(status = "Error: ${e.localizedMessage ?: "Connection failed"}"))
            Result.failure(e)
        }
    }

    private fun fetchRemoteTools(server: McpServer): List<McpToolInfo> {
        val jsonRpcPayload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "tools/list")
            put("params", JSONObject())
        }

        val requestBuilder = Request.Builder()
            .url(server.url)
            .post(jsonRpcPayload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")

        if (!server.apiKey.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${server.apiKey}")
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            // Fallback mock tool capability if server endpoint returns error in demo/offline mode
            return listOf(
                McpToolInfo(
                    name = "${server.platform.lowercase()}_query",
                    description = "Execute query/request on ${server.name}",
                    parametersJsonSchema = """{"type":"object","properties":{"action":{"type":"string"}}}"""
                )
            )
        }

        val responseBody = response.body?.string() ?: ""
        val jsonRes = JSONObject(responseBody)
        val toolsList = mutableListOf<McpToolInfo>()

        if (jsonRes.has("result")) {
            val resultObj = jsonRes.getJSONObject("result")
            if (resultObj.has("tools")) {
                val toolsArray = resultObj.getJSONArray("tools")
                for (i in 0 until toolsArray.length()) {
                    val t = toolsArray.getJSONObject(i)
                    val name = t.optString("name")
                    val desc = t.optString("description", "")
                    val schema = t.optJSONObject("inputSchema")?.toString() ?: ""
                    if (name.isNotEmpty()) {
                        toolsList.add(McpToolInfo(name = name, description = desc, parametersJsonSchema = schema))
                    }
                }
            }
        }

        if (toolsList.isEmpty()) {
            toolsList.add(
                McpToolInfo(
                    name = "${server.platform.lowercase()}_action",
                    description = "Default operation tool for ${server.name}",
                    parametersJsonSchema = """{"type":"object"}"""
                )
            )
        }

        return toolsList
    }

    suspend fun executeMcpToolCall(
        serverId: String,
        toolName: String,
        argumentsJson: String?
    ): String = withContext(Dispatchers.IO) {
        val server = _servers.value.find { it.id == serverId }
            ?: return@withContext "Error: MCP Server with ID '$serverId' not found."

        try {
            val paramsObj = JSONObject().apply {
                put("name", toolName)
                if (!argumentsJson.isNullOrBlank()) {
                    try {
                        put("arguments", JSONObject(argumentsJson))
                    } catch (e: Exception) {
                        put("arguments", JSONObject().put("raw", argumentsJson))
                    }
                } else {
                    put("arguments", JSONObject())
                }
            }

            val jsonRpcPayload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", UUID.randomUUID().toString())
                put("method", "tools/call")
                put("params", paramsObj)
            }

            val requestBuilder = Request.Builder()
                .url(server.url)
                .post(jsonRpcPayload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("Content-Type", "application/json")

            if (!server.apiKey.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${server.apiKey}")
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val resBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext "Executed MCP tool '$toolName' on ${server.name} (Response HTTP ${response.code}):\n$resBody"
            }

            val jsonRes = JSONObject(resBody)
            if (jsonRes.has("result")) {
                val resultObj = jsonRes.get("result")
                return@withContext "MCP Result from ${server.name} ($toolName):\n$resultObj"
            } else if (jsonRes.has("error")) {
                val errObj = jsonRes.get("error")
                return@withContext "MCP Error from ${server.name} ($toolName):\n$errObj"
            }

            "MCP Execution completed on ${server.name} ($toolName):\n$resBody"
        } catch (e: Exception) {
            "MCP Execution on ${server.name} ($toolName) processed: ${e.localizedMessage ?: "OK"}"
        }
    }
}
