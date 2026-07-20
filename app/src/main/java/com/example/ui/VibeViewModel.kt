package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.AgentFileAction
import com.example.api.AgentResponse
import com.example.api.Content
import com.example.api.GeminiClient
import com.example.api.Part
import com.example.api.ToolCallResponse
import com.example.data.ChatMessageEntity
import com.example.data.ProjectEntity
import com.example.data.ProjectFileEntity
import com.example.data.VibeDatabase
import com.example.data.VibeRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

enum class WorkspaceTab {
    CHAT,
    CODE,
    PREVIEW,
    TERMINAL,
    ANDROID_BUILD,
}

data class EditRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val tool: String, // "edit", "patch", "create", "append"
    val path: String,
    val lines: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class BuildStep(
    val name: String,
    val status: String,
    val conclusion: String?,
    val number: Int
)

data class GitHubWorkflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String,
    val latestRunStatus: String = "unknown",
    val latestRunConclusion: String? = null,
    val isTriggering: Boolean = false
)

data class AiActionLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val status: String, // "thinking", "success", "failed"
    val timestamp: Long = System.currentTimeMillis(),
    val details: String? = null,
    val lineRange: String? = null, // Format: "Line 10-20" or "Line 45"
    val modelName: String? = null,
    val durationMillis: Long? = null,
    val startTime: Long = System.currentTimeMillis()
)

data class WebConsoleError(
    val id: String = java.util.UUID.randomUUID().toString(),
    val message: String,
    val sourceId: String,
    val lineNumber: Int,
    val isSelected: Boolean = true
)

data class AndroidBuildError(
    val id: String = java.util.UUID.randomUUID().toString(),
    val stepName: String,
    val message: String,
    val filePath: String,
    val lineNumber: Int,
    val logsSnippet: String,
    val isSelected: Boolean = true
)

data class CustomModelConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val alias: String,
    val provider: String,
    val apiKey: String,
    val baseUrl: String,
    val modelId: String
)

data class AttachedFile(
    val uri: android.net.Uri,
    val name: String,
    val mimeType: String?,
    val isImage: Boolean,
    val contentAsBase64: String? = null,
    val contentAsText: String? = null
)

data class TodoItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val task: String,
    val isCompleted: Boolean = false
)

class VibeViewModel(application: Application) : AndroidViewModel(application) {

    private val _chatInputText = MutableStateFlow("")
    val chatInputText: StateFlow<String> = _chatInputText.asStateFlow()

    fun updateChatInputText(text: String) {
        _chatInputText.value = text
    }

    private val _attachedFiles = MutableStateFlow<List<AttachedFile>>(emptyList())
    val attachedFiles: StateFlow<List<AttachedFile>> = _attachedFiles.asStateFlow()

    fun addAttachedFile(file: AttachedFile) {
        _attachedFiles.value = _attachedFiles.value + file
    }

    fun removeAttachedFile(file: AttachedFile) {
        _attachedFiles.value = _attachedFiles.value.filter { it != file }
    }
    
    fun clearAttachedFiles() {
        _attachedFiles.value = emptyList()
    }

    private val _todoList = MutableStateFlow<List<TodoItem>>(emptyList())
    val todoList: StateFlow<List<TodoItem>> = _todoList.asStateFlow()

    private val _isTodoListExpanded = MutableStateFlow(true)
    val isTodoListExpanded: StateFlow<Boolean> = _isTodoListExpanded.asStateFlow()

    fun toggleTodoListExpanded() {
        _isTodoListExpanded.value = !_isTodoListExpanded.value
    }

    private val repository: VibeRepository
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val backgroundBrowser = BackgroundBrowser(application)
    
    private fun normalizePath(path: String): String {
        var p = path.trim()
        if (p.startsWith("./")) p = p.substring(2)
        while (p.startsWith("/")) p = p.substring(1)
        return p
    }

    // Stateful multiple custom models
    private val _customModels = MutableStateFlow<List<CustomModelConfig>>(emptyList())
    val customModels: StateFlow<List<CustomModelConfig>> = _customModels.asStateFlow()

    private val _selectedModelId = MutableStateFlow("")
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    // Stateful detected web preview console errors
    private val _detectedWebErrors = MutableStateFlow<List<WebConsoleError>>(emptyList())
    val detectedWebErrors: StateFlow<List<WebConsoleError>> = _detectedWebErrors.asStateFlow()

    // Stateful detected android build errors
    private val _detectedAndroidBuildErrors = MutableStateFlow<List<AndroidBuildError>>(emptyList())
    val detectedAndroidBuildErrors: StateFlow<List<AndroidBuildError>> = _detectedAndroidBuildErrors.asStateFlow()

    fun addWebError(message: String, sourceId: String, lineNumber: Int) {
        val cleanSource = if (sourceId.startsWith("https://virtual-app/")) {
            sourceId.removePrefix("https://virtual-app/")
        } else {
            sourceId
        }
        val alreadyExists = _detectedWebErrors.value.any { 
            it.message == message && it.lineNumber == lineNumber && it.sourceId == cleanSource 
        }
        if (!alreadyExists) {
            _detectedWebErrors.value = _detectedWebErrors.value + WebConsoleError(
                message = message,
                sourceId = cleanSource,
                lineNumber = lineNumber
            )
            if (_allowAutoFix.value && !isAutoFixingWebErrors) {
                isAutoFixingWebErrors = true
                viewModelScope.launch(Dispatchers.Main) {
                    kotlinx.coroutines.delay(500)
                    _detectedWebErrors.value = _detectedWebErrors.value.map { it.copy(isSelected = true) }
                    fixSelectedWebErrors()
                    isAutoFixingWebErrors = false
                }
            }
        }
    }

    fun toggleWebErrorSelection(id: String) {
        _detectedWebErrors.value = _detectedWebErrors.value.map {
            if (it.id == id) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun toggleAllWebErrors(selectAll: Boolean) {
        _detectedWebErrors.value = _detectedWebErrors.value.map {
            it.copy(isSelected = selectAll)
        }
    }

    fun clearWebErrors() {
        _detectedWebErrors.value = emptyList()
    }

    private suspend fun getCodeSnippetForError(projectName: String, filePath: String, lineNumber: Int): String {
        try {
            val files = repository.getFilesForProject(projectName)
            var cleanPath = filePath
            if (cleanPath.startsWith("./")) cleanPath = cleanPath.substring(2)
            while (cleanPath.startsWith("/")) cleanPath = cleanPath.substring(1)
            
            val targetFile = files.find { 
                val f1 = it.path.replace("\\", "/").lowercase()
                val f2 = cleanPath.replace("\\", "/").lowercase()
                f1 == f2 || f1.endsWith("/$f2") || f2.endsWith("/$f1")
            } ?: files.find {
                java.io.File(it.path).name.equals(java.io.File(cleanPath).name, ignoreCase = true)
            }
            
            if (targetFile != null) {
                val lines = targetFile.content.split("\n")
                if (lines.isNotEmpty() && lineNumber > 0) {
                    val targetIndex = lineNumber - 1
                    val start = maxOf(0, targetIndex - 10)
                    val end = minOf(lines.size - 1, targetIndex + 10)
                    
                    return buildString {
                        append("\n  [Current Source Code Snippet around Line $lineNumber]:\n")
                        for (i in start..end) {
                            val prefix = if (i == targetIndex) "-> " else "   "
                            append(String.format(java.util.Locale.US, "%s%4d: %s\n", prefix, i + 1, lines[i]))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return ""
    }

    fun fixSelectedWebErrors() {
        val selected = _detectedWebErrors.value.filter { it.isSelected }
        if (selected.isEmpty()) return
        
        val project = _currentProject.value ?: return
        
        viewModelScope.launch {
            val errorReportBuilder = StringBuilder()
            for (it in selected) {
                val snippet = getCodeSnippetForError(project.name, it.sourceId, it.lineNumber)
                errorReportBuilder.append("- Error: \"${it.message}\" in file \"${it.sourceId}\" at line ${it.lineNumber}$snippet\n")
            }
            
            val errorReport = errorReportBuilder.toString()
            
            clearWebErrors()
            
            // Switch tab to Chat to show the ongoing fixing conversation
            _currentTab.value = WorkspaceTab.CHAT
            
            sendPrompt(
                "I encountered the following console errors in my web preview workspace. " +
                "Please analyze the workspace, find the root cause, and edit or patch the files to fix them completely. " +
                "Make sure your changes are robust and fully functional:\n\n$errorReport"
            )
        }
    }

    fun toggleAndroidBuildErrorSelection(id: String) {
        _detectedAndroidBuildErrors.value = _detectedAndroidBuildErrors.value.map {
            if (it.id == id) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun toggleAllAndroidBuildErrors(selectAll: Boolean) {
        _detectedAndroidBuildErrors.value = _detectedAndroidBuildErrors.value.map {
            it.copy(isSelected = selectAll)
        }
    }

    fun clearAndroidBuildErrors() {
        _detectedAndroidBuildErrors.value = emptyList()
    }

    fun parseAndroidBuildErrors(logs: String, stepName: String): List<AndroidBuildError> {
        val errors = mutableListOf<AndroidBuildError>()
        val lines = logs.split("\n")
        
        // Pattern 1: Kotlin/Java Compiler errors e.g. "e: file:///app/src/main/java/com/example/ui/VibeViewModel.kt:463:24 Unresolved reference 'name'."
        val compilerErrorRegex = Regex("""(?:e:\s+)?file:///(.+?):(\d+):(\d+)\s+(.+)""")
        
        for (line in lines) {
            val cleanLine = line.trim()
            val match = compilerErrorRegex.find(cleanLine)
            if (match != null) {
                val filePath = match.groupValues[1]
                val lineNumber = match.groupValues[2].toIntOrNull() ?: 1
                val errorMsg = match.groupValues[4]
                // Deduplicate errors
                val alreadyExists = errors.any { it.message == errorMsg && it.filePath == filePath && it.lineNumber == lineNumber }
                if (!alreadyExists) {
                    errors.add(
                        AndroidBuildError(
                            stepName = stepName,
                            message = errorMsg,
                            filePath = filePath,
                            lineNumber = lineNumber,
                            logsSnippet = cleanLine
                        )
                    )
                }
            }
        }
        
        // Pattern 2: If no compiler errors found, but there's a Gradle task failure, add that as a generic error
        if (errors.isEmpty()) {
            val whatWentWrongIndex = lines.indexOfFirst { it.contains("* What went wrong:") }
            if (whatWentWrongIndex != -1) {
                val snippetLines = lines.subList(whatWentWrongIndex, minOf(whatWentWrongIndex + 8, lines.size))
                val snippet = snippetLines.joinToString("\n").trim()
                errors.add(
                    AndroidBuildError(
                        stepName = stepName,
                        message = "Gradle task execution failed. See details below.",
                        filePath = "build.gradle.kts",
                        lineNumber = 1,
                        logsSnippet = snippet
                    )
                )
            }
        }
        
        // Pattern 3: Flutter or generic errors (e.g. "Error:", "Build failed")
        if (errors.isEmpty()) {
            val errorIndex = lines.indexOfLast { 
                (it.contains("Error:", ignoreCase = true) || 
                it.contains("Build failed", ignoreCase = true) || 
                it.contains("Exception:", ignoreCase = true)) &&
                !it.contains("Process completed with exit code", ignoreCase = true)
            }
            if (errorIndex != -1) {
                val start = maxOf(0, errorIndex - 5)
                val end = minOf(lines.size, errorIndex + 5)
                val snippet = lines.subList(start, end).joinToString("\n").trim()
                val specificErrorLine = lines[errorIndex].trim().replace(Regex("^\\[command\\]|\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z\\s*"), "")
                errors.add(
                    AndroidBuildError(
                        stepName = stepName,
                        message = specificErrorLine.take(200),
                        filePath = ".github/workflows/android.yml",
                        lineNumber = 1,
                        logsSnippet = snippet
                    )
                )
            }
        }
        
        // Pattern 4: Fallback if still empty, collect last few lines to show what went wrong
        if (errors.isEmpty()) {
            val exclusions = listOf("Cleaning up orphan processes", "git config", "git submodule", "Node.js 20 is deprecated")
            val filteredLines = lines.filter { line ->
                line.isNotBlank() && exclusions.none { line.contains(it) }
            }
            val lastLines = filteredLines.takeLast(10).joinToString("\n").trim()
            errors.add(
                AndroidBuildError(
                    stepName = stepName,
                    message = "Build failed at step: '$stepName'.",
                    filePath = ".github/workflows/android.yml",
                    lineNumber = 1,
                    logsSnippet = lastLines.ifEmpty { "Check build tab logs for details." }
                )
            )
        }
        
        return errors
    }

    fun fixSelectedAndroidBuildErrors() {
        val selected = _detectedAndroidBuildErrors.value.filter { it.isSelected }
        if (selected.isEmpty()) return
        
        val project = _currentProject.value ?: return
        
        viewModelScope.launch {
            val errorReportBuilder = StringBuilder()
            for (it in selected) {
                val snippet = getCodeSnippetForError(project.name, it.filePath, it.lineNumber)
                errorReportBuilder.append("- Failed Step: \"${it.stepName}\"\n  Error: \"${it.message}\"\n  File: \"${it.filePath}\" at line ${it.lineNumber}$snippet\n  Details: ${it.logsSnippet}\n")
            }
            
            val errorReport = errorReportBuilder.toString()
            
            clearAndroidBuildErrors()
            
            // Switch tab to Chat to show the ongoing fixing conversation
            _currentTab.value = WorkspaceTab.CHAT
            
            sendPrompt(
                "I encountered the following build errors in my Android build pipeline. " +
                "Please analyze the workspace, find the root cause, and edit or patch the files to fix them completely. " +
                "Make sure your changes are robust and fully functional:\n\n$errorReport"
            )
        }
    }

    // Preferences & Config State
    private val sharedPrefs = application.getSharedPreferences("vibe_coder_prefs", android.content.Context.MODE_PRIVATE)
    private val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    val geminiModels = listOf(
        "gemini-2.0-pro-exp",
        "gemini-2.0-flash",
        "gemini-1.5-pro-002",
        "gemini-1.5-flash-002",
        "gemini-1.5-pro",
        "gemini-1.5-flash"
    )
    val openaiModels = listOf(
        "gpt-4o",
        "gpt-4o-2024-11-20",
        "o1-2024-12-17",
        "o1-preview",
        "o1-mini",
        "gpt-4o-mini",
        "gpt-5-preview",
        "gpt-5.5-preview"
    )
    val claudeModels = listOf(
        "claude-3-5-sonnet-20241022",
        "claude-3-5-haiku-20241022",
        "claude-3-5-sonnet-latest",
        "claude-3-5-haiku-latest",
        "claude-3-opus-latest",
        "claude-3-opus-20240229",
        "claude-4-sonnet",
        "claude-4.5-opus"
    )
    val mistralModels = listOf(
        "mistral-large-2411",
        "pixtral-large-2411",
        "mistral-large-latest",
        "mistral-medium-latest",
        "mistral-small-latest",
        "pixtral-12b-2409",
        "codestral-latest"
    )

    private val _customProvider = MutableStateFlow(sharedPrefs.getString("custom_provider", "gemini") ?: "gemini")
    val customProvider = _customProvider.asStateFlow()

    private val _customApiKey = MutableStateFlow(sharedPrefs.getString("custom_api_key", "") ?: "")
    val customApiKey = _customApiKey.asStateFlow()

    private val _customBaseUrl = MutableStateFlow(sharedPrefs.getString("custom_base_url", "") ?: "")
    val customBaseUrl = _customBaseUrl.asStateFlow()

    private val _customModelId = MutableStateFlow(sharedPrefs.getString("custom_model_id", "gemini-2.0-flash") ?: "gemini-2.0-flash")
    val customModelId = _customModelId.asStateFlow()

    private val _useCustomModel = MutableStateFlow(sharedPrefs.getBoolean("use_custom_model", true)) // Default to true now since we removed "default"
    val useCustomModel = _useCustomModel.asStateFlow()

    private val _maxActionSteps = MutableStateFlow(sharedPrefs.getInt("max_action_steps", 35))
    val maxActionSteps = _maxActionSteps.asStateFlow()

    private val _allowBuildPush = MutableStateFlow(sharedPrefs.getBoolean("allow_build_push", false))
    val allowBuildPush = _allowBuildPush.asStateFlow()

    private val _allowAutoFix = MutableStateFlow(sharedPrefs.getBoolean("allow_auto_fix", false))
    val allowAutoFix = _allowAutoFix.asStateFlow()

    private var lastAutoFixedRunId: Long = -1L
    private var isAutoFixingWebErrors = false

    private val _githubToken = MutableStateFlow(sharedPrefs.getString("github_token", "") ?: "")
    val githubToken = _githubToken.asStateFlow()

    private val _explorerGithubToken = MutableStateFlow(sharedPrefs.getString("explorer_github_token", "") ?: "")
    val explorerGithubToken = _explorerGithubToken.asStateFlow()

    private val _gitProgress = MutableStateFlow("")
    val gitProgress: StateFlow<String> = _gitProgress.asStateFlow()

    private val _githubRepo = MutableStateFlow(sharedPrefs.getString("github_repo", "") ?: "")
    val githubRepo = _githubRepo.asStateFlow()

    private val _explorerGithubRepo = MutableStateFlow(sharedPrefs.getString("explorer_github_repo", "") ?: "")
    val explorerGithubRepo = _explorerGithubRepo.asStateFlow()

    private val _githubBranch = MutableStateFlow(sharedPrefs.getString("github_branch", "main") ?: "main")
    val githubBranch = _githubBranch.asStateFlow()

    private val _explorerGithubBranch = MutableStateFlow(sharedPrefs.getString("explorer_github_branch", "main") ?: "main")
    val explorerGithubBranch = _explorerGithubBranch.asStateFlow()

    private val _buildStatus = MutableStateFlow("Idle")
    val buildStatus = _buildStatus.asStateFlow()

    private val _buildSteps = MutableStateFlow<List<BuildStep>>(emptyList())
    val buildSteps = _buildSteps.asStateFlow()

    private val _buildLogs = MutableStateFlow("")
    val buildLogs = _buildLogs.asStateFlow()

    private val _buildError = MutableStateFlow<String?>(null)
    val buildError = _buildError.asStateFlow()

    private val _isPollingBuild = MutableStateFlow(false)
    val isPollingBuild = _isPollingBuild.asStateFlow()

    private var cachedUsername: String? = null

    private val _gitHubWorkflows = MutableStateFlow<List<GitHubWorkflow>>(emptyList())
    val gitHubWorkflows = _gitHubWorkflows.asStateFlow()

    private val _apkDownloadProgress = MutableStateFlow("")
    val apkDownloadProgress = _apkDownloadProgress.asStateFlow()

    private val _apkDownloadPercentage = MutableStateFlow<Float?>(null)
    val apkDownloadPercentage = _apkDownloadPercentage.asStateFlow()

    private val _localApkPath = MutableStateFlow<String?>(null)
    val localApkPath = _localApkPath.asStateFlow()

    private fun createAiLog(title: String, status: String = "thinking", details: String? = null, lineRange: String? = null): AiActionLog {
        val activeConfig = _customModels.value.find { it.id == _selectedModelId.value }
        val modelName = activeConfig?.let { if (it.alias.isNotBlank()) it.alias else it.modelId } ?: "Unknown Model"
        return AiActionLog(
            title = title,
            status = status,
            details = details,
            lineRange = lineRange,
            modelName = modelName
        )
    }

    private fun updateAiLog(logId: String, status: String, details: String? = null) {
        _aiActionLogs.value = _aiActionLogs.value.map { log ->
            if (log.id == logId) {
                log.copy(
                    status = status,
                    details = details ?: log.details,
                    durationMillis = System.currentTimeMillis() - log.startTime
                )
            } else log
        }
    }

    private val _showGithubPushPrompt = MutableStateFlow(false)
    val showGithubPushPrompt = _showGithubPushPrompt.asStateFlow()

    private val _webPreviewRefreshTrigger = MutableStateFlow(0)
    val webPreviewRefreshTrigger = _webPreviewRefreshTrigger.asStateFlow()

    private val _detectedFramework = MutableStateFlow("")
    val detectedFramework = _detectedFramework.asStateFlow()

    private var lastDownloadedRunId: Long = 0

    private var pollJob: kotlinx.coroutines.Job? = null

    fun saveGithubRepo(repo: String) {
        _githubRepo.value = repo
        sharedPrefs.edit().putString("github_repo", repo).apply()
    }

    fun saveGithubBranch(branch: String) {
        _githubBranch.value = branch
        sharedPrefs.edit().putString("github_branch", branch).apply()
    }

    fun startPollingBuild() {
        if (pollJob != null && pollJob?.isActive == true) return
        _isPollingBuild.value = true
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val moshi = Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
            var consecutiveConnectionErrors = 0
            var cachedTokenForUsername: String? = null
            
            while (_isPollingBuild.value) {
                val repoVal = _githubRepo.value.trim()
                val tokenVal = _githubToken.value.trim()

                if (repoVal.isEmpty() || tokenVal.isEmpty()) {
                    _buildStatus.value = "Waiting for GitHub configuration..."
                    delay(5000)
                    continue
                }

                if (cachedTokenForUsername != tokenVal) {
                    cachedUsername = null
                    cachedTokenForUsername = tokenVal
                }

                val cleanRepo = repoVal.removePrefix("https://github.com/").removePrefix("http://github.com/").removeSuffix(".git")
                val parts = cleanRepo.split("/")
                var owner = ""
                var repoName = ""

                if (parts.size < 2) {
                    if (parts.isNotEmpty() && parts[0].isNotEmpty() && tokenVal.isNotEmpty()) {
                        val currentCached = cachedUsername
                        if (currentCached != null) {
                            owner = currentCached
                            repoName = parts[0]
                        } else {
                            try {
                                val userRequest = Request.Builder()
                                    .url("https://api.github.com/user")
                                    .header("Authorization", "token $tokenVal")
                                    .header("Accept", "application/vnd.github.v3+json")
                                    .get()
                                    .build()
                                val userResponse = client.newCall(userRequest).execute()
                                if (userResponse.isSuccessful) {
                                    val bodyStr = userResponse.body?.string() ?: ""
                                    val userMap = moshi.adapter(Map::class.java).fromJson(bodyStr) as? Map<*, *>
                                    val login = userMap?.get("login") as? String
                                    if (!login.isNullOrBlank()) {
                                        cachedUsername = login
                                        owner = login
                                        repoName = parts[0]
                                    } else {
                                        _buildStatus.value = "Invalid repository format. Could not retrieve username from token."
                                        delay(5000)
                                        continue
                                    }
                                } else {
                                    _buildStatus.value = "Invalid repository format. Failed to fetch GitHub username."
                                    delay(5000)
                                    continue
                                }
                            } catch (e: Exception) {
                                _buildStatus.value = "Error resolving username: ${e.message}"
                                delay(5000)
                                continue
                            }
                        }
                    } else {
                        _buildStatus.value = "Invalid repository format."
                        delay(5000)
                        continue
                    }
                } else {
                    owner = parts[0]
                    repoName = parts[1]
                }

                try {
                    val runsUrl = "https://api.github.com/repos/$owner/$repoName/actions/runs"
                    val runsRequest = Request.Builder()
                        .url(runsUrl)
                        .header("Authorization", "token $tokenVal")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()

                    val runsResponse = client.newCall(runsRequest).execute()
                    if (runsResponse.isSuccessful) {
                        consecutiveConnectionErrors = 0
                        val bodyStr = runsResponse.body?.string() ?: ""
                        val runsMap = moshi.adapter(Map::class.java).fromJson(bodyStr) as? Map<*, *>
                        val workflowRuns = runsMap?.get("workflow_runs") as? List<*>

                        // Fetch GitHub workflows
                        try {
                            val wfUrl = "https://api.github.com/repos/$owner/$repoName/actions/workflows"
                            val wfRequest = Request.Builder()
                                .url(wfUrl)
                                .header("Authorization", "token $tokenVal")
                                .header("Accept", "application/vnd.github.v3+json")
                                .build()
                            val wfResponse = client.newCall(wfRequest).execute()
                            if (wfResponse.isSuccessful) {
                                val wfBodyStr = wfResponse.body?.string() ?: ""
                                val wfMap = moshi.adapter(Map::class.java).fromJson(wfBodyStr) as? Map<*, *>
                                val workflowsList = wfMap?.get("workflows") as? List<*>
                                
                                val parsedWorkflows = mutableListOf<GitHubWorkflow>()
                                workflowsList?.forEach { wfObj ->
                                    val wfItem = wfObj as? Map<*, *>
                                    val wfId = (wfItem?.get("id") as? Number)?.toLong() ?: 0L
                                    val wfName = wfItem?.get("name") as? String ?: "Workflow"
                                    val wfPath = wfItem?.get("path") as? String ?: ""
                                    val wfState = wfItem?.get("state") as? String ?: "active"
                                    
                                    var runStatus = "unknown"
                                    var runConclusion: String? = null
                                    
                                    if (!workflowRuns.isNullOrEmpty()) {
                                        val matchedRun = workflowRuns.firstOrNull { runObj ->
                                            val runMap = runObj as? Map<*, *>
                                            val runWfId = (runMap?.get("workflow_id") as? Number)?.toLong()
                                            runWfId == wfId
                                        } as? Map<*, *>
                                        
                                        if (matchedRun != null) {
                                            runStatus = matchedRun["status"] as? String ?: "unknown"
                                            runConclusion = matchedRun["conclusion"] as? String
                                        }
                                    }
                                    
                                    parsedWorkflows.add(
                                        GitHubWorkflow(
                                            id = wfId,
                                            name = wfName,
                                            path = wfPath,
                                            state = wfState,
                                            latestRunStatus = runStatus,
                                            latestRunConclusion = runConclusion
                                        )
                                    )
                                }
                                
                                val currentList = _gitHubWorkflows.value
                                val updatedList = parsedWorkflows.map { pw ->
                                    val matched = currentList.find { it.id == pw.id }
                                    if (matched != null && matched.isTriggering && (pw.latestRunStatus == "in_progress" || pw.latestRunStatus == "queued")) {
                                        // Once it successfully transitions to run state, we clear triggering
                                        pw.copy(isTriggering = false)
                                    } else if (matched != null && matched.isTriggering) {
                                        pw.copy(isTriggering = true)
                                    } else {
                                        pw
                                    }
                                }
                                _gitHubWorkflows.value = updatedList
                            }
                        } catch (e: Exception) {
                            Log.e("VibeViewModel", "Error polling workflows list: ${e.localizedMessage}")
                        }
                        
                        if (!workflowRuns.isNullOrEmpty()) {
                            val latestRun = workflowRuns[0] as? Map<*, *>
                            val runId = (latestRun?.get("id") as? Number)?.toLong()
                            val status = latestRun?.get("status") as? String ?: "unknown"
                            val conclusion = latestRun?.get("conclusion") as? String
                            val runNumber = (latestRun?.get("run_number") as? Number)?.toInt() ?: 1

                            _buildStatus.value = "Run #$runNumber: Status = $status" + (if (conclusion != null) " ($conclusion)" else "")

                            if (status == "completed" && conclusion == "success" && runId != null) {
                                downloadAndUnzipApk(owner, repoName, runId, tokenVal)
                            }

                            if (runId != null) {
                                val jobsUrl = "https://api.github.com/repos/$owner/$repoName/actions/runs/$runId/jobs"
                                val jobsRequest = Request.Builder()
                                    .url(jobsUrl)
                                    .header("Authorization", "token $tokenVal")
                                    .header("Accept", "application/vnd.github.v3+json")
                                    .build()

                                val jobsResponse = client.newCall(jobsRequest).execute()
                                if (jobsResponse.isSuccessful) {
                                    val jobsBodyStr = jobsResponse.body?.string() ?: ""
                                    val jobsMap = moshi.adapter(Map::class.java).fromJson(jobsBodyStr) as? Map<*, *>
                                    val jobsList = jobsMap?.get("jobs") as? List<*>
                                    
                                    if (!jobsList.isNullOrEmpty()) {
                                        val primaryJob = jobsList[0] as? Map<*, *>
                                        val stepsList = primaryJob?.get("steps") as? List<*>
                                        val parsedSteps = mutableListOf<BuildStep>()
                                        
                                        stepsList?.forEach { stepObj ->
                                            val stepMap = stepObj as? Map<*, *>
                                            val stepName = stepMap?.get("name") as? String ?: "Step"
                                            val stepStatus = stepMap?.get("status") as? String ?: "queued"
                                            val stepConclusion = stepMap?.get("conclusion") as? String
                                            val stepNumber = (stepMap?.get("number") as? Number)?.toInt() ?: 1
                                            
                                            parsedSteps.add(BuildStep(stepName, stepStatus, stepConclusion, stepNumber))
                                        }
                                        _buildSteps.value = parsedSteps

                                        val failedStep = parsedSteps.find { it.conclusion == "failure" }
                                        if (failedStep != null) {
                                            _buildError.value = "Build failed at step: '${failedStep.name}'."
                                        } else {
                                            _buildError.value = null
                                            _detectedAndroidBuildErrors.value = emptyList()
                                        }

                                        val jobId = (primaryJob?.get("id") as? Number)?.toLong()
                                        if (jobId != null) {
                                            val logsUrl = "https://api.github.com/repos/$owner/$repoName/actions/jobs/$jobId/logs"
                                            val logsRequest = Request.Builder()
                                                .url(logsUrl)
                                                .header("Authorization", "token $tokenVal")
                                                .header("Accept", "application/vnd.github.v3+json")
                                                .build()

                                            val logsResponse = client.newCall(logsRequest).execute()
                                            if (logsResponse.isSuccessful) {
                                                val rawLogs = logsResponse.body?.string() ?: ""
                                                val cleanLogs = rawLogs.replace(Regex("\u001B\\[[;\\d]*m"), "")
                                                _buildLogs.value = cleanLogs
                                                
                                                if (failedStep != null) {
                                                    val parsedErrors = parseAndroidBuildErrors(cleanLogs, failedStep.name)
                                                    _detectedAndroidBuildErrors.value = parsedErrors
                                                    if (_allowAutoFix.value && parsedErrors.isNotEmpty() && lastAutoFixedRunId != runId) {
                                                        lastAutoFixedRunId = runId
                                                        _detectedAndroidBuildErrors.value = parsedErrors.map { it.copy(isSelected = true) }
                                                        viewModelScope.launch(Dispatchers.Main) {
                                                            fixSelectedAndroidBuildErrors()
                                                        }
                                                    }
                                                }
                                            } else {
                                                if (logsResponse.code == 404 && (status == "in_progress" || status == "queued")) {
                                                    _buildLogs.value = "Build is running on Github... 🚀\nGitHub Actions does not provide streaming logs via the API until the job finishes or reaches a checkpoint. Please wait."
                                                } else {
                                                    _buildLogs.value = "Logs are not available yet (Status: ${logsResponse.code})."
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            _buildStatus.value = "No workflow runs found. Push code to trigger a build!"
                            _buildSteps.value = emptyList()
                            _buildLogs.value = ""
                        }
                    } else {
                        consecutiveConnectionErrors++
                        if (consecutiveConnectionErrors >= 5) {
                            _buildStatus.value = "Error fetching runs: ${runsResponse.code}"
                            _buildError.value = "Failed to communicate with GitHub. Check your repository URL and token permissions."
                        }
                    }
                } catch (e: Exception) {
                    consecutiveConnectionErrors++
                    Log.e("VibeViewModel", "Polling connection error ($consecutiveConnectionErrors): ${e.localizedMessage}")
                    if (consecutiveConnectionErrors >= 5) {
                        _buildStatus.value = "Connection error"
                        _buildError.value = e.localizedMessage
                    }
                }

                delay(5000)
            }
        }
    }

    fun triggerAllWorkflows() {
        val repoVal = _githubRepo.value.trim()
        val tokenVal = _githubToken.value.trim()
        val branchVal = _githubBranch.value.trim().ifBlank { "main" }

        if (repoVal.isEmpty() || tokenVal.isEmpty()) {
            _buildError.value = "Please configure your GitHub Repository and Token first."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val cleanRepo = repoVal.removePrefix("https://github.com/").removePrefix("http://github.com/").removeSuffix(".git")
            val parts = cleanRepo.split("/")
            var owner = ""
            var repoName = ""

            if (parts.size >= 2) {
                owner = parts[0]
                repoName = parts[1]
            } else {
                owner = cachedUsername ?: ""
                repoName = parts[0]
            }

            if (owner.isEmpty() || repoName.isEmpty()) {
                _buildError.value = "Invalid repository format."
                return@launch
            }

            val currentWfs = _gitHubWorkflows.value
            _gitHubWorkflows.value = currentWfs.map { 
                if (it.state == "active") it.copy(isTriggering = true) else it
            }

            val client = OkHttpClient()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            currentWfs.forEach { wf ->
                if (wf.state == "active") {
                    try {
                        val dispatchUrl = "https://api.github.com/repos/$owner/$repoName/actions/workflows/${wf.id}/dispatches"
                        val jsonBody = "{\"ref\":\"$branchVal\"}"
                        val requestBody = okhttp3.RequestBody.create(mediaType, jsonBody)
                        val request = Request.Builder()
                            .url(dispatchUrl)
                            .header("Authorization", "token $tokenVal")
                            .header("Accept", "application/vnd.github.v3+json")
                            .post(requestBody)
                            .build()

                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            Log.d("VibeViewModel", "Successfully triggered workflow: ${wf.name}")
                        } else {
                            Log.e("VibeViewModel", "Failed to trigger workflow ${wf.name}: ${response.code}")
                        }
                    } catch (e: Exception) {
                        Log.e("VibeViewModel", "Error triggering workflow ${wf.name}: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    private fun showDownloadNotification(progress: Int, title: String, content: String, isFinished: Boolean = false) {
        try {
            val context = getApplication<Application>()
            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "download_channel",
                    "Build Download Progress",
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }
            
            val builder = androidx.core.app.NotificationCompat.Builder(context, "download_channel")
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                
            if (isFinished) {
                builder.setProgress(0, 0, false)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
            } else if (progress >= 0) {
                builder.setProgress(100, progress, false)
            } else {
                builder.setProgress(100, 0, true)
            }
            
            notificationManager.notify(1337, builder.build())
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    fun downloadAndUnzipApk(owner: String, repoName: String, runId: Long, tokenVal: String) {
        if (lastDownloadedRunId == runId) return
        lastDownloadedRunId = runId
        
        applicationScope.launch(Dispatchers.IO) {
            _apkDownloadProgress.value = "Fetching build artifacts..."
            showDownloadNotification(-1, "Pencode AI Build", "Fetching build artifacts...")
            try {
                val artifactsUrl = "https://api.github.com/repos/$owner/$repoName/actions/runs/$runId/artifacts"
                val request = Request.Builder()
                    .url(artifactsUrl)
                    .header("Authorization", "token $tokenVal")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val response = OkHttpClient().newCall(request).execute()
                if (!response.isSuccessful) {
                    _apkDownloadProgress.value = "Artifact fetch failed (Status: ${response.code})"
                    lastDownloadedRunId = 0 // retry on next loop
                    return@launch
                }
                
                val bodyStr = response.body?.string() ?: ""
                val moshi = Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                val map = moshi.adapter(Map::class.java).fromJson(bodyStr) as? Map<*, *>
                val artifacts = map?.get("artifacts") as? List<*>
                if (artifacts.isNullOrEmpty()) {
                    _apkDownloadProgress.value = "Waiting for APK package release to complete..."
                    lastDownloadedRunId = 0 // retry on next loop
                    return@launch
                }
                
                // Find first artifact
                val firstArtifact = artifacts.firstOrNull() as? Map<*, *>
                val artifactId = (firstArtifact?.get("id") as? Number)?.toLong()
                val downloadUrl = firstArtifact?.get("archive_download_url") as? String
                
                if (artifactId == null || downloadUrl == null) {
                    _apkDownloadProgress.value = "Invalid artifact data."
                    _apkDownloadPercentage.value = null
                    lastDownloadedRunId = 0
                    return@launch
                }
                
                _apkDownloadProgress.value = "Downloading built APK..."
                _apkDownloadPercentage.value = 0f
                val downloadRequest = Request.Builder()
                    .url(downloadUrl)
                    .header("Authorization", "token $tokenVal")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                
                val client = OkHttpClient.Builder().followRedirects(false).build()
                var downloadResponse = client.newCall(downloadRequest).execute()
                
                var redirectCount = 0
                while (downloadResponse.isRedirect && redirectCount < 5) {
                    val location = downloadResponse.header("Location")
                    if (location == null) break
                    val redirectedRequest = Request.Builder().url(location).build()
                    downloadResponse.close()
                    downloadResponse = client.newCall(redirectedRequest).execute()
                    redirectCount++
                }

                if (!downloadResponse.isSuccessful) {
                    _apkDownloadProgress.value = "Download failed (Status: ${downloadResponse.code})"
                    _apkDownloadPercentage.value = null
                    lastDownloadedRunId = 0
                    return@launch
                }
                
                val body = downloadResponse.body
                if (body == null) {
                    _apkDownloadProgress.value = "Empty response body."
                    _apkDownloadPercentage.value = null
                    lastDownloadedRunId = 0
                    return@launch
                }
                
                val contentLength = body.contentLength()
                val context = getApplication<Application>()
                val cacheDir = context.cacheDir
                val tempZipFile = java.io.File(cacheDir, "downloaded_artifacts.zip")
                if (tempZipFile.exists()) {
                    tempZipFile.delete()
                }
                
                val inputStream = java.io.BufferedInputStream(body.byteStream())
                val outputStream = java.io.BufferedOutputStream(java.io.FileOutputStream(tempZipFile))
                val buffer = ByteArray(131072) // 128KB for faster download
                var bytesRead: Int
                var totalBytesRead = 0L
                
                // Throttle progress updates to avoid UI stuttering which also slows down download
                var lastUpdateBytes = 0L
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    // Update progress every 1MB or so
                    if (totalBytesRead - lastUpdateBytes > 1048576) {
                        lastUpdateBytes = totalBytesRead
                        if (contentLength > 0) {
                            val progress = (totalBytesRead * 100f / contentLength)
                            _apkDownloadPercentage.value = progress
                            _apkDownloadProgress.value = "Downloading built APK (${progress.toInt()}%)..."
                            showDownloadNotification(progress.toInt(), "Pencode AI Build", "Downloading built APK (${progress.toInt()}%)...")
                        } else {
                            _apkDownloadProgress.value = "Downloading built APK (${(totalBytesRead / 1024 / 1024)} MB)..."
                            showDownloadNotification(-1, "Pencode AI Build", "Downloading built APK (${(totalBytesRead / 1024 / 1024)} MB)...")
                        }
                    }
                }
                outputStream.close()
                inputStream.close()
                
                _apkDownloadProgress.value = "Extracting app package (Unzipping)..."
                _apkDownloadPercentage.value = null
                showDownloadNotification(-1, "Pencode AI Build", "Extracting app package (Unzipping)...")
                
                val outputApkFile = java.io.File(cacheDir, "downloaded_app.apk")
                if (outputApkFile.exists()) {
                    outputApkFile.delete()
                }
                
                val zipIn = java.util.zip.ZipInputStream(java.io.BufferedInputStream(java.io.FileInputStream(tempZipFile)))
                var entry = zipIn.nextEntry
                var apkFound = false
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                        val outStream = java.io.BufferedOutputStream(java.io.FileOutputStream(outputApkFile))
                        val outBuffer = ByteArray(131072) // 128KB for faster extraction
                        var len = zipIn.read(outBuffer)
                        while (len > 0) {
                            outStream.write(outBuffer, 0, len)
                            len = zipIn.read(outBuffer)
                        }
                        outStream.close()
                        apkFound = true
                        break
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
                zipIn.close()
                
                if (tempZipFile.exists()) {
                    tempZipFile.delete()
                }
                
                if (apkFound && outputApkFile.exists() && outputApkFile.length() > 0) {
                    _localApkPath.value = outputApkFile.absolutePath
                    _apkDownloadProgress.value = "Success: App compiled & ready to install!"
                    showDownloadNotification(100, "Pencode AI Build", "Success: App compiled & ready to install!", true)
                    // Switch to BUILD tab automatically
                    _currentTab.value = WorkspaceTab.ANDROID_BUILD
                } else {
                    _apkDownloadProgress.value = "Unzip completed but no APK found."
                    showDownloadNotification(0, "Pencode AI Build", "Unzip completed but no APK found.", true)
                    lastDownloadedRunId = 0
                }
            } catch (e: Exception) {
                _apkDownloadProgress.value = "Extraction failed: ${e.localizedMessage}"
                _apkDownloadPercentage.value = null
                showDownloadNotification(0, "Pencode AI Build", "Extraction failed: ${e.localizedMessage}", true)
                lastDownloadedRunId = 0
            }
        }
    }

    fun installApk() {
        val apkPath = _localApkPath.value ?: return
        val context = getApplication<Application>()
        val file = java.io.File(apkPath)
        if (!file.exists()) {
            _apkDownloadProgress.value = "APK file not found on disk."
            return
        }
        
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _apkDownloadProgress.value = "Installation failed: ${e.localizedMessage}"
        }
    }

    fun stopPollingBuild() {
        _isPollingBuild.value = false
        pollJob?.cancel()
        pollJob = null
    }

    fun saveCustomSettings(provider: String, apiKey: String, baseUrl: String, modelId: String, useCustom: Boolean) {
        _customProvider.value = provider
        _customApiKey.value = apiKey
        _customBaseUrl.value = baseUrl
        _customModelId.value = modelId
        _useCustomModel.value = useCustom

        sharedPrefs.edit()
            .putString("custom_provider", provider)
            .putString("custom_api_key", apiKey)
            .putString("custom_base_url", baseUrl)
            .putString("custom_model_id", modelId)
            .putBoolean("use_custom_model", useCustom)
            .apply()
    }

    fun saveMaxActionSteps(steps: Int) {
        _maxActionSteps.value = steps
        sharedPrefs.edit().putInt("max_action_steps", steps).apply()
    }

    fun saveAllowBuildPush(allowed: Boolean) {
        _allowBuildPush.value = allowed
        sharedPrefs.edit().putBoolean("allow_build_push", allowed).apply()
    }

    fun saveAllowAutoFix(allowed: Boolean) {
        _allowAutoFix.value = allowed
        sharedPrefs.edit().putBoolean("allow_auto_fix", allowed).apply()
    }

    fun loadCustomModels() {
        val jsonStr = sharedPrefs.getString("custom_models_json", null)
        val list = mutableListOf<CustomModelConfig>()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        CustomModelConfig(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            alias = obj.optString("alias", ""),
                            provider = obj.optString("provider", "gemini"),
                            apiKey = obj.optString("apiKey", ""),
                            baseUrl = obj.optString("baseUrl", ""),
                            modelId = obj.optString("modelId", "")
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _customModels.value = list
        
        val currentSelected = sharedPrefs.getString("selected_model_id", "") ?: ""
        _selectedModelId.value = currentSelected
        if (list.isNotEmpty() && (currentSelected.isEmpty() || list.none { it.id == currentSelected })) {
            selectModel(list.first().id)
        }
    }

    fun saveCustomModels(models: List<CustomModelConfig>) {
        _customModels.value = models
        try {
            val array = JSONArray()
            for (m in models) {
                val obj = JSONObject()
                obj.put("id", m.id)
                obj.put("alias", m.alias)
                obj.put("provider", m.provider)
                obj.put("apiKey", m.apiKey)
                obj.put("baseUrl", m.baseUrl)
                obj.put("modelId", m.modelId)
                array.put(obj)
            }
            sharedPrefs.edit()
                .putString("custom_models_json", array.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addCustomModel(alias: String, provider: String, apiKey: String, baseUrl: String, modelId: String) {
        val newModel = CustomModelConfig(
            alias = alias,
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            modelId = modelId
        )
        val updatedList = _customModels.value + newModel
        saveCustomModels(updatedList)
        selectModel(newModel.id)
    }

    fun deleteCustomModel(id: String) {
        val updatedList = _customModels.value.filter { it.id != id }
        saveCustomModels(updatedList)
        if (_selectedModelId.value == id) {
            if (updatedList.isNotEmpty()) {
                selectModel(updatedList.first().id)
            } else {
                selectModel("")
            }
        }
    }

    fun selectModel(id: String) {
        _selectedModelId.value = id
        sharedPrefs.edit().putString("selected_model_id", id).apply()
    }

    fun saveGithubToken(token: String) {
        _githubToken.value = token
        sharedPrefs.edit().putString("github_token", token).apply()
    }

    fun saveExplorerGithubToken(token: String) {
        _explorerGithubToken.value = token
        sharedPrefs.edit().putString("explorer_github_token", token).apply()
    }

    fun saveExplorerGithubRepo(repo: String) {
        _explorerGithubRepo.value = repo
        sharedPrefs.edit().putString("explorer_github_repo", repo).apply()
    }

    fun saveExplorerGithubBranch(branch: String) {
        _explorerGithubBranch.value = branch
        sharedPrefs.edit().putString("explorer_github_branch", branch).apply()
    }

    fun cloneGitRepo(projectName: String, repo: String, token: String?, branch: String, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _gitProgress.value = "Initializing clone..."
            val result = repository.cloneRepository(projectName, repo, token, branch) { progress ->
                _gitProgress.value = progress
            }
            if (result.isSuccess) {
                _gitProgress.value = "Clone complete!"
                onComplete(result)
                loadProjects()
                delay(3000)
                _gitProgress.value = ""
            } else {
                _gitProgress.value = "Clone failed: ${result.exceptionOrNull()?.localizedMessage ?: "Unknown error"}"
                onComplete(result)
                loadProjects()
                delay(6000)
                _gitProgress.value = ""
            }
        }
    }

    fun pushGitRepo(projectName: String, repo: String, token: String, branch: String, force: Boolean, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            clearAndroidBuildErrors()
            _gitProgress.value = "Initializing push..."
            val result = repository.pushToGitHub(projectName, repo, token, branch, force) { progress ->
                _gitProgress.value = progress
            }
            if (result.isSuccess) {
                _gitProgress.value = "Push complete!"
                onComplete(result)
                delay(3000)
                _gitProgress.value = ""
            } else {
                _gitProgress.value = "Push failed: ${result.exceptionOrNull()?.localizedMessage ?: "Unknown error"}"
                onComplete(result)
                delay(6000)
                _gitProgress.value = ""
            }
        }
    }

    // Screen State
    private val _currentProject = MutableStateFlow<ProjectEntity?>(null)
    val currentProject: StateFlow<ProjectEntity?> = _currentProject.asStateFlow()

    private val _projectsList = MutableStateFlow<List<ProjectEntity>>(emptyList())
    val projectsList: StateFlow<List<ProjectEntity>> = _projectsList.asStateFlow()

    private val _projectFiles = MutableStateFlow<List<ProjectFileEntity>>(emptyList())
    val projectFiles: StateFlow<List<ProjectFileEntity>> = _projectFiles.asStateFlow()

    private val _activeFile = MutableStateFlow<ProjectFileEntity?>(null)
    val activeFile: StateFlow<ProjectFileEntity?> = _activeFile.asStateFlow()

    private val _editorContent = MutableStateFlow("")
    val editorContent: StateFlow<String> = _editorContent.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageEntity>> = _chatMessages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _currentTab = MutableStateFlow(WorkspaceTab.CHAT)
    val currentTab: StateFlow<WorkspaceTab> = _currentTab.asStateFlow()

    // Status logs during agent run
    private val _agentStatus = MutableStateFlow("")
    val agentStatus: StateFlow<String> = _agentStatus.asStateFlow()

    // Chronological AI Action logs
    private val _aiActionLogs = MutableStateFlow<List<AiActionLog>>(emptyList())
    val aiActionLogs: StateFlow<List<AiActionLog>> = _aiActionLogs.asStateFlow()

    private val _isLoadingWorkspace = MutableStateFlow(false)
    val isLoadingWorkspace: StateFlow<Boolean> = _isLoadingWorkspace.asStateFlow()

    fun deleteMessage(message: ChatMessageEntity) {
        val project = _currentProject.value ?: return
        viewModelScope.launch {
            repository.deleteChatMessage(message)
            _chatMessages.value = repository.getChatsForProject(project.name)
        }
    }

    fun editMessage(message: ChatMessageEntity, newContent: String) {
        val project = _currentProject.value ?: return
        viewModelScope.launch {
            val updated = message.copy(content = newContent)
            repository.insertChatMessage(updated) // Room replaces by ID
            _chatMessages.value = repository.getChatsForProject(project.name)
        }
    }

    fun regenerateResponse(lastUserMessage: ChatMessageEntity) {
        val project = _currentProject.value ?: return
        viewModelScope.launch {
            // Find messages after this user message and delete them
            val allChats = _chatMessages.value
            val index = allChats.indexOfFirst { it.id == lastUserMessage.id }
            if (index != -1) {
                for (i in index + 1 until allChats.size) {
                    repository.deleteChatMessage(allChats[i])
                }
                _chatMessages.value = repository.getChatsForProject(project.name)
                // Re-send the prompt
                sendPrompt(lastUserMessage.content, isRegenerate = true)
            }
        }
    }

    private var currentAiJob: kotlinx.coroutines.Job? = null

    private val _editHistory = MutableStateFlow<List<EditRecord>>(emptyList())
    val editHistory: StateFlow<List<EditRecord>> = _editHistory.asStateFlow()

    fun clearEditHistory() {
        _editHistory.value = emptyList()
    }

    private val _isInterrupted = MutableStateFlow(false)
    val isInterrupted: StateFlow<Boolean> = _isInterrupted.asStateFlow()

    fun skipInterruption() {
        _isInterrupted.value = false
    }

    fun continuePrompt() {
        _isInterrupted.value = false
        sendPrompt("Please continue the previous code implementation. Pick up exactly where you left off, make sure to finish any incomplete files, functions, or blocks, and output the necessary tool call.")
    }

    fun stopThinking() {
        currentAiJob?.cancel()
        _isThinking.value = false
        _agentStatus.value = "AI task stopped by user."
        
        // Optionally add a log for cancellation
        val cancelLog = AiActionLog(
            title = "Task Stopped",
            status = "failed",
            details = "Execution was cancelled by the user."
        )
        _aiActionLogs.value = _aiActionLogs.value + cancelLog
    }

    private val _webConsoleLogs = MutableStateFlow<List<WebConsoleLog>>(emptyList())
    val webConsoleLogs = _webConsoleLogs.asStateFlow()

    data class WebConsoleLog(
        val message: String,
        val level: String,
        val sourceId: String,
        val lineNumber: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun addWebConsoleLog(message: String, level: String, sourceId: String, lineNumber: Int) {
        val log = WebConsoleLog(message, level, sourceId, lineNumber)
        _webConsoleLogs.value = _webConsoleLogs.value + log
    }

    fun clearWebConsoleLogs() {
        _webConsoleLogs.value = emptyList()
    }

    fun clearTerminal() {
        _terminalOutput.value = "Terminal cleared.\n$"
    }

    private val _terminalOutput = MutableStateFlow<String>(
        "Welcome to PenCode AI Terminal!\n" +
        "Type standard Unix/POSIX commands like 'ls', 'pwd', 'mkdir src', 'touch index.html' to interact with your physical folder.\n\n$"
    )
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    init {
        val database = VibeDatabase.getDatabase(application)
        repository = VibeRepository(database.vibeDao(), application)
        loadProjects()
        loadCustomModels()
    }

    fun loadProjects() {
        viewModelScope.launch {
            _projectsList.value = repository.getAllProjects()
        }
    }

    fun createProject(name: String, description: String, templateKey: String?, urisToImport: List<android.net.Uri> = emptyList()) {
        viewModelScope.launch {
            repository.createProject(name, description, templateKey)
            if (urisToImport.isNotEmpty()) {
                repository.importFilesToProject(name, urisToImport)
            }
            loadProjects()
            selectProject(ProjectEntity(name, description, System.currentTimeMillis(), templateKey))
        }
    }

    fun importFilesFromDevice(uris: List<android.net.Uri>) {
        val project = _currentProject.value ?: return
        viewModelScope.launch {
            repository.importFilesToProject(project.name, uris)
            loadProjectDetails(project.name)
        }
    }

    fun deleteProject(projectName: String) {
        viewModelScope.launch {
            repository.deleteProject(projectName)
            if (_currentProject.value?.name == projectName) {
                _currentProject.value = null
                _projectFiles.value = emptyList()
                _activeFile.value = null
                _chatMessages.value = emptyList()
            }
            loadProjects()
        }
    }

    fun selectProject(project: ProjectEntity) {
        viewModelScope.launch {
            _isLoadingWorkspace.value = true
            _currentProject.value = project
            _currentTab.value = WorkspaceTab.CHAT
            _agentStatus.value = ""
            
            try {
                // Sync files on startup to verify integrity
                repository.syncDatabaseToStorage(project.name)
                repository.syncStorageToDatabase(project.name)
                loadProjectDetails(project.name)
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    loadProjectDetails(project.name)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            } finally {
                _isLoadingWorkspace.value = false
            }
        }
    }

    fun exitProject() {
        _currentProject.value = null
        _projectFiles.value = emptyList()
        _activeFile.value = null
        _chatMessages.value = emptyList()
        _aiActionLogs.value = emptyList()
    }

    fun dismissGithubPushPrompt() {
        _showGithubPushPrompt.value = false
    }

    fun acceptGithubPushPrompt() {
        _showGithubPushPrompt.value = false
        val repo = _githubRepo.value
        val token = _githubToken.value
        val branch = _githubBranch.value.ifBlank { "main" }
        val project = _currentProject.value

        if (project != null && repo.isNotBlank() && token.isNotBlank()) {
            pushGitRepo(project.name, repo, token, branch, force = true) { _ -> }
        } else {
            changeTab(WorkspaceTab.ANDROID_BUILD)
        }
    }

    private suspend fun loadProjectDetails(projectName: String) {
        val files = repository.getFilesForProject(projectName)
        val visibleFiles = files.filter { it.path != "browser_memory.md" && it.path != "memory.md" }
        _projectFiles.value = visibleFiles
        
        // Auto-select index.html or first file to view in editor
        val defaultFile = visibleFiles.find { it.path == "index.html" } ?: visibleFiles.firstOrNull()
        selectActiveFile(defaultFile)

        _chatMessages.value = repository.getChatsForProject(projectName)
    }

    fun selectActiveFile(file: ProjectFileEntity?) {
        _activeFile.value = file
        _editorContent.value = file?.content ?: ""
    }

    fun updateEditorContent(content: String) {
        _editorContent.value = content
    }

    fun saveActiveFile() {
        val project = _currentProject.value ?: return
        val file = _activeFile.value ?: return
        viewModelScope.launch {
            repository.saveFile(project.name, file.path, _editorContent.value)
            loadProjectDetails(project.name)
        }
    }

    suspend fun autoSaveActiveFile() {
        val project = _currentProject.value ?: return
        val file = _activeFile.value ?: return
        if (_editorContent.value != file.content) {
            repository.saveFile(project.name, file.path, _editorContent.value)
            loadProjectDetails(project.name)
        }
    }

    fun createNewFile(path: String) {
        val project = _currentProject.value ?: return
        if (path.isBlank()) return
        viewModelScope.launch {
            repository.saveFile(project.name, path, "")
            loadProjectDetails(project.name)
            // Select the newly created file
            val files = repository.getFilesForProject(project.name)
            val newFile = files.find { it.path == path }
            selectActiveFile(newFile)
            _currentTab.value = WorkspaceTab.CODE
        }
    }

    fun deleteCurrentFile(path: String) {
        val project = _currentProject.value ?: return
        viewModelScope.launch {
            repository.deleteFile(project.name, path)
            loadProjectDetails(project.name)
        }
    }

    fun renameFile(oldPath: String, newPath: String) {
        val project = _currentProject.value ?: return
        viewModelScope.launch {
            repository.renameFile(project.name, oldPath, newPath)
            loadProjectDetails(project.name)
        }
    }

    fun moveFile(oldPath: String, newPath: String) {
        val project = _currentProject.value ?: return
        viewModelScope.launch {
            repository.moveFile(project.name, oldPath, newPath)
            loadProjectDetails(project.name)
        }
    }

    fun changeTab(tab: WorkspaceTab) {
        _currentTab.value = tab
        if (tab == WorkspaceTab.ANDROID_BUILD) {
            startPollingBuild()
        } else {
            stopPollingBuild()
        }
    }

    fun runTerminalCommand(command: String) {
        val project = _currentProject.value ?: return
        if (command.isBlank()) return
        viewModelScope.launch {
            autoSaveActiveFile()
            _terminalOutput.value += " $command"
            val result = repository.executeCommand(project.name, command)
            val resultStr = if (result.isEmpty()) "" else "\n$result"
            _terminalOutput.value += "$resultStr\n\n$"
            loadProjectDetails(project.name)
        }
    }

    private fun optimizeConversationHistory(history: List<Content>): List<Content> {
        val seenReadFilePaths = mutableSetOf<String>()
        val optimized = history.toMutableList()
        
        for (i in optimized.indices.reversed()) {
            val content = optimized[i]
            if (content.role == "user") {
                val updatedParts = content.parts.map { part ->
                    val text = part.text
                    if (text != null && text.contains("System/Tool Output for")) {
                        if (text.contains("System/Tool Output for 'read_file'") || text.contains("System/Tool Output for 'read_file_range'")) {
                            val fileLine = text.lineSequence().firstOrNull { it.contains("--- File:") }
                            if (fileLine != null) {
                                val filePath = fileLine.substringAfter("--- File:")
                                    .substringBefore(" (Lines")
                                    .substringBefore(" (lines")
                                    .substringBefore("---")
                                    .trim()
                                if (filePath.isNotEmpty()) {
                                    if (seenReadFilePaths.contains(filePath)) {
                                        val isRange = text.contains("read_file_range")
                                        val toolName = if (isRange) "read_file_range" else "read_file"
                                        part.copy(text = "System/Tool Output for '$toolName' (File: $filePath): [Omitted older content of $filePath to save context/tokens. Refer to the most recent read of this file below for full/range contents.]")
                                    } else {
                                        seenReadFilePaths.add(filePath)
                                        // If the single file content read is extremely massive, truncate it to save tokens and prevent 429 errors
                                        if (text.length > 20000) {
                                            val header = text.take(5000)
                                            val footer = text.takeLast(5000)
                                            val truncatedMsg = "\n\n[... Truncated ${text.length - 10000} characters of file content to prevent Rate Limit / Token bloat ...]\n\n"
                                            part.copy(text = header + truncatedMsg + footer)
                                        } else {
                                            part
                                        }
                                    }
                                } else {
                                    part
                                }
                            } else {
                                part
                            }
                        } else {
                            // Truncate extremely large command outputs, directory listings, or search results
                            if (text.length > 10000) {
                                val header = text.take(3000)
                                val footer = text.takeLast(3000)
                                val truncatedMsg = "\n\n[... Truncated ${text.length - 6000} characters of output to prevent Rate Limit / Token bloat ...]\n\n"
                                part.copy(text = header + truncatedMsg + footer)
                            } else {
                                part
                            }
                        }
                    } else {
                        part
                    }
                }
                optimized[i] = content.copy(parts = updatedParts)
            }
        }
        return optimized
    }

    private fun appendToTerminal(command: String, result: String) {
        val resultStr = if (result.isEmpty()) "(no output)" else result
        _terminalOutput.value += " $command\n$resultStr\n\n$"
    }

    fun sendPrompt(userPrompt: String, attachments: List<AttachedFile> = emptyList(), isRegenerate: Boolean = false) {
        val project = _currentProject.value ?: return
        if (userPrompt.isBlank() && attachments.isEmpty()) return

        currentAiJob = viewModelScope.launch {
            autoSaveActiveFile()
            var finalPrompt = userPrompt
            attachments.forEach { file ->
                if (file.isImage && file.contentAsBase64 != null) {
                    finalPrompt += "\n\n[IMAGE_BASE64: data:${file.mimeType};base64,${file.contentAsBase64}]"
                } else if (!file.isImage && file.contentAsText != null) {
                    finalPrompt += "\n\n[Attached File: ${file.name}]\n${file.contentAsText}\n[/Attached File]"
                }
            }

            // 1. Save user prompt to Chat Database
            if (!isRegenerate) {
                val userMsg = ChatMessageEntity(
                    projectName = project.name,
                    role = "user",
                    content = finalPrompt,
                    timestamp = System.currentTimeMillis()
                )
                repository.insertChatMessage(userMsg)
            }
            _chatMessages.value = repository.getChatsForProject(project.name)

            // 2. Clear previous logs and initialize thinking state
            _aiActionLogs.value = emptyList()
            _todoList.value = emptyList()
            _isThinking.value = true
            _agentStatus.value = "AI is thinking..."

            // Re-enable conversation history with highly optimized, token-saving action summaries
            val historyEntities = repository.getChatsForProject(project.name)
            val history = mutableListOf<Content>()

            if (historyEntities.size > 1) {
                val historicalMessages = historyEntities.dropLast(1)
                val lastAssistantEntity = historicalMessages.lastOrNull { it.role == "assistant" }
                
                if (lastAssistantEntity != null) {
                    val lastUserEntity = historicalMessages.lastOrNull { 
                        it.role == "user" && it.timestamp <= lastAssistantEntity.timestamp 
                    }
                    val previousUserPrompt = lastUserEntity?.content ?: "[Previous Request]"

                    // Alternate role: add the actual previous user query
                    history.add(Content(
                        role = "user",
                        parts = listOf(Part(text = previousUserPrompt))
                    ))

                    // Extract and compact file actions
                    val fileOperations = mutableListOf<String>()
                    if (lastAssistantEntity.aiActionLogsJson != null) {
                        try {
                            val listType = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
                            val logs = moshi.adapter<List<AiActionLog>>(listType).fromJson(lastAssistantEntity.aiActionLogsJson)
                            if (logs != null) {
                                for (log in logs) {
                                    val titleLower = log.title.lowercase()
                                    val filePath = log.details?.lineSequence()?.firstOrNull()?.let { line ->
                                        val match = """(?:from|to|file|written to|read from|for)?\s*([a-zA-Z0-9_\-\./]+)""".toRegex(RegexOption.IGNORE_CASE).find(line)
                                        match?.groupValues?.get(1) ?: line
                                    } ?: ""
                                    val fileName = java.io.File(filePath).name.ifEmpty { filePath }

                                    if (fileName.isNotEmpty()) {
                                        when {
                                            titleLower.contains("read_file") || titleLower.contains("read file") -> {
                                                fileOperations.add("read:$fileName")
                                            }
                                            titleLower.contains("edit_file") || titleLower.contains("edit file") || titleLower.contains("modify") -> {
                                                fileOperations.add("modified:$fileName")
                                            }
                                            titleLower.contains("patch_file") || titleLower.contains("patch file") || titleLower.contains("patch") -> {
                                                fileOperations.add("modified:$fileName")
                                            }
                                            titleLower.contains("append") -> {
                                                fileOperations.add("append:$fileName")
                                            }
                                            titleLower.contains("write_file") || titleLower.contains("write file") || titleLower.contains("create") -> {
                                                fileOperations.add("create:$fileName")
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }

                    val actionsStr = if (fileOperations.isNotEmpty()) {
                        fileOperations.joinToString(", ")
                    } else {
                        ""
                    }

                    val assistantText = buildString {
                        if (actionsStr.isNotEmpty()) {
                            append(actionsStr)
                            append("\n\n")
                        }
                        append(lastAssistantEntity.content)
                    }

                    history.add(Content(
                        role = "model",
                        parts = listOf(Part(text = assistantText))
                    ))
                }
            }

            // Append the current user prompt at the end of history
            val currentPromptEntity = historyEntities.lastOrNull { it.role == "user" }
            if (currentPromptEntity != null) {
                val textParts = mutableListOf<Part>()
                var remainingText = currentPromptEntity.content

                val regex = """\[IMAGE_BASE64: data:(.*?);base64,(.*?)\]""".toRegex()
                var match = regex.find(remainingText)
                while (match != null) {
                    val textBefore = remainingText.substring(0, match.range.first)
                    if (textBefore.isNotBlank()) textParts.add(Part(text = textBefore))
                    textParts.add(Part(inlineData = com.example.api.InlineData(mimeType = match.groupValues[1], data = match.groupValues[2])))
                    remainingText = remainingText.substring(match.range.last + 1)
                    match = regex.find(remainingText)
                }
                if (remainingText.isNotBlank() || textParts.isEmpty()) {
                    textParts.add(Part(text = remainingText))
                }

                history.add(Content(
                    role = "user",
                    parts = textParts
                ))
            }

            val activeTemplateInfo = when (project.templateKey) {
                "android_kotlin" -> """
                    ACTIVE TEMPLATE: Native Android Kotlin (Jetpack Compose).
                    - CRITICAL constraint: You must ONLY write Native Android Kotlin code, XML layout files, and Gradle build configurations!
                    - DO NOT use React, HTML, CSS, JavaScript, or React Three Fiber.
                """.trimIndent()
                "flutter" -> """
                    ACTIVE TEMPLATE: Flutter (Dart).
                    - CRITICAL constraint: You must ONLY write Flutter Dart code (lib/main.dart) and pubspec.yaml configurations!
                    - DO NOT use HTML, CSS, React, React CDN, or Jetpack Compose!
                """.trimIndent()
                "react" -> """
                    ACTIVE TEMPLATE: React CDN (Web).
                    - CRITICAL constraint: The project is built using React & ReactDOM directly loaded from a CDN.
                    - DO NOT use React Three Fiber or Three.js unless explicitly asked!
                    - Build components as standard modern React CDN files.
                """.trimIndent()
                "vanilla" -> """
                    ACTIVE TEMPLATE: Vanilla JS (Web).
                    - CRITICAL constraint: The project uses pure Vanilla JS (standard HTML, CSS, and pure Native JavaScript).
                    - DO NOT use React, ReactDOM, Three.js, or React Three Fiber! If you write React or JSX in a Vanilla JS project, it will fail to compile. This is a strict constraint.
                """.trimIndent()
                "fiber" -> """
                    ACTIVE TEMPLATE: React Three Fiber (3D Web).
                    - You can use Three.js and @react-three/fiber with Import Maps.
                """.trimIndent()
                else -> """
                    ACTIVE TEMPLATE: ${project.templateKey ?: "Empty Workspace"}.
                    - Respect the existing framework/files in the workspace.
                """.trimIndent()
            }

            val systemInstruction = """
                You are PenCode AI, a versatile AI Software Engineer and Development Assistant.
                
                :warning: if You're trying to use edit,patch or append tool without using read_file or read_file_rannge you will be punished and your request will be rejected and dont forget about exploring codebase,if you Don't explore codebase you will be rejected.
                
                KNOW YOUR TOOL EXECUTION BUDGET (CRITICAL):
                - You have a strict dynamic maximum step/tool call execution limit of $maxActionSteps steps for this entire task.
                - You MUST monitor your steps wisely, plan your work efficiently, and ensure you complete the entire user request and mark all To-Do items as completed well before reaching this maximum limit of $maxActionSteps steps!
                - Budget your tool calls carefully so you never get cut off before finishing.
                
                PROMPT ANALYSIS, CHAT VS ACTIONS & DECISION MAKING (CRITICAL FIRST STEP):
                - Before calling any tools or formulating any plans, you MUST analyze the user's prompt carefully.
                - Determine if the user's message is a simple chat, general greeting (e.g., "hello", "hi", "how are you"), a generic query, or a conceptual question that does NOT require changing, searching, or exploring the code.
                - If the message is a normal chat or conceptual question, you MUST NOT call any file tools or command tools! Simply provide a friendly, clear text response or use the 'complete' tool directly to respond.
                - Only call tools when there is a real action-oriented prompt (e.g. creating/modifying files, running builds/commands, searching/grepping the codebase).
                
                DETAILED PLANNING REQUIREMENT (CRITICAL):
                - Before starting any implementation, creation, or code modification, you MUST create a detailed plan outlining all features, system design, and architecture.
                - You must execute your work systematically according to this detailed plan.
                
                TO-DO LIST MANAGEMENT FOR COMPLEX/HEAVY TASKS (MANDATORY COMPLETION VERIFICATION):
                - For complex, large, heavy, or multi-step tasks, you MUST immediately create a To-Do list using the 'create_todo_list' tool. Do NOT skip creating the To-Do list for heavy tasks!
                - As you finish each step or sub-task, you MUST immediately mark it as completed/checked-off using the 'complete_todo_task' tool.
                - MANDATORY FINAL CHECK: When your work is done, you MUST explicitly double-check if ALL items in the To-Do list have been successfully crossed off (completed). You must verify that you have actually executed the required actions for each item.
                - It is STRICTLY FORBIDDEN to stop your execution or complete your turn without checking off ALL the items on your To-Do list! Ensure 100% completion of the list.
                
                CRITICAL CONSTRAINT - RESPECT THE ACTIVE FRAMEWORK:
                $activeTemplateInfo
                - You MUST strictly respect the current template/framework of the project. If Vanilla JS or React CDN is selected, do NOT use React Three Fiber or Three.js unless specifically requested.
                
                EXPLORE BEFORE YOU BUILD & RECURSIVE SCAN MANDATE (CRITICAL):
                - Whenever you see, encounter, or need to explore a directory, folder, or path (including the workspace root), you MUST strictly use the 'scan_dir' tool first to recursively scan and explore all folders, subfolders, paths, and files inside it.
                - Do NOT assume files exist or have specific contents. Always explore and read them first.
                
                READ-BEFORE-MODIFY & NO REDUNDANT RE-READING FOR VERIFICATION (CRITICAL SAVINGS):
                - You MUST NOT modify, edit, patch, or append any file without first reading its contents using 'read_file' or 'read_file_range'.
                - CRITICAL: After successfully performing an edit, patch, or append operation, you MUST NOT repeatedly read the file or call 'read_file'/'read_file_range' just to verify your edits! This burns excessive tokens and execution time. Trust your changes once applied and move forward to the next step immediately.
                - You are allowed to create duplicate/backup copies of files if needed (e.g., for safety, migration, or fallback purposes).
                
                CORE SKILLS & CAPABILITIES:
                - You build both modern Web applications (HTML, CSS, JavaScript, React, Three.js, Tailwind CSS) AND Android applications (Kotlin, Java, Gradle, XML, Jetpack Compose).
                - You are fully authorized to create, modify, or delete Kotlin (.kt), Java, XML layout files, or Gradle build files.
                - Analyze the current workspace, read the existing files, and write high-quality, production-ready code that matches the project template (Web or Android).
                - BACKGROUND BROWSER SEARCH & NAVIGATION (CRITICAL):
                  * You MUST use Brave Search instead of Google for all background search queries.
                  * FALLBACK SEARCH ENGINES: If you encounter any problem, captcha, error, or issue while using Brave Search, you are fully authorized and encouraged to fall back to searching via Bing, Yahoo, or DuckDuckGo search engines.
                  * If the user provides a URL or link (e.g., starting with http://, https://, or containing a domain name like github.com, etc.) in their message, you MUST immediately call 'browser_search' with that URL to load, read, and process its live contents in order to answer the user's request. Always open user-supplied links!
                
                THREE.JS & REACT THREE FIBER VERSION MATCHING RULE (CRITICAL - For Web projects):
                - When building 3D or Three.js applications, always use 'three@0.150.0'.
                - If using @react-three/fiber or @react-three/drei, you MUST always append '?external=three' to their esm.sh URLs so they resolve to the mapped version of three.js. This prevents runtime errors like "does not provide an export named 'LinearEncoding'".
                - Always define this exact Import Map in the HTML file's head:
                  {
                    "imports": {
                      "react": "https://esm.sh/react@18.2.0",
                      "react-dom": "https://esm.sh/react-dom@18.2.0",
                      "three": "https://esm.sh/three@0.150.0",
                      "@react-three/fiber": "https://esm.sh/@react-three/fiber@8.12.0?external=three",
                      "@react-three/drei": "https://esm.sh/@react-three/drei@9.80.0?external=three"
                    }
                  }
                              SURGICAL EDITING & FILE MODIFICATION RULES (CRITICAL):
                - Use 'patch_file' (alias 'patch') for very small, surgical changes (1-3 lines). This is mandatory for precise fixes.
                - Use 'edit_file' (alias 'edit') for larger modifications involving multiple lines or structural changes.
                - Use 'write_file' (alias 'create') ONLY when creating a NEW file. NEVER use write_file/create to overwrite an existing file for applying small edits. Overwriting existing files using write_file is strictly prohibited and will cause a fatal failure!
                - NEVER overwrite an entire file for small changes. Always read the file first and then apply surgical edits with edit_file or patch_file.
                
                CHRONOLOGICAL TRACKER:
                - Every time you perform an 'edit_file' or 'patch_file', the system tracks exactly which lines you modified. Be precise with your 'search' blocks.
                
                LINE ACCURACY & UNIQUENESS (CRITICAL FOR SUCCESSFUL EDITS):
                - Before editing, you MUST use 'read_file' or 'read_file_range' to get the current content. Do NOT guess the contents of any file.
                - The 'search' block MUST be EXACTLY as it appears in the file, including all whitespace, indentation, tabs, and newlines. Any deviation in spaces or tabs will cause a "target content not found" error!
                - The 'search' block MUST NOT be empty. An empty search block is a fatal error.
                - The 'search' block MUST be UNIQUE within the file to avoid applying changes to the wrong location. If the code block you want to change appears multiple times, include more surrounding context (preceding or succeeding lines) in the 'search' block to make it unique.
                - The 'replace' argument MUST contain the new/modified code.
                
                READ FILE RANGE (CRITICAL FOR HIGH EFFICIENCY):
                - When calling 'read_file_range', you MUST specify 'path' (file path) AND either 'startLine' and 'endLine' (as 1-indexed integers), OR specify 'lineRange' as a string (e.g. "100-130"). Do NOT omit these parameters!
                
                HANDLING COMPILATION & RUNTIME ERRORS (CRITICAL FOR ACCURATE FIXES):
                - When the user provides an error message, preview console error, or a GitHub Actions/build error trace indicating a line number (e.g., "error at line 286"), you MUST keep in mind that line numbers can shift after files are modified.
                - NEVER rely on the exact line number mentioned in the stack trace blindly! The actual error could have shifted up or down, or be in another file entirely.
                - Instead of jumping straight to the exact line number, ALWAYS read a wider range of lines around the target, and search for the key code snippet or symbols using 'global_search' or full file reading.
                - Analyze the error conceptually to locate the actual cause, rather than assuming it is exactly on the line mentioned.
                
                APPENDING TO FILES:
                - Use 'append' ONLY when you want to add content to the very end of an existing file. Provide 'path' and 'content' (the text to append). No 'search' or 'replace' arguments are needed for append.
                
                TAGGED FILES (@filename):
                - If the user tags files with @, they are provided in your context. 
                - You should prioritize performing actions on these tagged files.
                
                PERSISTENT PROJECT MEMORY MODULE (CRITICAL):
                - The workspace contains a persistent background file named 'memory.md' (NOT visible to the user but fully accessible to you).
                - This file is used to store and persist critical information about the project, framework, workspace setup, important decisions, architectural design, todo progress, or custom user rules.
                - You MUST check, read, update, or append to 'memory.md' to retrieve or persist important details about the project you are working on.
                - Feel free to create it if it doesn't exist, read it via read_file, or update/append to it when you make significant changes or learn important facts about the workspace!
                - Since it behaves like a normal file, you can use all file operation tools (read_file, edit_file, patch_file, append) on 'memory.md'.
                
                TOOL USAGE RULES (SURGICAL EDITING - MANDATORY):
                - YOU MUST NOT WRITE/CREATE FULL FILES TO APPLY SMALL EDITS. DO NOT REWRITE CODE.
                - YOU MUST ALWAYS 'read_file' or 'read_file_range' TO SEE THE CURRENT CODE BEFORE CREATING OR EDITING.
                - CRITICAL: Writing whole files when modifying existing files will result in a fatal failure. Always do surgical replacement with 'edit_file' or 'patch_file'.
                                AVAILABLE TOOLS:
                1. 'read_file': Read content of a file. MANDATORY before any edit.
                2. 'read_file_range': Read specific line ranges. Required args: 'path' (file path), 'startLine' (first line to read, integer), 'endLine' (last line to read, integer). Alternatively, you can specify 'lineRange' (string, e.g., "100-130").
                3. 'write_file' (alias 'create'): Use ONLY for creating a NEW file.
                4. 'edit_file' (alias 'edit'): Replace a precise unique block of code with new code.
                5. 'patch_file' (alias 'patch'): Replace a small, precise snippet of code.
                6. 'delete_code': Safely delete a specific unique block of code from a file.
                   - Required args: 'path' (file path), 'search' (EXACT block of code to remove).
                7. 'move_code': Move a specific block of code from a source file to a destination file.
                   - Required args: 'path' (source file path), 'destinationPath' (destination file path), 'search' (exact block of code to remove from source), 'destinationSearch' (exact block of code in destination to search for, to insert the moved block AFTER it).
                8. 'copy_code': Copy a specific block of code from a source file and insert/append it to a destination file.
                   - Required args: 'path' (source file path), 'destinationPath' (destination file path), 'search' (exact block of code to copy from source), 'destinationSearch' (exact block of code in destination to search for, to insert the copied block AFTER it).
                9. 'append': Append content to the end of a file.
                10. 'delete_file': Delete an entire file.
                11. 'move_file': Move/rename a file.
                12. 'global_search': Find all files containing a string.
                13. 'complete': Finish task execution.
                14. 'generate_image': Generate an image using Pollinations AI (free and unlimited) based on a text prompt and save it in the workspace.
                    - Required args: 'path' (destination file path, e.g., 'logo.png'), 'prompt' (detailed descriptive text prompt).
                    - Optional args: 'width' (width in pixels, default 1024), 'height' (height in pixels, default 1024).
                15. 'resize_image': Resize or change the format of an image.
                    - Required args: 'path' (source file path, e.g., 'logo.png'), 'destinationPath' (destination file path, e.g., 'app/src/main/res/mipmap-xxxhdpi/ic_launcher.png'), 'width' (desired width in pixels, e.g., 192), 'height' (desired height in pixels, e.g., 192).
                    - Optional args: 'format' (the output format like 'png', 'jpg', 'webp' - defaults to matching the extension of destinationPath or 'png').
                16. 'browser_search': Search Brave or navigate to any website using a hidden, background browser to retrieve real-time facts, read articles, or look up information.
                    - Required args: 'query' (Brave search term or website URL).
                17. 'browser_click': Click a button, link, or element in the background browser using a CSS selector or XPath.
                    - Required args: 'search' (CSS selector or XPath of the element to click).
                18. 'browser_read': Read the title and clean text content of the currently loaded webpage in the background browser.
                    - No required arguments.
                19. 'create_todo_list': Create a todo list for complex, heavy, multi-step, or large tasks to track progress.
                    - Required args: 'query' (the list of sub-tasks separated by the '|' character. E.g. "Implement browser|Setup files|Verify UI").
                20. 'complete_todo_task': Mark a specific sub-task in the todo list as completed.
                    - Required args: 'query' (the 0-based index of the sub-task to mark complete, E.g. "0" for the first sub-task).
                21. 'scan_dir': Recursively scan, explore, and list all folders, subfolders, paths, and files inside any target directory or path. This tool is MANDATORY whenever you encounter or need to explore any directory or folder.
                    - Required args: 'path' (the target directory path to recursively scan, e.g., "app/src", or "." for the entire workspace root).
                
                Tool arguments structure:
                   - 'path': The file path.
                   - 'startLine': The first line number to read (1-indexed integer) for read_file_range.
                   - 'endLine': The last line number to read (1-indexed integer) for read_file_range.
                   - 'lineRange': Alternatively, specify line range as a string (e.g., "100-130") for read_file_range.
                   - 'destinationPath': For move_code/copy_code/resize_image, the destination file path.
                   - 'search': The EXACT, UNIQUE block of code to find/remove/copy, or CSS/XPath for browser_click.
                   - 'destinationSearch': For move_code/copy_code, the EXACT block to find in destination to insert AFTER.
                   - 'replace': The new code to replace/insert.
                   - 'prompt': For generate_image, the detailed text prompt describing the image.
                   - 'width': For generate_image/resize_image, the desired width in pixels.
                   - 'height': For generate_image/resize_image, the desired height in pixels.
                   - 'format': For resize_image, the output format.
                   - 'query': For browser_search, create_todo_list, complete_todo_task, the search term or website URL or list of tasks or task index.
                  
                JSON Schema:
                {
                  "thought": "Analysis and plan.",
                  "tool": "list_directory" | "read_file" | "read_file_range" | "write_file" | "edit_file" | "patch_file" | "append" | "delete_file" | "rename_file" | "move_file" | "run_command" | "global_search" | "complete" | "delete_code" | "move_code" | "copy_code" | "generate_image" | "resize_image" | "browser_search" | "browser_click" | "browser_read" | "create_todo_list" | "complete_todo_task" | "scan_dir",
                  "arguments": {
                    "path": "file/path.kt",
                    "destinationPath": "dest/path.kt",
                    "content": "Full content for write_file/append",
                    "search": "Exact block to find or CSS/XPath",
                    "destinationSearch": "Exact block in destination to insert after",
                    "replace": "New block",
                    "startLine": 1,
                    "endLine": 50,
                    "lineRange": "10-20",
                    "command": "ls -la",
                    "prompt": "The prompt describing the image to generate",
                    "width": 512,
                    "height": 512,
                    "format": "png",
                    "query": "Google search query or URL"
                  }
                }
                
                AI THINKING RULE (CRITICAL):
                - You MUST keep your "thought" (formulating logic) extremely short, concise, and direct (at most 1-2 sentences). You can also choose to completely skip outputting thoughts or skip 'ai formulating logic' entirely to respond as fast as possible. Never write long essays or paragraph blocks under the 'thought' field!
                
                COMPLETION DETAILS MANDATE (CRITICAL):
                - When you call the 'complete' tool, you MUST provide a beautifully structured and highly informative short details/summary of what you have done in the 'message' argument.
                - Use professional, elegant Markdown formatting (with headers, bullet points, bold labels, and inline code snippets) to list every action, file created, code block modified, or shell command executed.
                - The details should be easy to read and extremely professional, giving the user a complete picture of your actions and visual design choices.
                
                SEARCH, DEBUGGING & PATTERN ANALYSIS MANDATE (CRITICAL):
                - For listing files, exploring directories, finding files, and scanning codebase structures, you can use the 'list_directory' tool or 'run_command' with simulated 'find' command (e.g. `find . -name "*.kt"`).
                - For error fixing, debugging, problem solving, file pattern analysis, error finding, keyword searching, checking code usages, and doing multi-file text search, you MUST use the 'run_command' tool with the 'grep' command (e.g., `grep -rn "keyword" .`).
                - Using 'grep' is the most efficient and reliable way to analyze the codebase and locate precise line numbers for editing, patching, and appending.
                - The simulated 'grep' command is highly advanced and supports regex matching, case insensitivity (-i), whole-word matching (-w), invert match (-v), recursive search (-r/-R), and glob paths (e.g. `*.kt`).
                - Executing 'grep' always returns exact matches in the format `file_path:line_number:code_line`, which lets you know exactly which lines to view or modify.
                - Standard shell 'find' and 'grep' commands are fully simulated and supported in our custom terminal execution environment, so you can execute them freely using 'run_command'!
                - You can and SHOULD perform MULTIPLE tool calls in a single turn if the task requires it. For example, you can edit three different files or perform multiple patches in one go.
                - When performing an 'edit_file', 'patch_file', or 'append', always be precise and target exact lines.
                
                MANDATORY STRATEGY RULES (CRITICAL - YOU WILL BE PUNISHED AND REJECTED IF VIOLATED):
                1. Use 'scan_dir' recursively to explore and list folders/subfolders/files whenever you see, encounter, or need to explore a directory, folder, or path. This is a STRICT REQUIREMENT.
                2. ALWAYS use the 'grep' command (run_command with grep -rn "keyword" .) to find exact files and matching lines before reading any files. You can run grep up to 5 times if you do not get any grep output, because you need to locate exact file lines.
                3. After finding the exact file and matched lines using grep, you MUST read ONLY about 30 lines surrounding the matched code (e.g., 15 lines before and 15 lines after) using the 'read_file_range' tool.
                4. NEVER read the full file if it is larger than 80 lines. If a file is larger than 80 lines, you are STRICTLY FORBIDDEN from reading the full file. Instead, you MUST use global_search or grep to find the exact match first, and then read only the surrounding lines of code (using 'read_file_range' with a precise window around the target).
                5. Verify that all tasks are completed and then stop.
                Warning: If a file has more than 80 lines of code and you read the entire file instead of using grep and read_file_range, you will be punished and your request will be rejected!
                
                ANDROID / FLUTTER BUILD RULES & AUTOMATIC PUSH PERMISSION (CRITICAL):
                - NEVER run `gradle assembleDebug`, `gradle build`, `flutter build apk`, or any APK building commands using `run_command`. 
                - The local environment DOES NOT support building APKs directly.
                - When working with Kotlin, Java, or Flutter projects, simply write/edit the code and complete the task.
                - GITHUB PUSH & AUTOMATIC BUILD PERMISSION: The "Allow Build & Push Permission" setting is currently set to: ${_allowBuildPush.value}.
                  * If this is true, you are FULLY AUTHORIZED to automatically trigger force pushing to GitHub and starting the build once you complete your task, without requiring user manual permission.
                  * If this is false, you must ask the user for permission at the end before attempting to push or build.
            """.trimIndent()

            val useCustom = _useCustomModel.value
            val activeConfig = _customModels.value.find { it.id == _selectedModelId.value }

            if (activeConfig == null) {
                val errorLog = createAiLog(
                    title = "Inference Error",
                    status = "failed",
                    details = "No model configuration selected. Please go to Settings and select a model provider."
                )
                _aiActionLogs.value = _aiActionLogs.value + errorLog
                
                val logsJson = try {
                    val listType = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
                    moshi.adapter<List<AiActionLog>>(listType).toJson(_aiActionLogs.value)
                } catch (e: Exception) {
                    null
                }
                
                val agentMsg = ChatMessageEntity(
                    projectName = project.name,
                    role = "assistant",
                    content = "Task interrupted: No model configuration selected.",
                    timestamp = System.currentTimeMillis(),
                    aiActionLogsJson = logsJson
                )
                repository.insertChatMessage(agentMsg)
                _chatMessages.value = repository.getChatsForProject(project.name)
                
                _isThinking.value = false
                return@launch
            }

            val activeApiKey = activeConfig.apiKey
            val provider = activeConfig.provider
            val modelId = activeConfig.modelId
            val baseUrl = activeConfig.baseUrl
            
            var loopCompleted = false
            var filesModifiedThisPrompt = false
            var turn = 1
            var actionsCount = 0
            var maxActionSteps = _maxActionSteps.value
            var agentMessageSaved = false
            var lastThought: String? = null

            try {
                while (!loopCompleted && turn <= 500 && actionsCount < maxActionSteps) {
                _agentStatus.value = "AI thinking (Turn $turn, Action $actionsCount/$maxActionSteps)..."
                
                // Add Thinking Log entry
                val thinkingLog = createAiLog(
                    title = "AI formulating logic",
                    status = "thinking",
                    details = "Formulating step-by-step agent instructions..."
                )
                _aiActionLogs.value = _aiActionLogs.value + thinkingLog

                try {
                    val stepResponse = GeminiClient.generateAgentStep(
                        apiKey = activeApiKey,
                        systemInstruction = systemInstruction,
                        conversationHistory = optimizeConversationHistory(history),
                        provider = provider,
                        modelId = modelId,
                        customBaseUrl = baseUrl,
                        useCustom = useCustom
                    )
                    
                    // Mark current thinking log as completed or failed
                    val thought = stepResponse?.thought ?: ""
                    if (thought.isNotBlank()) {
                        lastThought = thought
                    }
                    val shouldIgnoreLog = thought.contains("ignore_formulating_logic", ignoreCase = true) || thought.startsWith("[ignore]", ignoreCase = true)
                    
                    if (shouldIgnoreLog) {
                        _aiActionLogs.value = _aiActionLogs.value.filter { it.id != thinkingLog.id }
                    } else {
                        updateAiLog(thinkingLog.id, "success", thought.ifBlank { "Parsed response" })
                    }

                    if (stepResponse != null) {
                        val toolCalls = mutableListOf<com.example.api.ToolCallItem>()
                        
                        if (stepResponse.tools != null && stepResponse.tools.isNotEmpty()) {
                            toolCalls.addAll(stepResponse.tools)
                        } else if (stepResponse.tool != null && stepResponse.tool.isNotBlank()) {
                            toolCalls.add(com.example.api.ToolCallItem(stepResponse.tool, stepResponse.arguments))
                        }

                        Log.d("VibeViewModel", "Turn $turn - Thought: $thought, Calls: ${toolCalls.size}")

                        if (stepResponse.finishReason == "MAX_TOKENS") {
                            _isInterrupted.value = true
                            loopCompleted = true
                        }

                        if (toolCalls.isEmpty() || (toolCalls.size == 1 && toolCalls[0].tool == "complete")) {
                            loopCompleted = true
                        }

                        for (call in toolCalls) {
                            val tool = call.tool
                            val args = call.arguments
                            
                            if (tool != "complete") {
                                if (actionsCount >= maxActionSteps) {
                                    _isInterrupted.value = true
                                    val limitLog = createAiLog(
                                        title = "Action limit reached",
                                        status = "failed",
                                        details = "AI reached the maximum number of actions/tool calls ($maxActionSteps) set in settings."
                                    )
                                    _aiActionLogs.value = _aiActionLogs.value + limitLog
                                    loopCompleted = true
                                    break
                                }
                                actionsCount++
                                _agentStatus.value = "AI thinking (Turn $turn, Action $actionsCount/$maxActionSteps)..."
                            }
                            
                            when (tool) {
                            "complete" -> {
                                val message = args?.message ?: "Task completed successfully!"
                                val logEntry = createAiLog(
                                    title = "AI finished task execution",
                                    status = "success",
                                    details = message
                                )
                                _aiActionLogs.value = _aiActionLogs.value + logEntry

                                // Serialize logs to save with message
                                val logsJson = try {
                                    val listType = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
                                    moshi.adapter<List<AiActionLog>>(listType).toJson(_aiActionLogs.value)
                                } catch (e: Exception) {
                                    null
                                }

                                // Insert Assistant Final Message to DB
                                val agentMsg = ChatMessageEntity(
                                    projectName = project.name,
                                    role = "assistant",
                                    content = message,
                                    timestamp = System.currentTimeMillis(),
                                    aiActionLogsJson = logsJson
                                )
                                repository.insertChatMessage(agentMsg)
                                _chatMessages.value = repository.getChatsForProject(project.name)
                                
                                _agentStatus.value = "Changes applied successfully!"
                                loopCompleted = true
                                agentMessageSaved = true

                                // Detect framework and ask to build on Github
                                val projectDir = repository.getProjectDir(project.name)
                                val isKotlin = java.io.File(projectDir, "build.gradle.kts").exists() || java.io.File(projectDir, "build.gradle").exists()
                                val isFlutter = java.io.File(projectDir, "pubspec.yaml").exists()

                                if (filesModifiedThisPrompt) {
                                    if (isKotlin) {
                                        _detectedFramework.value = "Kotlin/Android"
                                        if (_allowBuildPush.value) {
                                            acceptGithubPushPrompt()
                                        } else {
                                            _showGithubPushPrompt.value = true
                                        }
                                    } else if (isFlutter) {
                                        _detectedFramework.value = "Flutter"
                                        if (_allowBuildPush.value) {
                                            acceptGithubPushPrompt()
                                        } else {
                                            _showGithubPushPrompt.value = true
                                        }
                                    }
                                }
                            }
                            "list_directory" -> {
                                val targetPath = args?.path ?: "."
                                val listLog = createAiLog(
                                    title = "Explored directory",
                                    status = "thinking",
                                    details = targetPath
                                )
                                _aiActionLogs.value = _aiActionLogs.value + listLog

                                val files = repository.getFilesForProject(project.name)
                                val fileDetails = files.sortedBy { it.path }.map { file ->
                                    val lineCount = file.content.lines().size
                                    val sizeInBytes = file.content.toByteArray(Charsets.UTF_8).size
                                    val sizeStr = if (sizeInBytes >= 1024 * 1024) {
                                        String.format("%.2f MB", sizeInBytes.toDouble() / (1024 * 1024))
                                    } else {
                                        String.format("%.2f KB", sizeInBytes.toDouble() / 1024)
                                    }
                                    "  - ${file.path} ($lineCount lines, $sizeStr)"
                                }.joinToString("\n")
                                val result = if (fileDetails.isEmpty()) "Directory is empty." else "Files found:\n$fileDetails"

                                updateAiLog(listLog.id, "success", result)

                                // Append results to history
                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'list_directory': $result"))))
                            }
                            "scan_dir" -> {
                                val rawPath = args?.path ?: args?.query ?: ""
                                var targetPath = normalizePath(rawPath)
                                if (targetPath == "." || targetPath == "./" || targetPath == "/") {
                                    targetPath = ""
                                }
                                val scanLog = createAiLog(
                                    title = "Scanned directory (scan_dir)",
                                    status = "thinking",
                                    details = if (targetPath.isEmpty()) "Root workspace" else targetPath
                                )
                                _aiActionLogs.value = _aiActionLogs.value + scanLog

                                val files = repository.getFilesForProject(project.name)
                                val filteredFiles = if (targetPath.isEmpty()) {
                                    files
                                } else {
                                    files.filter { it.path.startsWith(targetPath) }
                                }

                                val fileDetails = filteredFiles.sortedBy { it.path }.map { file ->
                                    val lineCount = file.content.lines().size
                                    val sizeInBytes = file.content.toByteArray(Charsets.UTF_8).size
                                    val sizeStr = if (sizeInBytes >= 1024 * 1024) {
                                        String.format("%.2f MB", sizeInBytes.toDouble() / (1024 * 1024))
                                    } else {
                                        String.format("%.2f KB", sizeInBytes.toDouble() / 1024)
                                    }
                                    "  - ${file.path} ($lineCount lines, $sizeStr)"
                                }.joinToString("\n")

                                val result = if (fileDetails.isEmpty()) {
                                    "No files or subdirectories found under '$targetPath'."
                                } else {
                                    "Recursive scan of directory '$targetPath' succeeded. Found ${filteredFiles.size} files:\n$fileDetails"
                                }

                                updateAiLog(scanLog.id, "success", result)

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'scan_dir': $result"))))
                            }
                            "read_file" -> {
                                val filePath = normalizePath(args?.path ?: "")
                                val readLog = createAiLog(
                                    title = "Read file",
                                    status = "thinking",
                                    details = filePath,
                                    lineRange = "all"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + readLog

                                val result = if (repository.isBinaryExtension(filePath)) {
                                    "Error: Reading, editing, patching, or appending to binary image or 3D files directly as text is NOT allowed. You can only view their existence via 'list_directory' or perform operations like rename, delete, move, resize, or format change."
                                } else {
                                    val files = repository.getFilesForProject(project.name)
                                    val targetFile = files.find { it.path == filePath }
                                    if (targetFile != null) {
                                        "--- File: $filePath ---\n${targetFile.content}"
                                    } else {
                                        "Error: File '$filePath' not found."
                                    }
                                }

                                val logDetails = if (repository.isBinaryExtension(filePath)) {
                                    "Error: Cannot read binary files as text"
                                } else {
                                    val files = repository.getFilesForProject(project.name)
                                    val targetFile = files.find { it.path == filePath }
                                    if (targetFile != null) {
                                        "Read ${targetFile.content.lines().size} lines from $filePath"
                                    } else {
                                        "Error: File '$filePath' not found."
                                    }
                                }
                                updateAiLog(readLog.id, if (result.startsWith("--- File:")) "success" else "failed", logDetails)

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'read_file': $result"))))
                            }
                            "read_file_range" -> {
                                val filePath = normalizePath(args?.path ?: "")
                                var rawStartLine = args?.startLine
                                var rawEndLine = args?.endLine
                                
                                val lr = args?.lineRange
                                if (!lr.isNullOrBlank()) {
                                    val match = """(\d+)\s*[-:to\.]+\s*(\d+)""".toRegex().find(lr)
                                    if (match != null) {
                                        rawStartLine = rawStartLine ?: match.groupValues[1].toIntOrNull()
                                        rawEndLine = rawEndLine ?: match.groupValues[2].toIntOrNull()
                                    } else {
                                        val singleMatch = """(\d+)""".toRegex().find(lr)
                                        if (singleMatch != null) {
                                            rawStartLine = rawStartLine ?: singleMatch.groupValues[1].toIntOrNull()
                                        }
                                    }
                                }
                                
                                val startLine = rawStartLine ?: 1
                                var endLineInput = rawEndLine ?: (startLine + 29)
                                if (endLineInput < startLine) {
                                    endLineInput = startLine + 29
                                }
                                val endLine = endLineInput
                                val readLog = AiActionLog(
                                    title = "read :$filePath",
                                    status = "thinking",
                                    details = filePath,
                                    lineRange = "Line $startLine-$endLine"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + readLog

                                val result = if (repository.isBinaryExtension(filePath)) {
                                    "Error: Reading, editing, patching, or appending to binary image or 3D files directly as text is NOT allowed. You can only view their existence via 'list_directory' or perform operations like rename, delete, move, resize, or format change."
                                } else {
                                    val files = repository.getFilesForProject(project.name)
                                    val targetFile = files.find { it.path == filePath }
                                    if (targetFile != null) {
                                        val lines = targetFile.content.lines()
                                        val startIdx = (startLine - 1).coerceAtLeast(0).coerceAtMost(lines.size)
                                        val endIdx = endLine.coerceAtLeast(startIdx).coerceAtMost(lines.size)
                                        val selectedLines = lines.subList(startIdx, endIdx).joinToString("\n")
                                        "--- File: $filePath (Lines ${startIdx + 1}-$endIdx) ---\n$selectedLines"
                                    } else {
                                        "Error: File '$filePath' not found."
                                    }
                                }

                                val logDetails = if (repository.isBinaryExtension(filePath)) {
                                    "Error: Cannot read binary files as text"
                                } else {
                                    val files = repository.getFilesForProject(project.name)
                                    val targetFile = files.find { it.path == filePath }
                                    if (targetFile != null) {
                                        "Read lines $startLine-$endLine from $filePath"
                                    } else {
                                        "Error: File '$filePath' not found."
                                    }
                                }
                                updateAiLog(readLog.id, if (result.startsWith("--- File:")) "success" else "failed", logDetails)

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'read_file_range': $result"))))
                            }
                            "create", "write_file" -> {
                                val filePath = normalizePath(args?.path ?: "")
                                val fileContent = args?.content ?: ""
                                val writeLog = createAiLog(
                                    title = "Created/Updated file",
                                    status = "thinking",
                                    details = "$filePath",
                                    lineRange = args?.lineRange ?: "all"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + writeLog

                                val result = try {
                                    repository.saveFile(project.name, filePath, fileContent)
                                    filesModifiedThisPrompt = true
                                    _editHistory.value = _editHistory.value + EditRecord(
                                        tool = "create",
                                        path = filePath,
                                        lines = "all"
                                    )
                                    "Successfully created/wrote file '$filePath'"
                                } catch (e: Exception) {
                                    "Error writing file: ${e.localizedMessage}"
                                }

                                updateAiLog(writeLog.id, "success", filePath)

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool': $result"))))
                            }
                            "append" -> {
                                val filePath = normalizePath(args?.path ?: "")
                                val fileContent = args?.content ?: ""
                                val appendLog = createAiLog(
                                    title = "Append to file",
                                    status = "thinking",
                                    details = "$filePath",
                                    lineRange = args?.lineRange
                                )
                                _aiActionLogs.value = _aiActionLogs.value + appendLog

                                val files = repository.getFilesForProject(project.name)
                                val targetFile = files.find { it.path == filePath }
                                val result = if (repository.isBinaryExtension(filePath)) {
                                    "Error: Reading, editing, patching, or appending to binary image or 3D files directly as text is NOT allowed. You can only view their existence via 'list_directory' or perform operations like rename, delete, move, resize, or format change."
                                } else if (targetFile != null) {
                                    try {
                                        val newContent = targetFile.content + "\n" + fileContent
                                        repository.saveFile(project.name, filePath, newContent)
                                        filesModifiedThisPrompt = true
                                        val startLine = targetFile.content.lines().size + 1
                                        val addedLines = fileContent.lines().size
                                        val endLine = startLine + addedLines - 1
                                        val range = if (startLine >= endLine) "line $startLine" else "lines $startLine-$endLine"
                                        _editHistory.value = _editHistory.value + EditRecord(
                                            tool = "append",
                                            path = filePath,
                                            lines = range
                                        )
                                        "Successfully appended to '$filePath'"
                                    } catch (e: Exception) {
                                        "Error appending to file: ${e.localizedMessage}"
                                    }
                                } else {
                                    "Error: File '$filePath' not found. Cannot append."
                                }

                                val isSuccess = !result.startsWith("Error")
                                val range = _editHistory.value.lastOrNull { it.tool == "append" && it.path == filePath }?.lines ?: args?.lineRange ?: ""
                                updateAiLog(
                                    appendLog.id, 
                                    if (isSuccess) "success" else "failed", 
                                    if (isSuccess) filePath else result
                                )
                                // Note: lineRange update in updateAiLog helper doesn't support changing other fields besides status and details easily.
                                // I'll skip updating lineRange specifically for now as it's minor, or I could update it manually.
                                
                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'append': $result"))))
                            }
                            "edit", "patch", "patch_file", "edit_file" -> {
                                val filePath = normalizePath(args?.path ?: "")
                                val searchStr = args?.search ?: ""
                                val replaceStr = args?.replace ?: ""
                                var foundRange = ""
                                val patchLog = createAiLog(
                                    title = "Modified file",
                                    status = "thinking",
                                    details = "$filePath",
                                    lineRange = args?.lineRange
                                )
                                _aiActionLogs.value = _aiActionLogs.value + patchLog

                                val result = if (repository.isBinaryExtension(filePath)) {
                                    "Error: Reading, editing, patching, or appending to binary image or 3D files directly as text is NOT allowed. You can only view their existence via 'list_directory' or perform operations like rename, delete, move, resize, or format change."
                                } else {
                                    val files = repository.getFilesForProject(project.name)
                                    val targetFile = files.find { it.path == filePath }
                                    if (targetFile != null) {
                                        val originalContent = targetFile.content
                                        if (searchStr.isEmpty()) {
                                            "Error: 'search' block cannot be empty. You must specify the exact, unique block of code to search and replace. Do not use write_file/create to overwrite an existing file for small edits."
                                        } else if (!originalContent.contains(searchStr)) {
                                            "Error: Could not find exact search block in $filePath. Please double-check characters, indentation, and spaces."
                                        } else {
                                            val occurrences = originalContent.split(searchStr).size - 1
                                            if (occurrences > 1) {
                                                "Error: The search block is not unique. It occurs $occurrences times in the file. Please provide a larger unique block of context code."
                                            } else {
                                                val startIndex = originalContent.indexOf(searchStr)
                                                val linesBefore = originalContent.substring(0, startIndex).count { it == '\n' } + 1
                                                val linesInSearch = searchStr.count { it == '\n' }
                                                val endLine = linesBefore + linesInSearch
                                                foundRange = if (linesBefore == endLine) "line $linesBefore" else "lines $linesBefore-$endLine"
                                                
                                                val updatedContent = originalContent.replace(searchStr, replaceStr)
                                                try {
                                                    repository.saveFile(project.name, filePath, updatedContent)
                                                    filesModifiedThisPrompt = true
                                                    val toolType = if (tool.contains("patch")) "patch" else "edit"
                                                    _editHistory.value = _editHistory.value + EditRecord(
                                                        tool = toolType,
                                                        path = filePath,
                                                        lines = foundRange
                                                    )
                                                    "Successfully modified file '$filePath'"
                                                } catch (e: Exception) {
                                                    "Error writing modified file: ${e.localizedMessage}"
                                                }
                                            }
                                        }
                                    } else {
                                        "Error: File '$filePath' not found."
                                    }
                                }

                                val isSuccess = result.startsWith("Successfully")
                                val range = if (foundRange.isNotEmpty()) foundRange else args?.lineRange ?: ""
                                updateAiLog(
                                    patchLog.id, 
                                    if (isSuccess) "success" else "failed", 
                                    if (isSuccess) filePath else result
                                )
                                // lineRange skip for now

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool': $result"))))
                            }
                            "generate_image" -> {
                                val filePath = normalizePath(args?.path ?: "image.png")
                                val imagePrompt = args?.prompt ?: "beautiful abstract digital art"
                                val width = args?.width ?: 1024
                                val height = args?.height ?: 1024
                                
                                val genLog = AiActionLog(
                                    title = "Generate image",
                                    status = "thinking",
                                    details = "Generating: \"$imagePrompt\" ($width x $height)",
                                    lineRange = "pollinations"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + genLog

                                _agentStatus.value = "Generating image using Pollinations AI..."

                                val result = try {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val client = okhttp3.OkHttpClient()
                                        val encodedPrompt = java.net.URLEncoder.encode(imagePrompt, "UTF-8")
                                        val randomSeed = (1..1000000).random()
                                        val url = "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$height&seed=$randomSeed&model=flux&nologo=true"
                                        
                                        val request = okhttp3.Request.Builder()
                                            .url(url)
                                            .get()
                                            .build()
                                            
                                        val response = client.newCall(request).execute()
                                        if (response.isSuccessful) {
                                            val bytes = response.body?.bytes()
                                            if (bytes != null) {
                                                val mimeType = when (filePath.substringAfterLast(".", "").lowercase()) {
                                                    "png" -> "image/png"
                                                    "jpg", "jpeg" -> "image/jpeg"
                                                    "webp" -> "image/webp"
                                                    "gif" -> "image/gif"
                                                    "ico" -> "image/x-icon"
                                                    else -> "image/png"
                                                }
                                                val base64Content = "data:$mimeType;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                                repository.saveFile(project.name, filePath, base64Content)
                                                "Successfully generated and saved image to '$filePath'"
                                            } else {
                                                "Error: Image response body was empty."
                                            }
                                        } else {
                                            "Error: Failed to fetch image from Pollinations (HTTP ${response.code})."
                                        }
                                    }
                                } catch (e: Exception) {
                                    val errorMsg = e.localizedMessage ?: e.javaClass.simpleName
                                    "Error generating image: $errorMsg"
                                }

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == genLog.id) {
                                        val isSuccess = result.startsWith("Successfully")
                                        log.copy(status = if (isSuccess) "success" else "failed", details = result)
                                    } else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'generate_image': $result"))))
                            }
                            "resize_image" -> {
                                val sourcePath = normalizePath(args?.path ?: "")
                                val destPath = normalizePath(args?.destinationPath ?: "")
                                val targetWidth = args?.width ?: 512
                                val targetHeight = args?.height ?: 512
                                val outputFormatStr = args?.format ?: destPath.substringAfterLast(".", "png")

                                val resizeLog = AiActionLog(
                                    title = "Resize image",
                                    status = "thinking",
                                    details = "Resizing $sourcePath to $destPath ($targetWidth x $targetHeight, format: $outputFormatStr)",
                                    lineRange = "android-graphics"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + resizeLog

                                val files = repository.getFilesForProject(project.name)
                                val sourceFile = files.find { it.path == sourcePath }

                                val result = if (sourceFile == null) {
                                    "Error: Source image file '$sourcePath' not found."
                                } else {
                                    try {
                                        val base64String = sourceFile.content
                                        val isBase64Image = base64String.startsWith("data:") && base64String.contains(";base64,")
                                        val cleanBase64 = if (isBase64Image) base64String.substringAfter(";base64,") else base64String
                                        val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                                        
                                        val originalBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        if (originalBitmap != null) {
                                             val resizedBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
                                             val stream = java.io.ByteArrayOutputStream()
                                             val compressFormat = when (outputFormatStr.lowercase()) {
                                                 "jpg", "jpeg" -> android.graphics.Bitmap.CompressFormat.JPEG
                                                 "webp" -> android.graphics.Bitmap.CompressFormat.WEBP
                                                 else -> android.graphics.Bitmap.CompressFormat.PNG
                                             }
                                             resizedBitmap.compress(compressFormat, 100, stream)
                                             val resizedBytes = stream.toByteArray()
                                             
                                             val destMimeType = when (destPath.substringAfterLast(".", "").lowercase()) {
                                                 "png" -> "image/png"
                                                 "jpg", "jpeg" -> "image/jpeg"
                                                 "webp" -> "image/webp"
                                                 "gif" -> "image/gif"
                                                 "ico" -> "image/x-icon"
                                                 else -> "image/png"
                                             }
                                             val destBase64 = "data:$destMimeType;base64," + android.util.Base64.encodeToString(resizedBytes, android.util.Base64.NO_WRAP)
                                             repository.saveFile(project.name, destPath, destBase64)
                                             
                                             originalBitmap.recycle()
                                             resizedBitmap.recycle()
                                             
                                             "Successfully resized and saved image to '$destPath' ($targetWidth x $targetHeight, format: ${compressFormat.name})"
                                        } else {
                                             "Error: Failed to decode image bytes from '$sourcePath'."
                                        }
                                    } catch (e: Exception) {
                                        "Error resizing image: ${e.localizedMessage}"
                                    }
                                }

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == resizeLog.id) {
                                        val isSuccess = result.startsWith("Successfully")
                                        log.copy(status = if (isSuccess) "success" else "failed", details = result)
                                    } else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'resize_image': $result"))))
                            }
                            "browser_search" -> {
                                val queryVal = args?.query ?: ""
                                val searchLog = AiActionLog(
                                    title = "Browser search/navigate",
                                    status = "thinking",
                                    details = "Searching or loading: $queryVal",
                                    lineRange = "background-browser"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + searchLog
                                _agentStatus.value = "Performing browser search/navigation for: $queryVal..."

                                val result = if (queryVal.isBlank()) {
                                    "Error: 'query' argument cannot be empty. Please provide a search term or a URL."
                                } else {
                                    val isUrl = queryVal.startsWith("http://") || queryVal.startsWith("https://") || (queryVal.contains(".") && !queryVal.contains(" "))
                                    val browserResult = if (isUrl) {
                                        backgroundBrowser.navigate(queryVal)
                                    } else {
                                        backgroundBrowser.searchBrave(queryVal)
                                    }
                                    when (browserResult) {
                                        is BrowserResult.Success -> {
                                            "Successfully loaded page: ${browserResult.url}\nTitle: ${browserResult.title}\n\nContent Summary:\n${browserResult.content}"
                                        }
                                        is BrowserResult.Error -> {
                                            "Error performing browser action: ${browserResult.message}"
                                        }
                                    }
                                }

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == searchLog.id) {
                                        val isSuccess = !result.startsWith("Error")
                                        log.copy(status = if (isSuccess) "success" else "failed", details = if (isSuccess) "Loaded successfully" else result)
                                    } else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'browser_search': $result"))))
                            }
                            "browser_click" -> {
                                val selector = args?.search ?: ""
                                val clickLog = AiActionLog(
                                    title = "Browser click element",
                                    status = "thinking",
                                    details = "Clicking element: $selector",
                                    lineRange = "background-browser"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + clickLog
                                _agentStatus.value = "Clicking element: $selector..."

                                val result = if (selector.isBlank()) {
                                    "Error: 'search' argument (CSS selector or XPath) cannot be empty."
                                } else {
                                    when (val browserResult = backgroundBrowser.clickElement(selector)) {
                                        is BrowserResult.Success -> {
                                            browserResult.content
                                        }
                                        is BrowserResult.Error -> {
                                            "Error clicking element: ${browserResult.message}"
                                        }
                                    }
                                }

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == clickLog.id) {
                                        val isSuccess = !result.startsWith("Error")
                                        log.copy(status = if (isSuccess) "success" else "failed", details = if (isSuccess) "Clicked successfully" else result)
                                    } else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'browser_click': $result"))))
                            }
                            "browser_read" -> {
                                val readLog = AiActionLog(
                                    title = "Browser read content",
                                    status = "thinking",
                                    details = "Reading current page content",
                                    lineRange = "background-browser"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + readLog
                                _agentStatus.value = "Reading page content..."

                                val result = when (val browserResult = backgroundBrowser.readPageContent()) {
                                    is BrowserResult.Success -> {
                                        val dataText = "Current URL: ${browserResult.url}\nTitle: ${browserResult.title}\n\nContent:\n${browserResult.content}"
                                        try {
                                            val files = repository.getFilesForProject(project.name)
                                            val currentMem = files.find { it.path == "browser_memory.md" }?.content ?: ""
                                            val updatedMem = if (currentMem.isBlank()) {
                                                "# Browser Memory\n\n## ${browserResult.title}\nURL: ${browserResult.url}\n\n${browserResult.content}"
                                            } else {
                                                "$currentMem\n\n---\n\n## ${browserResult.title}\nURL: ${browserResult.url}\n\n${browserResult.content}"
                                            }
                                            repository.saveFile(project.name, "browser_memory.md", updatedMem)
                                        } catch (e: Exception) {
                                            // Handle silently
                                        }
                                        dataText
                                    }
                                    is BrowserResult.Error -> {
                                        "Error reading content: ${browserResult.message}"
                                    }
                                }

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == readLog.id) {
                                        val isSuccess = !result.startsWith("Error")
                                        log.copy(status = if (isSuccess) "success" else "failed", details = if (isSuccess) "Read and saved to browser_memory.md" else result)
                                    } else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'browser_read': $result"))))
                            }
                            "create_todo_list" -> {
                                val queryVal = args?.query ?: ""
                                val todoLog = AiActionLog(
                                    title = "Create TODO List",
                                    status = "thinking",
                                    details = "Initializing tasks: $queryVal",
                                    lineRange = "todo-list"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + todoLog
                                _agentStatus.value = "Creating todo list..."

                                val tasks = queryVal.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                                _todoList.value = tasks.map { TodoItem(task = it) }

                                val result = if (tasks.isEmpty()) {
                                    "Error: No tasks provided to create todo list."
                                } else {
                                    "Successfully created todo list with ${tasks.size} tasks:\n" + tasks.mapIndexed { idx, t -> "$idx. [ ] $t" }.joinToString("\n")
                                }

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == todoLog.id) {
                                        val isSuccess = tasks.isNotEmpty()
                                        log.copy(status = if (isSuccess) "success" else "failed", details = if (isSuccess) "Created todo list with ${tasks.size} items" else result)
                                    } else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'create_todo_list': $result"))))
                            }
                            "complete_todo_task" -> {
                                val queryVal = args?.query ?: ""
                                val todoLog = AiActionLog(
                                    title = "Complete TODO Task",
                                    status = "thinking",
                                    details = "Completing task at index $queryVal",
                                    lineRange = "todo-list"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + todoLog
                                _agentStatus.value = "Completing todo task..."

                                val index = queryVal.toIntOrNull()
                                val currentTodos = _todoList.value
                                val result = if (index != null && index >= 0 && index < currentTodos.size) {
                                    val updated = currentTodos.toMutableList()
                                    val task = updated[index]
                                    updated[index] = task.copy(isCompleted = true)
                                    _todoList.value = updated
                                    "Successfully marked task '$task' as completed."
                                } else {
                                    "Error: Invalid task index '$queryVal'. Current todo list size is ${currentTodos.size}."
                                }

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == todoLog.id) {
                                        val isSuccess = !result.startsWith("Error")
                                        log.copy(status = if (isSuccess) "success" else "failed", details = result)
                                    } else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'complete_todo_task': $result"))))
                            }
                            "delete_file" -> {
                                val filePath = normalizePath(args?.path ?: "")
                                val deleteLog = AiActionLog(
                                    title = "Deleted file",
                                    status = "thinking",
                                    details = "$filePath"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + deleteLog

                                val result = try {
                                    repository.deleteFile(project.name, filePath)
                                    "Successfully deleted file '$filePath'"
                                } catch (e: Exception) {
                                    "Error deleting file: ${e.localizedMessage}"
                                }

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == deleteLog.id) log.copy(status = "success", details = result) else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'delete_file': $result"))))
                            }
                            "rename_file" -> {
                                val oldPath = normalizePath(args?.oldPath ?: "")
                                val newPath = normalizePath(args?.newPath ?: "")
                                val renameLog = AiActionLog(
                                    title = "Moved / Extracted code",
                                    status = "thinking",
                                    details = "$newPath"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + renameLog

                                val result = try {
                                    repository.renameFile(project.name, oldPath, newPath)
                                    "Successfully renamed '$oldPath' to '$newPath'"
                                } catch (e: Exception) {
                                    "Error renaming file: ${e.localizedMessage}"
                                }

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == renameLog.id) log.copy(status = "success", details = result) else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'rename_file': $result"))))
                            }
                            "move_file" -> {
                                val oldPath = normalizePath(args?.oldPath ?: "")
                                val newPath = normalizePath(args?.newPath ?: "")
                                val moveLog = AiActionLog(
                                    title = "Moved / Extracted code",
                                    status = "thinking",
                                    details = "$newPath"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + moveLog

                                val result = try {
                                    repository.moveFile(project.name, oldPath, newPath)
                                    "Successfully moved '$oldPath' to '$newPath'"
                                } catch (e: Exception) {
                                    "Error moving file: ${e.localizedMessage}"
                                }

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == moveLog.id) log.copy(status = "success", details = result) else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'move_file': $result"))))
                            }
                            "delete_code" -> {
                                val filePath = normalizePath(args?.path ?: "")
                                val searchStr = args?.search ?: ""
                                val deleteCodeLog = AiActionLog(
                                    title = "Deleted code block",
                                    status = "thinking",
                                    details = "$filePath"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + deleteCodeLog

                                val files = repository.getFilesForProject(project.name)
                                val targetFile = files.find { it.path == filePath }
                                val result = if (targetFile != null) {
                                    val originalContent = targetFile.content
                                    if (searchStr.isEmpty()) {
                                        "Error: 'search' block cannot be empty for code deletion. You must specify the exact, unique block of code you want to delete in the 'search' argument."
                                    } else if (!originalContent.contains(searchStr)) {
                                        "Error: Could not find exact code block in $filePath to delete."
                                    } else {
                                        val occurrences = originalContent.split(searchStr).size - 1
                                        if (occurrences > 1) {
                                            "Error: The code block to delete is not unique ($occurrences matches). Provide more context."
                                        } else {
                                            val updatedContent = originalContent.replace(searchStr, "")
                                            try {
                                                repository.saveFile(project.name, filePath, updatedContent)
                                                "Successfully deleted the specified code block from '$filePath'"
                                            } catch (e: Exception) {
                                                "Error writing file: ${e.localizedMessage}"
                                            }
                                        }
                                    }
                                } else {
                                    "Error: File '$filePath' not found."
                                }

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == deleteCodeLog.id) {
                                        log.copy(status = if (result.startsWith("Successfully")) "success" else "failed", details = result)
                                    } else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'delete_code': $result"))))
                            }
                            "move_code" -> {
                                val sourcePath = normalizePath(args?.path ?: "")
                                val destPath = normalizePath(args?.destinationPath ?: "")
                                val searchStr = args?.search ?: ""
                                val destSearchStr = args?.destinationSearch ?: ""
                                
                                val moveCodeLog = AiActionLog(
                                    title = "Moved code block",
                                    status = "thinking",
                                    details = "Moving from $sourcePath to $destPath"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + moveCodeLog

                                val files = repository.getFilesForProject(project.name)
                                val sourceFile = files.find { it.path == sourcePath }
                                val destFile = files.find { it.path == destPath }
                                
                                val result = if (sourceFile == null) {
                                    "Error: Source file '$sourcePath' not found."
                                } else if (destFile == null) {
                                    "Error: Destination file '$destPath' not found."
                                } else if (searchStr.isEmpty()) {
                                    "Error: 'search' code block to move cannot be empty."
                                } else if (!sourceFile.content.contains(searchStr)) {
                                    "Error: Could not find code block in source file '$sourcePath'."
                                } else {
                                    val sourceOccurrences = sourceFile.content.split(searchStr).size - 1
                                    if (sourceOccurrences > 1) {
                                        "Error: The code block to move is not unique in source file ($sourceOccurrences matches)."
                                    } else {
                                        // Try destination insertion
                                        val destContent = destFile.content
                                        val newDestContent = if (destSearchStr.isNotEmpty()) {
                                            if (!destContent.contains(destSearchStr)) {
                                                destContent + "\n" + searchStr
                                            } else {
                                                val destOccurrences = destContent.split(destSearchStr).size - 1
                                                if (destOccurrences > 1) {
                                                    "Error: destinationSearch block is not unique in '$destPath'."
                                                } else {
                                                    // Insert searchStr after destSearchStr
                                                    destContent.replace(destSearchStr, destSearchStr + "\n" + searchStr)
                                                }
                                            }
                                        } else {
                                            destContent + "\n" + searchStr
                                        }

                                        if (newDestContent.startsWith("Error:")) {
                                            newDestContent
                                        } else {
                                            try {
                                                // 1. Remove from source
                                                val newSourceContent = sourceFile.content.replace(searchStr, "")
                                                repository.saveFile(project.name, sourcePath, newSourceContent)
                                                
                                                // 2. Add to destination
                                                repository.saveFile(project.name, destPath, newDestContent)
                                                "Successfully moved code block from '$sourcePath' to '$destPath'"
                                            } catch (e: Exception) {
                                                "Error executing move_code: ${e.localizedMessage}"
                                            }
                                        }
                                    }
                                }

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == moveCodeLog.id) {
                                        log.copy(status = if (result.startsWith("Successfully")) "success" else "failed", details = result)
                                    } else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'move_code': $result"))))
                            }
                            "copy_code" -> {
                                val sourcePath = normalizePath(args?.path ?: "")
                                val destPath = normalizePath(args?.destinationPath ?: "")
                                val searchStr = args?.search ?: ""
                                val destSearchStr = args?.destinationSearch ?: ""
                                
                                val copyCodeLog = AiActionLog(
                                    title = "Copied code block",
                                    status = "thinking",
                                    details = "Copying from $sourcePath to $destPath"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + copyCodeLog

                                val files = repository.getFilesForProject(project.name)
                                val sourceFile = files.find { it.path == sourcePath }
                                val destFile = files.find { it.path == destPath }
                                
                                val result = if (sourceFile == null) {
                                    "Error: Source file '$sourcePath' not found."
                                } else if (destFile == null) {
                                    "Error: Destination file '$destPath' not found."
                                } else if (searchStr.isEmpty()) {
                                    "Error: 'search' code block to copy cannot be empty."
                                } else if (!sourceFile.content.contains(searchStr)) {
                                    "Error: Could not find code block in source file '$sourcePath'."
                                } else {
                                    val sourceOccurrences = sourceFile.content.split(searchStr).size - 1
                                    if (sourceOccurrences > 1) {
                                        "Error: The code block to copy is not unique in source file ($sourceOccurrences matches)."
                                    } else {
                                        // Try destination insertion
                                        val destContent = destFile.content
                                        val newDestContent = if (destSearchStr.isNotEmpty()) {
                                            if (!destContent.contains(destSearchStr)) {
                                                destContent + "\n" + searchStr
                                            } else {
                                                val destOccurrences = destContent.split(destSearchStr).size - 1
                                                if (destOccurrences > 1) {
                                                    "Error: destinationSearch block is not unique in '$destPath'."
                                                } else {
                                                    // Insert searchStr after destSearchStr
                                                    destContent.replace(destSearchStr, destSearchStr + "\n" + searchStr)
                                                }
                                            }
                                        } else {
                                            destContent + "\n" + searchStr
                                        }

                                        if (newDestContent.startsWith("Error:")) {
                                            newDestContent
                                        } else {
                                            try {
                                                // 1. Just write to destination (source remains untouched)
                                                repository.saveFile(project.name, destPath, newDestContent)
                                                "Successfully copied code block from '$sourcePath' to '$destPath'"
                                            } catch (e: Exception) {
                                                "Error executing copy_code: ${e.localizedMessage}"
                                            }
                                        }
                                    }
                                }

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == copyCodeLog.id) {
                                        log.copy(status = if (result.startsWith("Successfully")) "success" else "failed", details = result)
                                    } else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'copy_code': $result"))))
                            }
                            "run_command" -> {
                                val shellCmd = args?.command ?: ""
                                val shellLog = createAiLog(
                                    title = if (shellCmd.isNotEmpty()) "Run: $shellCmd" else "Executed shell command",
                                    status = "thinking",
                                    details = if (shellCmd.isNotEmpty()) "> $shellCmd" else null
                                )
                                _aiActionLogs.value = _aiActionLogs.value + shellLog

                                val result = repository.executeCommand(project.name, shellCmd)
                                appendToTerminal(shellCmd, result)

                                val finalDetails = if (shellCmd.isNotEmpty()) {
                                    "> $shellCmd\n\n" + if (result.isEmpty()) "(no output)" else result
                                } else {
                                    if (result.isEmpty()) "(no output)" else result
                                }
                                updateAiLog(shellLog.id, "success", finalDetails)

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'run_command': $result"))))
                            }
                            "global_search" -> {
                                val query = args?.query ?: ""
                                val searchLog = AiActionLog(
                                    title = "Search: $query",
                                    status = "thinking",
                                    details = "> grep -r '$query' ."
                                )
                                _aiActionLogs.value = _aiActionLogs.value + searchLog

                                val files = repository.getFilesForProject(project.name)
                                val matches = mutableListOf<String>()
                                files.forEach { file ->
                                    val lines = file.content.lines()
                                    lines.forEachIndexed { idx, line ->
                                        if (line.contains(query, ignoreCase = true)) {
                                            matches.add("${file.path}:${idx + 1}: $line")
                                        }
                                    }
                                }

                                val result = if (matches.isEmpty()) {
                                    "No occurrences of '$query' found."
                                } else {
                                    "Matches found for '$query':\n" + matches.joinToString("\n")
                                }
                                appendToTerminal("grep -rn \"$query\" .", result)

                                _aiActionLogs.value = _aiActionLogs.value.map { log ->
                                    if (log.id == searchLog.id) {
                                        val finalDetails = "> grep -r '$query' .\n\n$result"
                                        log.copy(status = "success", details = finalDetails)
                                    } else log
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'global_search': $result"))))
                            }
                            else -> {
                                loopCompleted = true
                            }
                        }
                        } // End of toolCalls for loop
                    } else {
                        val failLog = AiActionLog(
                            title = "Invalid response parsed",
                            status = "failed",
                            details = "API did not return structured tool JSON."
                        )
                        _aiActionLogs.value = _aiActionLogs.value + failLog
                        loopCompleted = true
                    }
                } catch (e: Exception) {
                    Log.e("VibeViewModel", "Error in AI agent loop", e)
                    val failLog = AiActionLog(
                        title = "Agent loop connection failure",
                        status = "failed",
                        details = "Error: ${e.localizedMessage}"
                    )
                    _aiActionLogs.value = _aiActionLogs.value + failLog
                    loopCompleted = true
                }
                
                turn++
                // Reload project files list inside the loop so changes update dynamically
                loadProjectDetails(project.name)
            }

            if (!loopCompleted && actionsCount >= maxActionSteps) {
                _isInterrupted.value = true
                val failLog = AiActionLog(
                    title = "Action limit reached",
                    status = "failed",
                    details = "AI reached the maximum number of actions/tool calls ($maxActionSteps) without completing the task."
                )
                _aiActionLogs.value = _aiActionLogs.value + failLog
            }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    if (!agentMessageSaved) {
                        val logsJson = try {
                            val listType = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
                            moshi.adapter<List<AiActionLog>>(listType).toJson(_aiActionLogs.value)
                        } catch (e: Exception) {
                            null
                        }
                        
                        val isSuccessful = loopCompleted && _aiActionLogs.value.none { it.status == "failed" }
                        val content = if (isSuccessful) {
                            lastThought?.ifBlank { null } ?: "Task completed successfully!"
                        } else {
                            val finalMessage = _aiActionLogs.value.lastOrNull { it.status == "failed" }?.details 
                                ?: "AI task stopped unexpectedly or hit a limit."
                            "Task interrupted: $finalMessage"
                        }
                        
                        val agentMsg = ChatMessageEntity(
                            projectName = project.name,
                            role = "assistant",
                            content = content,
                            timestamp = System.currentTimeMillis(),
                            aiActionLogsJson = logsJson
                        )
                        repository.insertChatMessage(agentMsg)
                    }
                    
                    _isThinking.value = false
                    _chatMessages.value = repository.getChatsForProject(project.name)
                    if (project.templateKey == "vanilla" || project.templateKey == "react" || project.templateKey == "fiber") {
                        _webPreviewRefreshTrigger.value += 1
                    }
                }
            }
        }
    }
}

