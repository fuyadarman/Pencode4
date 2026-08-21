package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.AgentFileAction
import com.example.api.AgentResponse
import com.example.api.Content
import com.example.api.EditChunk
import com.example.api.ReadRangeItem
import com.example.api.GeminiClient
import com.example.api.Part
import com.example.api.ToolCallResponse
import com.example.data.ChatMessageEntity
import com.example.data.ProjectEntity
import com.example.data.ProjectFileEntity
import com.example.data.VibeDatabase
import com.example.data.VibeRepository
import com.example.data.VibeAgentService
import com.example.data.McpManager
import com.example.data.McpServer
import com.example.data.McpPlatformType
import com.example.context.ContextOptimizationManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
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
    val isSelected: Boolean = true,
    val cleanFilePath: String = "",
    val codeSnippet: String = ""
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

data class WebArtifactInfo(
    val name: String,
    val fileCount: Int,
    val zipSizeBytes: Long,
    val localDir: String,
    val indexHtmlContent: String?
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

    val mcpManager = McpManager(application)
    val mcpServers: StateFlow<List<McpServer>> = mcpManager.servers

    fun startMcpOAuthFlow(activity: android.content.Context, serverId: String, clientId: String?) {
        viewModelScope.launch {
            mcpManager.startOAuthFlow(activity, serverId, clientId)
        }
    }

    fun handleMcpOAuthCallback(uri: android.net.Uri?) {
        if (uri == null) return
        val rawState = uri.getQueryParameter("state") ?: ""
        val serverIdFromState = if (rawState.contains(":")) rawState.substringBefore(":") else rawState

        val serverId = uri.getQueryParameter("server_id")
            ?: (if (serverIdFromState.isNotBlank() && mcpServers.value.any { it.id == serverIdFromState }) serverIdFromState else null)
            ?: mcpManager.tokenStore.findServerIdByState(rawState)
            ?: mcpServers.value.find {
                it.status.contains("Browser", ignoreCase = true) ||
                it.status.contains("Discovering", ignoreCase = true) ||
                it.status.contains("Connecting", ignoreCase = true) ||
                it.status.contains("Awaiting", ignoreCase = true) ||
                it.status.contains("Authorizing", ignoreCase = true)
            }?.id
            ?: mcpServers.value.firstOrNull()?.id
            ?: return

        viewModelScope.launch {
            val result = mcpManager.handleOAuthCallback(serverId, uri)
            if (result.isSuccess) {
                mcpManager.testAndConnectServer(serverId)
            }
        }
    }

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

    private val _selectedMcpServerIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMcpServerIds: StateFlow<Set<String>> = _selectedMcpServerIds.asStateFlow()

    fun toggleSelectMcpServer(serverId: String) {
        val current = _selectedMcpServerIds.value.toMutableSet()
        if (current.contains(serverId)) {
            current.remove(serverId)
        } else {
            current.add(serverId)
        }
        _selectedMcpServerIds.value = current
    }

    fun selectAllConnectedMcpServers() {
        val connected = mcpManager.servers.value.filter { it.status.startsWith("Connected") || it.availableTools.isNotEmpty() }.map { it.id }.toSet()
        _selectedMcpServerIds.value = connected
    }

    fun clearSelectedMcpServers() {
        _selectedMcpServerIds.value = emptySet()
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
    private val attemptedWebErrorKeys = mutableSetOf<String>()

    // Stateful detected android build errors
    private val _detectedAndroidBuildErrors = MutableStateFlow<List<AndroidBuildError>>(emptyList())
    val detectedAndroidBuildErrors: StateFlow<List<AndroidBuildError>> = _detectedAndroidBuildErrors.asStateFlow()
    private val attemptedAndroidErrorKeys = mutableSetOf<String>()

    // Stateful downloaded web build artifact
    private val _webArtifactInfo = MutableStateFlow<WebArtifactInfo?>(null)
    val webArtifactInfo: StateFlow<WebArtifactInfo?> = _webArtifactInfo.asStateFlow()

    fun addWebError(message: String, sourceId: String, lineNumber: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val files = repository.getFilesForProject(_currentProject.value?.name ?: "")
            val resolved = WebErrorResolver.resolveError(message, sourceId, lineNumber, files)
            val cleanSource = resolved.cleanFilePath
            val errorKey = "$cleanSource:${resolved.lineNumber}:$message"
            val alreadyExists = _detectedWebErrors.value.any { 
                it.message == message && it.lineNumber == resolved.lineNumber && (it.sourceId == cleanSource || it.cleanFilePath == cleanSource) 
            }
            if (!alreadyExists) {
                val newError = WebConsoleError(
                    message = message,
                    sourceId = cleanSource,
                    lineNumber = resolved.lineNumber,
                    cleanFilePath = resolved.cleanFilePath,
                    codeSnippet = resolved.codeSnippet
                )
                _detectedWebErrors.value = _detectedWebErrors.value + newError
                
                val hasAttempted = attemptedWebErrorKeys.contains(errorKey)
                if (_allowAutoFix.value && !_isThinking.value && !isAutoFixingWebErrors && !hasAttempted) {
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
    }

    private fun checkAndTriggerAutoFixOnAgentFinish() {
        if (_allowAutoFix.value && !_isThinking.value) {
            viewModelScope.launch(Dispatchers.Main) {
                // Realtime check: Wait 2500ms for web preview to reload and emit fresh errors if the bug persists
                kotlinx.coroutines.delay(2500)
                if (_isThinking.value) return@launch

                val currentFiles = repository.getFilesForProject(_currentProject.value?.name ?: "")
                // Filter detected errors to ensure they still match existing project files and have not been attempted
                val unattemptedWebErrors = _detectedWebErrors.value.filter { err ->
                    val key = "${err.cleanFilePath.ifBlank { err.sourceId }}:${err.lineNumber}:${err.message}"
                    val rawKey = "${err.sourceId}:${err.lineNumber}:${err.message}"
                    val notAttempted = !attemptedWebErrorKeys.contains(key) && !attemptedWebErrorKeys.contains(rawKey)
                    val matchedFile = WebErrorResolver.findMatchingFile(err.cleanFilePath.ifBlank { err.sourceId }, currentFiles)
                    notAttempted && matchedFile != null
                }
                if (unattemptedWebErrors.isNotEmpty() && !isAutoFixingWebErrors) {
                    isAutoFixingWebErrors = true
                    _detectedWebErrors.value = unattemptedWebErrors.map { err ->
                        err.copy(isSelected = true)
                    }
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
        attemptedWebErrorKeys.clear()
    }

    fun previewWebArtifact() {
        _currentTab.value = WorkspaceTab.PREVIEW
        _webPreviewRefreshTrigger.value += 1
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
        
        for (it in selected) {
            val keySource = it.cleanFilePath.ifBlank { it.sourceId }
            attemptedWebErrorKeys.add("$keySource:${it.lineNumber}:${it.message}")
        }

        viewModelScope.launch(Dispatchers.Default) {
            val files = repository.getFilesForProject(project.name)
            val resolvedList = selected.map { err ->
                if (err.cleanFilePath.isNotBlank() && err.codeSnippet.isNotBlank()) {
                    ResolvedWebError(
                        rawMessage = err.message,
                        rawSourceId = err.sourceId,
                        cleanFilePath = err.cleanFilePath,
                        lineNumber = err.lineNumber,
                        codeSnippet = err.codeSnippet
                    )
                } else {
                    WebErrorResolver.resolveError(err.message, err.sourceId, err.lineNumber, files)
                }
            }

            val errorReport = WebErrorResolver.buildPromptReport(resolvedList, files)
            _detectedWebErrors.value = emptyList()
            
            withContext(Dispatchers.Main) {
                // Switch tab to Chat to show the ongoing fixing conversation
                _currentTab.value = WorkspaceTab.CHAT
                sendPrompt(errorReport)
            }
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
        attemptedAndroidErrorKeys.clear()
    }

    fun parseAndroidBuildErrors(logs: String, stepName: String, workflowPath: String = ".github/workflows/android.yml"): List<AndroidBuildError> {
        val errors = mutableListOf<AndroidBuildError>()
        val lines = logs.split("\n")
        
        // Pattern 1: Kotlin/Java/File Compiler errors e.g. "e: file:///app/src/main/java/.../VibeViewModel.kt:463:24 Unresolved reference" or "/path/to/file.kt:12:34: error"
        val compilerErrorRegex = Regex("""(?:e:\s+)?(?:file:///)?([^:\n]+?):(\d+):(\d+)\s+(.+)""")
        val webErrorRegex = Regex("""(?:Failed to compile|SyntaxError|Error|Type error):\s*(.+?)(?:\s+in\s+(.+?):(\d+):(\d+))?""", RegexOption.IGNORE_CASE)
        
        for (line in lines) {
            val cleanLine = line.trim()
            val match = compilerErrorRegex.find(cleanLine)
            if (match != null && !cleanLine.startsWith("at ") && !cleanLine.contains("Process completed with exit code")) {
                val filePath = match.groupValues[1].removePrefix("file:///")
                val lineNumber = match.groupValues[2].toIntOrNull() ?: 1
                val errorMsg = match.groupValues[4]
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
            } else if (cleanLine.contains("npm ERR!") || cleanLine.contains("[vite]") || cleanLine.contains("Failed to compile") || cleanLine.contains("esbuild:")) {
                val webMatch = webErrorRegex.find(cleanLine)
                val msg = if (webMatch != null) webMatch.groupValues[1].ifBlank { cleanLine } else cleanLine
                val path = if (webMatch != null && webMatch.groupValues.size > 2 && webMatch.groupValues[2].isNotBlank()) webMatch.groupValues[2] else "package.json"
                val lineNum = if (webMatch != null && webMatch.groupValues.size > 3) webMatch.groupValues[3].toIntOrNull() ?: 1 else 1
                
                val cleanMsg = msg.replace(Regex("^\\[command\\]|\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z\\s*"), "").take(200)
                val alreadyExists = errors.any { it.message == cleanMsg && it.filePath == path }
                if (!alreadyExists && cleanMsg.isNotBlank()) {
                    errors.add(
                        AndroidBuildError(
                            stepName = stepName,
                            message = cleanMsg,
                            filePath = path,
                            lineNumber = lineNum,
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
                        filePath = workflowPath,
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
                    filePath = workflowPath,
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
        
        for (it in selected) {
            attemptedAndroidErrorKeys.add("${it.filePath}:${it.lineNumber}:${it.message}")
        }

        viewModelScope.launch {
            val errorReportBuilder = StringBuilder()
            for (it in selected) {
                val snippet = getCodeSnippetForError(project.name, it.filePath, it.lineNumber)
                errorReportBuilder.append("- Failed Step: \"${it.stepName}\"\n  Error: \"${it.message}\"\n  File: \"${it.filePath}\" at line ${it.lineNumber}$snippet\n  Details: ${it.logsSnippet}\n")
            }
            
            val errorReport = errorReportBuilder.toString()
            
            _detectedAndroidBuildErrors.value = emptyList()
            
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

    val openrouterModels = listOf(
        "google/gemini-2.5-flash",
        "google/gemini-2.5-pro",
        "meta-llama/llama-3.3-70b-instruct",
        "deepseek/deepseek-chat",
        "anthropic/claude-3.5-sonnet",
        "qwen/qwen-2.5-72b-instruct"
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

    private val _scannedModels = MutableStateFlow<List<String>>(emptyList())
    val scannedModels = _scannedModels.asStateFlow()

    private val _isScanningModels = MutableStateFlow(false)
    val isScanningModels = _isScanningModels.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError = _scanError.asStateFlow()

    private val _maxActionSteps = MutableStateFlow(sharedPrefs.getInt("max_action_steps", 80))
    val maxActionSteps = _maxActionSteps.asStateFlow()

    private val _allowBuildPush = MutableStateFlow(sharedPrefs.getBoolean("allow_build_push", false))
    val allowBuildPush = _allowBuildPush.asStateFlow()

    private val _allowAutoFix = MutableStateFlow(sharedPrefs.getBoolean("allow_auto_fix", false))
    val allowAutoFix = _allowAutoFix.asStateFlow()

    private val _allowBackgroundExecution = MutableStateFlow(sharedPrefs.getBoolean("allow_background_execution", false))
    val allowBackgroundExecution = _allowBackgroundExecution.asStateFlow()

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

    // Agent Skills Management
    private val _agentSkills = MutableStateFlow<List<com.example.ui.AgentSkill>>(emptyList())
    val agentSkills: StateFlow<List<com.example.ui.AgentSkill>> = _agentSkills.asStateFlow()

    private val _isFetchingSkills = MutableStateFlow(false)
    val isFetchingSkills: StateFlow<Boolean> = _isFetchingSkills.asStateFlow()

    private fun loadAgentSkills() {
        val json = sharedPrefs.getString("installed_agent_skills_json", null)
        val loadedSkills = if (!json.isNullOrBlank()) {
            try {
                val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.ui.AgentSkill::class.java)
                val adapter = moshi.adapter<List<com.example.ui.AgentSkill>>(listType)
                adapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()

        // Sync with local disk storage (context.filesDir/agent-skills/)
        val skillsDir = java.io.File(getApplication<android.app.Application>().filesDir, "agent-skills")
        val localFilesMap = mutableMapOf<String, String>()
        if (skillsDir.exists() && skillsDir.isDirectory) {
            skillsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".md", ignoreCase = true)) {
                    val skillId = file.name.removeSuffix(".md").removeSuffix(".MD")
                    try {
                        localFilesMap[skillId] = file.readText()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val loadedMap = loadedSkills.associateBy { it.id }

        // Start with pre-bundled default skills merged with saved user states
        val mergedDefaults = com.example.ui.defaultAgentSkills.map { defaultSkill ->
            val saved = loadedMap[defaultSkill.id]
            val localContent = localFilesMap[defaultSkill.id]
            if (saved != null) {
                defaultSkill.copy(
                    isInstalled = saved.isInstalled || localContent != null,
                    isEnabled = saved.isEnabled,
                    skillPrompt = if (localContent != null && localContent.isNotBlank()) localContent else if (saved.skillPrompt.isNotBlank()) saved.skillPrompt else defaultSkill.skillPrompt
                )
            } else if (localContent != null) {
                defaultSkill.copy(
                    isInstalled = true,
                    isEnabled = true,
                    skillPrompt = localContent
                )
            } else defaultSkill
        }

        val defaultIds = com.example.ui.defaultAgentSkills.map { it.id }.toSet()
        val customLoaded = loadedSkills.filter { it.id !in defaultIds }.map { skill ->
            val localContent = localFilesMap[skill.id]
            if (localContent != null) {
                skill.copy(
                    isInstalled = true,
                    skillPrompt = if (localContent.isNotBlank()) localContent else skill.skillPrompt
                )
            } else skill
        }

        val existingIds = (defaultIds + customLoaded.map { it.id }).toSet()
        val extraDiskSkills = localFilesMap.filterKeys { it !in existingIds }.map { (id, content) ->
            var skillName = id.replace("-", " ").split(" ").joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
            var skillDesc = "Locally stored agent skill file ($id.md)."
            var skillAuthor = "Local Storage"
            
            if (content.startsWith("---")) {
                val parts = content.split("---", limit = 3)
                if (parts.size >= 3) {
                    parts[1].lines().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("name:")) {
                            val n = trimmed.substringAfter("name:").trim().removeSurrounding("\"").removeSurrounding("'")
                            if (n.isNotBlank()) skillName = n
                        } else if (trimmed.startsWith("description:")) {
                            val d = trimmed.substringAfter("description:").trim().removeSurrounding("\"").removeSurrounding("'")
                            if (d.isNotBlank()) skillDesc = d
                        } else if (trimmed.startsWith("author:")) {
                            val a = trimmed.substringAfter("author:").trim().removeSurrounding("\"").removeSurrounding("'")
                            if (a.isNotBlank()) skillAuthor = a
                        }
                    }
                }
            }

            com.example.ui.AgentSkill(
                id = id,
                name = skillName,
                author = skillAuthor,
                installs = "Installed",
                description = skillDesc,
                isInstalled = true,
                isEnabled = true,
                skillPrompt = content
            )
        }

        val finalSkills = mergedDefaults + customLoaded + extraDiskSkills
        _agentSkills.value = finalSkills
        saveAgentSkillsInternal()

        fetchOnlineAgentSkills()
    }

    fun fetchOnlineAgentSkills(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isFetchingSkills.value = true
            var newCount = 0
            try {
                val repos = listOf(
                    Triple("vercel-labs", "agent-skills", "https://github.com/vercel-labs/agent-skills"),
                    Triple("anthropics", "courses", "https://github.com/anthropics/courses"),
                    Triple("google-gemini", "cookbook", "https://github.com/google-gemini/cookbook"),
                    Triple("browser-use", "browser-use", "https://github.com/browser-use/browser-use"),
                    Triple("composiohq", "composio", "https://github.com/composiohq/composio"),
                    Triple("humanlayer", "humanlayer", "https://github.com/humanlayer/humanlayer"),
                    Triple("langchain-ai", "langchain", "https://github.com/langchain-ai/langchain")
                )

                val fetchedList = mutableListOf<com.example.ui.AgentSkill>()

                for ((orgName, repo, githubUrl) in repos) {
                    try {
                        val branches = listOf("main", "master")
                        var fetchedFromTree = false

                        for (branch in branches) {
                            if (fetchedFromTree) break
                            try {
                                val treeApiUrl = "https://api.github.com/repos/$orgName/$repo/git/trees/$branch?recursive=1"
                                val url = java.net.URL(treeApiUrl)
                                val conn = url.openConnection() as java.net.HttpURLConnection
                                conn.requestMethod = "GET"
                                conn.setRequestProperty("User-Agent", "PenCode-Android-Agent")
                                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                                conn.connectTimeout = 5000
                                conn.readTimeout = 5000

                                if (conn.responseCode == 200) {
                                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                                    val jsonObj = org.json.JSONObject(responseText)
                                    val treeArray = jsonObj.optJSONArray("tree") ?: org.json.JSONArray()

                                    for (i in 0 until treeArray.length()) {
                                        val item = treeArray.getJSONObject(i)
                                        val path = item.optString("path", "")

                                        val isSkillFile = (path.endsWith(".md", ignoreCase = true) || path.endsWith(".json", ignoreCase = true)) &&
                                            !path.equals("LICENSE", ignoreCase = true) &&
                                            !path.equals("package.json", ignoreCase = true) &&
                                            !path.contains(".github/")

                                        if (isSkillFile) {
                                            val fileName = path.substringAfterLast("/")
                                            val rawName = if (fileName.equals("SKILL.md", ignoreCase = true) || fileName.equals("SKILL.json", ignoreCase = true) || fileName.equals("README.md", ignoreCase = true)) {
                                                val segs = path.split("/")
                                                if (segs.size >= 2) segs[segs.size - 2] else fileName.removeSuffix(".md").removeSuffix(".json")
                                            } else {
                                                fileName.removeSuffix(".md").removeSuffix(".json")
                                            }

                                            val cleanName = rawName.replace("-", " ").replace("_", " ").trim()
                                            if (cleanName.isBlank()) continue

                                            val skillId = "$orgName-${path.lowercase().replace("/", "-").replace(".", "-")}"
                                            if (_agentSkills.value.none { it.id == skillId } && fetchedList.none { it.id == skillId }) {
                                                val formattedName = cleanName.split(" ")
                                                    .filter { it.isNotBlank() }
                                                    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

                                                val rawUrl = "https://raw.githubusercontent.com/$orgName/$repo/$branch/$path"

                                                fetchedList.add(
                                                    com.example.ui.AgentSkill(
                                                        id = skillId,
                                                        name = "$formattedName ($orgName)",
                                                        author = "$orgName/$repo",
                                                        installs = "GitHub Skill",
                                                        description = "Real agent skill from $orgName/$repo ($path).",
                                                        githubUrl = githubUrl,
                                                        isInstalled = false,
                                                        isEnabled = false,
                                                        skillPrompt = "Skill source file: $path ($githubUrl)\nRaw URL: $rawUrl",
                                                        filePath = path,
                                                        rawFileUrl = rawUrl
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    if (treeArray.length() > 0) {
                                        fetchedFromTree = true
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (fetchedList.isNotEmpty()) {
                    newCount = fetchedList.size
                    _agentSkills.value = _agentSkills.value + fetchedList
                    saveAgentSkillsInternal()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isFetchingSkills.value = false
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete?.invoke(newCount)
                }
            }
        }
    }

    fun fetchSkillFileContent(skillId: String, onLoaded: ((String) -> Unit)? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val skill = _agentSkills.value.find { it.id == skillId } ?: return@launch
            if (skill.skillPrompt.isNotBlank() && !skill.skillPrompt.startsWith("Skill source file") && !skill.skillPrompt.startsWith("Skill live fetched")) {
                withContext(kotlinx.coroutines.Dispatchers.Main) { onLoaded?.invoke(skill.skillPrompt) }
                return@launch
            }

            val rawUrl = skill.rawFileUrl.ifBlank {
                val parts = skill.author.split("/")
                if (parts.size == 2) {
                    "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/main/${skill.filePath.ifBlank { "SKILL.md" }}"
                } else ""
            }

            if (rawUrl.isNotBlank()) {
                try {
                    val url = java.net.URL(rawUrl)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    if (conn.responseCode == 200) {
                        val content = conn.inputStream.bufferedReader().use { it.readText() }
                        if (content.isNotBlank()) {
                            val updatedList = _agentSkills.value.map {
                                if (it.id == skillId) it.copy(skillPrompt = content) else it
                            }
                            _agentSkills.value = updatedList
                            saveAgentSkillsInternal()
                            withContext(kotlinx.coroutines.Dispatchers.Main) { onLoaded?.invoke(content) }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(kotlinx.coroutines.Dispatchers.Main) { onLoaded?.invoke(skill.skillPrompt) }
        }
    }

    fun updateSkillContent(skillId: String, newContent: String) {
        val updatedList = _agentSkills.value.map {
            if (it.id == skillId) it.copy(skillPrompt = newContent) else it
        }
        _agentSkills.value = updatedList
        saveAgentSkillsInternal()
    }

    private fun saveAgentSkillsInternal() {
        try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.ui.AgentSkill::class.java)
            val adapter = moshi.adapter<List<com.example.ui.AgentSkill>>(listType)
            val json = adapter.toJson(_agentSkills.value)
            sharedPrefs.edit().putString("installed_agent_skills_json", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Saved skills to system shared preferences in background
    }

    fun toggleAgentSkill(skillId: String, enabled: Boolean) {
        _agentSkills.value = _agentSkills.value.map { skill ->
            if (skill.id == skillId) skill.copy(isEnabled = enabled) else skill
        }
        saveAgentSkillsInternal()
    }

    fun installAgentSkill(skillId: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val skill = _agentSkills.value.find { it.id == skillId } ?: return@launch

            var content = skill.skillPrompt
            if (content.isBlank() || content.startsWith("Skill source file") || content.startsWith("Skill live fetched")) {
                val rawUrl = skill.rawFileUrl.ifBlank {
                    val parts = skill.author.split("/")
                    if (parts.size == 2) {
                        "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/main/${skill.filePath.ifBlank { "SKILL.md" }}"
                    } else ""
                }
                if (rawUrl.isNotBlank()) {
                    try {
                        val url = java.net.URL(rawUrl)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        if (conn.responseCode == 200) {
                            val fetched = conn.inputStream.bufferedReader().use { it.readText() }
                            if (fetched.isNotBlank()) {
                                content = fetched
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            try {
                val skillsDir = java.io.File(getApplication<android.app.Application>().filesDir, "agent-skills")
                if (!skillsDir.exists()) skillsDir.mkdirs()
                val localFile = java.io.File(skillsDir, "${skill.id}.md")
                localFile.writeText(content)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val finalContent = content
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _agentSkills.value = _agentSkills.value.map { s ->
                    if (s.id == skillId) s.copy(isInstalled = true, isEnabled = true, skillPrompt = finalContent) else s
                }
                saveAgentSkillsInternal()
            }
        }
    }

    fun uninstallAgentSkill(skillId: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val skillsDir = java.io.File(getApplication<android.app.Application>().filesDir, "agent-skills")
                val localFile = java.io.File(skillsDir, "$skillId.md")
                if (localFile.exists()) {
                    localFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _agentSkills.value = _agentSkills.value.map { skill ->
                    if (skill.id == skillId) skill.copy(isInstalled = false, isEnabled = false) else skill
                }
                saveAgentSkillsInternal()
            }
        }
    }

    fun addCustomAgentSkill(newSkill: com.example.ui.AgentSkill) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val skillsDir = java.io.File(getApplication<android.app.Application>().filesDir, "agent-skills")
                if (!skillsDir.exists()) skillsDir.mkdirs()
                val localFile = java.io.File(skillsDir, "${newSkill.id}.md")
                localFile.writeText(newSkill.skillPrompt)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                val installedCustom = newSkill.copy(isInstalled = true, isEnabled = true)
                val existing = _agentSkills.value.filter { it.id != newSkill.id }
                _agentSkills.value = existing + installedCustom
                saveAgentSkillsInternal()
            }
        }
    }

    // Skills are kept strictly in system background memory (SharedPreferences / ViewModel)
    // and never written as files in the user's workspace or File Explorer.

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

    private fun updateAiLog(logId: String, status: String, details: String? = null, lineRange: String? = null) {
        _aiActionLogs.value = _aiActionLogs.value.map { log ->
            if (log.id == logId) {
                log.copy(
                    status = status,
                    details = details ?: log.details,
                    lineRange = lineRange ?: log.lineRange,
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

    data class WriteFileConfirmInfo(
        val path: String,
        val newContent: String,
        val existingLinesCount: Int
    )

    private val _writeFileConfirmInfo = MutableStateFlow<WriteFileConfirmInfo?>(null)
    val writeFileConfirmInfo = _writeFileConfirmInfo.asStateFlow()

    private var writeFileConfirmationDeferred: kotlinx.coroutines.CompletableDeferred<Boolean>? = null

    fun approveWriteFile() {
        writeFileConfirmationDeferred?.complete(true)
        _writeFileConfirmInfo.value = null
    }

    fun rejectWriteFile() {
        writeFileConfirmationDeferred?.complete(false)
        _writeFileConfirmInfo.value = null
    }

    private var lastDownloadedRunId: Long
        get() = sharedPrefs.getLong("last_downloaded_run_id", 0L)
        set(value) {
            sharedPrefs.edit().putLong("last_downloaded_run_id", value).apply()
        }

    private var pollJob: kotlinx.coroutines.Job? = null

    fun saveGithubRepo(repo: String) {
        _githubRepo.value = repo
        sharedPrefs.edit().putString("github_repo", repo).apply()
        startPollingBuild()
    }

    fun saveGithubBranch(branch: String) {
        _githubBranch.value = branch
        sharedPrefs.edit().putString("github_branch", branch).apply()
        startPollingBuild()
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
                                    
                                    val isCommandWf = wfPath.lowercase().endsWith("command.yml") || wfName.contains("Command", ignoreCase = true)
                                    if (!isCommandWf) {
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
                                        val jobsMapList = jobsList.mapNotNull { it as? Map<*, *> }
                                        val failedJobInList = jobsMapList.find { (it["conclusion"] as? String) == "failure" }
                                        val primaryJob = failedJobInList 
                                            ?: jobsMapList.find { (it["status"] as? String) == "in_progress" } 
                                            ?: jobsMapList[0]

                                        val stepsList = primaryJob["steps"] as? List<*>
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
                                        val runName = (latestRun["name"] as? String) ?: "Workflow"
                                        val wfPath = (latestRun["path"] as? String) ?: ".github/workflows/android.yml"

                                        if (failedStep != null) {
                                            _buildError.value = "$runName failed at step: '${failedStep.name}'."
                                        } else if (conclusion == "failure") {
                                            _buildError.value = "$runName failed."
                                        } else {
                                            _buildError.value = null
                                            _detectedAndroidBuildErrors.value = emptyList()
                                        }

                                        val jobId = (primaryJob["id"] as? Number)?.toLong()
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
                                                
                                                if (failedStep != null || conclusion == "failure") {
                                                    if (lastAutoFixedRunId != runId) {
                                                        val stepTitle = failedStep?.name ?: "Workflow execution"
                                                        val parsedErrors = parseAndroidBuildErrors(cleanLogs, stepTitle, wfPath)
                                                        _detectedAndroidBuildErrors.value = parsedErrors
                                                        
                                                        val unattemptedErrors = parsedErrors.filter { err ->
                                                            val key = "${err.filePath}:${err.lineNumber}:${err.message}"
                                                            !attemptedAndroidErrorKeys.contains(key)
                                                        }
                                                        
                                                        if (_allowAutoFix.value && unattemptedErrors.isNotEmpty()) {
                                                            lastAutoFixedRunId = runId
                                                            _detectedAndroidBuildErrors.value = parsedErrors.map { err ->
                                                                val key = "${err.filePath}:${err.lineNumber}:${err.message}"
                                                                err.copy(isSelected = !attemptedAndroidErrorKeys.contains(key))
                                                            }
                                                            if (!_isThinking.value) {
                                                                viewModelScope.launch(Dispatchers.Main) {
                                                                    fixSelectedAndroidBuildErrors()
                                                                }
                                                            }
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

    fun triggerWorkflows(selectedWorkflowIds: Set<Long>? = null) {
        lastAutoFixedRunId = -1L
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
            val targetWfs = currentWfs.filter { wf ->
                val status = wf.latestRunStatus.lowercase()
                val isRunning = wf.isTriggering || 
                        status == "in_progress" || 
                        status == "queued" || 
                        status == "requested" || 
                        status == "waiting"
                val isActive = wf.state == "active"
                val isSelected = selectedWorkflowIds.isNullOrEmpty() || selectedWorkflowIds.contains(wf.id)
                isActive && !isRunning && isSelected
            }

            if (targetWfs.isEmpty()) {
                Log.d("VibeViewModel", "No eligible non-running workflows to trigger.")
                return@launch
            }

            val targetIds = targetWfs.map { it.id }.toSet()
            _gitHubWorkflows.value = currentWfs.map { wf ->
                if (targetIds.contains(wf.id)) wf.copy(isTriggering = true) else wf
            }

            val client = OkHttpClient()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            targetWfs.forEach { wf ->
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

    fun triggerAllWorkflows() {
        triggerWorkflows(null)
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

    fun downloadAndUnzipApk(owner: String, repoName: String, runId: Long, tokenVal: String, force: Boolean = false) {
        if (!force && lastDownloadedRunId == runId) return
        lastDownloadedRunId = runId
        
        applicationScope.launch(Dispatchers.IO) {
            _apkDownloadProgress.value = "Fetching build artifacts..."
            showDownloadNotification(-1, "Pencode AI Build", "Fetching build artifacts...")
            try {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .connectionPool(okhttp3.ConnectionPool(10, 5, java.util.concurrent.TimeUnit.MINUTES))
                    .retryOnConnectionFailure(true)
                    .build()
                val moshi = Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()

                var artifacts: List<*>? = null
                var attempt = 0
                val artifactsUrl = "https://api.github.com/repos/$owner/$repoName/actions/runs/$runId/artifacts"

                // Retry up to 3 times for run-specific artifacts
                while (attempt < 3 && artifacts.isNullOrEmpty()) {
                    attempt++
                    try {
                        val request = Request.Builder()
                            .url(artifactsUrl)
                            .header("Authorization", "token $tokenVal")
                            .header("Accept", "application/vnd.github.v3+json")
                            .build()
                        val response = okHttpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: ""
                            val map = moshi.adapter(Map::class.java).fromJson(bodyStr) as? Map<*, *>
                            artifacts = map?.get("artifacts") as? List<*>
                        }
                    } catch (e: Exception) {
                        Log.e("VibeViewModel", "Error fetching artifacts attempt $attempt: ${e.message}")
                    }
                    if (artifacts.isNullOrEmpty() && attempt < 3) {
                        delay(3000) // Wait 3s for GitHub Actions to register artifacts
                    }
                }

                // Fallback: Query repo-level artifacts if run-level artifacts are empty
                if (artifacts.isNullOrEmpty()) {
                    try {
                        val repoArtifactsUrl = "https://api.github.com/repos/$owner/$repoName/actions/artifacts?per_page=10"
                        val request = Request.Builder()
                            .url(repoArtifactsUrl)
                            .header("Authorization", "token $tokenVal")
                            .header("Accept", "application/vnd.github.v3+json")
                            .build()
                        val response = okHttpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: ""
                            val map = moshi.adapter(Map::class.java).fromJson(bodyStr) as? Map<*, *>
                            val repoArtifacts = map?.get("artifacts") as? List<*>
                            if (!repoArtifacts.isNullOrEmpty()) {
                                // Match artifact by runId if possible, else take most recent
                                val matched = repoArtifacts.firstOrNull { art ->
                                    val artMap = art as? Map<*, *>
                                    val wfRun = artMap?.get("workflow_run") as? Map<*, *>
                                    val rId = (wfRun?.get("id") as? Number)?.toLong()
                                    rId == runId
                                } ?: repoArtifacts.firstOrNull()
                                if (matched != null) {
                                    artifacts = listOf(matched)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VibeViewModel", "Fallback repo artifact fetch error: ${e.message}")
                    }
                }

                // Filter out expired artifacts
                val validArtifacts = artifacts?.mapNotNull { it as? Map<*, *> }?.filter { it["expired"] != true }

                if (validArtifacts.isNullOrEmpty()) {
                    _apkDownloadProgress.value = "Artifacts expired or not found for run #$runId. Click 'Build' to generate a fresh build."
                    lastDownloadedRunId = 0L // Reset so retry can happen
                    return@launch
                }
                
                // Find first valid artifact
                val firstArtifact = validArtifacts.firstOrNull()
                val artifactId = (firstArtifact?.get("id") as? Number)?.toLong()
                val downloadUrl = firstArtifact?.get("archive_download_url") as? String
                
                if (artifactId == null || downloadUrl == null) {
                    _apkDownloadProgress.value = "Invalid artifact data."
                    _apkDownloadPercentage.value = null
                    lastDownloadedRunId = 0L
                    return@launch
                }
                
                _apkDownloadProgress.value = "Downloading built APK..."
                _apkDownloadPercentage.value = 0f

                // Follow redirects manually. ONLY send Authorization on initial API call to api.github.com.
                // Do NOT send Authorization header on redirected CDN/storage URLs (e.g., pipelines.actions.githubusercontent.com / S3 / Azure Blob) as signed URLs reject custom Auth headers with 410/400.
                var currentUrl: String = downloadUrl
                var downloadResponse: okhttp3.Response? = null
                var redirectCount = 0
                val redirectClient = okHttpClient.newBuilder().followRedirects(false).build()

                while (redirectCount < 10) {
                    val reqBuilder = Request.Builder().url(currentUrl)
                    if (redirectCount == 0 && (currentUrl.contains("api.github.com") || currentUrl.contains("/actions/artifacts/"))) {
                        reqBuilder.header("Authorization", "token $tokenVal")
                        reqBuilder.header("Accept", "application/vnd.github.v3+json")
                    }
                    val req = reqBuilder.build()
                    val resp = redirectClient.newCall(req).execute()

                    if (resp.isRedirect) {
                        val loc = resp.header("Location")
                        resp.close()
                        if (loc.isNullOrEmpty()) {
                            downloadResponse = resp
                            break
                        }
                        currentUrl = loc
                        redirectCount++
                    } else {
                        downloadResponse = resp
                        break
                    }
                }

                if (downloadResponse == null || !downloadResponse.isSuccessful) {
                    val statusCode = downloadResponse?.code
                    if (statusCode == 410) {
                        _apkDownloadProgress.value = "Artifact expired on GitHub (Status: 410). Click 'Build' to generate a new build."
                    } else {
                        _apkDownloadProgress.value = "Download failed (Status: ${statusCode ?: "No Response"})"
                    }
                    _apkDownloadPercentage.value = null
                    lastDownloadedRunId = 0L
                    return@launch
                }
                
                val body = downloadResponse.body
                if (body == null) {
                    _apkDownloadProgress.value = "Empty response body."
                    _apkDownloadPercentage.value = null
                    lastDownloadedRunId = 0L
                    return@launch
                }
                
                val contentLength = body.contentLength()
                val context = getApplication<Application>()
                val cacheDir = context.cacheDir
                val tempZipFile = java.io.File(cacheDir, "downloaded_artifacts.zip")
                if (tempZipFile.exists()) {
                    tempZipFile.delete()
                }
                
                val inputStream = java.io.BufferedInputStream(body.byteStream(), 262144)
                val outputStream = java.io.BufferedOutputStream(java.io.FileOutputStream(tempZipFile), 262144)
                val buffer = ByteArray(262144) // 256KB buffer for max network speed
                var bytesRead: Int
                var totalBytesRead = 0L
                
                var lastNotificationTime = 0L
                var lastProgressPercent = -1
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    val currentTime = System.currentTimeMillis()
                    val progress = if (contentLength > 0) (totalBytesRead * 100f / contentLength) else -1f
                    val currentPercent = progress.toInt()

                    // Throttle progress updates to at most once every 800ms or 5% change to avoid IPC/UI lock slowdowns
                    if (currentTime - lastNotificationTime > 800L || currentPercent >= lastProgressPercent + 5) {
                        lastNotificationTime = currentTime
                        lastProgressPercent = currentPercent
                        
                        if (contentLength > 0) {
                            _apkDownloadPercentage.value = progress
                            _apkDownloadProgress.value = "Downloading built APK (${currentPercent}%)..."
                            showDownloadNotification(currentPercent, "Pencode AI Build", "Downloading built APK (${currentPercent}%)...")
                        } else {
                            val mbRead = totalBytesRead / (1024 * 1024)
                            _apkDownloadProgress.value = "Downloading built APK (${mbRead} MB)..."
                            showDownloadNotification(-1, "Pencode AI Build", "Downloading built APK (${mbRead} MB)...")
                        }
                    }
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                
                _apkDownloadProgress.value = "Extracting app package (Unzipping)..."
                _apkDownloadPercentage.value = null
                showDownloadNotification(-1, "Pencode AI Build", "Extracting app package (Unzipping)...")
                
                val outputApkFile = java.io.File(cacheDir, "downloaded_app.apk")
                if (outputApkFile.exists()) {
                    outputApkFile.delete()
                }
                
                val safeProjectName = (_currentProject.value?.name ?: "default").replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val webDistDir = java.io.File(cacheDir, "web_dist_$safeProjectName")
                if (webDistDir.exists()) {
                    webDistDir.deleteRecursively()
                }
                webDistDir.mkdirs()

                var zipIn = java.util.zip.ZipInputStream(java.io.BufferedInputStream(java.io.FileInputStream(tempZipFile)))
                var entry = zipIn.nextEntry
                var apkFound = false
                var webFilesExtracted = 0
                var indexHtmlContent: String? = null

                while (entry != null) {
                    val entryName = entry.name.replace('\\', '/')
                    if (!entry.isDirectory && entryName.endsWith(".apk")) {
                        val outStream = java.io.BufferedOutputStream(java.io.FileOutputStream(outputApkFile), 262144)
                        val outBuffer = ByteArray(262144) // 256KB for faster extraction
                        var len = zipIn.read(outBuffer)
                        while (len > 0) {
                            outStream.write(outBuffer, 0, len)
                            len = zipIn.read(outBuffer)
                        }
                        outStream.close()
                        apkFound = true
                        break
                    } else if (!entry.isDirectory) {
                        val destFile = java.io.File(webDistDir, entryName)
                        destFile.parentFile?.mkdirs()
                        val outStream = java.io.BufferedOutputStream(java.io.FileOutputStream(destFile), 262144)
                        val outBuffer = ByteArray(262144)
                        var len = zipIn.read(outBuffer)
                        while (len > 0) {
                            outStream.write(outBuffer, 0, len)
                            len = zipIn.read(outBuffer)
                        }
                        outStream.close()
                        webFilesExtracted++

                        if (destFile.name == "index.html") {
                            try {
                                indexHtmlContent = destFile.readText()
                            } catch (e: Exception) {
                                Log.e("VibeViewModel", "Error reading index.html: ${e.message}")
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
                zipIn.close()

                zipIn.close()

                // Recursive zip extraction to unpack any nested zip files (e.g. inner web-dist.zip)
                fun extractZipsInDir(targetDir: java.io.File, depth: Int = 0) {
                    if (depth > 5) return
                    val zips = mutableListOf<java.io.File>()
                    fun findZips(d: java.io.File) {
                        d.listFiles()?.forEach { f ->
                            if (f.isDirectory) findZips(f)
                            else if (f.isFile && f.name.endsWith(".zip", ignoreCase = true)) zips.add(f)
                        }
                    }
                    findZips(targetDir)
                    if (zips.isEmpty()) return
                    for (zipFile in zips) {
                        try {
                            val destDir = zipFile.parentFile ?: targetDir
                            val zIn = java.util.zip.ZipInputStream(java.io.BufferedInputStream(java.io.FileInputStream(zipFile)))
                            var zEntry = zIn.nextEntry
                            while (zEntry != null) {
                                if (!zEntry.isDirectory) {
                                    val entryName = zEntry.name.replace('\\', '/')
                                    val destFile = java.io.File(destDir, entryName)
                                    destFile.parentFile?.mkdirs()
                                    val outStream = java.io.BufferedOutputStream(java.io.FileOutputStream(destFile))
                                    val buffer = ByteArray(131072)
                                    var len = zIn.read(buffer)
                                    while (len > 0) {
                                        outStream.write(buffer, 0, len)
                                        len = zIn.read(buffer)
                                    }
                                    outStream.close()
                                    webFilesExtracted++
                                }
                                zIn.closeEntry()
                                zEntry = zIn.nextEntry
                            }
                            zIn.close()
                            zipFile.delete()
                        } catch (e: Exception) {
                            Log.e("VibeViewModel", "Error extracting zip ${zipFile.name}: ${e.message}")
                            zipFile.delete()
                        }
                    }
                    extractZipsInDir(targetDir, depth + 1)
                }

                extractZipsInDir(webDistDir)

                val zipSizeBytes = tempZipFile.length()
                if (tempZipFile.exists()) {
                    tempZipFile.delete()
                }

                // Recursively find index.html in webDistDir
                var foundIndexHtmlFile: java.io.File? = null
                fun findIndexHtml(dir: java.io.File) {
                    val list = dir.listFiles() ?: return
                    for (f in list) {
                        if (f.isFile && f.name.equals("index.html", ignoreCase = true)) {
                            foundIndexHtmlFile = f
                            return
                        }
                    }
                    for (f in list) {
                        if (f.isDirectory) {
                            findIndexHtml(f)
                            if (foundIndexHtmlFile != null) return
                        }
                    }
                }
                findIndexHtml(webDistDir)
                if (foundIndexHtmlFile != null) {
                    try {
                        indexHtmlContent = foundIndexHtmlFile!!.readText()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                
                if (apkFound && outputApkFile.exists() && outputApkFile.length() > 0) {
                    _localApkPath.value = outputApkFile.absolutePath
                    _apkDownloadProgress.value = "Success: App compiled & ready to install!"
                    showDownloadNotification(100, "Pencode AI Build", "Success: App compiled & ready to install!", true)
                    _currentTab.value = WorkspaceTab.ANDROID_BUILD
                } else if (webFilesExtracted > 0 || foundIndexHtmlFile != null) {
                    var effectiveWebDir = webDistDir
                    if (foundIndexHtmlFile != null) {
                        effectiveWebDir = foundIndexHtmlFile!!.parentFile ?: webDistDir
                    }
                    val htmlContentToUse = indexHtmlContent ?: java.io.File(effectiveWebDir, "index.html").let { if (it.exists()) it.readText() else null }
                    _webArtifactInfo.value = WebArtifactInfo(
                        name = "web-dist.zip",
                        fileCount = webFilesExtracted,
                        zipSizeBytes = zipSizeBytes,
                        localDir = effectiveWebDir.absolutePath,
                        indexHtmlContent = htmlContentToUse
                    )
                    
                    com.example.api.LocalHttpServer.setWebDistDir(effectiveWebDir.absolutePath)
                    com.example.api.LocalHttpServer.updateFiles(_projectFiles.value)

                    _apkDownloadProgress.value = "Success: Web Artifacts (web-dist.zip) downloaded, unzipped & running on Live Web Preview!"
                    showDownloadNotification(100, "Pencode AI Build", "Success: Web Artifacts downloaded & unzipped!", true)
                    _webPreviewRefreshTrigger.value += 1
                    _currentTab.value = WorkspaceTab.PREVIEW
                } else {
                    _apkDownloadProgress.value = "Unzip completed but no valid build artifacts found."
                    showDownloadNotification(0, "Pencode AI Build", "Unzip completed but no valid artifacts found.", true)
                    lastDownloadedRunId = 0L
                }
            } catch (e: Exception) {
                _apkDownloadProgress.value = "Extraction failed: ${e.localizedMessage}"
                _apkDownloadPercentage.value = null
                showDownloadNotification(0, "Pencode AI Build", "Extraction failed: ${e.localizedMessage}", true)
                lastDownloadedRunId = 0L
            }
        }
    }

    fun fetchLatestArtifact() {
        val repoVal = _githubRepo.value.trim()
        val tokenVal = _githubToken.value.trim()
        if (repoVal.isEmpty() || tokenVal.isEmpty()) {
            _apkDownloadProgress.value = "Please configure GitHub repo and token."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _apkDownloadProgress.value = "Fetching latest build artifacts..."
            val cleanRepo = repoVal.removePrefix("https://github.com/").removePrefix("http://github.com/").removeSuffix(".git")
            val parts = cleanRepo.split("/")
            val owner = if (parts.size >= 2) parts[0] else cachedUsername ?: ""
            val repoName = if (parts.size >= 2) parts[1] else parts[0]

            try {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val runsUrl = "https://api.github.com/repos/$owner/$repoName/actions/runs?per_page=10"
                val request = Request.Builder()
                    .url(runsUrl)
                    .header("Authorization", "token $tokenVal")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val moshi = Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                    val map = moshi.adapter(Map::class.java).fromJson(bodyStr) as? Map<*, *>
                    val runs = map?.get("workflow_runs") as? List<*>
                    val completedRun = runs?.firstOrNull { run ->
                        val rMap = run as? Map<*, *>
                        rMap?.get("status") == "completed" && rMap?.get("conclusion") == "success"
                    } as? Map<*, *>
                    val runId = (completedRun?.get("id") as? Number)?.toLong()
                    if (runId != null) {
                        downloadAndUnzipApk(owner, repoName, runId, tokenVal, force = true)
                    } else {
                        _apkDownloadProgress.value = "No successful workflow run found to fetch artifacts."
                    }
                } else {
                    _apkDownloadProgress.value = "Failed to list workflow runs (HTTP ${response.code})."
                }
            } catch (e: Exception) {
                _apkDownloadProgress.value = "Fetch failed: ${e.localizedMessage}"
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

    fun clearScannedModels() {
        _scannedModels.value = emptyList()
        _scanError.value = null
    }

    fun scanModels(provider: String, apiKey: String, baseUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanningModels.value = true
            _scanError.value = null
            _scannedModels.value = emptyList()
            
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val url = when (provider) {
                    "gemini" -> {
                        val key = apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
                        "https://generativelanguage.googleapis.com/v1beta/models?key=$key"
                    }
                    "cohere" -> "https://api.cohere.com/v1/models"
                    "openai" -> if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}/models" else "https://api.openai.com/v1/models"
                    "mistral" -> if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}/models" else "https://api.mistral.ai/v1/models"
                    "openrouter" -> if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}/models" else "https://openrouter.ai/api/v1/models"
                    "groq" -> if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}/models" else "https://api.groq.com/openai/v1/models"
                    "opencode_zen", "opencode" -> if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}/models" else "https://opencode.ai/zen/v1/models"
                    "ollama_cloud" -> if (baseUrl.isNotBlank()) "${baseUrl.trimEnd('/')}/v1/models" else "https://api.ollama.com/v1/models"
                    else -> {
                        if (baseUrl.isNotBlank()) {
                            if (baseUrl.contains("cloudflare")) {
                                "https://api.cloudflare.com/client/v4/accounts/YOUR_ACCOUNT_ID/ai/run"
                            } else {
                                "${baseUrl.trimEnd('/')}/models"
                            }
                        } else {
                            ""
                        }
                    }
                }

                if (url.isBlank()) {
                    _scanError.value = "Invalid or unsupported provider/base URL for scanning."
                    _isScanningModels.value = false
                    return@launch
                }

                if (provider == "cloudflare") {
                    val cfModels = listOf(
                        "@cf/meta/llama-3.3-70b-instruct",
                        "@cf/meta/llama-3-8b-instruct",
                        "@cf/deepseek-ai/deepseek-r1-distill-qwen-32b",
                        "@cf/qwen/qwen1.5-14b-chat",
                        "@cf/mistral/mistral-7b-instruct-v0.1"
                    )
                    _scannedModels.value = cfModels
                    _isScanningModels.value = false
                    return@launch
                }

                val requestBuilder = Request.Builder().url(url)
                if (apiKey.isNotBlank() && provider != "gemini") {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                } else if (provider == "openrouter" && apiKey.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                } else if (provider == "cohere" && apiKey.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                }

                val request = requestBuilder.get().build()
                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string()

                if (!response.isSuccessful || bodyStr == null) {
                    _scanError.value = "Error ${response.code}: ${bodyStr ?: "No response body"}"
                    _isScanningModels.value = false
                    return@launch
                }

                val modelsList = mutableListOf<String>()
                val json = JSONObject(bodyStr)

                if (json.has("data")) {
                    val dataArray = json.getJSONArray("data")
                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.getJSONObject(i)
                        if (item.has("id")) {
                            modelsList.add(item.getString("id"))
                        }
                    }
                } else if (json.has("models")) {
                    val modelsArray = json.getJSONArray("models")
                    for (i in 0 until modelsArray.length()) {
                        val item = modelsArray.getJSONObject(i)
                        if (item.has("name")) {
                            val name = item.getString("name")
                            modelsList.add(name)
                            if (name.startsWith("models/")) {
                                modelsList.add(name.substringAfter("models/"))
                            }
                        } else if (item.has("id")) {
                            modelsList.add(item.getString("id"))
                        }
                    }
                }

                if (modelsList.isEmpty()) {
                    _scanError.value = "No models found in the API response. Make sure your API key is correct."
                } else {
                    _scannedModels.value = modelsList.distinct()
                }

            } catch (e: Exception) {
                _scanError.value = "Scan failed: ${e.localizedMessage}"
            } finally {
                _isScanningModels.value = false
            }
        }
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

    fun saveAllowBackgroundExecution(allowed: Boolean) {
        _allowBackgroundExecution.value = allowed
        sharedPrefs.edit().putBoolean("allow_background_execution", allowed).apply()
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
        startPollingBuild()
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
            _gitProgress.value = if (force) "Force pushing code to GitHub..." else "Pushing code to GitHub..."
            val result = repository.pushToGitHub(projectName, repo, token, branch, force) { progress ->
                _gitProgress.value = progress
            }
            if (result.isSuccess) {
                _gitProgress.value = "Push complete! Workflows & build tracking started."
                startPollingBuild()
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

    // Execution Timer & Live Token Monitor state
    private val _executionElapsedTimeSeconds = MutableStateFlow(0L)
    val executionElapsedTimeSeconds: StateFlow<Long> = _executionElapsedTimeSeconds.asStateFlow()

    private val _currentRunningModelName = MutableStateFlow("")
    val currentRunningModelName: StateFlow<String> = _currentRunningModelName.asStateFlow()

    private val _liveSystemTokens = MutableStateFlow(0)
    val liveSystemTokens: StateFlow<Int> = _liveSystemTokens.asStateFlow()

    private val _liveUserTokens = MutableStateFlow(0)
    val liveUserTokens: StateFlow<Int> = _liveUserTokens.asStateFlow()

    private val _liveToolTokens = MutableStateFlow(0)
    val liveToolTokens: StateFlow<Int> = _liveToolTokens.asStateFlow()

    private val _liveTotalInputTokens = MutableStateFlow(0)
    val liveTotalInputTokens: StateFlow<Int> = _liveTotalInputTokens.asStateFlow()

    private val _liveTotalOutputTokens = MutableStateFlow(0)
    val liveTotalOutputTokens: StateFlow<Int> = _liveTotalOutputTokens.asStateFlow()

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

    // User file import loading bar state
    private val _isImportingFiles = MutableStateFlow(false)
    val isImportingFiles: StateFlow<Boolean> = _isImportingFiles.asStateFlow()

    private val _importProgress = MutableStateFlow(0f)
    val importProgress: StateFlow<Float> = _importProgress.asStateFlow()

    private val _importProgressMessage = MutableStateFlow("")
    val importProgressMessage: StateFlow<String> = _importProgressMessage.asStateFlow()

    // Version backups
    private val _backupsList = MutableStateFlow<List<BackupVersion>>(emptyList())
    val backupsList: StateFlow<List<BackupVersion>> = _backupsList.asStateFlow()

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
        checkAndTriggerAutoFixOnAgentFinish()
        
        // Optionally add a log for cancellation
        val cancelLog = AiActionLog(
            title = "Task Stopped",
            status = "failed",
            details = "Execution was cancelled by the user."
        )
        _aiActionLogs.value = _aiActionLogs.value + cancelLog
    }

    private fun checkHasCodeChangesThisTurn(editsAtPromptStart: Int): Boolean {
        val editHistoryChanged = _editHistory.value.size > editsAtPromptStart
        val logsHaveEdits = _aiActionLogs.value.any { log ->
            val t = log.title.lowercase()
            (t.startsWith("edit") || 
             t.startsWith("patch") || 
             t.startsWith("append") || 
             t.startsWith("create file") || 
             t.startsWith("write file") || 
             t.startsWith("delete file") || 
             t.startsWith("modified file") || 
             t.startsWith("rename file") || 
             t.startsWith("move file") ||
             t.contains("code modified") ||
             t.contains("file edited") ||
             t.contains("file written") ||
             t.contains("file created")) && 
            !t.contains("read") && !t.contains("scan") && !t.contains("list")
        }
        return editHistoryChanged || logsHaveEdits
    }

    private fun isActionPrompt(prompt: String): Boolean {
        val p = prompt.lowercase().trim()
        val chatGreetings = setOf("hi", "hello", "hey", "hola", "thanks", "thank you", "who are you", "what can you do")
        if (chatGreetings.contains(p) || p.length < 4) return false
        val nonActionPhrases = listOf("how are you", "good morning", "good evening", "what is your name", "who made you")
        if (nonActionPhrases.any { p.contains(it) }) return false
        return true
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
        viewModelScope.launch(Dispatchers.Default) {
            val files = repository.getFilesForProject(_currentProject.value?.name ?: "")
            val cleanSource = WebErrorResolver.cleanSourceId(sourceId, files)
            val log = WebConsoleLog(message, level, cleanSource, lineNumber)
            _webConsoleLogs.value = _webConsoleLogs.value + log
        }
    }

    fun clearWebConsoleLogs() {
        _webConsoleLogs.value = emptyList()
        _detectedWebErrors.value = emptyList()
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
        loadAgentSkills()

        com.example.api.GeminiClient.onRetryListener = { provider, attempt, maxAttempts, error ->
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                val activeConfig = _customModels.value.find { it.id == _selectedModelId.value }
                val modelDisplay = if (!activeConfig?.alias.isNullOrBlank()) {
                    activeConfig?.alias
                } else if (!activeConfig?.modelId.isNullOrBlank()) {
                    activeConfig?.modelId
                } else if (_currentRunningModelName.value.isNotBlank() && _currentRunningModelName.value != "Gemini Model") {
                    _currentRunningModelName.value
                } else {
                    when (provider.lowercase()) {
                        "opencode_zen", "opencode" -> "OpenCode Zen"
                        "ollama_cloud", "ollama" -> "Ollama"
                        "gemini", "direct gemini" -> "Gemini"
                        "openai" -> "OpenAI"
                        "claude" -> "Claude"
                        "groq" -> "Groq"
                        "mistral" -> "Mistral"
                        "cohere" -> "Cohere"
                        "openrouter" -> "OpenRouter"
                        "cloudflare" -> "Cloudflare"
                        else -> provider
                    }
                }
                _agentStatus.value = "Retrying API ($attempt/$maxAttempts) for $modelDisplay: $error"
                val retryLog = createAiLog(
                    title = "API Retry ($attempt/$maxAttempts)",
                    status = "thinking",
                    details = "Automatic retry for $modelDisplay: $error"
                )
                val currentLogs = _aiActionLogs.value.toMutableList()
                val lastRetryIndex = currentLogs.indexOfLast { it.title.startsWith("API Retry") && it.status == "thinking" }
                if (lastRetryIndex != -1) {
                    currentLogs[lastRetryIndex] = retryLog
                } else {
                    currentLogs.add(retryLog)
                }
                _aiActionLogs.value = currentLogs
            }
        }

        com.example.api.GeminiClient.onRetrySuccessListener = {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                val currentLogs = _aiActionLogs.value.toMutableList()
                val lastRetryIndex = currentLogs.indexOfLast { it.title.startsWith("API Retry") && it.status == "thinking" }
                if (lastRetryIndex != -1) {
                    val old = currentLogs[lastRetryIndex]
                    currentLogs[lastRetryIndex] = old.copy(
                        status = "success",
                        details = "${old.details} • Connection established successfully"
                    )
                    _aiActionLogs.value = currentLogs
                }
            }
        }
    }

    fun loadProjects() {
        viewModelScope.launch {
            _projectsList.value = repository.getAllProjects()
        }
    }

    fun updateProject(oldName: String, newName: String, newDescription: String) {
        viewModelScope.launch {
            repository.updateProject(oldName, newName, newDescription)
            loadProjects()
            if (_currentProject.value?.name == oldName) {
                _currentProject.value = _currentProject.value?.copy(name = newName, description = newDescription)
            }
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
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImportingFiles.value = true
            _importProgress.value = 0f
            _importProgressMessage.value = "Preparing file import..."
            try {
                uris.forEachIndexed { index, uri ->
                    val fileName = repository.getFileNameFromUri(uri) ?: "imported_${System.currentTimeMillis()}"
                    val progress = (index + 1).toFloat() / uris.size
                    _importProgress.value = progress
                    _importProgressMessage.value = "Importing file (${index + 1}/${uris.size}): $fileName"
                    repository.importFilesToProject(project.name, listOf(uri))
                }
                _importProgress.value = 1f
                _importProgressMessage.value = "Import completed successfully!"
                kotlinx.coroutines.delay(600)
                loadProjectDetails(project.name)
            } catch (e: Exception) {
                Log.e("VibeViewModel", "Error importing files from device", e)
            } finally {
                _isImportingFiles.value = false
                _importProgress.value = 0f
                _importProgressMessage.value = ""
            }
        }
    }

    fun decompileApk(apkPath: String) {
        val project = _currentProject.value ?: return
        viewModelScope.launch {
            _isLoadingWorkspace.value = true
            try {
                repository.decompileApkInProject(project.name, apkPath)
                loadProjectDetails(project.name)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingWorkspace.value = false
            }
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
            startPollingBuild()
            
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
        var files = repository.getFilesForProject(projectName)
        
        // Clean up accidental build artifacts from database
        val unwantedPrefixes = listOf("_next/", ".next/", "node_modules/", "out/", "dist/", "build/", "web-dist/")
        val toDelete = files.filter { file ->
            unwantedPrefixes.any { prefix -> 
                file.path.startsWith(prefix) || file.path.contains("/$prefix") || file.path.endsWith("web-dist.zip")
            }
        }
        if (toDelete.isNotEmpty()) {
            for (f in toDelete) {
                repository.deleteFile(projectName, f.path)
            }
            files = repository.getFilesForProject(projectName)
        }

        // Auto-migrate old/broken react_vite projects to a modern Vite+React+Tailwind structure
        val hasPackageJson = files.any { it.path == "package.json" }
        val hasIndexHtml = files.any { it.path == "index.html" }
        val hasMainJsx = files.any { it.path == "src/main.jsx" }
        if (hasPackageJson && hasIndexHtml && !hasMainJsx) {
            val packageJsonFile = files.find { it.path == "package.json" }
            val indexHtmlFile = files.find { it.path == "index.html" }
            if (packageJsonFile != null && indexHtmlFile != null &&
                packageJsonFile.content.contains("react-vite-app") &&
                indexHtmlFile.content.contains("text/babel")
            ) {
                android.util.Log.d("VibeViewModel", "Migrating old react_vite project to standard Vite+React+Tailwind")
                
                // 1. Update package.json
                val updatedPackageJson = """{
  "name": "react-vite-app",
  "private": true,
  "version": "0.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "esbuild": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0"
  },
  "devDependencies": {
    "@types/react": "^18.2.55",
    "@types/react-dom": "^18.2.19",
    "@vitejs/plugin-react": "^4.2.1",
    "autoprefixer": "^10.4.17",
    "postcss": "^8.4.35",
    "tailwindcss": "^3.4.1",
    "vite": "^5.1.0"
  }
}"""
                repository.saveFile(projectName, "package.json", updatedPackageJson)

                // 2. Update index.html
                val updatedIndexHtml = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>React Vite App</title>
</head>
<body class="bg-slate-950 text-white min-h-screen">
    <div id="root"></div>
    <script type="module" src="/src/main.jsx"></script>
</body>
</html>"""
                repository.saveFile(projectName, "index.html", updatedIndexHtml)

                // 3. Create tailwind.config.js
                val tailwindConfig = """/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {},
  },
  plugins: [],
}"""
                repository.saveFile(projectName, "tailwind.config.js", tailwindConfig)

                // 4. Create postcss.config.js
                val postcssConfig = """export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
}"""
                repository.saveFile(projectName, "postcss.config.js", postcssConfig)

                // 5. Create src/main.jsx
                val mainJsx = """import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)"""
                repository.saveFile(projectName, "src/main.jsx", mainJsx)

                // 6. Create src/index.css
                val indexCss = """@tailwind base;
@tailwind components;
@tailwind utilities;"""
                repository.saveFile(projectName, "src/index.css", indexCss)

                // 7. Create/Verify src/App.jsx if missing or having default content
                val appJsxFile = files.find { it.path == "src/App.jsx" }
                if (appJsxFile == null || appJsxFile.content.trim().isEmpty()) {
                    val defaultAppJsx = """export default function App() {
  return (
    <div className="min-h-screen bg-slate-950 text-white flex flex-col items-center justify-center p-6">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-8 max-w-md w-full text-center shadow-2xl">
        <div className="w-16 h-16 bg-purple-600/20 border border-purple-500/40 rounded-full flex items-center justify-center mx-auto mb-4">
          <span className="text-2xl font-black text-purple-400">👋</span>
        </div>
        <h1 className="text-3xl font-bold mb-2 bg-gradient-to-r from-purple-400 via-pink-400 to-cyan-400 bg-clip-text text-transparent">Hello World</h1>
        <p className="text-slate-400 text-sm">Welcome to your clean React + Vite + Tailwind application.</p>
      </div>
    </div>
  )
}"""
                    repository.saveFile(projectName, "src/App.jsx", defaultAppJsx)
                }

                // Sync and reload project files
                repository.syncDatabaseToStorage(projectName)
                files = repository.getFilesForProject(projectName)
            }
        }

        val visibleFiles = files.filter { it.path != "browser_memory.md" && it.path != "memory.md" }
        _projectFiles.value = visibleFiles
        
        // Dynamic configuration of local web preview path based on selected project
        val context = getApplication<Application>()
        val safeProjName = projectName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val webDistDir = java.io.File(context.cacheDir, "web_dist_$safeProjName")
        if (webDistDir.exists() && webDistDir.isDirectory) {
            com.example.api.LocalHttpServer.setWebDistDir(webDistDir.absolutePath)
        } else {
            com.example.api.LocalHttpServer.setWebDistDir(null)
        }

        // Auto-select index.html or first file to view in editor
        val defaultFile = visibleFiles.find { it.path == "index.html" } ?: visibleFiles.firstOrNull()
        selectActiveFile(defaultFile)

        _chatMessages.value = repository.getChatsForProject(projectName)
        loadBackupsForCurrentProject(projectName)
    }

    fun loadBackupsForCurrentProject(projectName: String? = _currentProject.value?.name) {
        val projName = projectName ?: return
        viewModelScope.launch {
            _backupsList.value = RestoreManager.getBackups(getApplication(), projName)
        }
    }

    fun restoreProjectVersion(backup: BackupVersion) {
        val project = _currentProject.value ?: return
        viewModelScope.launch {
            _isLoadingWorkspace.value = true
            try {
                val success = RestoreManager.restoreBackup(getApplication(), backup, repository)
                if (success) {
                    loadProjectDetails(project.name)
                    _agentStatus.value = "Restored version from ${formatTimestamp(backup.timestamp)}"
                }
            } catch (e: Exception) {
                Log.e("VibeViewModel", "Error restoring backup", e)
            } finally {
                _isLoadingWorkspace.value = false
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
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
        val lowerPath = path.lowercase().trim()
        if (lowerPath == "android.yml" || lowerPath.endsWith("/android.yml") || lowerPath.endsWith("\\android.yml")) {
            _terminalOutput.value += "[PROTECTED FILE] The 'android.yml' file is protected and cannot be deleted.\n\n$"
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteFile(project.name, path)
                loadProjectDetails(project.name)
            } catch (e: Exception) {
                _terminalOutput.value += "[DELETE ERROR] ${e.localizedMessage ?: "Failed to delete file"}\n\n$"
            }
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
        startPollingBuild()
    }

    private val gitHubCommandWorkflowManager = com.example.data.GitHubCommandWorkflowManager()

    fun runTerminalCommand(command: String) {
        val project = _currentProject.value ?: return
        if (command.isBlank()) return
        viewModelScope.launch {
            autoSaveActiveFile()
            _terminalOutput.value += " $command\n"

            val repo = _githubRepo.value
            val token = _githubToken.value
            val branch = _githubBranch.value.ifBlank { "main" }
            val projectDir = java.io.File(getApplication<Application>().filesDir, "projects/${project.name}")

            val isHeavy = gitHubCommandWorkflowManager.isHeavyCommand(command)

            if (isHeavy) {
                val credCheck = gitHubCommandWorkflowManager.checkGitHubCredentials(repo, token)
                if (!credCheck.isValid) {
                    _terminalOutput.value += "\n${credCheck.promptMessage}\n\n$"
                    return@launch
                }
                executeAndTrackCommandWorkflow(projectDir, command, repo, token, branch)
            } else {
                val result = repository.executeCommand(project.name, command)
                if (result.contains("inaccessible or not found") || result.contains("not found")) {
                    _terminalOutput.value += "[Local shell missing binary] Intercepting command and routing to GitHub Actions...\n"
                    val credCheck = gitHubCommandWorkflowManager.checkGitHubCredentials(repo, token)
                    if (!credCheck.isValid) {
                        _terminalOutput.value += "\n${credCheck.promptMessage}\n\n$"
                        return@launch
                    }
                    executeAndTrackCommandWorkflow(projectDir, command, repo, token, branch)
                } else {
                    val resultStr = if (result.isEmpty()) "(no output)" else result
                    _terminalOutput.value += "$resultStr\n\n$"
                }
            }
            loadProjectDetails(project.name)
        }
    }

    private suspend fun executeAndTrackCommandWorkflow(
        projectDir: java.io.File,
        command: String,
        repo: String,
        token: String,
        branch: String
    ) {
        val framework = gitHubCommandWorkflowManager.detectFramework(projectDir)
        val parts = repo.split("/")
        if (parts.size != 2) {
            _terminalOutput.value += "[ERROR] Invalid repository format '$repo'. Must be 'owner/repo'.\n\n$"
            return
        }
        val owner = parts[0].trim()
        val repoName = parts[1].trim()

        _terminalOutput.value += "[Detected Command] Framework: ${framework.displayName}\n"
        _terminalOutput.value += "[1/3] Updating .github/workflows/command.yml...\n"
        try {
            gitHubCommandWorkflowManager.updateCommandWorkflow(projectDir, command)
            val wfFile = java.io.File(projectDir, ".github/workflows/command.yml")
            if (wfFile.exists()) {
                repository.saveFile(
                    _currentProject.value?.name ?: "",
                    ".github/workflows/command.yml",
                    wfFile.readText()
                )
            }
        } catch (e: Exception) {
            Log.e("VibeViewModel", "Error creating workflow file: ${e.message}")
        }

        _terminalOutput.value += "[2/3] Force pushing codebase to GitHub repository...\n"
        var pushSuccess = false
        var pushErrorMsg = ""

        val latch = kotlinx.coroutines.CompletableDeferred<Boolean>()
        pushGitRepo(_currentProject.value?.name ?: "", repo, token, branch, force = true) { res ->
            if (res.isSuccess) {
                pushSuccess = true
                latch.complete(true)
            } else {
                pushErrorMsg = res.exceptionOrNull()?.localizedMessage ?: "Git push failed"
                latch.complete(false)
            }
        }
        latch.await()

        if (!pushSuccess) {
            _terminalOutput.value += "[PUSH ERROR] $pushErrorMsg\n\n$"
            return
        }

        _terminalOutput.value += "[3/3] Triggering command.yml workflow on GitHub Actions...\n"

        val client = OkHttpClient()
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val dispatchUrl = "https://api.github.com/repos/$owner/$repoName/actions/workflows/command.yml/dispatches"
        val dispatchBody = okhttp3.RequestBody.create(mediaType, "{\"ref\":\"$branch\"}")
        val dispatchReq = Request.Builder()
            .url(dispatchUrl)
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github.v3+json")
            .post(dispatchBody)
            .build()

        var dispatched = false
        try {
            val resp = client.newCall(dispatchReq).execute()
            if (resp.isSuccessful || resp.code == 204) {
                dispatched = true
            }
        } catch (e: Exception) {
            Log.e("VibeViewModel", "Dispatch error: ${e.message}")
        }

        if (!dispatched) {
            triggerAllWorkflows()
        }

        _terminalOutput.value += "\ncommand running....\n"

        val startTime = System.currentTimeMillis()
        val timeoutMs = 180_000L
        var runCompleted = false
        var finalOutput = ""
        var isSuccess = false

        delay(3000)

        while (System.currentTimeMillis() - startTime < timeoutMs && !runCompleted) {
            try {
                val runsUrl = "https://api.github.com/repos/$owner/$repoName/actions/runs?per_page=5"
                val runsReq = Request.Builder()
                    .url(runsUrl)
                    .header("Authorization", "token $token")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                val runsResp = client.newCall(runsReq).execute()
                if (runsResp.isSuccessful) {
                    val bodyStr = runsResp.body?.string() ?: ""
                    val rootObj = JSONObject(bodyStr)
                    val workflowRuns = rootObj.optJSONArray("workflow_runs")

                    var targetRun: JSONObject? = null
                    if (workflowRuns != null) {
                        for (i in 0 until workflowRuns.length()) {
                            val run = workflowRuns.optJSONObject(i) ?: continue
                            val path = run.optString("path", "")
                            if (path.endsWith("command.yml")) {
                                targetRun = run
                                break
                            }
                        }
                    }

                    if (targetRun != null) {
                        val status = targetRun.optString("status", "queued")
                        val conclusion = targetRun.optString("conclusion", "")
                        val runId = targetRun.optLong("id", -1L)

                        if (status == "completed") {
                            runCompleted = true
                            isSuccess = conclusion == "success"

                            if (runId != -1L) {
                                val jobsUrl = "https://api.github.com/repos/$owner/$repoName/actions/runs/$runId/jobs"
                                val jobsReq = Request.Builder()
                                    .url(jobsUrl)
                                    .header("Authorization", "token $token")
                                    .header("Accept", "application/vnd.github.v3+json")
                                    .build()
                                val jobsResp = client.newCall(jobsReq).execute()
                                if (jobsResp.isSuccessful) {
                                    val jobsBody = jobsResp.body?.string() ?: ""
                                    val jobsObj = JSONObject(jobsBody)
                                    val jobsList = jobsObj.optJSONArray("jobs")
                                    val firstJob = jobsList?.optJSONObject(0)
                                    val jobId = firstJob?.optLong("id", -1L) ?: -1L

                                    if (jobId != -1L) {
                                        val logsUrl = "https://api.github.com/repos/$owner/$repoName/actions/jobs/$jobId/logs"
                                        val logsReq = Request.Builder()
                                            .url(logsUrl)
                                            .header("Authorization", "token $token")
                                            .header("Accept", "application/vnd.github.v3+json")
                                            .build()
                                        val logsResp = client.newCall(logsReq).execute()
                                        if (logsResp.isSuccessful) {
                                            val rawLogs = logsResp.body?.string() ?: ""
                                            finalOutput = rawLogs.replace(Regex("\u001B\\[[;\\d]*m"), "")
                                        }
                                    }
                                }
                            }
                            if (finalOutput.isBlank()) {
                                finalOutput = "Execution finished with status: $conclusion"
                            }
                        } else {
                            _terminalOutput.value += "command running....\n"
                        }
                    } else {
                        _terminalOutput.value += "command running....\n"
                    }
                }
            } catch (e: Exception) {
                Log.e("VibeViewModel", "Error polling command run: ${e.message}")
            }

            if (!runCompleted) {
                delay(4000)
            }
        }

        if (!runCompleted) {
            finalOutput = "Command execution timed out after 3 minutes. Check GitHub Actions for details."
        }

        val formattedLog = gitHubCommandWorkflowManager.formatCommandExecutionLog(
            command = command,
            framework = framework,
            isSuccess = isSuccess,
            output = finalOutput
        )

        _terminalOutput.value += "\n$formattedLog\n\n$"
    }

    private fun isSameWork(a: com.example.api.ToolCallItem, b: com.example.api.ToolCallItem): Boolean {
        if (a.tool != b.tool) return false
        val argsA = a.arguments ?: return b.arguments == null
        val argsB = b.arguments ?: return false

        return when (a.tool) {
            "run_command" -> {
                val cmdA = argsA.command?.trim()?.replace("\\s+".toRegex(), " ") ?: ""
                val cmdB = argsB.command?.trim()?.replace("\\s+".toRegex(), " ") ?: ""
                cmdA.isNotEmpty() && cmdA == cmdB
            }
            "edit_file", "patch_file", "multi_edit_file", "multi_edit", "multi_patch" -> {
                val pathA = argsA.path?.trim() ?: argsA.targetFile?.trim() ?: ""
                val pathB = argsB.path?.trim() ?: argsB.targetFile?.trim() ?: ""
                val searchA = argsA.search?.trim() ?: ""
                val searchB = argsB.search?.trim() ?: ""
                val replaceA = argsA.replace?.trim() ?: ""
                val replaceB = argsB.replace?.trim() ?: ""
                val chunksA = argsA.chunks ?: argsA.replacementChunks ?: argsA.edits
                val chunksB = argsB.chunks ?: argsB.replacementChunks ?: argsB.edits
                pathA == pathB && searchA == searchB && replaceA == replaceB && chunksA == chunksB
            }
            "create_file", "write_file", "write", "append" -> {
                val pathA = argsA.path?.trim() ?: ""
                val pathB = argsB.path?.trim() ?: ""
                val contentA = argsA.content?.trim() ?: ""
                val contentB = argsB.content?.trim() ?: ""
                pathA == pathB && contentA == contentB
            }
            "read_file", "view_file", "read_file_range", "multi_read_file", "multi_read" -> {
                val pathA = argsA.path?.trim() ?: argsA.targetFile?.trim() ?: ""
                val pathB = argsB.path?.trim() ?: argsB.targetFile?.trim() ?: ""
                val startA = argsA.startLine
                val startB = argsB.startLine
                val endA = argsA.endLine
                val endB = argsB.endLine
                val rangeA = argsA.lineRange?.trim() ?: ""
                val rangeB = argsB.lineRange?.trim() ?: ""
                pathA == pathB && startA == startB && endA == endB && rangeA == rangeB
            }
            else -> {
                argsA == argsB
            }
        }
    }

    private val contextOptimizationManager = ContextOptimizationManager()

    private fun optimizeConversationHistory(history: List<Content>, activeQuery: String? = null): List<Content> {
        _currentProject.value?.let { proj ->
            val projectDir = java.io.File(getApplication<Application>().filesDir, "projects/${proj.name}")
            if (projectDir.exists()) {
                contextOptimizationManager.symbolIndexer.indexProjectDirectory(projectDir)
            }
        }
        return contextOptimizationManager.optimizeHistory(history, activeTaskQuery = activeQuery)
    }

    private fun appendToTerminal(command: String, result: String) {
        val resultStr = if (result.isEmpty()) "(no output)" else result
        _terminalOutput.value += " $command\n$resultStr\n\n$"
    }

    fun sendPrompt(userPrompt: String, attachments: List<AttachedFile> = emptyList(), isRegenerate: Boolean = false) {
        val project = _currentProject.value ?: return
        if (userPrompt.isBlank() && attachments.isEmpty()) return

        if (_allowBackgroundExecution.value) {
            try {
                val context = getApplication<Application>()
                val startIntent = Intent(context, VibeAgentService::class.java).apply {
                    action = VibeAgentService.ACTION_START
                }
                ContextCompat.startForegroundService(context, startIntent)
            } catch (e: Exception) {
                Log.e("VibeViewModel", "Error starting service", e)
            }
        }

        val scope = if (_allowBackgroundExecution.value) backgroundScope else viewModelScope
        currentAiJob = scope.launch {
            autoSaveActiveFile()
            _detectedWebErrors.value = emptyList()
            var finalPrompt = userPrompt
            attachments.forEach { file ->
                if (file.isImage && file.contentAsBase64 != null) {
                    finalPrompt += "\n\n[IMAGE_BASE64: data:${file.mimeType};base64,${file.contentAsBase64}]"
                } else if (!file.isImage && file.contentAsText != null) {
                    finalPrompt += "\n\n[Attached File: ${file.name}]\n${file.contentAsText}\n[/Attached File]"
                }
            }

            // Save full project version snapshot (keeping latest 3) before AI modifies project
            val currentFilesToBackup = repository.getFilesForProject(project.name)
            RestoreManager.saveBackup(getApplication(), project.name, finalPrompt, currentFilesToBackup)
            loadBackupsForCurrentProject(project.name)

            // 1. Save user prompt to Chat Database
            var userMsg: ChatMessageEntity? = null
            if (!isRegenerate) {
                val activeConfig = _customModels.value.find { it.id == _selectedModelId.value }
                val activeModel = _currentRunningModelName.value.ifBlank {
                    activeConfig?.let { if (it.modelId.isNotBlank()) it.modelId else it.alias } ?: ""
                }
                val initialUserMsg = ChatMessageEntity(
                    projectName = project.name,
                    role = "user",
                    content = finalPrompt,
                    timestamp = System.currentTimeMillis(),
                    modelName = activeModel
                )
                val insertedId = repository.insertChatMessage(initialUserMsg)
                userMsg = initialUserMsg.copy(id = insertedId.toInt())
            }
            _chatMessages.value = repository.getChatsForProject(project.name)

            // 2. Clear previous logs and initialize thinking state
            _aiActionLogs.value = emptyList()
            _todoList.value = emptyList()
            _showGithubPushPrompt.value = false
            _isThinking.value = true
            _agentStatus.value = "AI is thinking..."
            val editsAtPromptStart = _editHistory.value.size

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
                    val rawPrevPrompt = lastUserEntity?.content ?: "[Previous Request]"
                    val previousUserPrompt = rawPrevPrompt.replace("""\[IMAGE_BASE64: data:.*?;base64,.*?\]""".toRegex(), "[Attached Image]")

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
                                            titleLower.contains("edit_file") || titleLower.contains("edit file") || titleLower.contains("multi_edit") || titleLower.contains("modify") -> {
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
                    - DO NOT use React, HTML, CSS, or JavaScript.
                """.trimIndent()
                "flutter" -> """
                    ACTIVE TEMPLATE: Flutter (Dart).
                    - CRITICAL constraint: You must ONLY write Flutter Dart code (lib/main.dart) and pubspec.yaml configurations!
                    - DO NOT use HTML, CSS, React, React CDN, or Jetpack Compose!
                """.trimIndent()
                "react" -> """
                    ACTIVE TEMPLATE: React CDN (Web).
                    - CRITICAL constraint: The project is built using React & ReactDOM directly loaded from a CDN.
                    - DO NOT use Three.js unless explicitly asked!
                    - Build components as standard modern React CDN files.
                """.trimIndent()
                "vanilla" -> """
                    ACTIVE TEMPLATE: Vanilla JS (Web).
                    - CRITICAL constraint: The project uses pure Vanilla JS (standard HTML, CSS, and pure Native JavaScript).
                    - DO NOT use React, ReactDOM, or Three.js! If you write React or JSX in a Vanilla JS project, it will fail to compile. This is a strict constraint.
                """.trimIndent()
                "vanilla_three" -> """
                    ACTIVE TEMPLATE: Vanilla Three.js (3D Web Canvas).
                    - CRITICAL constraint: The project uses Three.js via CDN window.THREE or script imports for 3D games, objects, and 3D web design.
                    - Build clean, interactive 3D Web animations using THREE.Scene, THREE.PerspectiveCamera, THREE.WebGLRenderer, lights, geometries, materials, and animation loops.
                """.trimIndent()
                "chrome_extension" -> """
                    ACTIVE TEMPLATE: Chrome Extension (Manifest V3).
                    - CRITICAL constraint: This is a modern Google Chrome Extension adhering to Manifest V3 specifications.
                    - Structure includes:
                      * manifest.json: Declarative permissions, host_permissions, background service worker, action popup, options page, and content scripts.
                      * popup/: HTML, CSS, and JS for extension popup when the user clicks the toolbar icon.
                      * background/: Service worker (service_worker.js) for background tasks, alarms, listeners, context menus, and storage.
                      * content/: Content scripts and styling injected into web pages.
                      * options/: Settings and preferences management page.
                      * index.html: In-app live test & interactive preview hub.
                    - Follow Chrome Extension Manifest V3 best practices (use chrome.storage.local/sync, chrome.runtime.sendMessage, chrome.tabs.sendMessage, chrome.action).
                """.trimIndent()
                else -> """
                    ACTIVE TEMPLATE: ${project.templateKey ?: "Empty Workspace"}.
                    - Respect the existing framework/files in the workspace.
                """.trimIndent()
            }

            val activeSkills = _agentSkills.value.filter { it.isInstalled && it.isEnabled }
            val activeSkillsPrompt = buildString {
                if (activeSkills.isNotEmpty()) {
                    append("\n\nINSTALLED & ACTIVE AGENT SKILLS LIBRARY:\n")
                    append("The following skills are installed and enabled for this session:\n\n")
                    activeSkills.forEach { skill ->
                        append("--- SKILL: ${skill.name} (${skill.author}) ---\n")
                        append("Description: ${skill.description}\n")
                        if (skill.skillPrompt.isNotBlank()) {
                            append("Instructions:\n${skill.skillPrompt}\n")
                        }
                        append("--- END SKILL ---\n\n")
                    }
                }
            }

            val selectedIds = _selectedMcpServerIds.value
            val allMcpServers = mcpManager.servers.value
            val effectiveMcpServers = if (selectedIds.isNotEmpty()) {
                allMcpServers.filter { selectedIds.contains(it.id) }
            } else {
                val enabled = mcpManager.getEnabledServersForWorkspace(project.name)
                if (enabled.isNotEmpty()) enabled else allMcpServers.filter { it.status.startsWith("Connected") || it.availableTools.isNotEmpty() }
            }
            val mcpToolsPrompt = if (effectiveMcpServers.isNotEmpty()) {
                mcpManager.toolRegistry.buildMcpToolsPromptForServers(effectiveMcpServers)
            } else ""

            val fileTreeStr = generateFileTree(_projectFiles.value)
            val systemInstruction = com.example.agent.AgentInstructionEngine.buildDynamicSystemInstruction(
                userPrompt = currentPromptEntity?.content ?: "",
                project = project,
                allFiles = _projectFiles.value,
                fileTreeSummary = fileTreeStr,
                activeSkills = activeSkills,
                effectiveMcpServers = effectiveMcpServers,
                mcpToolsPrompt = mcpToolsPrompt,
                activeTemplateInfo = activeTemplateInfo,
                maxActionSteps = _maxActionSteps.value,
                allowBuildPush = _allowBuildPush.value
            )

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

            val executionStartTime = System.currentTimeMillis()
            _executionElapsedTimeSeconds.value = 0L
            _currentRunningModelName.value = if (modelId.isNotBlank()) modelId else activeConfig.alias.ifBlank { "Gemini Model" }

            val estimatedSys = maxOf(10, systemInstruction.length / 4)
            val estimatedUsr = maxOf(1, finalPrompt.length / 4)
            val estimatedHist = history.sumOf { content -> content.parts?.sumOf { part -> part.text?.length ?: 0 } ?: 0 } / 4

            _liveSystemTokens.value = estimatedSys
            _liveUserTokens.value = estimatedUsr
            _liveToolTokens.value = 0
            _liveTotalInputTokens.value = estimatedSys + estimatedUsr + estimatedHist
            _liveTotalOutputTokens.value = 0

            val timerJob = viewModelScope.launch(Dispatchers.Default) {
                while (isActive && _isThinking.value) {
                    _executionElapsedTimeSeconds.value = (System.currentTimeMillis() - executionStartTime) / 1000
                    kotlinx.coroutines.delay(1000)
                }
            }
            
            var loopCompleted = false
            var filesModifiedThisPrompt = false
            var turn = 1
            var actionsCount = 0
            var maxActionSteps = _maxActionSteps.value
            var agentMessageSaved = false
            var lastThought: String? = null
            var consecutiveThinkOnlyCount = 0
            val recentToolCallsHistory = mutableListOf<com.example.api.ToolCallItem>()
            val readFilesThisSession = mutableSetOf<String>()

            // Initial automatic thinking log triggered ONCE when processing user prompt starts
            val initialThoughtLog = createAiLog(
                title = "AI formulating logic",
                status = "success",
                details = "Analyzing user prompt and formulating initial step-by-step task execution plan..."
            )
            _aiActionLogs.value = _aiActionLogs.value + initialThoughtLog

            try {
                while (!loopCompleted && turn <= maxActionSteps && actionsCount < maxActionSteps) {
                _agentStatus.value = "AI thinking (Turn $turn, Action $actionsCount/$maxActionSteps)..."
                
                try {
                    val remainingSteps = maxActionSteps - actionsCount
                    if (remainingSteps <= 0 || actionsCount >= maxActionSteps) {
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

                    val dynamicSystemInstruction = """
                        $systemInstruction
                        
                        ${com.example.agent.ReadLoopSafetyManager.SYSTEM_READ_WARNING}
                        
                        DYNAMIC AGENT TOOL EXECUTION BUDGET (CRITICAL UPDATES):
                        - Total Step Execution Limit: $maxActionSteps
                        - Steps Already Executed: $actionsCount
                        - Steps Remaining: $remainingSteps
                        - You are currently at step ${actionsCount + 1}. You have exactly $remainingSteps actions/tool calls remaining for this task.
                        - Plan your tasks and use the 'complete' tool to terminate before you run out of actions!
                    """.trimIndent()

                    val stepResponse = GeminiClient.generateAgentStep(
                        apiKey = activeApiKey,
                        systemInstruction = dynamicSystemInstruction,
                        conversationHistory = optimizeConversationHistory(history, userPrompt),
                        provider = provider,
                        modelId = modelId,
                        customBaseUrl = baseUrl,
                        useCustom = useCustom
                    )
                    
                    val thought = stepResponse?.thought ?: ""
                    val stepMsg = stepResponse?.arguments?.message ?: stepResponse?.arguments?.content
                    val addedOut = maxOf(1, thought.length / 4)
                    _liveTotalOutputTokens.value += addedOut
                    if (thought.isNotBlank()) {
                        lastThought = thought
                        val currentLogs = _aiActionLogs.value.toMutableList()
                        val logicIdx = currentLogs.indexOfFirst { it.title == "AI formulating logic" }
                        if (logicIdx != -1) {
                            currentLogs[logicIdx] = currentLogs[logicIdx].copy(
                                status = "success",
                                details = thought.trim()
                            )
                            _aiActionLogs.value = currentLogs
                        }
                    } else if (!stepMsg.isNullOrBlank()) {
                        lastThought = stepMsg
                        val currentLogs = _aiActionLogs.value.toMutableList()
                        val logicIdx = currentLogs.indexOfFirst { it.title == "AI formulating logic" }
                        if (logicIdx != -1) {
                            currentLogs[logicIdx] = currentLogs[logicIdx].copy(
                                status = "success",
                                details = stepMsg.trim()
                            )
                            _aiActionLogs.value = currentLogs
                        }
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

                        // Auto-completion detection: if no action tools or only complete/think called
                        val realActionTools = toolCalls.filter { it.tool != "ai_think" && it.tool != "ai_response" && it.tool != "complete" }
                        if (realActionTools.isEmpty()) {
                            consecutiveThinkOnlyCount++
                        } else {
                            consecutiveThinkOnlyCount = 0
                        }

                        if (toolCalls.isEmpty() || (toolCalls.size == 1 && toolCalls[0].tool == "complete") || consecutiveThinkOnlyCount >= 2) {
                            if (consecutiveThinkOnlyCount >= 2) {
                                val autoFinishLog = createAiLog(
                                    title = "AI task auto-finalized",
                                    status = "success",
                                    details = lastThought ?: "Task completed after reasoning."
                                )
                                _aiActionLogs.value = _aiActionLogs.value + autoFinishLog
                            }
                            loopCompleted = true
                        }

                        for (call in toolCalls) {
                            val tool = call.tool
                            val args = call.arguments
                            
                            if (tool != "complete" && tool != "ai_think" && tool != "ai_response") {
                                // Track tool call to detect infinite loops (direct and oscillating sequence patterns)
                                recentToolCallsHistory.add(call)
                                val size = recentToolCallsHistory.size
                                var loopHandled = false

                                // Pattern-based sequence loop detection for pattern length k from 1 to 4
                                for (k in 1..4) {
                                    if (loopHandled) break
                                    if (size >= k * 2) {
                                        var isMatch = true
                                        for (i in 0 until k) {
                                            if (!isSameWork(recentToolCallsHistory[size - 1 - i], recentToolCallsHistory[size - 1 - k - i])) {
                                                isMatch = false
                                                break
                                            }
                                        }
                                        if (isMatch) {
                                            var cycles = 2
                                            var offset = size - 1 - 2 * k
                                            while (offset - k + 1 >= 0) {
                                                var cycleMatch = true
                                                for (i in 0 until k) {
                                                    if (!isSameWork(recentToolCallsHistory[size - 1 - i], recentToolCallsHistory[offset - i])) {
                                                        cycleMatch = false
                                                        break
                                                    }
                                                }
                                                if (cycleMatch) {
                                                    cycles++
                                                    offset -= k
                                                } else {
                                                    break
                                                }
                                            }

                                            if (k == 1) {
                                                if (com.example.agent.ReadLoopSafetyManager.isReadTool(tool) && cycles >= 2) {
                                                    _agentStatus.value = "Generating AI task completion summary..."
                                                    val autoSummary = com.example.agent.ReadLoopSafetyManager.generateAiCompletionSummary(
                                                        userPrompt = userPrompt,
                                                        activeApiKey = activeApiKey,
                                                        systemInstruction = systemInstruction,
                                                        history = history,
                                                        provider = provider,
                                                        modelId = modelId,
                                                        baseUrl = baseUrl,
                                                        useCustom = useCustom,
                                                        lastTool = tool,
                                                        path = args?.path ?: args?.targetFile
                                                    )
                                                    val loopLog = createAiLog(
                                                        title = "AI finished task execution",
                                                        status = "success",
                                                        details = autoSummary
                                                    )
                                                    _aiActionLogs.value = _aiActionLogs.value + loopLog
                                                    
                                                    val logsJson = try {
                                                        val listType = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
                                                        moshi.adapter<List<AiActionLog>>(listType).toJson(_aiActionLogs.value)
                                                    } catch (e: Exception) {
                                                        null
                                                    }

                                                    val agentMsg = ChatMessageEntity(
                                                        projectName = project.name,
                                                        role = "assistant",
                                                        content = autoSummary,
                                                        timestamp = System.currentTimeMillis(),
                                                        aiActionLogsJson = logsJson
                                                    )
                                                    repository.insertChatMessage(agentMsg)
                                                    _chatMessages.value = repository.getChatsForProject(project.name)
                                                    _agentStatus.value = "Task completed"
                                                    loopCompleted = true
                                                    agentMessageSaved = true
                                                    loopHandled = true
                                                    break
                                                } else if (cycles >= 5) {
                                                    _isInterrupted.value = true
                                                    val loopAbortedLog = createAiLog(
                                                        title = "Infinite Loop Blocked",
                                                        status = "failed",
                                                        details = "Aborted execution: AI was stuck in an infinite loop, repeating the same task $cycles times consecutively."
                                                    )
                                                    _aiActionLogs.value = _aiActionLogs.value + loopAbortedLog
                                                    loopCompleted = true
                                                    loopHandled = true
                                                    break
                                                } else if (cycles >= 3) {
                                                    val warningText = """
                                                        SYSTEM ALERT (INFINITE LOOP WARNING):
                                                        You have performed the exact same action $cycles times consecutively.
                                                        Tool: $tool
                                                        Arguments: ${args?.toString() ?: "None"}
                                                        
                                                        This has resulted in the same outcome!
                                                        You MUST stop repeating this action. Use 'grep' or 'read_file' to check the file state first before taking another action.
                                                    """.trimIndent()
                                                    
                                                    history.add(Content(
                                                        role = "user",
                                                        parts = listOf(Part(text = warningText))
                                                    ))
                                                    
                                                    val warningLog = createAiLog(
                                                        title = "Loop warning injected",
                                                        status = "failed",
                                                        details = "Injected warning: AI repeated tool '$tool' $cycles times consecutively."
                                                    )
                                                    _aiActionLogs.value = _aiActionLogs.value + warningLog
                                                    loopHandled = true
                                                }
                                            } else {
                                                val isAllReadToolsInSeq = (0 until k).all { idx -> com.example.agent.ReadLoopSafetyManager.isReadTool(recentToolCallsHistory[size - k + idx].tool) }
                                                if (isAllReadToolsInSeq && cycles >= 2) {
                                                    _agentStatus.value = "Generating AI task completion summary..."
                                                    val autoSummary = com.example.agent.ReadLoopSafetyManager.generateAiCompletionSummary(
                                                        userPrompt = userPrompt,
                                                        activeApiKey = activeApiKey,
                                                        systemInstruction = systemInstruction,
                                                        history = history,
                                                        provider = provider,
                                                        modelId = modelId,
                                                        baseUrl = baseUrl,
                                                        useCustom = useCustom,
                                                        lastTool = tool,
                                                        path = args?.path ?: args?.targetFile
                                                    )
                                                    val loopLog = createAiLog(
                                                        title = "AI finished task execution",
                                                        status = "success",
                                                        details = autoSummary
                                                    )
                                                    _aiActionLogs.value = _aiActionLogs.value + loopLog
                                                    
                                                    val logsJson = try {
                                                        val listType = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
                                                        moshi.adapter<List<AiActionLog>>(listType).toJson(_aiActionLogs.value)
                                                    } catch (e: Exception) {
                                                        null
                                                    }

                                                    val agentMsg = ChatMessageEntity(
                                                        projectName = project.name,
                                                        role = "assistant",
                                                        content = autoSummary,
                                                        timestamp = System.currentTimeMillis(),
                                                        aiActionLogsJson = logsJson
                                                    )
                                                    repository.insertChatMessage(agentMsg)
                                                    _chatMessages.value = repository.getChatsForProject(project.name)
                                                    _agentStatus.value = "Task completed"
                                                    loopCompleted = true
                                                    agentMessageSaved = true
                                                    loopHandled = true
                                                    break
                                                } else if (cycles >= 3) {
                                                    _isInterrupted.value = true
                                                    val loopAbortedLog = createAiLog(
                                                        title = "Sequence Loop Blocked",
                                                        status = "failed",
                                                        details = "Aborted execution: AI was stuck in a $k-step sequence infinite loop repeated $cycles cycles."
                                                    )
                                                    _aiActionLogs.value = _aiActionLogs.value + loopAbortedLog
                                                    loopCompleted = true
                                                    loopHandled = true
                                                    break
                                                } else if (cycles >= 2) {
                                                    val seqNames = (0 until k).map { idx -> recentToolCallsHistory[size - k + idx].tool }.joinToString(" -> ")
                                                    val warningText = """
                                                        SYSTEM ALERT (SEQUENCE LOOP DETECTED):
                                                        You are repeating a $k-step sequence cycle ($seqNames) for $cycles cycles!
                                                        This indicates an oscillating infinite loop where previous steps keep failing or reverting.
                                                        
                                                        You MUST stop this sequence cycle immediately! Try a completely different approach or use 'grep' to inspect code before proceeding.
                                                    """.trimIndent()
                                                    
                                                    history.add(Content(
                                                        role = "user",
                                                        parts = listOf(Part(text = warningText))
                                                    ))
                                                    
                                                    val warningLog = createAiLog(
                                                        title = "Sequence loop warning injected",
                                                        status = "failed",
                                                        details = "Injected warning: $k-step sequence pattern ($seqNames) repeated $cycles times."
                                                    )
                                                    _aiActionLogs.value = _aiActionLogs.value + warningLog
                                                    loopHandled = true
                                                }
                                            }
                                        }
                                    }
                                }

                                 // Consecutive failures detection (last 3 actions failed)
                                if (!loopHandled) {
                                    val recentLogs = _aiActionLogs.value.takeLast(3)
                                    if (recentLogs.size >= 3 && recentLogs.all { it.status == "failed" }) {
                                        val warningText = """
                                            SYSTEM WARNING (CONSECUTIVE FAILURES):
                                            Your last 3 consecutive tool executions have failed.
                                            Stop guessing file contents or line ranges!
                                            
                                            Before you try any more edits:
                                            1. Run a 'grep' command to locate the file and exact lines.
                                            2. Read the surrounding lines of the target file to verify its structure and syntax.
                                            3. Adjust your path or content matching to be perfectly accurate.
                                        """.trimIndent()
                                        
                                        history.add(Content(
                                            role = "user",
                                            parts = listOf(Part(text = warningText))
                                        ))
                                    }
                                }



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
                                val cleanMessage = message.replace(Regex("(?i)</?tool_call>"), "")
                                    .replace(Regex("(?i)</?function_call>"), "")
                                    .trim()
                                val agentMsg = ChatMessageEntity(
                                    projectName = project.name,
                                    role = "assistant",
                                    content = if (cleanMessage.isNotBlank()) cleanMessage else "Task completed successfully!",
                                    timestamp = System.currentTimeMillis(),
                                    aiActionLogsJson = logsJson
                                )
                                repository.insertChatMessage(agentMsg)
                                _chatMessages.value = repository.getChatsForProject(project.name)
                                
                                _agentStatus.value = "Changes applied successfully!"
                                loopCompleted = true
                                agentMessageSaved = true

                                // Smart Code Change Tracker: Detect framework and ask to build on Github ONLY if code changed this turn
                                val hasFileModifications = checkHasCodeChangesThisTurn(editsAtPromptStart)
                                if (hasFileModifications) {
                                    val projectDir = repository.getProjectDir(project.name)
                                    val pFiles = _projectFiles.value
                                    val isKotlin = java.io.File(projectDir, "build.gradle.kts").exists() || java.io.File(projectDir, "build.gradle").exists() || pFiles.any { it.path.endsWith("build.gradle.kts") || it.path.endsWith("build.gradle") }
                                    val isFlutter = java.io.File(projectDir, "pubspec.yaml").exists() || pFiles.any { it.path.endsWith("pubspec.yaml") }
                                    val isNextJs = java.io.File(projectDir, "next.config.js").exists() || java.io.File(projectDir, "next.config.mjs").exists() || pFiles.any { it.path.contains("next.config") }
                                    val isReactVite = java.io.File(projectDir, "vite.config.js").exists() || java.io.File(projectDir, "vite.config.ts").exists() || pFiles.any { it.path.contains("vite.config") || (it.path.endsWith("package.json") && it.content.contains("vite", ignoreCase = true)) }
                                    val isWebPackage = java.io.File(projectDir, "package.json").exists() || pFiles.any { it.path.endsWith("package.json") }

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
                                    } else if (isNextJs) {
                                        _detectedFramework.value = "Next.js"
                                        if (_allowBuildPush.value) {
                                            acceptGithubPushPrompt()
                                        } else {
                                            _showGithubPushPrompt.value = true
                                        }
                                    } else if (isReactVite) {
                                        _detectedFramework.value = "React Vite"
                                        if (_allowBuildPush.value) {
                                            acceptGithubPushPrompt()
                                        } else {
                                            _showGithubPushPrompt.value = true
                                        }
                                    } else if (isWebPackage) {
                                        _detectedFramework.value = "Web App"
                                        if (_allowBuildPush.value) {
                                            acceptGithubPushPrompt()
                                        } else {
                                            _showGithubPushPrompt.value = true
                                        }
                                    }
                                } else {
                                    _showGithubPushPrompt.value = false
                                }
                            }
                            "ai_think", "ai_response" -> {
                                val message = args?.message ?: args?.query ?: args?.content ?: args?.prompt ?: "Analyzing and thinking through task requirements."
                                val isThink = stepResponse.tool == "ai_think"
                                val title = if (isThink) "AI Thinking & Analysis" else "AI formulating logic"
                                val responseLog = createAiLog(
                                    title = title,
                                    status = "success",
                                    details = message
                                )
                                _aiActionLogs.value = _aiActionLogs.value + responseLog

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                val toolName = stepResponse.tool ?: "ai_think"
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$toolName': $title logged successfully. Proceed with your plan."))))
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

                                if (targetPath.isEmpty()) {
                                    val errorMsg = "Error: Scanning the entire project root with 'scan_dir' is strictly forbidden to protect the context limit. You MUST specify a specific target subdirectory path (e.g., 'app', 'app/src', 'app/src/main/java/com/example') to scan its contents. This is a mandatory safety rule."
                                    updateAiLog(scanLog.id, "failed", errorMsg)
                                    history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                    history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'scan_dir': $errorMsg"))))
                                } else {
                                    val files = repository.getFilesForProject(project.name)
                                    val filteredFiles = files.filter { it.path.startsWith(targetPath) }

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
                                    val targetFile = files.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }
                                    if (targetFile != null) {
                                        readFilesThisSession.add(filePath)
                                        readFilesThisSession.add(normalizePath(filePath))
                                        readFilesThisSession.add(targetFile.path)
                                        readFilesThisSession.add(normalizePath(targetFile.path))
                                        "--- File: $filePath ---\n${targetFile.content}"
                                    } else {
                                        "Error: File '$filePath' not found."
                                    }
                                }

                                val logDetails = if (repository.isBinaryExtension(filePath)) {
                                    "Error: Cannot read binary files as text"
                                } else {
                                    val files = repository.getFilesForProject(project.name)
                                    val targetFile = files.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }
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
                                    val targetFile = files.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }
                                    if (targetFile != null) {
                                        readFilesThisSession.add(filePath)
                                        readFilesThisSession.add(normalizePath(filePath))
                                        readFilesThisSession.add(targetFile.path)
                                        readFilesThisSession.add(normalizePath(targetFile.path))
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
                                    val targetFile = files.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }
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
                            "multi_read_file", "multi_read" -> {
                                val filePath = normalizePath(args?.path ?: args?.targetFile ?: "")
                                val rangePairs = mutableListOf<Pair<Int, Int>>()

                                val rawRanges = args?.ranges
                                val rawRangeList = args?.rangeList
                                val rawLineRange = args?.lineRange ?: args?.query ?: args?.search ?: ""

                                if (!rawRanges.isNullOrEmpty()) {
                                    for (item in rawRanges) {
                                        val s = item.startLine
                                        val e = item.endLine ?: item.startLine
                                        if (s != null) {
                                            rangePairs.add(Pair(s, e ?: s))
                                        } else if (!item.range.isNullOrBlank()) {
                                            val match = """(\d+)\s*[-:to\.]+\s*(\d+)""".toRegex().find(item.range)
                                            if (match != null) {
                                                val st = match.groupValues[1].toIntOrNull()
                                                val en = match.groupValues[2].toIntOrNull()
                                                if (st != null) rangePairs.add(Pair(st, en ?: st))
                                            } else {
                                                val single = """(\d+)""".toRegex().find(item.range)?.groupValues?.get(1)?.toIntOrNull()
                                                if (single != null) rangePairs.add(Pair(single, single))
                                            }
                                        }
                                    }
                                }

                                if (rangePairs.isEmpty() && !rawRangeList.isNullOrEmpty()) {
                                    for (str in rawRangeList) {
                                        val match = """(\d+)\s*[-:to\.]+\s*(\d+)""".toRegex().find(str)
                                        if (match != null) {
                                            val st = match.groupValues[1].toIntOrNull()
                                            val en = match.groupValues[2].toIntOrNull()
                                            if (st != null) rangePairs.add(Pair(st, en ?: st))
                                        } else {
                                            val single = """(\d+)""".toRegex().find(str)?.groupValues?.get(1)?.toIntOrNull()
                                            if (single != null) rangePairs.add(Pair(single, single))
                                        }
                                    }
                                }

                                if (rangePairs.isEmpty() && rawLineRange.isNotBlank()) {
                                    val matches = """(\d+)\s*[-:to\.]+\s*(\d+)""".toRegex().findAll(rawLineRange)
                                    for (m in matches) {
                                        val st = m.groupValues[1].toIntOrNull()
                                        val en = m.groupValues[2].toIntOrNull()
                                        if (st != null) rangePairs.add(Pair(st, en ?: st))
                                    }
                                    if (rangePairs.isEmpty()) {
                                        val singleMatches = """(\d+)""".toRegex().findAll(rawLineRange)
                                        for (sm in singleMatches) {
                                            val num = sm.groupValues[1].toIntOrNull()
                                            if (num != null) rangePairs.add(Pair(num, num))
                                        }
                                    }
                                }

                                if (rangePairs.isEmpty()) {
                                    rangePairs.add(Pair(1, 30))
                                }

                                val formattedRanges = rangePairs.joinToString(", ") { (st, en) ->
                                    if (st == en) "$st" else "$st-$en"
                                }

                                val readLog = AiActionLog(
                                    title = "read :$filePath",
                                    status = "thinking",
                                    details = filePath,
                                    lineRange = "Line $formattedRanges"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + readLog

                                val result = if (repository.isBinaryExtension(filePath)) {
                                    "Error: Cannot read binary files as text."
                                } else {
                                    val files = repository.getFilesForProject(project.name)
                                    val targetFile = files.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }
                                    if (targetFile != null) {
                                        readFilesThisSession.add(filePath)
                                        readFilesThisSession.add(normalizePath(filePath))
                                        readFilesThisSession.add(targetFile.path)
                                        readFilesThisSession.add(normalizePath(targetFile.path))
                                        val lines = targetFile.content.lines()

                                        val blocks = mutableListOf<String>()
                                        for ((st, en) in rangePairs) {
                                            val startIdx = (st - 1).coerceAtLeast(0).coerceAtMost(lines.size)
                                            val endIdx = en.coerceAtLeast(startIdx).coerceAtMost(lines.size)
                                            val selectedLines = lines.subList(startIdx, endIdx).joinToString("\n")
                                            blocks.add("--- File: $filePath (Lines ${startIdx + 1}-$endIdx) ---\n$selectedLines")
                                        }
                                        blocks.joinToString("\n\n")
                                    } else {
                                        "Error: File '$filePath' not found."
                                    }
                                }

                                val isSuccess = result.startsWith("--- File:")
                                val logDetails = if (isSuccess) "Read lines $formattedRanges from $filePath" else result
                                updateAiLog(readLog.id, if (isSuccess) "success" else "failed", logDetails, lineRange = "Line $formattedRanges")

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool': $result"))))
                            }
                            "create_file" -> {
                                val filePath = normalizePath(args?.path ?: "")
                                val fileContent = args?.content ?: ""
                                val createLog = createAiLog(
                                    title = "Created new file",
                                    status = "thinking",
                                    details = "$filePath",
                                    lineRange = args?.lineRange ?: "all"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + createLog

                                val existingFiles = _projectFiles.value
                                val fileAlreadyExists = existingFiles.any { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }

                                val result = if (fileAlreadyExists) {
                                    "Error: File '$filePath' already exists. Overwriting or recreating existing files with 'create_file' is strictly prohibited. You MUST call 'read_file' or 'read_file_range' first and then use 'edit_file' or 'patch_file' to modify existing files."
                                } else {
                                    try {
                                        repository.saveFile(project.name, filePath, fileContent)
                                        filesModifiedThisPrompt = true
                                        _editHistory.value = _editHistory.value + EditRecord(
                                            tool = "create_file",
                                            path = filePath,
                                            lines = "all"
                                        )
                                        "Successfully created new file '$filePath'"
                                    } catch (e: Exception) {
                                        "Error creating file: ${e.localizedMessage}"
                                    }
                                }

                                updateAiLog(createLog.id, if (result.startsWith("Error")) "failed" else "success", filePath)

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'create_file': $result"))))
                            }
                            "write_file", "write" -> {
                                val filePath = normalizePath(args?.path ?: "")
                                val fileContent = args?.content ?: ""
                                val writeLog = createAiLog(
                                    title = "Writing file",
                                    status = "thinking",
                                    details = "$filePath",
                                    lineRange = args?.lineRange ?: "all"
                                )
                                _aiActionLogs.value = _aiActionLogs.value + writeLog

                                val existingFiles = _projectFiles.value
                                val targetFile = existingFiles.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }
                                val linesCount = targetFile?.content?.lines()?.size ?: 0

                                val fileHasBeenRead = if (targetFile != null) {
                                    readFilesThisSession.contains(filePath) ||
                                    readFilesThisSession.contains(normalizePath(filePath)) ||
                                    readFilesThisSession.contains(targetFile.path) ||
                                    readFilesThisSession.contains(normalizePath(targetFile.path)) ||
                                    history.any { content ->
                                        content.parts.any { part ->
                                            val t = part.text ?: ""
                                            (t.contains("System/Tool Output for 'read_file'") || t.contains("System/Tool Output for 'read_file_range'")) &&
                                            (t.contains(filePath) || t.contains(normalizePath(filePath)) || t.contains(targetFile.path) || t.contains(normalizePath(targetFile.path)))
                                        }
                                    }
                                } else true

                                var allowed = true
                                if (false) {
                                    _writeFileConfirmInfo.value = WriteFileConfirmInfo(filePath, fileContent, linesCount)
                                    val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                                    writeFileConfirmationDeferred = deferred
                                    
                                    _agentStatus.value = "Waiting for user permission to overwrite $filePath..."
                                    allowed = deferred.await()
                                }

                                val result = if (targetFile != null && linesCount > 30) {
                                    "Error: SYSTEM REJECTION - File '$filePath' has $linesCount lines (more than 30 lines). Overwriting or recreating existing files larger than 30 lines with 'write_file' is STRICTLY FORBIDDEN to prevent code destruction. You MUST use 'edit_file' or 'patch_file' to make precise surgical edits."
                                } else if (targetFile != null && !fileHasBeenRead) {
                                    "Error: SYSTEM REJECTION - Overwriting/recreating existing file '$filePath' without reading it first is STRICTLY FORBIDDEN! You MUST call 'read_file' or 'read_file_range' on '$filePath' before attempting to modify or overwrite it. Furthermore, 'write_file' is a RESTRICTED tool—prefer using 'edit_file' or 'patch_file' for surgical code edits instead of overwriting full files."
                                } else if (!allowed) {
                                    "Error: Overwriting '$filePath' (which has $linesCount lines) was denied by the user. You must use 'edit_file' or 'patch_file' instead."
                                } else {
                                    try {
                                        repository.saveFile(project.name, filePath, fileContent)
                                        filesModifiedThisPrompt = true
                                        _editHistory.value = _editHistory.value + EditRecord(
                                            tool = "write_file",
                                            path = filePath,
                                            lines = "all"
                                        )
                                        if (targetFile != null) {
                                            "Successfully overwrote existing file '$filePath'"
                                        } else {
                                            "Successfully created new file '$filePath'"
                                        }
                                    } catch (e: Exception) {
                                        "Error writing file: ${e.localizedMessage}"
                                    }
                                }

                                updateAiLog(writeLog.id, if (result.startsWith("Error")) "failed" else "success", filePath)

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'write_file': $result"))))
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
                                val targetFile = files.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }
                                val fileHasBeenRead = if (targetFile != null) {
                                    readFilesThisSession.contains(filePath) ||
                                    readFilesThisSession.contains(normalizePath(filePath)) ||
                                    readFilesThisSession.contains(targetFile.path) ||
                                    readFilesThisSession.contains(normalizePath(targetFile.path)) ||
                                    history.any { content ->
                                        content.parts.any { part ->
                                            val t = part.text ?: ""
                                            (t.contains("System/Tool Output for 'read_file'") || t.contains("System/Tool Output for 'read_file_range'") || t.contains("System/Tool Output for 'view_file'")) &&
                                            (t.contains(filePath) || t.contains(normalizePath(filePath)) || t.contains(targetFile.path) || t.contains(normalizePath(targetFile.path)))
                                        }
                                    }
                                } else true

                                val result = if (repository.isBinaryExtension(filePath)) {
                                    "Error: Reading, editing, patching, or appending to binary image or 3D files directly as text is NOT allowed. You can only view their existence via 'list_directory' or perform operations like rename, delete, move, resize, or format change."
                                } else if (targetFile != null && !fileHasBeenRead) {
                                    "Error: SYSTEM REJECTION - You cannot append to existing file '$filePath' without reading it first! You MUST call 'read_file' or 'read_file_range' on '$filePath' to inspect its current content before appending. Please read the file first."
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

                                val files = repository.getFilesForProject(project.name)
                                val targetFile = files.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }
                                val fileHasBeenRead = if (targetFile != null) {
                                    readFilesThisSession.contains(filePath) ||
                                    readFilesThisSession.contains(normalizePath(filePath)) ||
                                    readFilesThisSession.contains(targetFile.path) ||
                                    readFilesThisSession.contains(normalizePath(targetFile.path)) ||
                                    history.any { content ->
                                        content.parts.any { part ->
                                            val t = part.text ?: ""
                                            (t.contains("System/Tool Output for 'read_file'") || t.contains("System/Tool Output for 'read_file_range'") || t.contains("System/Tool Output for 'view_file'")) &&
                                            (t.contains(filePath) || t.contains(normalizePath(filePath)) || t.contains(targetFile.path) || t.contains(normalizePath(targetFile.path)))
                                        }
                                    }
                                } else true

                                val result = if (repository.isBinaryExtension(filePath)) {
                                    "Error: Reading, editing, patching, or appending to binary image or 3D files directly as text is NOT allowed. You can only view their existence via 'list_directory' or perform operations like rename, delete, move, resize, or format change."
                                } else if (targetFile != null && !fileHasBeenRead) {
                                    "Error: SYSTEM REJECTION - You cannot edit or patch file '$filePath' without reading it first! You MUST call 'read_file' or 'read_file_range' on '$filePath' to inspect its exact and current contents before making any changes. Please read the file first."
                                } else if (targetFile != null) {
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

                                val isSuccess = result.startsWith("Successfully")
                                val range = if (foundRange.isNotEmpty()) foundRange else args?.lineRange ?: ""
                                updateAiLog(
                                    patchLog.id, 
                                    if (isSuccess) "success" else "failed", 
                                    if (isSuccess) filePath else result,
                                    lineRange = range
                                )

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool': $result"))))
                            }
                            "multi_edit_file", "multi_edit", "multi_patch" -> {
                                val filePath = normalizePath(args?.path ?: args?.targetFile ?: "")
                                val rawChunks = args?.chunks ?: args?.replacementChunks ?: args?.edits ?: emptyList()
                                val chunks = if (rawChunks.isEmpty() && !args?.search.isNullOrEmpty()) {
                                    listOf(EditChunk(search = args?.search, replace = args?.replace))
                                } else {
                                    rawChunks
                                }

                                val multiLog = createAiLog(
                                    title = "Multi-edited file",
                                    status = "thinking",
                                    details = "$filePath",
                                    lineRange = args?.lineRange
                                )
                                _aiActionLogs.value = _aiActionLogs.value + multiLog

                                var foundRange = ""
                                val files = repository.getFilesForProject(project.name)
                                val targetFile = files.find { it.path == filePath || normalizePath(it.path) == filePath || it.path.endsWith(filePath) || filePath.endsWith(it.path) }
                                val fileHasBeenRead = if (targetFile != null) {
                                    readFilesThisSession.contains(filePath) ||
                                    readFilesThisSession.contains(normalizePath(filePath)) ||
                                    readFilesThisSession.contains(targetFile.path) ||
                                    readFilesThisSession.contains(normalizePath(targetFile.path)) ||
                                    history.any { content ->
                                        content.parts.any { part ->
                                            val t = part.text ?: ""
                                            (t.contains("System/Tool Output for 'read_file'") || t.contains("System/Tool Output for 'read_file_range'") || t.contains("System/Tool Output for 'view_file'")) &&
                                            (t.contains(filePath) || t.contains(normalizePath(filePath)) || t.contains(targetFile.path) || t.contains(normalizePath(targetFile.path)))
                                        }
                                    }
                                } else true

                                val result = if (repository.isBinaryExtension(filePath)) {
                                    "Error: Reading or editing binary files directly as text is NOT allowed."
                                } else if (targetFile != null && !fileHasBeenRead) {
                                    "Error: SYSTEM REJECTION - You cannot multi-edit file '$filePath' without reading it first! You MUST call 'read_file' or 'read_file_range' on '$filePath' to inspect its exact and current contents before making any changes. Please read the file first."
                                } else if (targetFile != null) {
                                    var currentContent = targetFile.content
                                    if (chunks.isEmpty()) {
                                        "Error: No edit chunks provided for multi_edit_file. Provide 'chunks' or 'replacementChunks' list with search and replace blocks."
                                    } else {
                                        var chunkError: String? = null
                                        val lineRanges = mutableListOf<String>()

                                        for ((index, chunk) in chunks.withIndex()) {
                                            val searchStr = chunk.search ?: chunk.targetContent ?: ""
                                            val replaceStr = chunk.replace ?: chunk.replacementContent ?: ""

                                            if (searchStr.isEmpty()) {
                                                chunkError = "Error in chunk #${index + 1}: 'search' block cannot be empty."
                                                break
                                            }
                                            if (!currentContent.contains(searchStr)) {
                                                chunkError = "Error in chunk #${index + 1}: Could not find exact search block in $filePath. Please double-check characters, indentation, and spaces."
                                                break
                                            }
                                            val occurrences = currentContent.split(searchStr).size - 1
                                            if (occurrences > 1) {
                                                chunkError = "Error in chunk #${index + 1}: The search block is not unique. It occurs $occurrences times in the file."
                                                break
                                            }

                                            val startIndex = currentContent.indexOf(searchStr)
                                            val linesBefore = currentContent.substring(0, startIndex).count { it == '\n' } + 1
                                            val linesInSearch = searchStr.count { it == '\n' }
                                            val endLine = linesBefore + linesInSearch
                                            val chunkRangeStr = if (linesBefore == endLine) "$linesBefore" else "$linesBefore-$endLine"
                                            lineRanges.add(chunkRangeStr)

                                            currentContent = currentContent.replace(searchStr, replaceStr)
                                        }

                                        if (chunkError != null) {
                                            chunkError
                                        } else {
                                            foundRange = if (lineRanges.isNotEmpty()) lineRanges.joinToString(", ") else ""
                                            try {
                                                repository.saveFile(project.name, filePath, currentContent)
                                                filesModifiedThisPrompt = true
                                                _editHistory.value = _editHistory.value + EditRecord(
                                                    tool = "multi_edit",
                                                    path = filePath,
                                                    lines = foundRange
                                                )
                                                "Successfully multi-edited file '$filePath' ($foundRange)"
                                            } catch (e: Exception) {
                                                "Error writing modified file: ${e.localizedMessage}"
                                            }
                                        }
                                    }
                                } else {
                                    "Error: File '$filePath' not found."
                                }

                                val isSuccess = result.startsWith("Successfully")
                                val range = if (foundRange.isNotEmpty()) foundRange else args?.lineRange ?: ""
                                updateAiLog(
                                    multiLog.id,
                                    if (isSuccess) "success" else "failed",
                                    if (isSuccess) filePath else result,
                                    lineRange = range
                                )

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool': $result"))))
                            }
                            "mcp_call_tool", "mcp_call", "mcp_execute", "call_mcp_tool", "use_mcp_tool", "mcp_tool", "mcp_list_tools", "mcp_list", "mcp_read_resource" -> {
                                val result = McpToolHandler.handleMcpToolCall(
                                    tool = tool,
                                    args = args,
                                    project = project,
                                    mcpManager = mcpManager,
                                    createLog = { title, details -> createAiLog(title = title, status = "thinking", details = details) },
                                    updateLog = { id, status, details -> updateAiLog(id, status, details) },
                                    addLog = { log -> _aiActionLogs.value = _aiActionLogs.value + log }
                                )
                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool': $result"))))
                            }
                            "generate_image", "resize_image", "browser_search", "browser_click", "browser_read", "create_todo_list", "complete_todo_task", "delete_file", "rename_file", "move_file",
                            "open_url", "navigate", "browse_url", "get_page_source", "inspect_dom", "inspect_css", "get_computed_styles", "take_screenshot", "click", "type", "scroll", "get_links", "get_images", "get_fonts", "run_javascript", "execute_javascript", "eval_js", "compare_screenshot" -> {
                                val result = ExtraToolHandlers.handleExtraToolCall(
                                    tool = tool,
                                    args = args,
                                    project = project,
                                    repository = repository,
                                    backgroundBrowser = backgroundBrowser,
                                    todoList = _todoList.value,
                                    updateTodoList = { _todoList.value = it },
                                    createLog = { title, status, details, lineRange -> createAiLog(title = title, status = status, details = details, lineRange = lineRange) },
                                    addLog = { log -> _aiActionLogs.value = _aiActionLogs.value + log },
                                    updateLog = { id, status, details -> updateAiLog(id, status, details) },
                                    setAgentStatus = { status -> _agentStatus.value = status },
                                    normalizePath = { path -> normalizePath(path) }
                                )
                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool': $result"))))
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
                            "load_skill" -> {
                                val skillQuery = args?.path ?: args?.query ?: args?.message ?: args?.content ?: ""
                                val activeSkills = _agentSkills.value.filter { it.isInstalled && it.isEnabled }
                                val matchedSkill = activeSkills.find { 
                                    it.id.equals(skillQuery, ignoreCase = true) || 
                                    it.name.contains(skillQuery, ignoreCase = true) ||
                                    skillQuery.contains(it.id, ignoreCase = true)
                                }

                                val result = if (matchedSkill != null) {
                                    val skillLog = createAiLog(
                                        title = "Loaded Skill: ${matchedSkill.name}",
                                        status = "success",
                                        details = "Agent analyzed prompt and loaded skill '${matchedSkill.name}' by ${matchedSkill.author}"
                                    )
                                    _aiActionLogs.value = _aiActionLogs.value + skillLog

                                    "Loaded Skill: ${matchedSkill.name} (${matchedSkill.author})\nDescription: ${matchedSkill.description}\nInstructions:\n${matchedSkill.skillPrompt}"
                                } else {
                                    val skillLog = createAiLog(
                                        title = "Load Skill Failed",
                                        status = "failed",
                                        details = "Skill '$skillQuery' not active or not installed."
                                    )
                                    _aiActionLogs.value = _aiActionLogs.value + skillLog

                                    val availableList = activeSkills.joinToString(", ") { "${it.id} (${it.name})" }
                                    "Skill '$skillQuery' not found or not active. Active installed skills: $availableList"
                                }

                                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'load_skill': $result"))))
                            }
                            else -> {
                                val isMcpCandidate = tool.startsWith("mcp_") || 
                                    tool.startsWith("gsc_") || 
                                    tool.contains("__") || 
                                    mcpManager.toolRegistry.findToolInServers(tool, mcpManager.servers.value) != null

                                if (isMcpCandidate) {
                                    val result = McpToolHandler.handleMcpToolCall(
                                        tool = tool,
                                        args = args,
                                        project = project,
                                        mcpManager = mcpManager,
                                        createLog = { title, details -> createAiLog(title = title, status = "thinking", details = details) },
                                        updateLog = { id, status, details -> updateAiLog(id, status, details) },
                                        addLog = { log -> _aiActionLogs.value = _aiActionLogs.value + log }
                                    )
                                    history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                                    history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool': $result"))))
                                } else {
                                    loopCompleted = true
                                }
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
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Log.d("VibeViewModel", "AI agent loop cancelled")
                    loopCompleted = true
                    throw e
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
                
                // Add a smart, polite delay of 4.5 seconds between successive turns to prevent slamming the API (429 rate limit errors)
                if (!loopCompleted && turn <= 500 && actionsCount < maxActionSteps) {
                    kotlinx.coroutines.delay(4500)
                }
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
                    timerJob.cancel()
                    val finalSecs = maxOf(1L, (System.currentTimeMillis() - executionStartTime) / 1000)
                    _executionElapsedTimeSeconds.value = finalSecs

                    val finalSys = _liveSystemTokens.value
                    val finalUsr = _liveUserTokens.value
                    val finalTool = _liveToolTokens.value
                    val finalIn = _liveTotalInputTokens.value
                    val finalOut = _liveTotalOutputTokens.value
                    val activeModelVal = _currentRunningModelName.value

                    if (userMsg != null) {
                        val updatedUserMsg = userMsg.copy(
                            modelName = activeModelVal,
                            executionTimeSeconds = finalSecs,
                            systemTokens = finalSys,
                            userTokens = finalUsr,
                            toolTokens = finalTool,
                            historyTokens = 420,
                            skillTokens = finalTool,
                            totalInputTokens = finalIn,
                            totalOutputTokens = finalOut
                        )
                        repository.insertChatMessage(updatedUserMsg)
                    }

                    if (!agentMessageSaved) {
                        val logsJson = try {
                            val listType = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
                            moshi.adapter<List<AiActionLog>>(listType).toJson(_aiActionLogs.value)
                        } catch (e: Exception) {
                            null
                        }
                        
                        val isSuccessful = loopCompleted && _aiActionLogs.value.none { it.status == "failed" }
                        val rawContent = if (isSuccessful) {
                            lastThought?.ifBlank { null } ?: "Task completed successfully!"
                        } else {
                            val finalMessage = _aiActionLogs.value.lastOrNull { it.status == "failed" }?.details 
                                ?: "AI task stopped unexpectedly or hit a limit."
                            "Task interrupted: $finalMessage"
                        }
                        val content = rawContent.replace(Regex("(?i)</?tool_call>"), "")
                            .replace(Regex("(?i)</?function_call>"), "")
                            .trim()
                        
                        val agentMsg = ChatMessageEntity(
                            projectName = project.name,
                            role = "assistant",
                            content = if (content.isNotBlank()) content else "Task completed successfully!",
                            timestamp = System.currentTimeMillis(),
                            aiActionLogsJson = logsJson,
                            modelName = activeModelVal,
                            executionTimeSeconds = finalSecs,
                            systemTokens = finalSys,
                            userTokens = finalUsr,
                            toolTokens = finalTool,
                            historyTokens = 420,
                            skillTokens = finalTool,
                            totalInputTokens = finalIn,
                            totalOutputTokens = finalOut
                        )
                        repository.insertChatMessage(agentMsg)
                    }
                    
                    _isThinking.value = false
                    _chatMessages.value = repository.getChatsForProject(project.name)
                    if (project.templateKey == "vanilla" || project.templateKey == "react" || project.templateKey == "vanilla_three") {
                        // Clear old detected errors right before reloading preview so newly reloaded preview can report fresh errors if any
                        _detectedWebErrors.value = emptyList()
                        _webConsoleLogs.value = emptyList()
                        _webPreviewRefreshTrigger.value += 1
                    }
                    checkAndTriggerAutoFixOnAgentFinish()

                    // Auto-trigger framework detection and Github push prompting on task completion
                    try {
                        val hasFileModifications = checkHasCodeChangesThisTurn(editsAtPromptStart)
                        if (hasFileModifications) {
                            val projectDir = repository.getProjectDir(project.name)
                            val pFiles = _projectFiles.value
                            val isKotlin = java.io.File(projectDir, "build.gradle.kts").exists() || java.io.File(projectDir, "build.gradle").exists() || pFiles.any { it.path.endsWith("build.gradle.kts") || it.path.endsWith("build.gradle") }
                            val isFlutter = java.io.File(projectDir, "pubspec.yaml").exists() || pFiles.any { it.path.endsWith("pubspec.yaml") }
                            val isNextJs = java.io.File(projectDir, "next.config.js").exists() || java.io.File(projectDir, "next.config.mjs").exists() || pFiles.any { it.path.contains("next.config") }
                            val isReactVite = java.io.File(projectDir, "vite.config.js").exists() || java.io.File(projectDir, "vite.config.ts").exists() || pFiles.any { it.path.contains("vite.config") || (it.path.endsWith("package.json") && it.content.contains("vite", ignoreCase = true)) }
                            val isWebPackage = java.io.File(projectDir, "package.json").exists() || pFiles.any { it.path.endsWith("package.json") }

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
                            } else if (isNextJs) {
                                _detectedFramework.value = "Next.js"
                                if (_allowBuildPush.value) {
                                    acceptGithubPushPrompt()
                                } else {
                                    _showGithubPushPrompt.value = true
                                }
                            } else if (isReactVite) {
                                _detectedFramework.value = "React Vite"
                                if (_allowBuildPush.value) {
                                    acceptGithubPushPrompt()
                                } else {
                                    _showGithubPushPrompt.value = true
                                }
                            } else if (isWebPackage) {
                                _detectedFramework.value = "Web App"
                                if (_allowBuildPush.value) {
                                    acceptGithubPushPrompt()
                                } else {
                                    _showGithubPushPrompt.value = true
                                }
                            }
                        } else {
                            _showGithubPushPrompt.value = false
                        }
                    } catch (e: Exception) {
                        Log.e("VibeViewModel", "Error in post-thinking framework detection", e)
                    }

                    try {
                        val context = getApplication<Application>()
                        val stopIntent = Intent(context, VibeAgentService::class.java).apply {
                            action = VibeAgentService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    } catch (e: Exception) {
                        Log.e("VibeViewModel", "Error stopping service", e)
                    }
                }
            }
        }
    }

    private fun generateFileTree(files: List<ProjectFileEntity>): String {
        val visibleFiles = files.filter { it.path != "browser_memory.md" && it.path != "memory.md" }
        if (visibleFiles.isEmpty()) return "Workspace is empty."
        
        val rootFiles = mutableSetOf<String>()
        val rootDirs = mutableSetOf<String>()
        
        for (file in visibleFiles) {
            val parts = file.path.split("/")
            if (parts.size == 1) {
                rootFiles.add(parts[0])
            } else if (parts.size > 1 && parts[0].isNotEmpty()) {
                rootDirs.add(parts[0] + "/")
            }
        }
        
        val sortedDirs = rootDirs.sorted()
        val sortedFiles = rootFiles.sorted()
        
        val sb = java.lang.StringBuilder()
        sb.append("PROJECT WORKSPACE ROOT DIRECTORY CONTENTS:\n")
        
        val allItems = sortedDirs + sortedFiles
        for (i in allItems.indices) {
            val item = allItems[i]
            val isLast = i == allItems.size - 1
            val marker = if (isLast) "└── " else "├── "
            sb.append(marker).append(item).append("\n")
        }
        
        sb.append("\n==================================================\n")
        sb.append("TOTAL WORKSPACE FILES: ${visibleFiles.size}\n")
        sb.append("==================================================\n")
        sb.append("- Only ROOT files/folders shown. Use 'scan_dir' with specific subfolder path (e.g. 'app', 'src') to view nested files. Root scanning ('.') is forbidden.\n")
        
        return sb.toString()
    }

    companion object {
        private val backgroundScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
        )
    }
}

