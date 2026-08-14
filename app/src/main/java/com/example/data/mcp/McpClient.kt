package com.example.data.mcp

import com.example.data.McpServer
import com.example.data.McpToolInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class McpClient(
    private val tokenStore: McpTokenStore,
    private val authManager: McpAuthManager
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch authorization header if available and valid
     */
    private suspend fun getOrRefreshAuthHeader(server: McpServer, tokenEndpoint: String?): String? {
        // 1. Direct API key override
        if (!server.apiKey.isNullOrBlank()) {
            return "Bearer ${server.apiKey}"
        }

        // 2. Token store check
        val tokens = tokenStore.getTokens(server.id) ?: return null
        if (tokens.accessToken.isNotBlank() && !tokens.isExpired()) {
            return "${tokens.tokenType} ${tokens.accessToken}"
        }

        // 3. Refresh expired token
        if (tokens.refreshToken != null && !tokenEndpoint.isNullOrBlank()) {
            val refreshResult = authManager.refreshAccessToken(server.id, tokenEndpoint)
            if (refreshResult.isSuccess) {
                val newTokens = refreshResult.getOrNull()
                if (newTokens != null) {
                    return "${newTokens.tokenType} ${newTokens.accessToken}"
                }
            }
        }

        return if (tokens.accessToken.isNotBlank()) "${tokens.tokenType} ${tokens.accessToken}" else null
    }

    /**
     * Send JSON-RPC 2.0 Request
     */
    suspend fun sendJsonRpc(
        server: McpServer,
        tokenEndpoint: String?,
        method: String,
        params: JSONObject = JSONObject()
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val requestId = UUID.randomUUID().toString()
            val jsonRpcPayload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", requestId)
                put("method", method)
                put("params", params)
            }

            var authHeader = getOrRefreshAuthHeader(server, tokenEndpoint)

            fun buildRequest(auth: String?): Request {
                val b = Request.Builder()
                    .url(server.url)
                    .post(jsonRpcPayload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")

                if (!server.apiKey.isNullOrBlank()) {
                    val key = server.apiKey.trim()
                    b.addHeader("Authorization", "Bearer $key")
                    b.addHeader("X-Goog-Api-Key", key)
                    b.addHeader("x-api-key", key)
                } else if (!auth.isNullOrBlank()) {
                    b.addHeader("Authorization", auth)
                }
                return b.build()
            }

            var response = httpClient.newCall(buildRequest(authHeader)).execute()

            // 401 Handling: Retry once after token refresh
            if (response.code == 401 && !tokenEndpoint.isNullOrBlank()) {
                val refreshRes = authManager.refreshAccessToken(server.id, tokenEndpoint)
                if (refreshRes.isSuccess) {
                    val freshToken = refreshRes.getOrNull()
                    if (freshToken != null) {
                        authHeader = "${freshToken.tokenType} ${freshToken.accessToken}"
                        response = httpClient.newCall(buildRequest(authHeader)).execute()
                    }
                }
            }

            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("MCP HTTP ${response.code}: $respBody"))
            }

            val jsonRes = JSONObject(respBody)
            Result.success(jsonRes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Perform handshake / initialize call
     */
    suspend fun initialize(server: McpServer, tokenEndpoint: String?): Result<JSONObject> {
        val initParams = JSONObject().apply {
            put("protocolVersion", "2024-11-05")
            put("capabilities", JSONObject().apply {
                put("tools", JSONObject())
            })
            put("clientInfo", JSONObject().apply {
                put("name", "Pencode AI Agent")
                put("version", "1.0.0")
            })
        }
        return sendJsonRpc(server, tokenEndpoint, "initialize", initParams)
    }

    /**
     * Discover tools via tools/list
     */
    suspend fun listTools(server: McpServer, tokenEndpoint: String?): Result<List<McpToolInfo>> = withContext(Dispatchers.IO) {
        // Send initialize first (best effort)
        initialize(server, tokenEndpoint)

        val res = sendJsonRpc(server, tokenEndpoint, "tools/list")
        if (res.isFailure) {
            return@withContext Result.failure(res.exceptionOrNull() ?: Exception("Failed to list MCP tools."))
        }

        val json = res.getOrNull() ?: JSONObject()
        val toolsList = mutableListOf<McpToolInfo>()

        if (json.has("result")) {
            val resultObj = json.getJSONObject("result")
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
                    name = "${server.name.lowercase().replace(" ", "_")}_action",
                    description = "Generic operation tool for ${server.name}",
                    parametersJsonSchema = """{"type":"object"}"""
                )
            )
        }

        Result.success(toolsList)
    }

    /**
     * Call tool via tools/call
     */
    suspend fun callTool(
        server: McpServer,
        tokenEndpoint: String?,
        toolName: String,
        argumentsJson: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val params = JSONObject().apply {
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

        val res = sendJsonRpc(server, tokenEndpoint, "tools/call", params)
        if (res.isFailure) {
            return@withContext Result.failure(res.exceptionOrNull() ?: Exception("Tool call failed."))
        }

        val json = res.getOrNull() ?: JSONObject()
        if (json.has("result")) {
            return@withContext Result.success("MCP Tool Result ($toolName):\n${json.get("result")}")
        } else if (json.has("error")) {
            return@withContext Result.failure(Exception("MCP Error ($toolName):\n${json.get("error")}"))
        }

        Result.success("MCP Execution Completed:\n$json")
    }
}
