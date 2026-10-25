package com.example.git

import android.util.Log
import com.example.auth.github.GitHubRepoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GitHubRepoCreationEngine
 * Handles creating brand new repositories on GitHub using authenticated user tokens.
 * Supports public/private repositories, description, and automatic README initialization.
 */
object GitHubRepoCreationEngine {
    private const val TAG = "GitHubRepoCreation"
    private const val GITHUB_USER_REPOS_API = "https://api.github.com/user/repos"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun createRepository(
        token: String,
        name: String,
        isPrivate: Boolean,
        description: String = "",
        autoInit: Boolean = true
    ): Result<GitHubRepoItem> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim().removePrefix("Bearer ").removePrefix("token ").trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("GitHub access token is required. Please connect your GitHub account."))
        }

        val sanitizedRepoName = name.trim().replace(" ", "-").replace(Regex("[^a-zA-Z0-9_.-]"), "")
        if (sanitizedRepoName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Repository name cannot be empty."))
        }

        try {
            val payload = JSONObject().apply {
                put("name", sanitizedRepoName)
                put("private", isPrivate)
                if (description.isNotBlank()) {
                    put("description", description.trim())
                }
                put("auto_init", autoInit)
            }

            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(GITHUB_USER_REPOS_API)
                .post(requestBody)
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (response.isSuccessful || response.code == 201) {
                val json = JSONObject(responseBody)
                val fullName = json.optString("full_name", "${json.optJSONObject("owner")?.optString("login") ?: ""}/$sanitizedRepoName")
                val repoItem = GitHubRepoItem(
                    name = json.optString("name", sanitizedRepoName),
                    fullName = fullName,
                    description = json.optString("description", description),
                    isPrivate = json.optBoolean("private", isPrivate),
                    defaultBranch = json.optString("default_branch", "main"),
                    starsCount = json.optInt("stargazers_count", 0),
                    updatedAt = json.optString("updated_at", "")
                )
                Log.i(TAG, "Successfully created GitHub repository: $fullName (private=$isPrivate)")
                Result.success(repoItem)
            } else {
                val errorJson = try { JSONObject(responseBody) } catch (_: Exception) { null }
                val errorMsg = errorJson?.optString("message") ?: "HTTP ${response.code}: ${response.message}"
                val errorDetails = errorJson?.optJSONArray("errors")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("message") }.joinToString(", ")
                }
                val fullError = if (!errorDetails.isNullOrBlank()) "$errorMsg ($errorDetails)" else errorMsg
                Log.e(TAG, "Failed to create repo $sanitizedRepoName: $fullError")
                Result.failure(Exception("GitHub API Error: $fullError"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating GitHub repository $sanitizedRepoName: ${e.message}", e)
            Result.failure(e)
        }
    }
}
