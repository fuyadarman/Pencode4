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
        return listOf(
            com.example.data.McpToolInfo(
                name = "github_list_repositories",
                description = "List all repositories accessible to the authorized GitHub account.",
                parametersJsonSchema = """{"type":"object","properties":{}}"""
            ),
            com.example.data.McpToolInfo(
                name = "github_get_file",
                description = "Get content of a file in a GitHub repository.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"path":{"type":"string"},"branch":{"type":"string"}},"required":["path"]}"""
            ),
            com.example.data.McpToolInfo(
                name = "github_commit_file",
                description = "Create or update a file in a GitHub repository.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"path":{"type":"string"},"content":{"type":"string"},"message":{"type":"string"}},"required":["path","content","message"]}"""
            ),
            com.example.data.McpToolInfo(
                name = "github_list_workflows",
                description = "List GitHub Actions workflows for a repository.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"}}}"""
            ),
            com.example.data.McpToolInfo(
                name = "github_trigger_workflow",
                description = "Trigger a GitHub Actions workflow dispatch.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"workflow":{"type":"string"},"branch":{"type":"string"}},"required":["workflow"]}"""
            ),
            com.example.data.McpToolInfo(
                name = "github_get_workflow_runs",
                description = "Get latest workflow runs and CI status for a repository.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"}}}"""
            ),
            com.example.data.McpToolInfo(
                name = "github_get_workflow_logs",
                description = "Get build logs for a GitHub Actions workflow run.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"run_id":{"type":"string"}}}"""
            )
        )
    }

    suspend fun executeTool(toolName: String, args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val token = authManager.authState.value.accessToken
        if (token.isBlank()) {
            return@withContext "Error: GitHub account is not connected. Please authorize GitHub in the Build Tab or MCP Tab."
        }

        try {
            when (toolName) {
                "github_list_repositories" -> {
                    val request = Request.Builder()
                        .url("https://api.github.com/user/repos?per_page=50&sort=updated")
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val res = httpClient.newCall(request).execute()
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val arr = JSONArray(body)
                        val summary = StringBuilder("Found ${arr.length()} repositories:\n")
                        for (i in 0 until arr.length()) {
                            val r = arr.getJSONObject(i)
                            summary.append("• ${r.getString("full_name")} (branch: ${r.optString("default_branch", "main")}, stars: ${r.optInt("stargazers_count", 0)})\n")
                        }
                        summary.toString()
                    } else {
                        "Failed to list repositories: HTTP ${res.code} - $body"
                    }
                }

                "github_get_file" -> {
                    val repo = args["repo"]?.toString() ?: authManager.authState.value.selectedRepo
                    val path = args["path"]?.toString() ?: "README.md"
                    val ref = args["branch"]?.toString() ?: args["ref"]?.toString() ?: "main"
                    val url = "https://api.github.com/repos/$repo/contents/$path?ref=$ref"

                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val res = httpClient.newCall(request).execute()
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val json = JSONObject(body)
                        val contentB64 = json.optString("content", "")
                        val decoded = try {
                            val clean = contentB64.replace("\n", "").replace("\r", "")
                            String(android.util.Base64.decode(clean, android.util.Base64.DEFAULT))
                        } catch (e: Exception) {
                            contentB64
                        }
                        "File: $path (size: ${json.optInt("size", 0)} bytes)\n\n$decoded"
                    } else {
                        "Failed to fetch file: HTTP ${res.code} - $body"
                    }
                }

                "github_list_workflows" -> {
                    val repo = args["repo"]?.toString() ?: authManager.authState.value.selectedRepo
                    val url = "https://api.github.com/repos/$repo/actions/workflows"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val res = httpClient.newCall(request).execute()
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val json = JSONObject(body)
                        val workflows = json.optJSONArray("workflows") ?: JSONArray()
                        val sb = StringBuilder("Found ${workflows.length()} workflows in $repo:\n")
                        for (i in 0 until workflows.length()) {
                            val wf = workflows.getJSONObject(i)
                            sb.append("• [ID: ${wf.getLong("id")}] ${wf.getString("name")} (${wf.getString("path")}) - state: ${wf.getString("state")}\n")
                        }
                        sb.toString()
                    } else {
                        "Failed to list workflows: HTTP ${res.code} - $body"
                    }
                }

                "github_trigger_workflow" -> {
                    val repo = args["repo"]?.toString() ?: authManager.authState.value.selectedRepo
                    val workflowId = args["workflow"]?.toString() ?: args["workflow_id"]?.toString() ?: "command.yml"
                    val branch = args["branch"]?.toString() ?: "main"
                    val url = "https://api.github.com/repos/$repo/actions/workflows/$workflowId/dispatches"

                    val jsonPayload = JSONObject().apply {
                        put("ref", branch)
                    }
                    val request = Request.Builder()
                        .url(url)
                        .post(jsonPayload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val res = httpClient.newCall(request).execute()
                    if (res.code in 200..299) {
                        "Workflow '$workflowId' triggered successfully on $repo (branch: $branch)."
                    } else {
                        val body = res.body?.string().orEmpty()
                        "Failed to trigger workflow: HTTP ${res.code} - $body"
                    }
                }

                "github_get_workflow_runs" -> {
                    val repo = args["repo"]?.toString() ?: authManager.authState.value.selectedRepo
                    val url = "https://api.github.com/repos/$repo/actions/runs?per_page=10"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val res = httpClient.newCall(request).execute()
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val json = JSONObject(body)
                        val runs = json.optJSONArray("workflow_runs") ?: JSONArray()
                        val sb = StringBuilder("Latest workflow runs in $repo:\n")
                        for (i in 0 until runs.length()) {
                            val r = runs.getJSONObject(i)
                            sb.append("• Run #${r.getLong("run_number")} - ${r.getString("name")}: status=${r.getString("status")}, conclusion=${r.optString("conclusion", "pending")}\n")
                        }
                        sb.toString()
                    } else {
                        "Failed to fetch runs: HTTP ${res.code} - $body"
                    }
                }

                else -> "Unknown GitHub MCP tool: $toolName"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing GitHub MCP tool $toolName: ${e.message}", e)
            "Error executing $toolName: ${e.localizedMessage}"
        }
    }
}
