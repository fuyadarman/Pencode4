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
    private val sessionIds = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val activeEndpoints = java.util.concurrent.ConcurrentHashMap<String, String>()

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
     * Send JSON-RPC 2.0 Request with automatic 404/406 fallback discovery & SSE endpoint support
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

            // Ensure session id from store if memory is empty
            if (!sessionIds.containsKey(server.id)) {
                val persisted = tokenStore.getSessionId(server.id)
                if (!persisted.isNullOrBlank()) {
                    sessionIds[server.id] = persisted
                }
            }

            // If non-initialize call and no session id exists, initialize first
            if (method != "initialize" && !sessionIds.containsKey(server.id)) {
                try {
                    val initParams = JSONObject().apply {
                        put("protocolVersion", "2024-11-05")
                        put("capabilities", JSONObject().apply { put("tools", JSONObject()) })
                        put("clientInfo", JSONObject().apply {
                            put("name", "Pencode AI Agent")
                            put("version", "1.0.0")
                        })
                    }
                    val initReq = Request.Builder()
                        .url(activeEndpoints[server.id] ?: server.url.trim())
                        .post(JSONObject().apply {
                            put("jsonrpc", "2.0")
                            put("id", UUID.randomUUID().toString())
                            put("method", "initialize")
                            put("params", initParams)
                        }.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                        .addHeader("Content-Type", "application/json; charset=utf-8")
                        .addHeader("Accept", "application/json, text/event-stream;q=0.9, */*;q=0.8")
                    if (!server.apiKey.isNullOrBlank()) initReq.addHeader("Authorization", "Bearer ${server.apiKey.trim()}")
                    else if (!authHeader.isNullOrBlank()) initReq.addHeader("Authorization", authHeader)
                    val initResp = httpClient.newCall(initReq.build()).execute()
                    val initSid = initResp.header("Mcp-Session-Id") ?: initResp.header("mcp-session-id") ?: initResp.header("X-Mcp-Session-Id")
                    if (!initSid.isNullOrBlank()) {
                        sessionIds[server.id] = initSid
                        tokenStore.saveSessionId(server.id, initSid)
                    }
                    initResp.close()
                } catch (_: Exception) {}
            }

            fun buildRequest(url: String, auth: String?): Request {
                val b = Request.Builder()
                    .url(url)
                    .post(jsonRpcPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .addHeader("Accept", "application/json, text/event-stream;q=0.9, */*;q=0.8")
                    .addHeader("User-Agent", "Pencode-MCP-Client/1.0 (Android; Mobile)")

                val sessionId = sessionIds[server.id] ?: tokenStore.getSessionId(server.id)
                if (!sessionId.isNullOrBlank()) {
                    b.addHeader("Mcp-Session-Id", sessionId)
                    b.addHeader("mcp-session-id", sessionId)
                    b.addHeader("X-Mcp-Session-Id", sessionId)
                }

                if (!server.apiKey.isNullOrBlank()) {
                    val key = server.apiKey.trim()
                    b.addHeader("Authorization", "Bearer $key")
                    b.addHeader("X-Goog-Api-Key", key)
                    b.addHeader("x-api-key", key)
                    b.addHeader("apikey", key)
                } else if (!auth.isNullOrBlank()) {
                    b.addHeader("Authorization", auth)
                }
                return b.build()
            }

            val targetUrl = activeEndpoints[server.id] ?: server.url.trim()
            var response = httpClient.newCall(buildRequest(targetUrl, authHeader)).execute()

            // 400 Mcp-Session-Id handling: Perform initialize handshake and retry once
            if (response.code == 400) {
                val peekBody = response.peekBody(2048).string()
                if (peekBody.contains("Mcp-Session-Id", ignoreCase = true) || peekBody.contains("session", ignoreCase = true)) {
                    // Execute initialize handshake to get new session id
                    val initParams = JSONObject().apply {
                        put("protocolVersion", "2024-11-05")
                        put("capabilities", JSONObject().apply { put("tools", JSONObject()) })
                        put("clientInfo", JSONObject().apply {
                            put("name", "Pencode AI Agent")
                            put("version", "1.0.0")
                        })
                    }
                    val initReq = Request.Builder()
                        .url(targetUrl)
                        .post(JSONObject().apply {
                            put("jsonrpc", "2.0")
                            put("id", UUID.randomUUID().toString())
                            put("method", "initialize")
                            put("params", initParams)
                        }.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                        .addHeader("Content-Type", "application/json; charset=utf-8")
                        .addHeader("Accept", "application/json, text/event-stream;q=0.9, */*;q=0.8")
                    if (!server.apiKey.isNullOrBlank()) initReq.addHeader("Authorization", "Bearer ${server.apiKey.trim()}")
                    else if (!authHeader.isNullOrBlank()) initReq.addHeader("Authorization", authHeader)
                    val initResp = httpClient.newCall(initReq.build()).execute()
                    val newSid = initResp.header("Mcp-Session-Id") ?: initResp.header("mcp-session-id") ?: initResp.header("X-Mcp-Session-Id")
                    if (!newSid.isNullOrBlank()) {
                        sessionIds[server.id] = newSid
                        tokenStore.saveSessionId(server.id, newSid)
                    }
                    initResp.close()
                    response.close()
                    response = httpClient.newCall(buildRequest(targetUrl, authHeader)).execute()
                }
            }

            // 401 Handling: Retry once after token refresh
            if (response.code == 401 && !tokenEndpoint.isNullOrBlank()) {
                val refreshRes = authManager.refreshAccessToken(server.id, tokenEndpoint)
                if (refreshRes.isSuccess) {
                    val freshToken = refreshRes.getOrNull()
                    if (freshToken != null) {
                        authHeader = "${freshToken.tokenType} ${freshToken.accessToken}"
                        response.close()
                        response = httpClient.newCall(buildRequest(targetUrl, authHeader)).execute()
                    }
                }
            }

            // 404 / 405 / 406 Auto-discovery fallback for alternative MCP endpoints
            if ((response.code == 404 || response.code == 405 || response.code == 406) && activeEndpoints[server.id] == null) {
                val rawBase = server.url.trim().trimEnd('/')
                val candidates = mutableListOf<String>()
                
                if (rawBase.endsWith("/sse")) {
                    candidates.add(rawBase.removeSuffix("/sse"))
                    candidates.add(rawBase.removeSuffix("/sse") + "/mcp")
                    candidates.add(rawBase.removeSuffix("/sse") + "/messages")
                } else if (rawBase.endsWith("/mcp")) {
                    candidates.add(rawBase.removeSuffix("/mcp"))
                    candidates.add(rawBase.removeSuffix("/mcp") + "/sse")
                } else {
                    candidates.add("$rawBase/mcp")
                    candidates.add("$rawBase/sse")
                    candidates.add("$rawBase/rpc")
                    candidates.add("$rawBase/api/mcp")
                    candidates.add(rawBase)
                }

                for (cand in candidates) {
                    if (cand == targetUrl) continue
                    try {
                        val testReq = buildRequest(cand, authHeader)
                        val testResp = httpClient.newCall(testReq).execute()
                        val testBody = testResp.body?.string() ?: ""
                        if (testResp.isSuccessful || (testBody.contains("\"jsonrpc\"") && testResp.code != 404)) {
                            activeEndpoints[server.id] = cand
                            response.close()
                            response = testResp
                            break
                        }
                        testResp.close()
                    } catch (ignored: Exception) {}
                }
            }

            // Capture session id from response headers
            val respSessionId = response.header("Mcp-Session-Id")
                ?: response.header("mcp-session-id")
                ?: response.header("X-Mcp-Session-Id")
                ?: response.header("x-mcp-session-id")

            if (!respSessionId.isNullOrBlank()) {
                sessionIds[server.id] = respSessionId
            }

            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val parsedError = try {
                    val errJson = parseJsonFromResponse(respBody)
                    if (errJson.has("error")) {
                        val errObj = errJson.optJSONObject("error")
                        errObj?.optString("message") ?: errJson.optString("error")
                    } else respBody
                } catch (e: Exception) {
                    respBody
                }
                return@withContext Result.failure(Exception("MCP Server HTTP ${response.code}: $parsedError"))
            }

            val jsonRes = parseJsonFromResponse(respBody)

            // Also check if result contains sessionId
            if (jsonRes.has("result")) {
                val resObj = jsonRes.optJSONObject("result")
                val inResultSessionId = resObj?.optString("sessionId")?.takeIf { it.isNotBlank() }
                    ?: resObj?.optString("session_id")?.takeIf { it.isNotBlank() }
                if (!inResultSessionId.isNullOrBlank()) {
                    sessionIds[server.id] = inResultSessionId
                }
            }

            Result.success(jsonRes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseJsonFromResponse(body: String): JSONObject {
        val trimmed = body.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return JSONObject(trimmed)
        }
        // Handle Server-Sent Events (SSE) stream format e.g. "data: {...}"
        val lines = trimmed.lines()
        for (line in lines) {
            val l = line.trim()
            if (l.startsWith("data:")) {
                val jsonPart = l.substring(5).trim()
                if (jsonPart.startsWith("{") && jsonPart.endsWith("}")) {
                    try {
                        return JSONObject(jsonPart)
                    } catch (ignored: Exception) {}
                }
            }
        }
        return JSONObject(body)
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
        val actualToolName = if (toolName.contains("__")) toolName.substringAfterLast("__") else toolName
        val parsedArgs = JSONObject().apply {
            if (!argumentsJson.isNullOrBlank()) {
                try {
                    val rawObj = JSONObject(argumentsJson)
                    // If model wrapped arguments inside {"arguments": {...}} or {"params": {...}}
                    if (rawObj.has("arguments") && rawObj.optJSONObject("arguments") != null) {
                        val inner = rawObj.getJSONObject("arguments")
                        inner.keys().forEach { k -> put(k, inner.get(k)) }
                    } else if (rawObj.has("params") && rawObj.optJSONObject("params") != null) {
                        val inner = rawObj.getJSONObject("params")
                        inner.keys().forEach { k -> put(k, inner.get(k)) }
                    } else {
                        rawObj.keys().forEach { k -> put(k, rawObj.get(k)) }
                    }
                } catch (e: Exception) {
                    put("query", argumentsJson)
                }
            }
        }

        val params = JSONObject().apply {
            put("name", actualToolName)
            put("arguments", parsedArgs)
        }

        val res = sendJsonRpc(server, tokenEndpoint, "tools/call", params)
        if (res.isFailure) {
            return@withContext Result.failure(res.exceptionOrNull() ?: Exception("Tool call failed."))
        }

        val json = res.getOrNull() ?: JSONObject()
        if (json.has("result")) {
            val resultObj = json.optJSONObject("result")
            if (resultObj != null && resultObj.has("content")) {
                val contentArr = resultObj.optJSONArray("content")
                if (contentArr != null && contentArr.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until contentArr.length()) {
                        val item = contentArr.optJSONObject(i)
                        val text = item?.optString("text") ?: item?.toString() ?: ""
                        if (text.isNotBlank()) sb.append(text).append("\n")
                    }
                    if (sb.isNotBlank()) {
                        val isErr = resultObj.optBoolean("isError", false)
                        return@withContext if (isErr) {
                            Result.failure(Exception("MCP Tool Error ($actualToolName): ${sb.toString().trim()}"))
                        } else {
                            Result.success(sb.toString().trim())
                        }
                    }
                }
            }
            return@withContext Result.success("MCP Tool Result ($actualToolName):\n${json.get("result")}")
        } else if (json.has("error")) {
            val errObj = json.optJSONObject("error")
            val errMsg = errObj?.optString("message") ?: json.optString("error")
            return@withContext Result.failure(Exception("MCP Server Error ($actualToolName): $errMsg"))
        }

        Result.success("MCP Execution Completed:\n$json")
    }
}
