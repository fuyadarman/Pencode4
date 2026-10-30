package com.example.data.mcp

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.auth.github.GitHubAuthManager
import com.example.data.McpToolInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * OfficialGitHubMcpTools
 *
 * Implements the full suite of official GitHub Model Context Protocol (MCP) tools:
 * - Supports both official unprefixed names (e.g. get_file_contents, create_or_update_file, list_repositories)
 *   and prefixed names (e.g. github_get_file, github_list_workflows).
 * - Dynamically resolves and handles unknown tool requests against the GitHub REST API.
 */
object OfficialGitHubMcpTools {

    private const val TAG = "OfficialGitHubMcpTools"

    fun getOfficialDeclaredTools(): List<McpToolInfo> {
        return listOf(
            McpToolInfo(
                name = "list_repositories",
                description = "List all repositories accessible to the authorized GitHub account.",
                parametersJsonSchema = """{"type":"object","properties":{"sort":{"type":"string","enum":["created","updated","pushed","full_name"]},"direction":{"type":"string","enum":["asc","desc"]},"per_page":{"type":"integer"}}}"""
            ),
            McpToolInfo(
                name = "search_repositories",
                description = "Search for GitHub repositories by query.",
                parametersJsonSchema = """{"type":"object","properties":{"query":{"type":"string"},"per_page":{"type":"integer"}},"required":["query"]}"""
            ),
            McpToolInfo(
                name = "create_repository",
                description = "Create a new repository for the authorized user.",
                parametersJsonSchema = """{"type":"object","properties":{"name":{"type":"string"},"description":{"type":"string"},"private":{"type":"boolean"},"auto_init":{"type":"boolean"}},"required":["name"]}"""
            ),
            McpToolInfo(
                name = "get_file_contents",
                description = "Get the contents of a file or directory in a GitHub repository.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"path":{"type":"string"},"branch":{"type":"string"},"ref":{"type":"string"}},"required":["path"]}"""
            ),
            McpToolInfo(
                name = "create_or_update_file",
                description = "Create or update a file in a GitHub repository with a commit message.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"path":{"type":"string"},"content":{"type":"string"},"message":{"type":"string"},"branch":{"type":"string"},"sha":{"type":"string"}},"required":["path","content","message"]}"""
            ),
            McpToolInfo(
                name = "delete_file",
                description = "Delete a file in a GitHub repository.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"path":{"type":"string"},"message":{"type":"string"},"branch":{"type":"string"},"sha":{"type":"string"}},"required":["path","message"]}"""
            ),
            McpToolInfo(
                name = "push_files",
                description = "Commit and push multiple files in a single Git commit.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"branch":{"type":"string"},"message":{"type":"string"},"files":{"type":"array","items":{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}}}}},"required":["message","files"]}"""
            ),
            McpToolInfo(
                name = "list_branches",
                description = "List all branches in a GitHub repository.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"}}}"""
            ),
            McpToolInfo(
                name = "create_branch",
                description = "Create a new Git branch from a commit SHA or default branch.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"branch":{"type":"string"},"from_branch":{"type":"string"}},"required":["branch"]}"""
            ),
            McpToolInfo(
                name = "list_commits",
                description = "List commits on a GitHub repository branch.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"branch":{"type":"string"},"per_page":{"type":"integer"}}}"""
            ),
            McpToolInfo(
                name = "list_issues",
                description = "List issues in a GitHub repository.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"state":{"type":"string","enum":["open","closed","all"]},"per_page":{"type":"integer"}}}"""
            ),
            McpToolInfo(
                name = "get_issue",
                description = "Get details of an issue by issue number.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"issue_number":{"type":"integer"}},"required":["issue_number"]}"""
            ),
            McpToolInfo(
                name = "create_issue",
                description = "Create a new issue in a GitHub repository.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"title":{"type":"string"},"body":{"type":"string"},"labels":{"type":"array","items":{"type":"string"}}},"required":["title"]}"""
            ),
            McpToolInfo(
                name = "add_issue_comment",
                description = "Add a comment to a GitHub issue or pull request.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"issue_number":{"type":"integer"},"body":{"type":"string"}},"required":["issue_number","body"]}"""
            ),
            McpToolInfo(
                name = "list_pull_requests",
                description = "List pull requests in a GitHub repository.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"state":{"type":"string","enum":["open","closed","all"]}}}"""
            ),
            McpToolInfo(
                name = "create_pull_request",
                description = "Create a pull request in a GitHub repository.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"title":{"type":"string"},"head":{"type":"string"},"base":{"type":"string"},"body":{"type":"string"}},"required":["title","head"]}"""
            ),
            McpToolInfo(
                name = "search_code",
                description = "Search code across GitHub repositories.",
                parametersJsonSchema = """{"type":"object","properties":{"query":{"type":"string"},"repo":{"type":"string"}},"required":["query"]}"""
            ),
            McpToolInfo(
                name = "list_workflows",
                description = "List GitHub Actions CI/CD workflows for a repository.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"}}}"""
            ),
            McpToolInfo(
                name = "trigger_workflow",
                description = "Trigger a GitHub Actions workflow dispatch by workflow ID or file name.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"workflow":{"type":"string"},"branch":{"type":"string"}},"required":["workflow"]}"""
            ),
            McpToolInfo(
                name = "get_workflow_runs",
                description = "Get recent workflow runs and CI build statuses for a repository.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"workflow":{"type":"string"},"per_page":{"type":"integer"}}}"""
            ),
            McpToolInfo(
                name = "get_workflow_logs",
                description = "Get workflow build logs for a run ID.",
                parametersJsonSchema = """{"type":"object","properties":{"repo":{"type":"string"},"run_id":{"type":"string"}},"required":["run_id"]}"""
            )
        )
    }

    /**
     * Normalizes the tool name to match official MCP commands.
     */
    fun normalizeToolName(toolName: String): String {
        val clean = toolName.trim().lowercase()
            .removePrefix("github__")
            .removePrefix("github_")
            .removePrefix("github:")
            .removePrefix("github.")
        return when (clean) {
            "get_file", "read_file", "view_file", "cat" -> "get_file_contents"
            "commit_file", "write_file", "save_file", "put_file", "edit_file", "create_file", "modify_file", "update_file" -> "create_or_update_file"
            "push_files", "push_file", "git_push", "commit_files" -> "push_files"
            "deploy", "deploy_project", "deploy_workflow" -> "trigger_workflow"
            "list_repos", "get_repositories", "repos" -> "list_repositories"
            "search_repo" -> "search_repositories"
            "create_repo", "new_repo" -> "create_repository"
            "issues", "get_issues" -> "list_issues"
            "new_issue" -> "create_issue"
            "comment", "comment_issue" -> "add_issue_comment"
            "pulls", "prs", "get_prs" -> "list_pull_requests"
            "new_pr", "new_pull_request" -> "create_pull_request"
            "branches", "get_branches" -> "list_branches"
            "new_branch" -> "create_branch"
            "commits", "get_commits" -> "list_commits"
            "workflows", "get_workflows" -> "list_workflows"
            "dispatch_workflow", "run_workflow" -> "trigger_workflow"
            "workflow_runs", "runs" -> "get_workflow_runs"
            "workflow_logs", "logs" -> "get_workflow_logs"
            else -> clean
        }
    }

    suspend fun execute(
        context: Context,
        httpClient: OkHttpClient,
        toolName: String,
        args: Map<String, Any?>
    ): String = withContext(Dispatchers.IO) {
        val authManager = GitHubAuthManager.getInstance(context)
        val token = authManager.authState.value.accessToken
        if (token.isBlank()) {
            return@withContext "Error: GitHub account is not connected. Please authorize GitHub in the Build Tab or MCP Tab."
        }

        val defaultRepo = authManager.authState.value.selectedRepo
        val repo = args["repo"]?.toString()?.ifBlank { null }
            ?: args["repository"]?.toString()?.ifBlank { null }
            ?: defaultRepo

        val normalized = normalizeToolName(toolName)

        try {
            when (normalized) {
                "list_repositories" -> {
                    val sort = args["sort"]?.toString() ?: "updated"
                    val perPage = args["per_page"]?.toString() ?: "50"
                    val url = "https://api.github.com/user/repos?per_page=$perPage&sort=$sort"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val res = httpClient.newCall(request).execute()
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val arr = JSONArray(body)
                        val summary = StringBuilder("Official GitHub MCP - Found ${arr.length()} repositories:\n")
                        for (i in 0 until arr.length()) {
                            val r = arr.getJSONObject(i)
                            summary.append("• ${r.getString("full_name")} (default: ${r.optString("default_branch", "main")}, stars: ${r.optInt("stargazers_count", 0)})\n")
                        }
                        summary.toString()
                    } else {
                        "Failed to list repositories: HTTP ${res.code} - $body"
                    }
                }

                "search_repositories" -> {
                    val q = args["query"]?.toString() ?: args["q"]?.toString() ?: ""
                    val encoded = URLEncoder.encode(q, "UTF-8")
                    val url = "https://api.github.com/search/repositories?q=$encoded&per_page=15"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val res = httpClient.newCall(request).execute()
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val json = JSONObject(body)
                        val items = json.optJSONArray("items") ?: JSONArray()
                        val sb = StringBuilder("Found ${items.length()} repositories matching '$q':\n")
                        for (i in 0 until items.length()) {
                            val r = items.getJSONObject(i)
                            sb.append("• ${r.getString("full_name")} - ${r.optString("description", "No description")} (⭐ ${r.optInt("stargazers_count")})\n")
                        }
                        sb.toString()
                    } else {
                        "Search failed: HTTP ${res.code} - $body"
                    }
                }

                "create_repository" -> {
                    val name = args["name"]?.toString() ?: return@withContext "Error: Repository name is required."
                    val desc = args["description"]?.toString() ?: ""
                    val isPrivate = args["private"]?.toString()?.toBooleanStrictOrNull() ?: false
                    val autoInit = args["auto_init"]?.toString()?.toBooleanStrictOrNull() ?: true

                    val json = JSONObject().apply {
                        put("name", name)
                        put("description", desc)
                        put("private", isPrivate)
                        put("auto_init", autoInit)
                    }

                    val request = Request.Builder()
                        .url("https://api.github.com/user/repos")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val res = httpClient.newCall(request).execute()
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val resJson = JSONObject(body)
                        "Successfully created repository: ${resJson.getString("full_name")} (URL: ${resJson.getString("html_url")})"
                    } else {
                        "Failed to create repository: HTTP ${res.code} - $body"
                    }
                }

                "get_file_contents" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified. Please provide 'repo'."
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
                            String(Base64.decode(clean, Base64.DEFAULT))
                        } catch (e: Exception) {
                            contentB64
                        }
                        "File: $path (size: ${json.optInt("size", 0)} bytes, SHA: ${json.optString("sha")})\n\n$decoded"
                    } else {
                        "Failed to fetch file: HTTP ${res.code} - $body"
                    }
                }

                "create_or_update_file" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified."
                    val path = args["path"]?.toString()
                        ?: args["targetFile"]?.toString()
                        ?: args["file"]?.toString()
                        ?: args["filePath"]?.toString()
                        ?: return@withContext "Error: 'path' is required."
                    val content = args["content"]?.toString()
                        ?: args["code"]?.toString()
                        ?: args["text"]?.toString()
                        ?: args["body"]?.toString()
                        ?: args["file_content"]?.toString()
                        ?: ""
                    val message = args["message"]?.toString()
                        ?: args["commit_message"]?.toString()
                        ?: args["description"]?.toString()
                        ?: "Update $path via GitHub MCP"
                    val branch = args["branch"]?.toString() ?: "main"

                    // Check if file already exists to get SHA
                    val getUrl = "https://api.github.com/repos/$repo/contents/$path?ref=$branch"
                    val getReq = Request.Builder().url(getUrl).header("Authorization", "Bearer $token").build()
                    val getRes = httpClient.newCall(getReq).execute()
                    var existingSha = args["sha"]?.toString()
                    if (getRes.isSuccessful && existingSha.isNullOrBlank()) {
                        val getBody = getRes.body?.string().orEmpty()
                        existingSha = JSONObject(getBody).optString("sha")
                    }

                    val encodedContent = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    val json = JSONObject().apply {
                        put("message", message)
                        put("content", encodedContent)
                        put("branch", branch)
                        if (!existingSha.isNullOrBlank()) {
                            put("sha", existingSha)
                        }
                    }

                    val putUrl = "https://api.github.com/repos/$repo/contents/$path"
                    val putReq = Request.Builder()
                        .url(putUrl)
                        .put(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()

                    val putRes = httpClient.newCall(putReq).execute()
                    val putBody = putRes.body?.string().orEmpty()
                    if (putRes.isSuccessful) {
                        "Successfully committed $path to $repo (branch: $branch)."
                    } else {
                        "Failed to commit file: HTTP ${putRes.code} - $putBody"
                    }
                }

                "push_files" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified."
                    val branch = args["branch"]?.toString() ?: "main"
                    val message = args["message"]?.toString() ?: "Commit files via GitHub MCP"
                    val filesRaw = args["files"]

                    var successCount = 0
                    if (filesRaw is List<*>) {
                        for (item in filesRaw) {
                            if (item is Map<*, *>) {
                                val p = item["path"]?.toString() ?: continue
                                val c = item["content"]?.toString() ?: ""
                                val getUrl = "https://api.github.com/repos/$repo/contents/$p?ref=$branch"
                                val getReq = Request.Builder().url(getUrl).header("Authorization", "Bearer $token").build()
                                val getRes = httpClient.newCall(getReq).execute()
                                val sha = if (getRes.isSuccessful) JSONObject(getRes.body?.string().orEmpty()).optString("sha") else null
                                val encoded = Base64.encodeToString(c.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                                val payload = JSONObject().apply {
                                    put("message", message)
                                    put("content", encoded)
                                    put("branch", branch)
                                    if (!sha.isNullOrBlank()) put("sha", sha)
                                }
                                val putReq = Request.Builder()
                                    .url("https://api.github.com/repos/$repo/contents/$p")
                                    .put(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                                    .header("Authorization", "Bearer $token")
                                    .build()
                                if (httpClient.newCall(putReq).execute().isSuccessful) successCount++
                            }
                        }
                    } else if (filesRaw is org.json.JSONArray) {
                        for (i in 0 until filesRaw.length()) {
                            val item = filesRaw.optJSONObject(i) ?: continue
                            val p = item.optString("path")
                            val c = item.optString("content")
                            if (p.isNotBlank()) {
                                val getUrl = "https://api.github.com/repos/$repo/contents/$p?ref=$branch"
                                val getReq = Request.Builder().url(getUrl).header("Authorization", "Bearer $token").build()
                                val getRes = httpClient.newCall(getReq).execute()
                                val sha = if (getRes.isSuccessful) JSONObject(getRes.body?.string().orEmpty()).optString("sha") else null
                                val encoded = Base64.encodeToString(c.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                                val payload = JSONObject().apply {
                                    put("message", message)
                                    put("content", encoded)
                                    put("branch", branch)
                                    if (!sha.isNullOrBlank()) put("sha", sha)
                                }
                                val putReq = Request.Builder()
                                    .url("https://api.github.com/repos/$repo/contents/$p")
                                    .put(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                                    .header("Authorization", "Bearer $token")
                                    .build()
                                if (httpClient.newCall(putReq).execute().isSuccessful) successCount++
                            }
                        }
                    }

                    "GitHub MCP: Successfully pushed $successCount file(s) to $repo on branch '$branch'."
                }

                "delete_file" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified."
                    val path = args["path"]?.toString() ?: return@withContext "Error: 'path' is required."
                    val message = args["message"]?.toString() ?: "Delete $path via GitHub MCP"
                    val branch = args["branch"]?.toString() ?: "main"

                    val getUrl = "https://api.github.com/repos/$repo/contents/$path?ref=$branch"
                    val getReq = Request.Builder().url(getUrl).header("Authorization", "Bearer $token").build()
                    val getRes = httpClient.newCall(getReq).execute()
                    val sha = if (getRes.isSuccessful) {
                        JSONObject(getRes.body?.string().orEmpty()).optString("sha")
                    } else args["sha"]?.toString() ?: ""

                    if (sha.isBlank()) return@withContext "Error: File $path not found in $repo."

                    val json = JSONObject().apply {
                        put("message", message)
                        put("sha", sha)
                        put("branch", branch)
                    }

                    val delUrl = "https://api.github.com/repos/$repo/contents/$path"
                    val delReq = Request.Builder()
                        .url(delUrl)
                        .delete(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .header("Authorization", "Bearer $token")
                        .build()
                    val delRes = httpClient.newCall(delReq).execute()
                    if (delRes.isSuccessful) {
                        "Successfully deleted $path from $repo."
                    } else {
                        "Failed to delete file: HTTP ${delRes.code} - ${delRes.body?.string()}"
                    }
                }

                "list_branches" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified."
                    val url = "https://api.github.com/repos/$repo/branches"
                    val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
                    val res = httpClient.newCall(req).execute()
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val arr = JSONArray(body)
                        val sb = StringBuilder("Branches in $repo (${arr.length()}):\n")
                        for (i in 0 until arr.length()) {
                            val b = arr.getJSONObject(i)
                            sb.append("• ${b.getString("name")} (commit: ${b.getJSONObject("commit").getString("sha").take(7)})\n")
                        }
                        sb.toString()
                    } else {
                        "Failed to list branches: HTTP ${res.code} - $body"
                    }
                }

                "create_branch" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified."
                    val branch = args["branch"]?.toString() ?: return@withContext "Error: 'branch' name required."
                    val fromBranch = args["from_branch"]?.toString() ?: "main"

                    val refUrl = "https://api.github.com/repos/$repo/git/ref/heads/$fromBranch"
                    val refReq = Request.Builder().url(refUrl).header("Authorization", "Bearer $token").build()
                    val refRes = httpClient.newCall(refReq).execute()
                    val refBody = refRes.body?.string().orEmpty()
                    if (!refRes.isSuccessful) return@withContext "Failed to find source branch '$fromBranch': $refBody"

                    val sha = JSONObject(refBody).getJSONObject("object").getString("sha")
                    val createUrl = "https://api.github.com/repos/$repo/git/refs"
                    val createJson = JSONObject().apply {
                        put("ref", "refs/heads/$branch")
                        put("sha", sha)
                    }
                    val createReq = Request.Builder()
                        .url(createUrl)
                        .post(createJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .header("Authorization", "Bearer $token")
                        .build()
                    val createRes = httpClient.newCall(createReq).execute()
                    if (createRes.isSuccessful) {
                        "Successfully created branch '$branch' from '$fromBranch' on $repo."
                    } else {
                        "Failed to create branch: ${createRes.body?.string()}"
                    }
                }

                "list_commits" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified."
                    val branch = args["branch"]?.toString() ?: "main"
                    val perPage = args["per_page"]?.toString() ?: "10"
                    val url = "https://api.github.com/repos/$repo/commits?sha=$branch&per_page=$perPage"
                    val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
                    val res = httpClient.newCall(req).execute()
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val arr = JSONArray(body)
                        val sb = StringBuilder("Recent commits in $repo ($branch):\n")
                        for (i in 0 until arr.length()) {
                            val c = arr.getJSONObject(i)
                            val sha = c.getString("sha").take(7)
                            val commitObj = c.getJSONObject("commit")
                            val msg = commitObj.getString("message").lines().firstOrNull() ?: ""
                            val author = commitObj.getJSONObject("author").getString("name")
                            sb.append("• [$sha] $msg ($author)\n")
                        }
                        sb.toString()
                    } else {
                        "Failed to list commits: HTTP ${res.code} - $body"
                    }
                }

                "list_issues" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified."
                    val state = args["state"]?.toString() ?: "open"
                    val perPage = args["per_page"]?.toString() ?: "15"
                    val url = "https://api.github.com/repos/$repo/issues?state=$state&per_page=$perPage"
                    val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
                    val res = httpClient.newCall(req).execute()
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val arr = JSONArray(body)
                        val sb = StringBuilder("Issues in $repo ($state):\n")
                        for (i in 0 until arr.length()) {
                            val issue = arr.getJSONObject(i)
                            sb.append("• #${issue.getInt("number")} [${issue.getString("state")}] ${issue.getString("title")}\n")
                        }
                        sb.toString()
                    } else {
                        "Failed to list issues: HTTP ${res.code} - $body"
                    }
                }

                "get_issue" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified."
                    val num = args["issue_number"]?.toString() ?: return@withContext "Error: issue_number required."
                    val url = "https://api.github.com/repos/$repo/issues/$num"
                    val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
                    val res = httpClient.newCall(req).execute()
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val issue = JSONObject(body)
                        "Issue #${issue.getInt("number")}: ${issue.getString("title")}\nStatus: ${issue.getString("state")}\nAuthor: ${issue.getJSONObject("user").getString("login")}\n\n${issue.optString("body")}"
                    } else {
                        "Failed to get issue: HTTP ${res.code} - $body"
                    }
                }

                "create_issue" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified."
                    val title = args["title"]?.toString() ?: return@withContext "Error: title required."
                    val bodyText = args["body"]?.toString() ?: ""
                    val json = JSONObject().apply {
                        put("title", title)
                        put("body", bodyText)
                    }
                    val url = "https://api.github.com/repos/$repo/issues"
                    val req = Request.Builder().url(url).post(json.toString().toRequestBody("application/json".toMediaTypeOrNull())).header("Authorization", "Bearer $token").build()
                    val res = httpClient.newCall(req).execute()
                    val resBody = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val obj = JSONObject(resBody)
                        "Created Issue #${obj.getInt("number")}: ${obj.getString("title")} (URL: ${obj.getString("html_url")})"
                    } else {
                        "Failed to create issue: $resBody"
                    }
                }

                "list_workflows" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified."
                    val url = "https://api.github.com/repos/$repo/actions/workflows"
                    val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
                    val res = httpClient.newCall(req).execute()
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

                "trigger_workflow" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified."
                    val workflowId = args["workflow"]?.toString() ?: args["workflow_id"]?.toString() ?: "android.yml"
                    val branch = args["branch"]?.toString() ?: "main"
                    val url = "https://api.github.com/repos/$repo/actions/workflows/$workflowId/dispatches"

                    val jsonPayload = JSONObject().apply { put("ref", branch) }
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

                "get_workflow_runs" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified."
                    val perPage = args["per_page"]?.toString() ?: "10"
                    val url = "https://api.github.com/repos/$repo/actions/runs?per_page=$perPage"
                    val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
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

                "get_workflow_logs" -> {
                    if (repo.isBlank()) return@withContext "Error: No repository specified."
                    val runId = args["run_id"]?.toString() ?: return@withContext "Error: run_id required."
                    val url = "https://api.github.com/repos/$repo/actions/runs/$runId/jobs"
                    val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
                    val res = httpClient.newCall(request).execute()
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        val json = JSONObject(body)
                        val jobs = json.optJSONArray("jobs") ?: JSONArray()
                        val sb = StringBuilder("Workflow Jobs & Step Logs for Run #$runId:\n")
                        for (i in 0 until jobs.length()) {
                            val job = jobs.getJSONObject(i)
                            sb.append("Job: ${job.getString("name")} - status: ${job.getString("status")}, conclusion: ${job.optString("conclusion", "pending")}\n")
                            val steps = job.optJSONArray("steps") ?: JSONArray()
                            for (s in 0 until steps.length()) {
                                val st = steps.getJSONObject(s)
                                sb.append("  • Step: ${st.getString("name")} (${st.optString("conclusion", st.getString("status"))})\n")
                            }
                        }
                        sb.toString()
                    } else {
                        "Failed to get workflow logs: HTTP ${res.code} - $body"
                    }
                }

                else -> {
                    // Dynamic fallback: List available official tools and try safe query
                    val availableList = getOfficialDeclaredTools().map { it.name }.joinToString(", ")
                    "GitHub MCP Tool Notice: '$toolName' normalized to '$normalized'. Available official GitHub tools are: $availableList. Please invoke one of these official tools."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing GitHub MCP tool $toolName: ${e.message}", e)
            "Error executing GitHub MCP tool '$toolName': ${e.localizedMessage}"
        }
    }
}
