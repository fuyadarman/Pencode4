package com.example.auth.github

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
import java.util.concurrent.TimeUnit

data class GitHubRepoItem(
    val name: String,
    val fullName: String,
    val description: String = "",
    val isPrivate: Boolean = false,
    val defaultBranch: String = "main",
    val starsCount: Int = 0,
    val updatedAt: String = ""
)

data class GitHubAuthState(
    val isConnected: Boolean = false,
    val accessToken: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val displayName: String = "",
    val selectedRepo: String = "",
    val selectedBranch: String = "main",
    val userRepos: List<GitHubRepoItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val workerUrl: String = ""
)

class GitHubAuthManager(private val context: Context) {

    companion object {
        const val CLIENT_ID = "Iv23liJ1YMYgjNpHUToe"
        const val APP_ID = "5369463"
        const val APP_PUBLIC_URL = "https://github.com/apps/pencode"
        const val DEFAULT_WORKER_URL = "https://pencode-auth.fuyadarman.workers.dev"
        private const val PREFS_NAME = "pencode_github_auth_prefs"
        private const val TAG = "GitHubAuthManager"

        @Volatile
        private var INSTANCE: GitHubAuthManager? = null

        fun getInstance(context: Context): GitHubAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GitHubAuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val _authState = MutableStateFlow(GitHubAuthState())
    val authState: StateFlow<GitHubAuthState> = _authState.asStateFlow()

    init {
        loadPersistedState()
    }

    private fun loadPersistedState() {
        val token = prefs.getString("access_token", "") ?: ""
        val username = prefs.getString("username", "") ?: ""
        val avatarUrl = prefs.getString("avatar_url", "") ?: ""
        val displayName = prefs.getString("display_name", "") ?: ""
        val selectedRepo = prefs.getString("selected_repo", "") ?: ""
        val selectedBranch = prefs.getString("selected_branch", "main") ?: "main"
        val workerUrl = prefs.getString("worker_url", DEFAULT_WORKER_URL) ?: DEFAULT_WORKER_URL

        val isConnected = token.isNotBlank()
        _authState.value = GitHubAuthState(
            isConnected = isConnected,
            accessToken = token,
            username = username,
            avatarUrl = avatarUrl,
            displayName = displayName,
            selectedRepo = selectedRepo,
            selectedBranch = selectedBranch,
            workerUrl = workerUrl
        )

        if (isConnected) {
            scope.launch {
                refreshUserProfileAndRepos(token)
            }
        }
    }

    fun updateWorkerUrl(url: String) {
        val trimmed = url.trim().trimEnd('/')
        prefs.edit().putString("worker_url", trimmed).apply()
        _authState.value = _authState.value.copy(workerUrl = trimmed)
    }

    fun selectRepo(repoFullName: String, defaultBranch: String = "main") {
        val cleanName = repoFullName.trim()
        val cleanBranch = defaultBranch.ifBlank { "main" }
        prefs.edit()
            .putString("selected_repo", cleanName)
            .putString("selected_branch", cleanBranch)
            .apply()

        _authState.value = _authState.value.copy(
            selectedRepo = cleanName,
            selectedBranch = cleanBranch
        )
    }

    suspend fun exchangeOAuthCode(code: String, customWorkerUrl: String? = null): Result<String> = withContext(Dispatchers.IO) {
        _authState.value = _authState.value.copy(isLoading = true, errorMessage = null)
        val worker = customWorkerUrl?.trimEnd('/') ?: _authState.value.workerUrl.trimEnd('/')

        val endpointsToTry = mutableListOf<String>()
        if (worker.isNotBlank()) {
            endpointsToTry.add("$worker/api/oauth/token")
            endpointsToTry.add("$worker/oauth/callback")
        }

        var tokenObtained = ""
        var lastErr = ""

        for (endpoint in endpointsToTry) {
            try {
                val jsonBody = JSONObject().apply {
                    put("code", code)
                    put("client_id", CLIENT_ID)
                }
                val request = Request.Builder()
                    .url(endpoint)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .header("Accept", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful && bodyStr.isNotBlank()) {
                    val json = JSONObject(bodyStr)
                    tokenObtained = json.optString("access_token", "")
                    if (tokenObtained.isBlank()) {
                        tokenObtained = json.optString("token", "")
                    }
                    if (tokenObtained.isNotBlank()) {
                        break
                    }
                } else {
                    lastErr = "Worker responded with HTTP ${response.code}: $bodyStr"
                }
            } catch (e: Exception) {
                lastErr = e.localizedMessage ?: "Network error calling worker"
                Log.w(TAG, "Worker token exchange attempt failed at $endpoint: $lastErr")
            }
        }

        if (tokenObtained.isNotBlank()) {
            saveTokenAndFetchData(tokenObtained)
            Result.success(tokenObtained)
        } else {
            val err = if (lastErr.isNotBlank()) lastErr else "Failed to exchange OAuth code. Check Cloudflare Worker deployment."
            _authState.value = _authState.value.copy(isLoading = false, errorMessage = err)
            Result.failure(Exception(err))
        }
    }

    suspend fun connectWithToken(token: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(Exception("Token cannot be blank"))
        }
        _authState.value = _authState.value.copy(isLoading = true, errorMessage = null)
        try {
            saveTokenAndFetchData(cleanToken)
            Result.success(cleanToken)
        } catch (e: Exception) {
            _authState.value = _authState.value.copy(isLoading = false, errorMessage = e.localizedMessage)
            Result.failure(e)
        }
    }

    private suspend fun saveTokenAndFetchData(token: String) {
        prefs.edit().putString("access_token", token).apply()
        _authState.value = _authState.value.copy(
            accessToken = token,
            isConnected = true
        )
        refreshUserProfileAndRepos(token)
    }

    suspend fun refreshUserProfileAndRepos(token: String = _authState.value.accessToken) = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext
        try {
            // 1. Fetch user profile
            val userRequest = Request.Builder()
                .url("https://api.github.com/user")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val userRes = httpClient.newCall(userRequest).execute()
            val userBody = userRes.body?.string().orEmpty()
            var username = ""
            var avatarUrl = ""
            var displayName = ""

            if (userRes.isSuccessful && userBody.isNotBlank()) {
                val userJson = JSONObject(userBody)
                username = userJson.optString("login", "")
                avatarUrl = userJson.optString("avatar_url", "")
                displayName = userJson.optString("name", username)

                prefs.edit()
                    .putString("username", username)
                    .putString("avatar_url", avatarUrl)
                    .putString("display_name", displayName)
                    .apply()
            }

            // 2. Fetch user repositories (up to 100 recent)
            val reposRequest = Request.Builder()
                .url("https://api.github.com/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val reposRes = httpClient.newCall(reposRequest).execute()
            val reposBody = reposRes.body?.string().orEmpty()
            val reposList = mutableListOf<GitHubRepoItem>()

            if (reposRes.isSuccessful && reposBody.isNotBlank()) {
                val jsonArr = JSONArray(reposBody)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.optJSONObject(i) ?: continue
                    reposList.add(
                        GitHubRepoItem(
                            name = obj.optString("name", ""),
                            fullName = obj.optString("full_name", ""),
                            description = obj.optString("description", ""),
                            isPrivate = obj.optBoolean("private", false),
                            defaultBranch = obj.optString("default_branch", "main"),
                            starsCount = obj.optInt("stargazers_count", 0),
                            updatedAt = obj.optString("updated_at", "")
                        )
                    )
                }
            }

            _authState.value = _authState.value.copy(
                isConnected = true,
                accessToken = token,
                username = username.ifBlank { _authState.value.username },
                avatarUrl = avatarUrl.ifBlank { _authState.value.avatarUrl },
                displayName = displayName.ifBlank { _authState.value.displayName },
                userRepos = reposList,
                isLoading = false,
                errorMessage = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching GitHub user info: ${e.message}", e)
            _authState.value = _authState.value.copy(
                isLoading = false,
                errorMessage = e.localizedMessage
            )
        }
    }

    fun disconnect() {
        prefs.edit()
            .remove("access_token")
            .remove("username")
            .remove("avatar_url")
            .remove("display_name")
            .remove("selected_repo")
            .remove("selected_branch")
            .apply()

        _authState.value = GitHubAuthState(
            workerUrl = _authState.value.workerUrl
        )
    }
}
