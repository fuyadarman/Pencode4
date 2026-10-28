package com.example.data.mcp

import android.content.Context
import android.util.Log
import com.example.auth.github.GitHubAuthManager
import com.example.data.McpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GitHubMcpServerProvider(private val context: Context) {

    companion object {
        const val GITHUB_SERVER_ID = "builtin-mcp-github"
        const val GITHUB_SERVER_NAME = "GitHub Platform & CI/CD"
        private const val TAG = "GitHubMcpProvider"
    }

    private val authManager = GitHubAuthManager.getInstance(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getBuiltInServer(): McpServer {
        val auth = authManager.authState.value
        return McpServer(
            id = GITHUB_SERVER_ID,
            name = GITHUB_SERVER_NAME,
            url = "https://api.github.com",
            platform = "GITHUB",
            apiKey = auth.accessToken.ifBlank { null },
            enabledWorkspaces = listOf("all")
        )
    }

    fun getDeclaredTools(): List<com.example.data.McpToolInfo> {
        return OfficialGitHubMcpTools.getOfficialDeclaredTools()

    }

    suspend fun executeTool(toolName: String, args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        OfficialGitHubMcpTools.execute(context, httpClient, toolName, args)
    }
}
