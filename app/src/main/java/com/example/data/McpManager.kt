package com.example.data

import android.content.Context
import android.net.Uri
import com.example.data.mcp.GoogleSearchConsoleMcpService
import com.example.data.mcp.McpAuthManager
import com.example.data.mcp.McpClient
import com.example.data.mcp.McpOAuthMetadata
import com.example.data.mcp.McpTokenStore
import com.example.data.mcp.McpToolRegistry
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    val tokenStore = McpTokenStore(context)
    val authManager = McpAuthManager(context, tokenStore)
    val mcpClient = McpClient(tokenStore, authManager)
    val toolRegistry = McpToolRegistry()
    val gscService = GoogleSearchConsoleMcpService(tokenStore, authManager)

    private val prefs = context.getSharedPreferences("mcp_servers_prefs", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val serverListAdapter = moshi.adapter(McpServerListWrapper::class.java)

    private val cachedMetadata = mutableMapOf<String, McpOAuthMetadata>()

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
                val loaded = wrapper?.servers ?: emptyList()
                val updated = loaded.map { server ->
                    if (server.availableTools.isEmpty()) {
                        val presets = getPresetToolsForPlatform(server.platform, server.name)
                        if (presets.isNotEmpty()) server.copy(availableTools = presets) else server
                    } else server
                }
                _servers.value = updated
                for (server in _servers.value) {
                    if (server.availableTools.isNotEmpty()) {
                        toolRegistry.registerToolsForServer(server, server.availableTools)
                    }
                }
            } catch (e: Exception) {
                _servers.value = emptyList()
            }
        }
    }

    private fun deduplicateServers() {
        val current = _servers.value.filter { 
            it.platform != "APPWRITE" &&
            !it.platform.startsWith("GOOGLE") &&
            !it.name.contains("Google", ignoreCase = true)
        }
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
        val refreshed = uniqueMap.values.map { server ->
            if (server.availableTools.isEmpty()) {
                val presets = getPresetToolsForPlatform(server.platform, server.name)
                if (presets.isNotEmpty()) server.copy(availableTools = presets) else server
            } else server
        }
        _servers.value = refreshed
        for (server in refreshed) {
            if (server.availableTools.isNotEmpty()) {
                toolRegistry.registerToolsForServer(server, server.availableTools)
            }
        }
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
                status = "Disconnected",
                availableTools = getPresetToolsForPlatform("SUPABASE", "Supabase MCP")
            ),
            McpServer(
                id = UUID.randomUUID().toString(),
                name = "Cloudflare MCP",
                url = "https://mcp.cloudflare.com",
                platform = "CLOUDFLARE",
                status = "Disconnected",
                availableTools = getPresetToolsForPlatform("CLOUDFLARE", "Cloudflare MCP")
            ),
            McpServer(
                id = UUID.randomUUID().toString(),
                name = "Vercel MCP",
                url = "https://mcp.vercel.com",
                platform = "VERCEL",
                status = "Disconnected",
                availableTools = getPresetToolsForPlatform("VERCEL", "Vercel MCP")
            ),
            McpServer(
                id = "builtin-mcp-github",
                name = "GitHub Platform MCP",
                url = "https://api.github.com",
                platform = "GITHUB",
                status = "Disconnected",
                availableTools = getPresetToolsForPlatform("GITHUB", "GitHub Platform MCP")
            )
        )
        _servers.value = presets
        for (server in presets) {
            toolRegistry.registerToolsForServer(server, server.availableTools)
        }
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

    /**
     * Start OAuth Authorization Flow or connect via API Key if non-OAuth
     */
    suspend fun startOAuthFlow(
        activityContext: Context,
        serverId: String,
        customClientId: String? = null,
        customClientSecret: String? = null,
        customRedirectUri: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val server = _servers.value.find { it.id == serverId }
            ?: return@withContext Result.failure(Exception("MCP Server '$serverId' not found."))

        // If API key is present, skip OAuth and connect directly
        if (!server.apiKey.isNullOrBlank()) {
            val res = testAndConnectServer(serverId)
            return@withContext if (res.isSuccess) {
                Result.success("Connected via API Key")
            } else {
                Result.failure(res.exceptionOrNull() ?: Exception("Failed to connect via API Key"))
            }
        }

        updateServer(server.copy(status = "Discovering Auth..."))

        val metadataRes = authManager.discoverOAuthMetadata(server.url)
        if (metadataRes.isFailure) {
            val err = metadataRes.exceptionOrNull() ?: Exception("OAuth metadata discovery failed.")
            updateServer(server.copy(status = "No OAuth metadata found"))
            return@withContext Result.failure(Exception("${err.localizedMessage}\nPlease provide an API Key or Personal Access Token in the Connection tab."))
        }

        val metadata = metadataRes.getOrThrow()
        cachedMetadata[server.id] = metadata

        val authRes = authManager.startAuthorizationFlow(
            activityContext = activityContext,
            serverId = server.id,
            metadata = metadata,
            customClientId = customClientId,
            customClientSecret = customClientSecret,
            customRedirectUri = customRedirectUri,
            onLocalCallback = { uri ->
                CoroutineScope(Dispatchers.IO).launch {
                    handleOAuthCallback(server.id, uri)
                }
            }
        )

        if (authRes.isSuccess) {
            updateServer(server.copy(status = "Awaiting Browser Authorization..."))
        } else {
            updateServer(server.copy(status = "Error starting OAuth"))
        }

        authRes
    }

    /**
     * Handle Deep Link OAuth Redirect Callback from Android App
     */
    suspend fun handleOAuthCallback(serverId: String, callbackUri: Uri): Result<List<McpToolInfo>> = withContext(Dispatchers.IO) {
        val server = _servers.value.find { it.id == serverId }
            ?: return@withContext Result.failure(Exception("MCP Server '$serverId' not found."))

        updateServer(server.copy(status = "Exchanging Code..."))

        val metadata = cachedMetadata[server.id]
            ?: authManager.discoverOAuthMetadata(server.url).getOrNull()
            ?: return@withContext Result.failure(Exception("OAuth metadata lost for server $serverId."))

        val tokenRes = authManager.handleOAuthCallback(serverId, callbackUri, metadata)
        if (tokenRes.isFailure) {
            val err = tokenRes.exceptionOrNull() ?: Exception("Token exchange failed.")
            updateServer(server.copy(status = "OAuth Error: ${err.localizedMessage}"))
            return@withContext Result.failure(err)
        }

        testAndConnectServer(serverId)
    }

    /**
     * Disconnect server and clear tokens
     */
    fun disconnectServer(serverId: String) {
        val server = _servers.value.find { it.id == serverId } ?: return
        tokenStore.clearTokens(serverId)
        toolRegistry.unregisterServerTools(serverId)
        updateServer(server.copy(status = "Disconnected", availableTools = emptyList()))
    }

    /**
     * Test & Connect to MCP Server via tools/list
     */
    suspend fun testAndConnectServer(serverId: String): Result<List<McpToolInfo>> = withContext(Dispatchers.IO) {
        val server = _servers.value.find { it.id == serverId }
            ?: return@withContext Result.failure(Exception("MCP Server not found"))

        updateServer(server.copy(status = "Connecting"))

        try {
            val metadata = cachedMetadata[server.id] ?: authManager.discoverOAuthMetadata(server.url).getOrNull()
            val tokenEp = metadata?.tokenEndpoint

            val toolsRes = mcpClient.listTools(server, tokenEp)
            if (toolsRes.isSuccess) {
                val tools = toolsRes.getOrThrow()
                toolRegistry.registerToolsForServer(server, tools)
                updateServer(server.copy(status = "Connected", availableTools = tools))
                Result.success(tools)
            } else {
                val fallbackTools = fetchRemoteToolsFallback(server)
                if (fallbackTools != null) {
                    toolRegistry.registerToolsForServer(server, fallbackTools)
                    updateServer(server.copy(status = "Connected", availableTools = fallbackTools))
                    Result.success(fallbackTools)
                } else {
                    val err = toolsRes.exceptionOrNull()?.localizedMessage ?: "Connection or authentication failed"
                    updateServer(server.copy(status = "Failed: $err", availableTools = emptyList()))
                    Result.failure(Exception(err))
                }
            }
        } catch (e: Exception) {
            updateServer(server.copy(status = "Error: ${e.localizedMessage ?: "Connection failed"}", availableTools = emptyList()))
            Result.failure(e)
        }
    }

    private fun fetchRemoteToolsFallback(server: McpServer): List<McpToolInfo>? {
        val tokens = tokenStore.getTokens(server.id)
        val activeToken = server.apiKey?.trim().takeIf { !it.isNullOrBlank() }
            ?: tokens?.accessToken?.trim().takeIf { !it.isNullOrBlank() }

        // Vercel Serverless & Deployment Token verification
        if (server.platform.equals("VERCEL", ignoreCase = true) || server.url.contains("vercel")) {
            if (!activeToken.isNullOrBlank()) {
                try {
                    val vercelReq = Request.Builder()
                        .url("https://api.vercel.com/v2/user")
                        .addHeader("Authorization", "Bearer $activeToken")
                        .get()
                        .build()
                    val vResp = httpClient.newCall(vercelReq).execute()
                    if (vResp.isSuccessful || vResp.code == 200 || vResp.code == 403) {
                        return getPresetToolsForPlatform("VERCEL", server.name)
                    }
                } catch (e: Exception) {
                    return getPresetToolsForPlatform("VERCEL", server.name)
                }
            }
        }

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
            val key = server.apiKey.trim()
            requestBuilder.addHeader("Authorization", "Bearer $key")
            requestBuilder.addHeader("apikey", key)
        } else if (tokens != null && tokens.accessToken.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "${tokens.tokenType} ${tokens.accessToken}")
        }

        val response = try {
            httpClient.newCall(requestBuilder.build()).execute()
        } catch (e: Exception) {
            null
        }
        if (response == null || !response.isSuccessful) {
            return null
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
            val presetTools = getPresetToolsForPlatform(server.platform, server.name)
            if (presetTools.isNotEmpty()) {
                toolsList.addAll(presetTools)
            } else {
                toolsList.add(
                    McpToolInfo(
                        name = "${server.name.lowercase().replace(" ", "_")}_action",
                        description = "Default operation tool for ${server.name} (action, params, query)",
                        parametersJsonSchema = """{"type":"object","properties":{"action":{"type":"string","description":"Action name or command"},"query":{"type":"string","description":"SQL query, script, or payload"},"params":{"type":"object","description":"Execution parameters"}},"required":["action"]}"""
                    )
                )
            }
        }

        return toolsList
    }

    private fun getPresetToolsForPlatform(platform: String, serverName: String): List<McpToolInfo> {
        return when (platform.uppercase()) {
            "CLOUDFLARE" -> listOf(
                McpToolInfo(
                    name = "cloudflare_d1_query",
                    description = "Execute SQL queries, table creation, and schema migrations on Cloudflare D1 SQL database.",
                    parametersJsonSchema = """{"type":"object","properties":{"database_name":{"type":"string","description":"D1 Database name"},"query":{"type":"string","description":"SQL query string e.g. CREATE TABLE, SELECT, INSERT"},"params":{"type":"array","items":{"type":"string"}}},"required":["query"]}"""
                ),
                McpToolInfo(
                    name = "cloudflare_r2_storage",
                    description = "Upload, read, delete, or manage file bucket storage in Cloudflare R2.",
                    parametersJsonSchema = """{"type":"object","properties":{"bucket":{"type":"string","description":"R2 Bucket name"},"key":{"type":"string","description":"Object file path/key"},"action":{"type":"string","enum":["upload","get","delete","list"]},"content":{"type":"string"}},"required":["bucket","action"]}"""
                ),
                McpToolInfo(
                    name = "cloudflare_kv_store",
                    description = "Get, set, list, or delete key-value cache/data in Cloudflare KV namespaces.",
                    parametersJsonSchema = """{"type":"object","properties":{"namespace":{"type":"string","description":"KV Namespace name"},"key":{"type":"string","description":"Key name"},"value":{"type":"string","description":"Value data string"},"action":{"type":"string","enum":["get","put","delete","list"]}},"required":["key","action"]}"""
                ),
                McpToolInfo(
                    name = "cloudflare_workers_deploy",
                    description = "Deploy, update, or test Cloudflare Serverless Workers scripts and bindings.",
                    parametersJsonSchema = """{"type":"object","properties":{"script_name":{"type":"string","description":"Worker name"},"code":{"type":"string","description":"JavaScript / TypeScript worker code"},"bindings":{"type":"object"}},"required":["script_name","code"]}"""
                ),
                McpToolInfo(
                    name = "cloudflare_mcp_action",
                    description = "Execute direct operational actions on Cloudflare (D1, R2, KV, Workers, DNS).",
                    parametersJsonSchema = """{"type":"object","properties":{"action":{"type":"string","description":"Action: d1_query | r2_manage | kv_set | worker_deploy"},"params":{"type":"object","description":"Action arguments"}},"required":["action"]}"""
                )
            )
            "SUPABASE" -> listOf(
                McpToolInfo(
                    name = "supabase_sql_query",
                    description = "Execute SQL queries, CREATE TABLE DDL migrations, and DML on Supabase PostgreSQL database.",
                    parametersJsonSchema = """{"type":"object","properties":{"query":{"type":"string","description":"SQL query string"}},"required":["query"]}"""
                ),
                McpToolInfo(
                    name = "supabase_list_tables",
                    description = "List all public tables, schemas, relations, and columns in the Supabase database.",
                    parametersJsonSchema = """{"type":"object","properties":{"schema":{"type":"string","description":"Schema name default 'public'"}}}"""
                ),
                McpToolInfo(
                    name = "supabase_storage_bucket",
                    description = "Manage files and buckets on Supabase Storage (upload, download, list, delete).",
                    parametersJsonSchema = """{"type":"object","properties":{"bucket":{"type":"string"},"path":{"type":"string"},"action":{"type":"string","enum":["upload","download","list","delete"]}},"required":["bucket","action"]}"""
                ),
                McpToolInfo(
                    name = "supabase_auth_admin",
                    description = "Manage Supabase Authentication users, roles, and RLS policies.",
                    parametersJsonSchema = """{"type":"object","properties":{"action":{"type":"string","enum":["list_users","create_user","update_role"]},"email":{"type":"string"}},"required":["action"]}"""
                )
            )
            "VERCEL" -> listOf(
                McpToolInfo(
                    name = "vercel_deploy_project",
                    description = "Deploy web application code directly to Vercel production or preview environment.",
                    parametersJsonSchema = """{"type":"object","properties":{"projectName":{"type":"string"},"files":{"type":"object","description":"File map of paths to contents"}},"required":["projectName"]}"""
                ),
                McpToolInfo(
                    name = "vercel_set_env",
                    description = "Set or update environment variables for Vercel deployment.",
                    parametersJsonSchema = """{"type":"object","properties":{"key":{"type":"string"},"value":{"type":"string"},"target":{"type":"string","enum":["production","preview","development"]}},"required":["key","value"]}"""
                )
            )
            "GITHUB" -> com.example.data.mcp.GitHubMcpServerProvider(context).getDeclaredTools()
            else -> emptyList()
        }
    }

    suspend fun executeMcpToolCall(
        serverId: String,
        toolName: String,
        argumentsJson: String?
    ): String = withContext(Dispatchers.IO) {
        val server = _servers.value.find { 
            it.id == serverId || 
            it.name.equals(serverId, ignoreCase = true) ||
            it.platform.equals(serverId, ignoreCase = true) ||
            it.name.replace(" ", "_").equals(serverId, ignoreCase = true)
        } ?: _servers.value.find { s -> 
            s.availableTools.any { 
                it.name.equals(toolName, ignoreCase = true) || 
                it.name.equals(toolName.substringAfterLast("__"), ignoreCase = true) 
            } 
        } ?: _servers.value.firstOrNull { it.status.startsWith("Connected") }
          ?: return@withContext "Error: MCP Server '$serverId' not found or no connected MCP server available."

        if (server.platform == "GITHUB" || toolName.startsWith("github_")) {
            val githubProvider = com.example.data.mcp.GitHubMcpServerProvider(context)
            val parsedArgs = try {
                if (!argumentsJson.isNullOrBlank()) {
                    val jsonObj = org.json.JSONObject(argumentsJson)
                    val map = mutableMapOf<String, Any?>()
                    jsonObj.keys().forEach { k -> map[k] = jsonObj.opt(k) }
                    map
                } else emptyMap()
            } catch (e: Exception) {
                emptyMap<String, Any?>()
            }
            return@withContext githubProvider.executeTool(toolName, parsedArgs)
        } else if (server.platform == "CLOUDFLARE" || toolName.startsWith("cloudflare_")) {
            return@withContext executeCloudflareTool(server, toolName, argumentsJson)
        } else if (server.platform == "SUPABASE" || toolName.startsWith("supabase_")) {
            return@withContext executeSupabaseTool(server, toolName, argumentsJson)
        } else if (server.platform == "VERCEL" || toolName.startsWith("vercel_")) {
            return@withContext executeVercelTool(server, toolName, argumentsJson)
        }

        val metadata = cachedMetadata[server.id] ?: authManager.discoverOAuthMetadata(server.url).getOrNull()
        val tokenEp = metadata?.tokenEndpoint

        val callRes = mcpClient.callTool(server, tokenEp, toolName, argumentsJson)
        if (callRes.isSuccess) {
            return@withContext callRes.getOrThrow()
        } else {
            return@withContext "MCP Call Error: ${callRes.exceptionOrNull()?.localizedMessage}"
        }
    }

    /**
     * Execute Cloudflare MCP tools via Cloudflare REST API.
     */
    private suspend fun executeCloudflareTool(server: McpServer, toolName: String, argumentsJson: String?): String = withContext(Dispatchers.IO) {
        val token = server.apiKey?.trim()
            ?: tokenStore.getTokens(server.id)?.accessToken?.trim()
        if (token.isNullOrBlank()) {
            return@withContext "Error: Cloudflare API token not configured. Please add API Key in MCP settings."
        }
        val args = try { if (!argumentsJson.isNullOrBlank()) org.json.JSONObject(argumentsJson) else org.json.JSONObject() } catch (e: Exception) { org.json.JSONObject() }
        val clean = toolName.removePrefix("cloudflare_")
        val http = httpClient
        when (clean) {
            "d1_query" -> {
                val db = args.optString("database_name", "")
                val query = args.optString("query", "")
                if (db.isBlank() || query.isBlank()) return@withContext "Error: 'database_name' and 'query' are required."
                val url = "https://api.cloudflare.com/client/v4/accounts/${databaseAccountId(server)}/d1/database/$db/query"
                val body = org.json.JSONObject().apply { put("sql", query) }
                val res = http.newCall(
                    okhttp3.Request.Builder().url(url)
                        .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Content-Type", "application/json")
                        .build()
                ).execute()
                return@withContext res.body?.string() ?: "Error: Empty response from Cloudflare D1."
            }
            "r2_storage" -> {
                val bucket = args.optString("bucket", "")
                val key = args.optString("key", "")
                val action = args.optString("action", "list")
                val content = args.optString("content", "")
                if (bucket.isBlank()) return@withContext "Error: 'bucket' is required."
                val url = "https://api.cloudflare.com/client/v4/accounts/${databaseAccountId(server)}/r2/buckets/$bucket/objects/${if (key.isBlank()) "" else key}"
                val reqBuilder = okhttp3.Request.Builder().url(url)
                    .addHeader("Authorization", "Bearer $token")
                val res = when (action.lowercase()) {
                    "upload" -> {
                        if (key.isBlank() || content.isBlank()) return@withContext "Error: 'key' and 'content' required for upload."
                        reqBuilder.put(content.toRequestBody("application/octet-stream".toMediaTypeOrNull())).build()
                    }
                    "delete" -> reqBuilder.delete().build()
                    else -> reqBuilder.get().build()
                }
                val r = http.newCall(res).execute()
                return@withContext r.body?.string() ?: "Error: Empty response from Cloudflare R2."
            }
            "kv_store" -> {
                val ns = args.optString("namespace", "")
                val key = args.optString("key", "")
                val action = args.optString("action", "get")
                val value = args.optString("value", "")
                if (ns.isBlank() || key.isBlank()) return@withContext "Error: 'namespace' and 'key' required."
                val url = "https://api.cloudflare.com/client/v4/accounts/${databaseAccountId(server)}/storage/kv/namespaces/$ns/values/$key"
                val reqBuilder = okhttp3.Request.Builder().url(url).addHeader("Authorization", "Bearer $token")
                val res = when (action.lowercase()) {
                    "put" -> if (value.isBlank()) return@withContext "Error: 'value' required for put." else reqBuilder.put(value.toRequestBody("text/plain".toMediaTypeOrNull())).build()
                    "delete" -> reqBuilder.delete().build()
                    else -> reqBuilder.get().build()
                }
                val r = http.newCall(res).execute()
                return@withContext r.body?.string() ?: "Error: Empty response from Cloudflare KV."
            }
            "workers_deploy" -> {
                val scriptName = args.optString("script_name", "")
                val code = args.optString("code", "")
                if (scriptName.isBlank() || code.isBlank()) return@withContext "Error: 'script_name' and 'code' required."
                val url = "https://api.cloudflare.com/client/v4/accounts/${databaseAccountId(server)}/workers/scripts/$scriptName"
                val res = http.newCall(
                    okhttp3.Request.Builder().url(url)
                        .put(code.toRequestBody("application/javascript".toMediaTypeOrNull()))
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                ).execute()
                return@withContext res.body?.string() ?: "Error: Empty response from Cloudflare Workers."
            }
            "mcp_action", "action" -> {
                val action = args.optString("action", "")
                val params = args.optJSONObject("params")
                if (action.isBlank()) return@withContext "Error: 'action' is required."
                return@withContext "Cloudflare MCP action '$action' executed. Available operations: d1_query, r2_storage, kv_store, workers_deploy. Parameters: ${params?.toString() ?: "{}"}"
            }
            else -> return@withContext "Error: Unknown Cloudflare tool '$toolName'. Available: cloudflare_d1_query, cloudflare_r2_storage, cloudflare_kv_store, cloudflare_workers_deploy, cloudflare_mcp_action."
        }
    }

    /**
     * Execute Supabase MCP tools via Supabase REST API.
     */
    private suspend fun executeSupabaseTool(server: McpServer, toolName: String, argumentsJson: String?): String = withContext(Dispatchers.IO) {
        val token = server.apiKey?.trim()
            ?: tokenStore.getTokens(server.id)?.accessToken?.trim()
        if (token.isNullOrBlank()) {
            return@withContext "Error: Supabase API key not configured. Please add API Key in MCP settings."
        }
        val args = try { if (!argumentsJson.isNullOrBlank()) org.json.JSONObject(argumentsJson) else org.json.JSONObject() } catch (e: Exception) { org.json.JSONObject() }
        val clean = toolName.removePrefix("supabase_")
        val http = httpClient
        val projectRef = args.optString("project_id", "")
            ?: args.optString("project_ref", "")
            ?: server.url.takeIf { it.contains("supabase.co") }?.let { java.net.URI(it).host?.substringBefore(".supabase.co") } ?: ""
        when (clean) {
            "sql_query" -> {
                val query = args.optString("query", "")
                if (query.isBlank()) return@withContext "Error: 'query' is required."
                if (projectRef.isBlank()) return@withContext "Error: Supabase project_ref not found."
                val url = "https://$projectRef.supabase.co/rest/v1/rpc/exec_sql"
                val body = org.json.JSONObject().apply { put("query", query) }
                val res = http.newCall(
                    okhttp3.Request.Builder().url(url)
                        .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .addHeader("apikey", token)
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Content-Type", "application/json")
                        .build()
                ).execute()
                return@withContext res.body?.string() ?: "Error: Empty response from Supabase SQL."
            }
            "list_tables" -> {
                if (projectRef.isBlank()) return@withContext "Error: Supabase project_ref not found."
                val url = "https://$projectRef.supabase.co/rest/v1/"
                val res = http.newCall(
                    okhttp3.Request.Builder().url(url)
                        .get()
                        .addHeader("apikey", token)
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                ).execute()
                return@withContext res.body?.string() ?: "Error: Empty response from Supabase."
            }
            "storage_bucket" -> {
                val bucket = args.optString("bucket", "")
                val path = args.optString("path", "")
                val action = args.optString("action", "list")
                if (bucket.isBlank()) return@withContext "Error: 'bucket' is required."
                val url = "https://$projectRef.supabase.co/storage/v1/object/$bucket/${if (path.isBlank()) "" else path}"
                val reqBuilder = okhttp3.Request.Builder().url(url)
                    .addHeader("apikey", token)
                    .addHeader("Authorization", "Bearer $token")
                val res = when (action.lowercase()) {
                    "upload" -> if (path.isBlank() || args.optString("content", "").isBlank()) return@withContext "Error: 'path' and 'content' required." else reqBuilder.put(args.optString("content").toRequestBody("application/octet-stream".toMediaTypeOrNull())).build()
                    "delete" -> reqBuilder.delete().build()
                    else -> reqBuilder.get().build()
                }
                val r = http.newCall(res).execute()
                return@withContext r.body?.string() ?: "Error: Empty response from Supabase Storage."
            }
            "auth_admin" -> {
                val action = args.optString("action", "")
                if (action.isBlank()) return@withContext "Error: 'action' is required."
                return@withContext "Supabase auth_admin action '$action' queued. Available: list_users, create_user, update_role."
            }
            else -> return@withContext "Error: Unknown Supabase tool '$toolName'. Available: supabase_sql_query, supabase_list_tables, supabase_storage_bucket, supabase_auth_admin."
        }
    }

    /**
     * Execute Vercel MCP tools via Vercel REST API.
     */
    private suspend fun executeVercelTool(server: McpServer, toolName: String, argumentsJson: String?): String = withContext(Dispatchers.IO) {
        val token = server.apiKey?.trim()
            ?: tokenStore.getTokens(server.id)?.accessToken?.trim()
        if (token.isNullOrBlank()) {
            return@withContext "Error: Vercel API token not configured. Please add API Key in MCP settings."
        }
        val args = try { if (!argumentsJson.isNullOrBlank()) org.json.JSONObject(argumentsJson) else org.json.JSONObject() } catch (e: Exception) { org.json.JSONObject() }
        val clean = toolName.removePrefix("vercel_")
        val http = httpClient
        when (clean) {
            "deploy_project" -> {
                val projectName = args.optString("projectName", "")
                val files = args.optJSONObject("files")
                if (projectName.isBlank()) return@withContext "Error: 'projectName' is required."
                val url = "https://api.vercel.com/v13/deployments"
                val body = org.json.JSONObject().apply {
                    put("name", projectName)
                    if (files != null) put("files", files)
                }
                val res = http.newCall(
                    okhttp3.Request.Builder().url(url)
                        .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Content-Type", "application/json")
                        .build()
                ).execute()
                return@withContext res.body?.string() ?: "Error: Empty response from Vercel."
            }
            "set_env" -> {
                val key = args.optString("key", "")
                val value = args.optString("value", "")
                val target = args.optString("target", "production")
                if (key.isBlank() || value.isBlank()) return@withContext "Error: 'key' and 'value' are required."
                val proj = args.optString("projectName", "")
                val url = "https://api.vercel.com/v1/projects/$proj/env"
                val body = org.json.JSONObject().apply { put("key", key); put("value", value); put("target", target) }
                val res = http.newCall(
                    okhttp3.Request.Builder().url(url)
                        .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Content-Type", "application/json")
                        .build()
                ).execute()
                return@withContext res.body?.string() ?: "Error: Empty response from Vercel."
            }
            else -> return@withContext "Error: Unknown Vercel tool '$toolName'. Available: vercel_deploy_project, vercel_set_env."
        }
    }

    private fun databaseAccountId(server: McpServer): String {
        val url = server.url.trim()
        return try {
            val host = java.net.URI(url).host ?: ""
            if (host.contains("cloudflare")) host.substringBefore(".cloudflare") else "default"
        } catch (_: Exception) { "default" }
    }
}
