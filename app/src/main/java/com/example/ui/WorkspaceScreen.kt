package com.example.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.api.AgentFileAction
import com.example.data.ChatMessageEntity
import com.example.data.ProjectEntity
import com.example.data.ProjectFileEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.ByteArrayInputStream
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WorkspaceScreen(
    project: ProjectEntity,
    files: List<ProjectFileEntity>,
    activeFile: ProjectFileEntity?,
    editorContent: String,
    chatMessages: List<ChatMessageEntity>,
    isThinking: Boolean,
    agentStatus: String,
    currentTab: WorkspaceTab,
    aiActionLogs: List<AiActionLog>,
    mcpServers: List<com.example.data.McpServer> = emptyList(),
    onAddMcpServer: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onToggleMcpWorkspace: (String, Boolean) -> Unit = { _, _ -> },
    onTestConnectMcp: suspend (String) -> Unit = {},
    onStartMcpOAuthFlow: (android.content.Context, String, String?) -> Unit = { _, _, _ -> },
    onDeleteMcpServer: (String) -> Unit = {},
    terminalOutput: String,
    gitProgress: String,
    customProvider: String,
    customApiKey: String,
    customBaseUrl: String,
    customModelId: String,
    useCustomModel: Boolean,
    githubToken: String,
    githubRepo: String = "",
    githubBranch: String = "main",
    explorerGithubToken: String = "",
    explorerGithubRepo: String = "",
    explorerGithubBranch: String = "main",
    gitHubWorkflows: List<GitHubWorkflow> = emptyList(),
    onTriggerWorkflows: (Set<Long>?) -> Unit = {},
    buildStatus: String = "Idle",
    buildSteps: List<BuildStep> = emptyList(),
    buildLogs: String = "",
    buildError: String? = null,
    isPollingBuild: Boolean = false,
    apkDownloadProgress: String = "",
    apkDownloadPercentage: Float? = null,
    onInstallApk: () -> Unit = {},
    onSaveGithubRepo: (String) -> Unit = {},
    onSaveGithubBranch: (String) -> Unit = {},
    onSaveExplorerGithubRepo: (String) -> Unit = {},
    onSaveExplorerGithubBranch: (String) -> Unit = {},
    onSaveSettings: (String, String, String, String, Boolean) -> Unit,
    maxActionSteps: Int = 80,
    allowBuildPush: Boolean = false,
    allowAutoFix: Boolean = false,
    allowBackgroundExecution: Boolean = false,
    onSaveAllowBuildPush: (Boolean) -> Unit = {},
    onSaveAllowAutoFix: (Boolean) -> Unit = {},
    onSaveAllowBackgroundExecution: (Boolean) -> Unit = {},
    isLoadingWorkspace: Boolean = false,
    onSaveMaxActionSteps: (Int) -> Unit = {},
    onSaveGithubToken: (String) -> Unit,
    onSaveExplorerGithubToken: (String) -> Unit = {},
    onPushGitRepo: (String, String, String, Boolean, (Result<Unit>) -> Unit) -> Unit,
    onTabSelected: (WorkspaceTab) -> Unit,
    onBack: () -> Unit,
    onSelectFile: (ProjectFileEntity) -> Unit,
    onUpdateEditor: (String) -> Unit,
    onSaveFile: () -> Unit,
    onCreateFile: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onRenameFile: (String, String) -> Unit,
    onMoveFile: (String, String) -> Unit,
    onSendPrompt: (String, List<com.example.ui.AttachedFile>) -> Unit,
    onImportFiles: (List<android.net.Uri>) -> Unit,
    onDecompileApk: (String) -> Unit = {},
    onPushProject: () -> Unit = {},
    onSearch: () -> Unit = {},
    onTerminalCommand: (String) -> Unit,
    onClearTerminal: () -> Unit = {},
    onStopAI: () -> Unit = {},
    onDeleteMessage: (ChatMessageEntity) -> Unit = {},
    onEditMessage: (ChatMessageEntity, String) -> Unit = { _, _ -> },
    onRegenerate: (ChatMessageEntity) -> Unit = {},
    isInterrupted: Boolean = false,
    onContinue: () -> Unit = {},
    onSkip: () -> Unit = {},
    webConsoleLogs: List<VibeViewModel.WebConsoleLog> = emptyList(),
    onAddWebConsoleLog: (String, String, String, Int) -> Unit = { _, _, _, _ -> },
    onClearWebConsoleLogs: () -> Unit = {},
    detectedWebErrors: List<WebConsoleError> = emptyList(),
    onToggleError: (String) -> Unit = {},
    onToggleAllErrors: (Boolean) -> Unit = {},
    onClearErrors: () -> Unit = {},
    onFixErrors: () -> Unit = {},
    detectedAndroidBuildErrors: List<AndroidBuildError> = emptyList(),
    scannedModels: List<String> = emptyList(),
    isScanningModels: Boolean = false,
    scanError: String? = null,
    onScanModels: (String, String, String) -> Unit = { _, _, _ -> },
    onClearScannedModels: () -> Unit = {},
    onToggleAndroidBuildError: (String) -> Unit = {},
    onToggleAllAndroidBuildErrors: (Boolean) -> Unit = {},
    onClearAndroidBuildErrors: () -> Unit = {},
    onFixAndroidBuildErrors: () -> Unit = {},
    onWebError: (String, String, Int) -> Unit = { _, _, _ -> },
    customModels: List<CustomModelConfig> = emptyList(),
    selectedModelId: String = "",
    geminiModels: List<String> = emptyList(),
    openaiModels: List<String> = emptyList(),
    claudeModels: List<String> = emptyList(),
    mistralModels: List<String> = emptyList(),
    onAddCustomModel: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onDeleteCustomModel: (String) -> Unit = {},
    onSelectCustomModel: (String) -> Unit = {},
    showGithubPushPrompt: Boolean = false,
    webPreviewRefreshTrigger: Int = 0,
    detectedFramework: String = "",
    onDismissGithubPushPrompt: () -> Unit = {},
    onAcceptGithubPushPrompt: () -> Unit = {},
    todoList: List<TodoItem> = emptyList(),
    isTodoListExpanded: Boolean = true,
    onToggleTodoListExpanded: () -> Unit = {},
    chatInputText: String = "",
    onUpdateChatInputText: (String) -> Unit = {},
    attachedFiles: List<com.example.ui.AttachedFile> = emptyList(),
    onAddAttachedFile: (com.example.ui.AttachedFile) -> Unit = {},
    onRemoveAttachedFile: (com.example.ui.AttachedFile) -> Unit = {},
    onClearAttachedFiles: () -> Unit = {},
    agentSkills: List<com.example.ui.AgentSkill> = emptyList(),
    onToggleAgentSkill: (String, Boolean) -> Unit = { _, _ -> },
    onInstallAgentSkill: (String) -> Unit = {},
    onUninstallAgentSkill: (String) -> Unit = {},
    onAddCustomAgentSkill: (com.example.ui.AgentSkill) -> Unit = {},
    onFetchOnlineAgentSkills: () -> Unit = {},
    isFetchingSkills: Boolean = false,
    onUpdateSkillContent: (String, String) -> Unit = { _, _ -> },
    onFetchSkillFileContent: (String, ((String) -> Unit)?) -> Unit = { _, _ -> },
    webArtifactInfo: WebArtifactInfo? = null,
    onPreviewWebArtifact: () -> Unit = {},
    onFetchLatestArtifact: () -> Unit = {},
    isImportingFiles: Boolean = false,
    importProgress: Float = 0f,
    importProgressMessage: String = "",
    backupsList: List<BackupVersion> = emptyList(),
    onRestoreBackup: (BackupVersion) -> Unit = {},
    executionElapsedTimeSeconds: Long = 0L,
    currentRunningModelName: String = "",
    liveSystemTokens: Int = 0,
    liveUserTokens: Int = 0,
    liveToolTokens: Int = 0,
    liveTotalInputTokens: Int = 0,
    liveTotalOutputTokens: Int = 0
) {
    var showExplorer by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAgentSkillsDialog by remember { mutableStateOf(false) }
    var showMcpDialog by remember { mutableStateOf(false) }
    var showPushDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { showExplorer = !showExplorer }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Files Explorer",
                                tint = Color(0xFF38BDF8)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = project.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isThinking) Color(0xFF38BDF8) else Color(0xFF2ED573)
                                        )
                                )
                                Text(
                                    text = if (isThinking) agentStatus else "Vibe Agent Ready",
                                    fontSize = 11.sp,
                                    color = if (isThinking) Color(0xFF38BDF8) else Color(0xFF80809B)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    Surface(
                        onClick = { showAgentSkillsDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF161B22),
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = "Skills",
                                tint = Color(0xFF58A6FF),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Skills",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFC9D1D9)
                            )
                        }
                    }
                    Surface(
                        onClick = { showMcpDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF161B22),
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "MCP",
                                tint = Color(0xFFA371F7),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "MCP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFC9D1D9)
                            )
                        }
                    }
                    IconButton(onClick = { showRestoreDialog = true }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Version Backups",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "AI settings",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = { showCreateFileDialog = true }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.NoteAdd,
                            contentDescription = "New file",
                            tint = Color(0xFF58A6FF),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0B10),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (!androidx.compose.foundation.layout.WindowInsets.isImeVisible) {
                WorkspaceBottomNavigation(
                    currentTab = currentTab,
                    onTabSelected = onTabSelected
                )
            }
        },
        containerColor = Color(0xFF0A0B10)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .background(Color(0xFF08080C))
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Side Navigation Files Panel Tree-View
                AnimatedVisibility(
                    visible = showExplorer,
                    enter = slideInHorizontally { -it } + fadeIn(),
                    exit = slideOutHorizontally { -it } + fadeOut()
                ) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        ExplorerPanel(
                            files = files,
                            activeFile = activeFile,
                            onSelectFile = {
                                onSelectFile(it)
                                showExplorer = false
                            },
                            onCreateFile = onCreateFile,
                            onDeleteFile = onDeleteFile,
                            onRenameFile = onRenameFile,
                            onMoveFile = onMoveFile,
                            onImportFiles = onImportFiles,
                            onDecompileApk = onDecompileApk,
                            onPush = { showPushDialog = true },
                            onSearch = { showSearchDialog = true },
                            modifier = Modifier
                                .width(280.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF0C0D14))
                        )
                        VerticalDivider(color = Color(0xFF222533))
                    }
                }

                // Workspace Content Display based on selected tab
                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        WorkspaceTab.CHAT -> {
                            ChatTabContent(
                                messages = chatMessages,
                                isThinking = isThinking,
                                agentStatus = agentStatus,
                                aiActionLogs = aiActionLogs,
                                onSendPrompt = onSendPrompt,
                                onStopAI = onStopAI,
                                onDeleteMessage = onDeleteMessage,
                                onEditMessage = onEditMessage,
                                onRegenerate = onRegenerate,
                                isInterrupted = isInterrupted,
                                onContinue = onContinue,
                                onSkip = onSkip,
                                files = files,
                                onViewFileInEditor = { path ->
                                    val match = files.find { it.path == path }
                                    if (match != null) {
                                        onSelectFile(match)
                                        onTabSelected(WorkspaceTab.CODE)
                                    }
                                },
                                detectedWebErrors = detectedWebErrors,
                                onToggleError = onToggleError,
                                onToggleAllErrors = onToggleAllErrors,
                                onClearErrors = onClearErrors,
                                onFixErrors = onFixErrors,
                                detectedAndroidBuildErrors = detectedAndroidBuildErrors,
                                onToggleAndroidBuildError = onToggleAndroidBuildError,
                                onToggleAllAndroidBuildErrors = onToggleAllAndroidBuildErrors,
                                onClearAndroidBuildErrors = onClearAndroidBuildErrors,
                                onFixAndroidBuildErrors = onFixAndroidBuildErrors,
                                allowAutoFix = allowAutoFix,
                                todoList = todoList,
                                isTodoListExpanded = isTodoListExpanded,
                                onToggleTodoListExpanded = onToggleTodoListExpanded,
                                chatInputText = chatInputText,
                                onUpdateChatInputText = onUpdateChatInputText,
                                attachedFiles = attachedFiles,
                                onAddAttachedFile = onAddAttachedFile,
                                onRemoveAttachedFile = onRemoveAttachedFile,
                                onClearAttachedFiles = onClearAttachedFiles,
                                agentSkills = agentSkills,
                                onOpenSettings = { showSettingsDialog = true },
                                executionElapsedTimeSeconds = executionElapsedTimeSeconds,
                                currentRunningModelName = currentRunningModelName,
                                liveSystemTokens = liveSystemTokens,
                                liveUserTokens = liveUserTokens,
                                liveToolTokens = liveToolTokens,
                                liveTotalInputTokens = liveTotalInputTokens,
                                liveTotalOutputTokens = liveTotalOutputTokens
                            )
                        }
                        WorkspaceTab.CODE -> {
                            CodeTabContent(
                                files = files,
                                activeFile = activeFile,
                                editorContent = editorContent,
                                onSelectFile = onSelectFile,
                                onUpdateEditor = onUpdateEditor,
                                onSaveFile = onSaveFile,
                                onDeleteFile = onDeleteFile
                            )
                        }
                        WorkspaceTab.PREVIEW -> {
                            PreviewTabContent(
                                files = files,
                                consoleLogs = webConsoleLogs,
                                onConsoleLog = onAddWebConsoleLog,
                                onClearLogs = onClearWebConsoleLogs,
                                onWebError = { msg, src, line ->
                                    onAddWebConsoleLog(msg, "error", src, line)
                                    onWebError(msg, src, line)
                                },
                                onClearErrors = onClearErrors,
                                onInspectorElementSelected = { identifier, outerHTML ->
                                    val fileName = "index.html"
                                    val htmlContent = files.find { it.path == fileName }?.content ?: ""
                                    
                                    val regex = Regex("[\\s\"']")
                                    val normalizedHtml = htmlContent.replace(regex, "")
                                    val normalizedOuter = outerHTML.replace(regex, "")
                                    
                                    var startLine = -1
                                    var endLine = -1
                                    
                                    val exactIndex = htmlContent.indexOf(outerHTML)
                                    if (exactIndex != -1) {
                                        startLine = htmlContent.substring(0, exactIndex).count { it == '\n' } + 1
                                        endLine = startLine + outerHTML.count { it == '\n' }
                                    } else {
                                        val matchIndex = normalizedHtml.indexOf(normalizedOuter)
                                        if (matchIndex != -1) {
                                            var originalStart = 0
                                            var normalizedCount = 0
                                            while (originalStart < htmlContent.length && normalizedCount < matchIndex) {
                                                if (!htmlContent[originalStart].toString().matches(regex)) {
                                                    normalizedCount++
                                                }
                                                originalStart++
                                            }
                                            startLine = htmlContent.substring(0, originalStart).count { it == '\n' } + 1
                                            
                                            var originalEnd = originalStart
                                            var endCount = 0
                                            while (originalEnd < htmlContent.length && endCount < normalizedOuter.length) {
                                                if (!htmlContent[originalEnd].toString().matches(regex)) {
                                                    endCount++
                                                }
                                                originalEnd++
                                            }
                                            endLine = htmlContent.substring(0, originalEnd).count { it == '\n' } + 1
                                        } else {
                                            // Try partial match for start line
                                            val partialOuter = if (normalizedOuter.length > 30) normalizedOuter.substring(0, 30) else normalizedOuter
                                            val partialIndex = normalizedHtml.indexOf(partialOuter)
                                            if (partialIndex != -1) {
                                                var originalStart = 0
                                                var normalizedCount = 0
                                                while (originalStart < htmlContent.length && normalizedCount < partialIndex) {
                                                    if (!htmlContent[originalStart].toString().matches(regex)) {
                                                        normalizedCount++
                                                    }
                                                    originalStart++
                                                }
                                                startLine = htmlContent.substring(0, originalStart).count { it == '\n' } + 1
                                                endLine = startLine
                                            }
                                        }
                                    }

                                    val name = if (startLine != -1 && endLine != -1) {
                                        if (startLine == endLine) "@$fileName (line $startLine)" else "@$fileName (lines $startLine-$endLine)"
                                    } else {
                                        "@$fileName"
                                    }
                                    
                                    val file = com.example.ui.AttachedFile(
                                        uri = android.net.Uri.EMPTY,
                                        name = name,
                                        mimeType = "text/html",
                                        isImage = false,
                                        contentAsText = "User clicked on this element ($identifier) in the preview:\n```html\n$outerHTML\n```\nModify this part as requested."
                                    )
                                    onAddAttachedFile(file)
                                    onTabSelected(WorkspaceTab.CHAT)
                                },
                                webPreviewRefreshTrigger = webPreviewRefreshTrigger
                            )
                        }
                        WorkspaceTab.TERMINAL -> {
                            TerminalTabContent(
                                terminalOutput = terminalOutput,
                                onSendCommand = onTerminalCommand,
                                onClear = onClearTerminal
                            )
                        }
                        WorkspaceTab.ANDROID_BUILD -> {
                            AndroidBuildTabContent(
                                project = project,
                                githubRepo = githubRepo,
                                githubToken = githubToken,
                                githubBranch = githubBranch,
                                gitHubWorkflows = gitHubWorkflows,
                                onTriggerWorkflows = onTriggerWorkflows,
                                buildStatus = buildStatus,
                                buildSteps = buildSteps,
                                buildLogs = buildLogs,
                                buildError = buildError,
                                isPollingBuild = isPollingBuild,
                                gitProgress = gitProgress,
                                apkDownloadProgress = apkDownloadProgress,
                                apkDownloadPercentage = apkDownloadPercentage,
                                webArtifactInfo = webArtifactInfo,
                                onInstallApk = onInstallApk,
                                onPreviewWebArtifact = onPreviewWebArtifact,
                                onFetchLatestArtifact = onFetchLatestArtifact,
                                onSaveRepo = onSaveGithubRepo,
                                onSaveToken = onSaveGithubToken,
                                onSaveBranch = onSaveGithubBranch,
                                onTriggerBuild = {
                                    onPushGitRepo(githubRepo, githubToken, githubBranch, true) { _ -> }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRestoreDialog) {
        RestoreDialog(
            projectName = project.name,
            backups = backupsList,
            onDismiss = { showRestoreDialog = false },
            onRestoreConfirmed = { backup ->
                showRestoreDialog = false
                onRestoreBackup(backup)
            }
        )
    }

    if (isImportingFiles) {
        Dialog(onDismissRequest = {}) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E2E),
                border = BorderStroke(1.dp, Color(0xFF313244))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color(0xFFEC4899),
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Importing Files",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { importProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFFEC4899),
                        trackColor = Color(0xFF313244),
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (importProgressMessage.isNotBlank()) importProgressMessage else "Processing imported files...",
                        color = Color(0xFFCDD6F4),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    if (showCreateFileDialog) {
        CreateFileDialog(
            onDismiss = { showCreateFileDialog = false },
            onCreate = { path ->
                onCreateFile(path)
                showCreateFileDialog = false
            }
        )
    }

    if (showSettingsDialog) {
        CustomSettingsDialog(
            provider = customProvider,
            apiKey = customApiKey,
            baseUrl = customBaseUrl,
            modelId = customModelId,
            useCustom = useCustomModel,
            customModels = customModels,
            selectedModelId = selectedModelId,
            geminiModels = geminiModels,
            openaiModels = openaiModels,
            claudeModels = claudeModels,
            mistralModels = mistralModels,
            scannedModels = scannedModels,
            isScanningModels = isScanningModels,
            scanError = scanError,
            onScanModels = onScanModels,
            onClearScannedModels = onClearScannedModels,
            maxActionSteps = maxActionSteps,
            allowBuildPush = allowBuildPush,
            allowAutoFix = allowAutoFix,
            allowBackgroundExecution = allowBackgroundExecution,
            onSaveAllowBuildPush = onSaveAllowBuildPush,
            onSaveAllowAutoFix = onSaveAllowAutoFix,
            onSaveAllowBackgroundExecution = onSaveAllowBackgroundExecution,
            onSaveMaxActionSteps = onSaveMaxActionSteps,
            onAddCustomModel = onAddCustomModel,
            onDeleteCustomModel = onDeleteCustomModel,
            onSelectCustomModel = onSelectCustomModel,
            onOpenAgentSkills = {
                showSettingsDialog = false
                showAgentSkillsDialog = true
            },
            onDismiss = { showSettingsDialog = false },
            onSave = { p, k, b, m, uc ->
                onSaveSettings(p, k, b, m, uc)
                showSettingsDialog = false
            }
        )
    }

    if (showAgentSkillsDialog) {
        AgentSkillsDialog(
            skills = agentSkills,
            onToggleSkill = onToggleAgentSkill,
            onInstallSkill = onInstallAgentSkill,
            onUninstallSkill = onUninstallAgentSkill,
            onAddCustomSkill = onAddCustomAgentSkill,
            onFetchOnlineSkills = onFetchOnlineAgentSkills,
            isFetchingSkills = isFetchingSkills,
            onUpdateSkillContent = onUpdateSkillContent,
            onFetchSkillFileContent = onFetchSkillFileContent,
            onDismiss = { showAgentSkillsDialog = false }
        )
    }

    if (showMcpDialog) {
        McpManagementDialog(
            workspaceId = project.name,
            workspaceName = project.name,
            servers = mcpServers,
            onAddServer = onAddMcpServer,
            onToggleWorkspace = onToggleMcpWorkspace,
            onTestConnect = onTestConnectMcp,
            onStartOAuthFlow = onStartMcpOAuthFlow,
            onDeleteServer = onDeleteMcpServer,
            onDismiss = { showMcpDialog = false }
        )
    }

    if (showPushDialog) {
        PushProjectDialog(
            gitProgress = gitProgress,
            initialToken = explorerGithubToken.ifBlank { githubToken },
            initialRepo = explorerGithubRepo,
            initialBranch = explorerGithubBranch,
            onDismiss = { showPushDialog = false },
            onSaveToken = onSaveExplorerGithubToken,
            onSaveRepo = onSaveExplorerGithubRepo,
            onSaveBranch = onSaveExplorerGithubBranch,
            onPush = { repo, token, branch, force ->
                onPushGitRepo(repo, token, branch, force) { result ->
                    if (result.isSuccess) {
                        showPushDialog = false
                    }
                }
            }
        )
    }

    if (showSearchDialog) {
        GlobalSearchDialog(
            files = files,
            onSelectFile = onSelectFile,
            onTabSelected = onTabSelected,
            onDismiss = { showSearchDialog = false }
        )
    }

    if (showGithubPushPrompt) {
        AlertDialog(
            onDismissRequest = onDismissGithubPushPrompt,
            containerColor = Color(0xFF1E2130),
            title = {
                Text(
                    text = "Build to Github? \uD83D\uDE80",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "AI detected a $detectedFramework project. Would you like to force push the changes to Github so it can build?",
                    color = Color(0xFFC0C5CE)
                )
            },
            confirmButton = {
                Button(
                    onClick = onAcceptGithubPushPrompt,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                ) {
                    Text("Allow & Push", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissGithubPushPrompt) {
                    Text("Deny", color = Color(0xFF80809B))
                }
            }
        )
    }

    if (isLoadingWorkspace) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08080C))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = Color(0xFF38BDF8),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Loading Workspace Content...",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Verifying integrity and setting up files",
                    color = Color(0xFF80809B),
                    fontSize = 12.sp
                )
            }
        }
    }
}
}

@Composable
fun WorkspaceBottomNavigation(
    currentTab: WorkspaceTab,
    onTabSelected: (WorkspaceTab) -> Unit
) {
    Surface(
        color = Color(0xFF0D1117),
        border = BorderStroke(1.dp, Color(0xFF21262D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationBar(
            containerColor = Color(0xFF0D1117),
            tonalElevation = 0.dp,
            modifier = Modifier.height(64.dp)
        ) {
            NavigationBarItem(
                selected = currentTab == WorkspaceTab.CHAT,
                onClick = { onTabSelected(WorkspaceTab.CHAT) },
                icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Agent Chat", modifier = Modifier.size(20.dp)) },
                label = { Text("Agent", fontWeight = FontWeight.Medium, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF58A6FF),
                    selectedTextColor = Color(0xFF58A6FF),
                    unselectedIconColor = Color(0xFF8B949E),
                    unselectedTextColor = Color(0xFF8B949E),
                    indicatorColor = Color(0xFF161B22)
                )
            )
            NavigationBarItem(
                selected = currentTab == WorkspaceTab.CODE,
                onClick = { onTabSelected(WorkspaceTab.CODE) },
                icon = { Icon(Icons.Default.Code, contentDescription = "Editor", modifier = Modifier.size(20.dp)) },
                label = { Text("Editor", fontWeight = FontWeight.Medium, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF58A6FF),
                    selectedTextColor = Color(0xFF58A6FF),
                    unselectedIconColor = Color(0xFF8B949E),
                    unselectedTextColor = Color(0xFF8B949E),
                    indicatorColor = Color(0xFF161B22)
                )
            )
            NavigationBarItem(
                selected = currentTab == WorkspaceTab.PREVIEW,
                onClick = { onTabSelected(WorkspaceTab.PREVIEW) },
                icon = { Icon(Icons.Default.Language, contentDescription = "Preview", modifier = Modifier.size(20.dp)) },
                label = { Text("Preview", fontWeight = FontWeight.Medium, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF3FB950),
                    selectedTextColor = Color(0xFF3FB950),
                    unselectedIconColor = Color(0xFF8B949E),
                    unselectedTextColor = Color(0xFF8B949E),
                    indicatorColor = Color(0xFF161B22)
                )
            )
            NavigationBarItem(
                selected = currentTab == WorkspaceTab.TERMINAL,
                onClick = { onTabSelected(WorkspaceTab.TERMINAL) },
                icon = { Icon(Icons.Default.Terminal, contentDescription = "Terminal", modifier = Modifier.size(20.dp)) },
                label = { Text("Terminal", fontWeight = FontWeight.Medium, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFD29922),
                    selectedTextColor = Color(0xFFD29922),
                    unselectedIconColor = Color(0xFF8B949E),
                    unselectedTextColor = Color(0xFF8B949E),
                    indicatorColor = Color(0xFF161B22)
                )
            )
            NavigationBarItem(
                selected = currentTab == WorkspaceTab.ANDROID_BUILD,
                onClick = { onTabSelected(WorkspaceTab.ANDROID_BUILD) },
                icon = { Icon(Icons.Default.Build, contentDescription = "Android Build", modifier = Modifier.size(20.dp)) },
                label = { Text("Build", fontWeight = FontWeight.Medium, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFA371F7),
                    selectedTextColor = Color(0xFFA371F7),
                    unselectedIconColor = Color(0xFF8B949E),
                    unselectedTextColor = Color(0xFF8B949E),
                    indicatorColor = Color(0xFF161B22)
                )
            )
        }
    }
}

@Composable
fun ChatTabContent(
    messages: List<ChatMessageEntity>,
    isThinking: Boolean,
    agentStatus: String,
    aiActionLogs: List<AiActionLog>,
    onSendPrompt: (String, List<com.example.ui.AttachedFile>) -> Unit,
    onStopAI: () -> Unit,
    onDeleteMessage: (ChatMessageEntity) -> Unit,
    onEditMessage: (ChatMessageEntity, String) -> Unit,
    onRegenerate: (ChatMessageEntity) -> Unit,
    isInterrupted: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    files: List<ProjectFileEntity>,
    onViewFileInEditor: (String) -> Unit,
    detectedWebErrors: List<WebConsoleError>,
    onToggleError: (String) -> Unit,
    onToggleAllErrors: (Boolean) -> Unit,
    onClearErrors: () -> Unit,
    onFixErrors: () -> Unit,
    detectedAndroidBuildErrors: List<AndroidBuildError> = emptyList(),
    onToggleAndroidBuildError: (String) -> Unit = {},
    onToggleAllAndroidBuildErrors: (Boolean) -> Unit = {},
    onClearAndroidBuildErrors: () -> Unit = {},
    onFixAndroidBuildErrors: () -> Unit = {},
    allowAutoFix: Boolean = false,
    todoList: List<TodoItem> = emptyList(),
    isTodoListExpanded: Boolean = true,
    onToggleTodoListExpanded: () -> Unit = {},
    chatInputText: String = "",
    onUpdateChatInputText: (String) -> Unit = {},
    attachedFiles: List<com.example.ui.AttachedFile> = emptyList(),
    onAddAttachedFile: (com.example.ui.AttachedFile) -> Unit = {},
    onRemoveAttachedFile: (com.example.ui.AttachedFile) -> Unit = {},
    onClearAttachedFiles: () -> Unit = {},
    agentSkills: List<com.example.ui.AgentSkill> = emptyList(),
    onOpenSettings: () -> Unit = {},
    executionElapsedTimeSeconds: Long = 0L,
    currentRunningModelName: String = "",
    liveSystemTokens: Int = 0,
    liveUserTokens: Int = 0,
    liveToolTokens: Int = 0,
    liveTotalInputTokens: Int = 0,
    liveTotalOutputTokens: Int = 0
) {
    var taggedFiles by remember { mutableStateOf<List<ProjectFileEntity>>(emptyList()) }
    var taggedSkills by remember { mutableStateOf<List<com.example.ui.AgentSkill>>(emptyList()) }
    var showFileSuggestions by remember { mutableStateOf(false) }
    var showSkillSuggestions by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val filteredFiles = remember(chatInputText, files) {
        if (chatInputText.length > 500) emptyList()
        else {
            val lastAt = chatInputText.lastIndexOf('@')
            val lastSpace = maxOf(chatInputText.lastIndexOf(' '), chatInputText.lastIndexOf('\n'))
            if (lastAt != -1 && lastAt >= lastSpace && (lastAt + 1) <= chatInputText.length) {
                val query = chatInputText.substring(lastAt + 1)
                if (query.length < 50) {
                    files.filter { it.path.contains(query, ignoreCase = true) }.take(20)
                } else emptyList()
            } else emptyList()
        }
    }

    val filteredSkills = remember(chatInputText, agentSkills) {
        if (chatInputText.length > 500) emptyList()
        else {
            val lastSlash = chatInputText.lastIndexOf('/')
            val lastSpace = maxOf(chatInputText.lastIndexOf(' '), chatInputText.lastIndexOf('\n'))
            if (lastSlash != -1 && lastSlash >= lastSpace && (lastSlash + 1) <= chatInputText.length) {
                val query = chatInputText.substring(lastSlash + 1)
                if (query.length < 50) {
                    agentSkills.filter { skill ->
                        skill.isEnabled && (skill.name.contains(query, ignoreCase = true) || skill.id.contains(query, ignoreCase = true))
                    }.take(20)
                } else emptyList()
            } else emptyList()
        }
    }

    LaunchedEffect(chatInputText) {
        if (chatInputText.length > 500) {
            showFileSuggestions = false
            showSkillSuggestions = false
        } else {
            val lastAt = chatInputText.lastIndexOf('@')
            val lastSlash = chatInputText.lastIndexOf('/')
            val lastSpace = maxOf(chatInputText.lastIndexOf(' '), chatInputText.lastIndexOf('\n'))
            showFileSuggestions = lastAt != -1 && lastAt >= lastSpace && (lastAt + 1 <= chatInputText.length) && (chatInputText.substring(lastAt + 1).length < 50) && filteredFiles.isNotEmpty()
            showSkillSuggestions = lastSlash != -1 && lastSlash >= lastSpace && (lastSlash + 1 <= chatInputText.length) && (chatInputText.substring(lastSlash + 1).length < 50) && filteredSkills.isNotEmpty()
        }
    }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val newAttachments = uris.mapNotNull { uri ->
                val type = context.contentResolver.getType(uri) ?: ""
                val isImage = type.startsWith("image/")
                var name = "Unknown_File"
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex != -1) {
                            name = cursor.getString(nameIndex) ?: "Unknown_File"
                        }
                    }
                } catch (e: Exception) { }
                
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        if (isImage && bytes.size <= 5_000_000) {
                            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                            com.example.ui.AttachedFile(uri, name, type, true, contentAsBase64 = base64)
                        } else if (!isImage && bytes.size <= 2_000_000) {
                            val text = String(bytes, Charsets.UTF_8)
                            com.example.ui.AttachedFile(uri, name, type, false, contentAsText = text)
                        } else null
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                newAttachments.forEach { onAddAttachedFile(it) }
            }
        }
    }

    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty() && !isThinking) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Logo",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = "DEVELOPED BY MUSTASIM FUYAD",
                        color = Color(0xFFECEFF4),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Hello! I am your AI coding assistant.\nDescribe the app you want to build, and I\nwill generate the code for you.",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(messages) { index, message ->
                    var nextModelName: String? = null
                    var nextDuration: String? = null
                    if (message.role == "user") {
                        val nextMsg = messages.getOrNull(index + 1)
                        if (nextMsg != null && nextMsg.role == "assistant") {
                            val nextLogs = if (!nextMsg.aiActionLogsJson.isNullOrBlank()) {
                                try {
                                    val moshi = com.squareup.moshi.Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                                    val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, AiActionLog::class.java)
                                    moshi.adapter<List<AiActionLog>>(listType).fromJson(nextMsg.aiActionLogsJson) ?: emptyList()
                                } catch (e: Exception) {
                                    emptyList<AiActionLog>()
                                }
                            } else {
                                emptyList<AiActionLog>()
                            }
                            if (nextLogs.isNotEmpty()) {
                                nextModelName = nextLogs.firstOrNull { !it.modelName.isNullOrBlank() }?.modelName ?: "Gemini 1.5 Flash"
                                val totalMs = nextLogs.sumOf { it.durationMillis ?: 0L }
                                nextDuration = if (totalMs > 0) {
                                    "${maxOf(1, totalMs / 1000)}s"
                                } else {
                                    val firstLog = nextLogs.firstOrNull()
                                    val lastLog = nextLogs.lastOrNull()
                                    if (firstLog != null && lastLog != null) {
                                        val diff = lastLog.timestamp - firstLog.startTime
                                        "${maxOf(1, diff / 1000)}s"
                                    } else ""
                                }
                            }
                        }
                    }

                    val isLatestUserAndThinking = isThinking && message.role == "user" && (index == messages.size - 1 || messages.subList(index + 1, messages.size).none { it.role == "user" })

                    ChatBubble(
                        message = message,
                        onDeleteMessage = onDeleteMessage,
                        onEditMessage = onEditMessage,
                        onRegenerate = onRegenerate,
                        onFileTagClick = onViewFileInEditor,
                        assistantModelName = nextModelName,
                        assistantDuration = nextDuration,
                        isCurrentlyActiveThinking = isLatestUserAndThinking,
                        currentRunningModelName = currentRunningModelName,
                        executionElapsedTimeSeconds = executionElapsedTimeSeconds,
                        liveSystemTokens = liveSystemTokens,
                        liveUserTokens = liveUserTokens,
                        liveToolTokens = liveToolTokens,
                        liveTotalInputTokens = liveTotalInputTokens,
                        liveTotalOutputTokens = liveTotalOutputTokens
                    )
                }

                if (isThinking || (aiActionLogs.isNotEmpty() && (messages.isEmpty() || messages.lastOrNull()?.role == "user"))) {
                    item {
                        WorkspaceOperationsTimeline(
                            displayLogs = aiActionLogs.filter { log ->
                                val title = log.title
                                val isFormulatingLogic = title.contains("formulating logic", ignoreCase = true)
                                val hasForbiddenThinkingKeywords = title.contains("thought process", ignoreCase = true) || 
                                                                   title.contains("thinking", ignoreCase = true) || 
                                                                   (title.contains("formulating", ignoreCase = true) && !isFormulatingLogic)
                                !title.contains("finished task execution", ignoreCase = true) && (isFormulatingLogic || !hasForbiddenThinkingKeywords)
                            },
                            isThinking = isThinking
                        )
                    }
                }
            }
        }

        if (messages.size <= 2 && !isThinking) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf(
                    "🎨 Make the style super modern dark glassmorphism",
                    "➕ Add dynamic statistics counters",
                    "⚡ Add slick entrance animations",
                    "🌙 Add modern ambient starry backdrop"
                )
                suggestions.forEach { suggestion ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E111A)),
                        border = BorderStroke(1.dp, Color(0xFF1F2437)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onUpdateChatInputText(suggestion) }
                    ) {
                        Text(
                            text = suggestion,
                            fontSize = 12.sp,
                            color = Color(0xFFECEFF4),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        Surface(
            color = Color(0xFF080A0E),
            border = BorderStroke(1.dp, Color(0xFF1F2437)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (detectedWebErrors.isNotEmpty() && !allowAutoFix) {
                    var isErrorsExpanded by remember(detectedWebErrors.size) { mutableStateOf(true) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161822))
                            .border(BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isErrorsExpanded) 10.dp else 0.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isErrorsExpanded = !isErrorsExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Preview Error Detected (${detectedWebErrors.size} টি এরর পাওয়া গেছে)",
                                    color = Color(0xFFF1F5F9),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isErrorsExpanded) {
                                    // Select All checkbox/switch
                                    val allSelected = detectedWebErrors.all { it.isSelected }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { onToggleAllErrors(!allSelected) }
                                    ) {
                                        Checkbox(
                                            checked = allSelected,
                                            onCheckedChange = { onToggleAllErrors(it) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Color(0xFF38BDF8),
                                                checkmarkColor = Color.Black
                                            ),
                                            modifier = Modifier.scale(0.8f)
                                        )
                                        Text(
                                            text = "Select All",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                
                                Icon(
                                    imageVector = if (isErrorsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isErrorsExpanded) "Collapse" else "Expand",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (isErrorsExpanded) {
                            // List of errors
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                detectedWebErrors.forEach { err ->
                                    val displayFile = err.cleanFilePath.ifBlank { err.sourceId.ifBlank { "index.html" } }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0F111A))
                                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                            .clickable { onToggleError(err.id) }
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = err.isSelected,
                                                onCheckedChange = { onToggleError(err.id) },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color(0xFF8B5CF6)
                                                ),
                                                modifier = Modifier.scale(0.8f)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = err.message,
                                                    color = Color(0xFFF1F5F9),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.InsertDriveFile,
                                                        contentDescription = "File",
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "$displayFile : Line ${err.lineNumber}",
                                                        color = Color(0xFF38BDF8),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }

                                        if (err.codeSnippet.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF05070D),
                                                border = BorderStroke(0.5.dp, Color(0xFF1E2638)),
                                                modifier = Modifier.fillMaxWidth().padding(start = 28.dp, top = 2.dp)
                                            ) {
                                                Text(
                                                    text = err.codeSnippet.trim(),
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 10.sp,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    modifier = Modifier.padding(6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Action Buttons: Allow / Deny
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val selectedCount = detectedWebErrors.count { it.isSelected }
                                Button(
                                    onClick = onFixErrors,
                                    enabled = selectedCount > 0,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF8B5CF6),
                                        disabledContainerColor = Color(0xFF2E3136)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1.5f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Build,
                                            contentDescription = "Fix",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White
                                        )
                                        Text(
                                            text = "Allow / Fix Selected ($selectedCount)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Button(
                                    onClick = onClearErrors,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                                        contentColor = Color(0xFFEF4444)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Cancel / Deny",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                if (detectedAndroidBuildErrors.isNotEmpty() && !allowAutoFix) {
                    var isBuildErrorsExpanded by remember(detectedAndroidBuildErrors.size) { mutableStateOf(true) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161822))
                            .border(BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isBuildErrorsExpanded) 10.dp else 0.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isBuildErrorsExpanded = !isBuildErrorsExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Android Build Error Detected (${detectedAndroidBuildErrors.size} টি এরর পাওয়া গেছে)",
                                    color = Color(0xFFF1F5F9),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isBuildErrorsExpanded) {
                                    // Select All checkbox/switch
                                    val allSelected = detectedAndroidBuildErrors.all { it.isSelected }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { onToggleAllAndroidBuildErrors(!allSelected) }
                                    ) {
                                        Checkbox(
                                            checked = allSelected,
                                            onCheckedChange = { onToggleAllAndroidBuildErrors(it) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Color(0xFF38BDF8),
                                                checkmarkColor = Color.Black
                                            ),
                                            modifier = Modifier.scale(0.8f)
                                        )
                                        Text(
                                            text = "Select All",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                
                                Icon(
                                    imageVector = if (isBuildErrorsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isBuildErrorsExpanded) "Collapse" else "Expand",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (isBuildErrorsExpanded) {
                            // List of errors
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                detectedAndroidBuildErrors.forEach { err ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF0F111A))
                                            .clickable { onToggleAndroidBuildError(err.id) }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = err.isSelected,
                                            onCheckedChange = { onToggleAndroidBuildError(err.id) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Color(0xFF8B5CF6)
                                            ),
                                            modifier = Modifier.scale(0.8f)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = err.message,
                                                color = Color(0xFFF1F5F9),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Step: ${err.stepName} | ${err.filePath}:${err.lineNumber}",
                                                color = Color(0xFF64748B),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Action Buttons: Allow / Deny
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val selectedCount = detectedAndroidBuildErrors.count { it.isSelected }
                                Button(
                                    onClick = onFixAndroidBuildErrors,
                                    enabled = selectedCount > 0,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF8B5CF6),
                                        disabledContainerColor = Color(0xFF2E3136)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1.5f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Build,
                                            contentDescription = "Fix",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White
                                        )
                                        Text(
                                            text = "Allow / Fix Selected ($selectedCount)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Button(
                                    onClick = onClearAndroidBuildErrors,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                                        contentColor = Color(0xFFEF4444)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Cancel / Deny",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                if (messages.isNotEmpty() && !isThinking && isInterrupted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onContinue,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Continue (কাজ চালিয়ে যান)", fontSize = 11.sp)
                            }
                        }
                        OutlinedButton(
                            onClick = onSkip,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Skip", fontSize = 11.sp)
                        }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (todoList.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161822)),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleTodoListExpanded() }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.List,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "AI Task Progress (Todo List)",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val completedCount = todoList.count { it.isCompleted }
                                        Text(
                                            text = "$completedCount/${todoList.size}",
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Icon(
                                            imageVector = if (isTodoListExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isTodoListExpanded) "Collapse" else "Expand",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                if (isTodoListExpanded) {
                                    HorizontalDivider(color = Color(0xFF222533))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        todoList.forEachIndexed { index, item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (item.isCompleted) Color(0xFF0F111A).copy(alpha = 0.5f)
                                                        else Color(0xFF0F111A)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = if (item.isCompleted) "Completed" else "Pending",
                                                    tint = if (item.isCompleted) Color(0xFF10B981) else Color(0xFF64748B),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "${index + 1}. ${item.task}",
                                                    color = if (item.isCompleted) Color(0xFF64748B) else Color(0xFFF1F5F9),
                                                    fontSize = 12.sp,
                                                    style = if (item.isCompleted) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Workspace Context Included",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Prompt: ${chatInputText.length / 4} tokens",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }

                    if (showFileSuggestions && filteredFiles.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .padding(horizontal = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            LazyColumn(modifier = Modifier.padding(8.dp)) {
                                items(filteredFiles) { file ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val lastAt = chatInputText.lastIndexOf('@')
                                                onUpdateChatInputText(chatInputText.substring(0, lastAt) + "@${file.path} ")
                                                if (!taggedFiles.any { it.path == file.path }) {
                                                    taggedFiles = taggedFiles + file
                                                }
                                                showFileSuggestions = false
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                        Text(file.path, color = Color.White, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (showSkillSuggestions && filteredSkills.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .padding(horizontal = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                            border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            LazyColumn(modifier = Modifier.padding(8.dp)) {
                                items(filteredSkills) { skill ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val lastSlash = chatInputText.lastIndexOf('/')
                                                onUpdateChatInputText(chatInputText.substring(0, lastSlash) + "/${skill.id} ")
                                                if (!taggedSkills.any { it.id == skill.id }) {
                                                    taggedSkills = taggedSkills + skill
                                                }
                                                showSkillSuggestions = false
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(18.dp))
                                        Column {
                                            Text("/${skill.name}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text(skill.description, color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (attachedFiles.isNotEmpty() || taggedFiles.isNotEmpty() || taggedSkills.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            attachedFiles.forEach { file ->
                                AssistChip(
                                    onClick = { },
                                    label = { Text(file.name, color = Color.White) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { onRemoveAttachedFile(file) },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White)
                                        }
                                    },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E2130)),
                                    border = BorderStroke(1.dp, Color(0xFF222533))
                                )
                            }
                            taggedFiles.forEach { file ->
                                AssistChip(
                                    onClick = { },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.Tag, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                            Text(file.path, color = Color.White)
                                        }
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { taggedFiles = taggedFiles.filter { it != file } },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White)
                                        }
                                    },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E2130)),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                                )
                            }
                            taggedSkills.forEach { skill ->
                                AssistChip(
                                    onClick = { },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(14.dp))
                                            Text("/${skill.name}", color = Color(0xFFA855F7))
                                        }
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { taggedSkills = taggedSkills.filter { it.id != skill.id } },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White)
                                        }
                                    },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E2130)),
                                    border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.5f))
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = chatInputText,
                        onValueChange = { onUpdateChatInputText(it) },
                        placeholder = { Text("Describe your request (@ files, / skills)...", color = Color(0xFF8D96A0), fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            focusedBorderColor = Color(0xFF2F81F7),
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedContainerColor = Color(0xFF161B22),
                            unfocusedContainerColor = Color(0xFF161B22)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 5,
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                IconButton(onClick = { onOpenSettings() }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF8D96A0), modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { filePickerLauncher.launch("*/*") }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = Color(0xFF8D96A0), modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        if (isThinking) {
                                            onStopAI()
                                        } else if (chatInputText.isNotBlank() || attachedFiles.isNotEmpty() || taggedFiles.isNotEmpty() || taggedSkills.isNotEmpty()) {
                                            val fileAttachments = taggedFiles.map { file ->
                                                com.example.ui.AttachedFile(
                                                    uri = android.net.Uri.EMPTY,
                                                    name = file.path,
                                                    mimeType = "text/plain",
                                                    isImage = false,
                                                    contentAsText = "File Context (@${file.path}):\n${file.content}"
                                                )
                                            }
                                            val skillAttachments = taggedSkills.map { skill ->
                                                com.example.ui.AttachedFile(
                                                    uri = android.net.Uri.EMPTY,
                                                    name = "Skill: ${skill.name}",
                                                    mimeType = "text/plain",
                                                    isImage = false,
                                                    contentAsText = "[ACTIVE SKILL CONTEXT: ${skill.name}]\nDescription: ${skill.description}\nInstructions:\n${skill.skillPrompt}"
                                                )
                                            }
                                            val combinedAttachments = attachedFiles + fileAttachments + skillAttachments
                                            onSendPrompt(chatInputText, combinedAttachments)
                                            onUpdateChatInputText("")
                                            onClearAttachedFiles()
                                            taggedFiles = emptyList()
                                            taggedSkills = emptyList()
                                        }
                                    },
                                    enabled = (chatInputText.isNotBlank() || attachedFiles.isNotEmpty() || taggedFiles.isNotEmpty() || taggedSkills.isNotEmpty()) || isThinking,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            if ((chatInputText.isNotBlank() || attachedFiles.isNotEmpty()) || isThinking) Color(0xFF238636)
                                            else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                ) {
                                    if (isThinking) {
                                        Box(contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                            Icon(
                                                Icons.Default.Stop,
                                                contentDescription = "Stop",
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = if (chatInputText.isNotBlank() || attachedFiles.isNotEmpty()) Color.White else Color(0xFF6E7681),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FormattedMarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val lines = text.split("\n")
    androidx.compose.foundation.text.selection.SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (trimmed.startsWith("###")) {
                    val headerText = trimmed.substring(3).trim()
                    Text(
                        text = parseInlineMarkdown(headerText),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF3F4F6),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        lineHeight = 22.sp
                    )
                } else if (trimmed.startsWith("##")) {
                    val headerText = trimmed.substring(2).trim()
                    Text(
                        text = parseInlineMarkdown(headerText),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF3F4F6),
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                        lineHeight = 24.sp
                    )
                } else if (trimmed.startsWith("#")) {
                    val headerText = trimmed.substring(1).trim()
                    Text(
                        text = parseInlineMarkdown(headerText),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                        lineHeight = 26.sp
                    )
                } else if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                    val bulletText = trimmed.substring(1).trim()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "•",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6)
                        )
                        Text(
                            text = parseInlineMarkdown(bulletText),
                            fontSize = 13.sp,
                            color = Color(0xFFE5E7EB),
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Text(
                        text = parseInlineMarkdown(trimmed),
                        fontSize = 13.sp,
                        color = Color(0xFFE5E7EB),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

fun parseInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val nextBold = text.indexOf("**", index)
            val nextCode = text.indexOf("`", index)

            if (nextBold == -1 && nextCode == -1) {
                append(text.substring(index))
                break
            }

            if (nextBold != -1 && (nextCode == -1 || nextBold < nextCode)) {
                if (nextBold > index) {
                    append(text.substring(index, nextBold))
                }
                val endBold = text.indexOf("**", nextBold + 2)
                if (endBold != -1) {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color.White))
                    val boldContent = text.substring(nextBold + 2, endBold)
                    append(boldContent)
                    pop()
                    index = endBold + 2
                } else {
                    append("**")
                    index = nextBold + 2
                }
            } else {
                if (nextCode > index) {
                    append(text.substring(index, nextCode))
                }
                val endCode = text.indexOf("`", nextCode + 1)
                if (endCode != -1) {
                    pushStyle(
                        androidx.compose.ui.text.SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF38BDF8),
                            background = Color(0xFF1E293B)
                        )
                    )
                    append(text.substring(nextCode + 1, endCode))
                    pop()
                    index = endCode + 1
                } else {
                    append("`")
                    index = nextCode + 1
                }
            }
        }
    }
}

data class MentionChipItem(
    val label: String,
    val isSkill: Boolean = false,
    val isImage: Boolean = false
)

fun parseUserMessageDisplay(fullContent: String): Pair<String, List<MentionChipItem>> {
    val attachedIndex = fullContent.indexOf("\n\n[Attached File:")
    val imageIndex = fullContent.indexOf("\n\n[IMAGE_BASE64:")
    
    val cutoffIndex = when {
        attachedIndex != -1 && imageIndex != -1 -> minOf(attachedIndex, imageIndex)
        attachedIndex != -1 -> attachedIndex
        imageIndex != -1 -> imageIndex
        else -> -1
    }
    
    val rawText = if (cutoffIndex != -1) fullContent.substring(0, cutoffIndex).trim() else fullContent.trim()
    val attachmentsPart = if (cutoffIndex != -1) fullContent.substring(cutoffIndex) else ""
    
    val chipItems = mutableListOf<MentionChipItem>()
    
    val fileMatches = Regex("""\[Attached File:\s*([^\]]+)\]""").findAll(attachmentsPart)
    for (match in fileMatches) {
        val name = match.groupValues[1].trim()
        if (name.startsWith("Skill:", ignoreCase = true)) {
            val skillName = name.substringAfter("Skill:").trim()
            chipItems.add(MentionChipItem(label = if (skillName.startsWith("/")) skillName else "/$skillName", isSkill = true))
        } else {
            val fileName = if (name.startsWith("@")) name else "@$name"
            chipItems.add(MentionChipItem(label = fileName, isSkill = false))
        }
    }
    
    if (attachmentsPart.contains("[IMAGE_BASE64:")) {
        chipItems.add(MentionChipItem(label = "Image Attachment", isImage = true))
    }
    
    val tagRegex = Regex("""(@[a-zA-Z0-9_\-\./]+|/[a-zA-Z0-9_\-]+)""")
    val textMatches = tagRegex.findAll(rawText)
    for (match in textMatches) {
        val tag = match.groupValues[1]
        val isSkillTag = tag.startsWith("/")
        val normalizedLabel = if (isSkillTag) tag else (if (tag.startsWith("@")) tag else "@$tag")
        if (!chipItems.any { it.label.equals(normalizedLabel, ignoreCase = true) }) {
            chipItems.add(MentionChipItem(label = normalizedLabel, isSkill = isSkillTag))
        }
    }
    
    var cleanText = rawText
    for (chip in chipItems) {
        if (cleanText.startsWith(chip.label, ignoreCase = true)) {
            cleanText = cleanText.substring(chip.label.length).trim()
        }
    }
    if (cleanText.isBlank() && rawText.isNotBlank()) {
        cleanText = rawText
    }
    
    return Pair(cleanText, chipItems.distinctBy { it.label })
}

@Composable
fun MentionChipBadge(chip: MentionChipItem) {
    val chipBg = when {
        chip.isSkill -> Color(0xFF2D1F47)
        chip.isImage -> Color(0xFF1B382B)
        else -> Color(0xFF1E293B)
    }
    val chipBorder = when {
        chip.isSkill -> Color(0xFFA855F7).copy(alpha = 0.5f)
        chip.isImage -> Color(0xFF4ADE80).copy(alpha = 0.5f)
        else -> Color(0xFF38BDF8).copy(alpha = 0.5f)
    }
    val iconTint = when {
        chip.isSkill -> Color(0xFFA855F7)
        chip.isImage -> Color(0xFF4ADE80)
        else -> Color(0xFF38BDF8)
    }
    val iconVector = when {
        chip.isSkill -> Icons.Default.Psychology
        chip.isImage -> Icons.Default.Image
        else -> Icons.Default.Code
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = chipBg,
        border = BorderStroke(1.dp, chipBorder),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = chip.label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessageEntity,
    onDeleteMessage: (ChatMessageEntity) -> Unit,
    onEditMessage: (ChatMessageEntity, String) -> Unit,
    onRegenerate: (ChatMessageEntity) -> Unit,
    onFileTagClick: (String) -> Unit,
    assistantModelName: String? = null,
    assistantDuration: String? = null,
    isCurrentlyActiveThinking: Boolean = false,
    currentRunningModelName: String = "",
    executionElapsedTimeSeconds: Long = 0L,
    liveSystemTokens: Int = 0,
    liveUserTokens: Int = 0,
    liveToolTokens: Int = 0,
    liveTotalInputTokens: Int = 0,
    liveTotalOutputTokens: Int = 0
) {
    val isUser = message.role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start
    val bg = if (isUser) Color(0xFF21262D) else Color(0xFF161B22)
    val border = if (isUser) Color(0xFF30363D) else Color(0xFF21262D)
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val (userDisplayText, mentionChips) = remember(message.content) {
        if (isUser) parseUserMessageDisplay(message.content) else Pair(message.content, emptyList())
    }

    var isEditing by remember { mutableStateOf(false) }
    var editedContent by remember { mutableStateOf(if (isUser) userDisplayText else message.content) }

    val logs = remember(message.aiActionLogsJson) {
        if (!message.aiActionLogsJson.isNullOrBlank()) {
            try {
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val listType = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
                moshi.adapter<List<AiActionLog>>(listType).fromJson(message.aiActionLogsJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList<AiActionLog>()
            }
        } else {
            emptyList<AiActionLog>()
        }
    }

    androidx.compose.foundation.text.selection.SelectionContainer {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = align
        ) {
            if (!isUser && logs.isNotEmpty()) {
                val filteredLogs = logs.filter { log ->
                    val title = log.title
                    val isFormulatingLogic = title.contains("formulating logic", ignoreCase = true)
                    val hasForbiddenThinkingKeywords = title.contains("thought process", ignoreCase = true) || 
                                                       title.contains("thinking", ignoreCase = true) || 
                                                       (title.contains("formulating", ignoreCase = true) && !isFormulatingLogic)
                    !title.contains("finished task execution", ignoreCase = true) && (isFormulatingLogic || !hasForbiddenThinkingKeywords)
                }
                if (filteredLogs.isNotEmpty()) {
                    WorkspaceOperationsTimeline(
                        displayLogs = filteredLogs,
                        isThinking = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Card(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 6.dp,
                    bottomEnd = if (isUser) 6.dp else 18.dp
                ),
                colors = CardDefaults.cardColors(containerColor = bg),
                border = BorderStroke(1.dp, border),
                modifier = Modifier.widthIn(max = 600.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Background watermark: DEVELOPED BY MUSTASIM FUYAD
                    Text(
                        text = "DEVELOPED BY MUSTASIM FUYAD",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.03f),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .rotate(-15f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2
                    )

                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isEditing) {
                            OutlinedTextField(
                                value = editedContent,
                                onValueChange = { editedContent = it },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF8B5CF6)
                                ),
                                textStyle = TextStyle(fontSize = 14.sp, color = Color.White, fontFamily = FontFamily.Default)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { isEditing = false }) {
                                    Text("Cancel", color = Color.Gray, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        onEditMessage(message, editedContent)
                                        isEditing = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Save", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        } else {
                            if (isUser) {
                                if (mentionChips.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .padding(bottom = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        mentionChips.forEach { chip ->
                                            MentionChipBadge(chip = chip)
                                        }
                                    }
                                }
                                if (userDisplayText.isNotBlank()) {
                                    FormattedMarkdownText(text = userDisplayText)
                                }
                            } else {
                                FormattedMarkdownText(text = message.content)
                            }
                        }
                    }
                }
            }

            if (isUser) {
                if (isCurrentlyActiveThinking) {
                    val liveMetrics = com.example.data.TokenMetrics(
                        modelName = currentRunningModelName.ifBlank { "gemini-3.1-flash-lite" },
                        executionTimeSeconds = executionElapsedTimeSeconds,
                        isLive = true,
                        systemTokens = liveSystemTokens,
                        userTokens = liveUserTokens,
                        historyTokens = 420,
                        skillTokens = liveToolTokens,
                        totalInputTokens = liveTotalInputTokens,
                        totalOutputTokens = liveTotalOutputTokens
                    )
                    CollapsibleTokenMonitor(
                        metrics = liveMetrics,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    val displayModel = message.modelName ?: assistantModelName ?: "gemini-3.1-flash-lite"
                    val displaySecs = if (message.executionTimeSeconds > 0) message.executionTimeSeconds else (assistantDuration?.removeSuffix("s")?.toLongOrNull() ?: 0L)
                    val sysTok = if (message.systemTokens > 0) message.systemTokens else 2440
                    val usrTok = if (message.userTokens > 0) message.userTokens else maxOf(1, message.content.length / 4)
                    val toolTok = message.toolTokens
                    val histTok = if (message.historyTokens > 0) message.historyTokens else 320
                    val skillTok = if (message.skillTokens > 0) message.skillTokens else toolTok
                    val inTok = if (message.totalInputTokens > 0) message.totalInputTokens else (sysTok + usrTok + histTok + skillTok)
                    val outTok = message.totalOutputTokens

                    val storedMetrics = com.example.data.TokenMetrics(
                        modelName = displayModel,
                        executionTimeSeconds = displaySecs,
                        isLive = false,
                        systemTokens = sysTok,
                        userTokens = usrTok,
                        historyTokens = histTok,
                        skillTokens = skillTok,
                        totalInputTokens = inTok,
                        totalOutputTokens = outTok
                    )
                    CollapsibleTokenMonitor(
                        metrics = storedMetrics,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Message Actions
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isUser && !isEditing) {
                    MessageActionButton(Icons.Default.Edit, "Edit") { isEditing = true }
                    MessageActionButton(Icons.Default.Refresh, "Regenerate") { onRegenerate(message) }
                }
                
                MessageActionButton(Icons.Default.ContentCopy, "Copy") {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(if (isUser) userDisplayText else message.content))
                }
                
                MessageActionButton(Icons.Default.Delete, "Delete", tint = Color(0xFFEE5253).copy(alpha = 0.7f)) {
                    onDeleteMessage(message)
                }
            }
        }
    }
}

@Composable
fun MessageActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color = Color.Gray,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(24.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
fun CodeTabContent(
    files: List<ProjectFileEntity>,
    activeFile: ProjectFileEntity?,
    editorContent: String,
    onSelectFile: (ProjectFileEntity) -> Unit,
    onUpdateEditor: (String) -> Unit,
    onSaveFile: () -> Unit,
    onDeleteFile: (String) -> Unit
) {
    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B22))
                .border(BorderStroke(1.dp, Color(0xFF30363D))),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tabs Row
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                files.forEach { file ->
                    val isActive = file.path == activeFile?.path
                    Row(
                        modifier = Modifier
                            .background(if (isActive) Color(0xFF0D1117) else Color.Transparent)
                            .clickable { onSelectFile(file) }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                            .let {
                                if (isActive) it.border(BorderStroke(1.dp, Color(0xFF30363D)), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)) else it
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = if (isActive) Color(0xFF58A6FF) else Color(0xFF8B949E),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = file.path.substringAfterLast("/"),
                            color = if (isActive) Color(0xFFE6EDF3) else Color(0xFF8B949E),
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                        )
                        if (file.path != "index.html") {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = if (isActive) Color(0xFF8B949E) else Color(0xFF484F58),
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { showDeleteConfirmDialog = file.path }
                            )
                        }
                    }
                }
            }
            
            // Actions Row
            val activeExtension = activeFile?.path?.substringAfterLast(".", "")?.lowercase() ?: ""
            val activeIsImageOr3D = activeExtension in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "ico", "obj", "gltf", "glb", "fbx", "3ds", "stl")

            if (!activeIsImageOr3D) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(editorContent))
                            android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF21262D),
                        border = BorderStroke(1.dp, Color(0xFF30363D))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color(0xFFC9D1D9),
                                modifier = Modifier.size(12.dp)
                            )
                            Text("Copy", color = Color(0xFFC9D1D9), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    
                    var showQuickEditDialog by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { showQuickEditDialog = true },
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF238636),
                        border = BorderStroke(1.dp, Color(0xFF2EA043))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text("Edit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (showQuickEditDialog) {
                        QuickEditDialog(
                            content = editorContent,
                            onDismiss = { showQuickEditDialog = false },
                            onApply = { updated ->
                                onUpdateEditor(updated)
                                showQuickEditDialog = false
                            }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Binary File",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Editor Area
        if (activeFile != null) {
            val extension = activeFile.path.substringAfterLast(".", "").lowercase()
            val isImage = extension in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "ico")
            val is3D = extension in setOf("obj", "gltf", "glb", "fbx", "3ds", "stl")

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isImage) {
                    val base64String = activeFile.content
                    val isBase64Image = base64String.startsWith("data:") && base64String.contains(";base64,")
                    val imageBitmap = remember(base64String) {
                        try {
                            val cleanBase64 = if (isBase64Image) base64String.substringAfter(";base64,") else base64String
                            val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF090C10))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                            border = BorderStroke(1.dp, Color(0xFF30363D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (imageBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = imageBitmap,
                                        contentDescription = "Preview of ${activeFile.path}",
                                        modifier = Modifier
                                            .fillMaxSize(0.85f)
                                            .padding(16.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BrokenImage,
                                            contentDescription = "Error",
                                            tint = Color(0xFFF85149),
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Unable to preview image",
                                            color = Color(0xFFF85149),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                        
                        Text(
                            text = activeFile.path.substringAfterLast("/"),
                            color = Color(0xFFE6EDF3),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val kbSize = activeFile.content.length * 3 / 4 / 1024.0
                        Text(
                            text = "Binary Image • format: ${extension.uppercase()} • approx. ${String.format("%.1f", kbSize)} KB",
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp
                        )
                    }
                } else if (is3D) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF090C10))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                            border = BorderStroke(1.dp, Color(0xFF30363D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(32.dp).fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ViewInAr,
                                    contentDescription = "3D Asset",
                                    tint = Color(0xFF58A6FF),
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "3D Asset File",
                                    color = Color(0xFFE6EDF3),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Editing is disabled for 3D Assets. You can manage this file (rename, delete, move) using tools.",
                                    color = Color(0xFF8B949E),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Text(
                            text = activeFile.path.substringAfterLast("/"),
                            color = Color(0xFFE6EDF3),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Format: ${extension.uppercase()}",
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        val lineCount = editorContent.count { it == '\n' } + 1
                        val lineNumbers = (1..lineCount).joinToString("\n")
                        val editorTextStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 16.dp, bottom = 16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = lineNumbers,
                                color = Color(0xFF6E7681),
                                style = editorTextStyle,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                textAlign = TextAlign.End
                            )
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = editorContent,
                                    onValueChange = onUpdateEditor,
                                    modifier = Modifier.fillMaxHeight(),
                                    textStyle = editorTextStyle.copy(color = Color(0xFFE6EDF3)),
                                    visualTransformation = com.example.ui.SyntaxHighlighter(),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White)
                                )
                            }
                        }
                    }
                    
                    // Save button
                    val isChanged = editorContent != activeFile.content
                    if (isChanged) {
                        FloatingActionButton(
                            onClick = onSaveFile,
                            containerColor = Color(0xFF238636),
                            contentColor = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save changes"
                            )
                        }
                    }
                }
            }
            
            // Status Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF161B22),
                border = BorderStroke(1.dp, Color(0xFF30363D))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lineCount = editorContent.count { it == '\n' } + 1
                    val kbSize = editorContent.toByteArray().size / 1024.0
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("UTF-8", color = Color(0xFF8B949E), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    Text("•", color = Color(0xFF484F58), fontSize = 10.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Lines: $lineCount", color = Color(0xFF8B949E), fontSize = 11.sp)
                    }
                    Text("•", color = Color(0xFF484F58), fontSize = 10.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${String.format("%.1f", kbSize)} KB", color = Color(0xFF8B949E), fontSize = 11.sp)
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF3FB950)))
                        Text("${editorContent.length / 4} tokens", color = Color(0xFF3FB950), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Select a file above or from sidebar to edit code.", color = Color(0xFF8B949E))
            }
        }
    }

    if (showDeleteConfirmDialog != null) {
        val isProtectedAndroidYml = showDeleteConfirmDialog?.lowercase()?.trim()?.let {
            it == "android.yml" || it.endsWith("/android.yml") || it.endsWith("\\android.yml")
        } ?: false
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text(if (isProtectedAndroidYml) "Protected File" else "Delete File") },
            text = { 
                Text(
                    if (isProtectedAndroidYml) "The 'android.yml' file is protected and cannot be deleted."
                    else "Are you sure you want to delete ${showDeleteConfirmDialog}?"
                ) 
            },
            confirmButton = {
                if (!isProtectedAndroidYml) {
                    TextButton(
                        onClick = {
                            onDeleteFile(showDeleteConfirmDialog!!)
                            showDeleteConfirmDialog = null
                        }
                    ) {
                        Text("Delete", color = Color(0xFFEE5253))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text(if (isProtectedAndroidYml) "OK" else "Cancel")
                }
            }
        )
    }
}

class WebAppInspectorInterface(private val onElementSelected: (String, String) -> Unit) {
    @android.webkit.JavascriptInterface
    fun onElementClicked(identifier: String, outerHTML: String) {
        onElementSelected(identifier, outerHTML)
    }
}

fun getInlinedHtml(files: List<ProjectFileEntity>): String {
    val htmlFile = files.find { it.path == "index.html" } ?: return ""
    var content = htmlFile.content

    val cssRegex = Regex("""<link\s+[^>]*rel=["']stylesheet["'][^>]*href=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
    content = cssRegex.replace(content) { matchResult ->
        val href = matchResult.groups[1]?.value ?: ""
        val cssFile = files.find { it.path.equals(href, ignoreCase = true) }
        if (cssFile != null) {
            "<style>\n${cssFile.content}\n</style>"
        } else {
            matchResult.value
        }
    }

    val jsRegex = Regex("""<script\s+[^>]*src=["']([^"']+)["'][^>]*>\s*</script>""", RegexOption.IGNORE_CASE)
    content = jsRegex.replace(content) { matchResult ->
        val src = matchResult.groups[1]?.value ?: ""
        val jsFile = files.find { it.path.equals(src, ignoreCase = true) }
        if (jsFile != null) {
            "<script>\n${jsFile.content}\n</script>"
        } else {
            matchResult.value
        }
    }

    return content
}

fun preprocessHtmlForBabel(html: String): String {
    var content = html

    // 1. Inject Babel setup to register 'react-classic' preset with classic runtime
    val babelCdnRegex = Regex("""(<script\s+[^>]*src=["'][^"']*babel\.min\.js["'][^>]*>\s*</script>)""", RegexOption.IGNORE_CASE)
    if (babelCdnRegex.containsMatchIn(content)) {
        content = babelCdnRegex.replace(content) { matchResult ->
            matchResult.value + "\n<script>\n" +
                    "if (window.Babel) {\n" +
                    "  Babel.registerPreset('react-classic', {\n" +
                    "    presets: [\n" +
                    "      [Babel.availablePresets['react'], { runtime: 'classic' }]\n" +
                    "    ]\n" +
                    "  });\n" +
                    "}\n" +
                    "</script>"
        }
    } else {
        val headRegex = Regex("""(<head>)""", RegexOption.IGNORE_CASE)
        if (headRegex.containsMatchIn(content)) {
            content = headRegex.replace(content) { matchResult ->
                matchResult.value + "\n<script>\n" +
                        "window.addEventListener('DOMContentLoaded', () => {\n" +
                        "  if (window.Babel) {\n" +
                        "    Babel.registerPreset('react-classic', {\n" +
                        "      presets: [\n" +
                        "        [Babel.availablePresets['react'], { runtime: 'classic' }]\n" +
                        "      ]\n" +
                        "    });\n" +
                        "  }\n" +
                        "});\n" +
                        "</script>"
            }
        }
    }

    // 2. Replace 'react' preset with 'react-classic' in data-presets attribute
    val dataPresetsRegex = Regex("""data-presets\s*=\s*["']([^"']*)\breact\b([^"']*)["']""", RegexOption.IGNORE_CASE)
    content = dataPresetsRegex.replace(content) { matchResult ->
        val before = matchResult.groups[1]?.value ?: ""
        val after = matchResult.groups[2]?.value ?: ""
        "data-presets=\"${before}react-classic${after}\""
    }

    // 3. Keep data-type="module" additions to avoid 'Cannot use import statement outside a module'
    val babelScriptRegex = Regex("""<script\s+type\s*=\s*["']text/babel["'](?![^>]*data-type\s*=)([^>]*)>""", RegexOption.IGNORE_CASE)
    content = babelScriptRegex.replace(content) { matchResult ->
        val attrs = matchResult.groups[1]?.value ?: ""
        "<script type=\"text/babel\" data-type=\"module\"$attrs>"
    }

    return content
}

fun uploadToPasteEe(htmlContent: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    val client = okhttp3.OkHttpClient()
    val url = "https://uguu.se/api.php?d=upload-tool"

    val mediaType = "text/html; charset=utf-8".toMediaTypeOrNull()
    val fileBody = okhttp3.RequestBody.create(mediaType, htmlContent)
    val requestBody = okhttp3.MultipartBody.Builder()
        .setType(okhttp3.MultipartBody.FORM)
        .addFormDataPart("files[]", "index.html", fileBody)
        .build()

    val request = okhttp3.Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
        .post(requestBody)
        .build()

    client.newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
            mainHandler.post {
                onError(e.message ?: "Network error")
            }
        }

        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            response.use {
                if (!response.isSuccessful) {
                    mainHandler.post {
                        onError("HTTP Error: ${response.code}")
                    }
                    return
                }
                val bodyString = response.body?.string() ?: ""
                try {
                    val json = org.json.JSONObject(bodyString)
                    val success = json.optBoolean("success", false)
                    if (success) {
                        val filesArray = json.getJSONArray("files")
                        if (filesArray.length() > 0) {
                            val fileObj = filesArray.getJSONObject(0)
                            val rawUrl = fileObj.getString("url")
                            mainHandler.post {
                                onSuccess(rawUrl)
                            }
                        } else {
                            mainHandler.post {
                                onError("Upload returned empty files list")
                            }
                        }
                    } else {
                        mainHandler.post {
                            onError("Upload failed on server side")
                        }
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        onError("JSON parsing error: ${e.message}")
                    }
                }
            }
        }
    })
}

private class FilesHolder(var filesList: List<ProjectFileEntity> = emptyList())

@Composable
fun PreviewTabContent(
    files: List<ProjectFileEntity>,
    consoleLogs: List<VibeViewModel.WebConsoleLog>,
    onConsoleLog: (String, String, String, Int) -> Unit,
    onClearLogs: () -> Unit,
    onWebError: (message: String, sourceId: String, lineNumber: Int) -> Unit,
    onClearErrors: () -> Unit = {},
    onInspectorElementSelected: (identifier: String, html: String) -> Unit = { _, _ -> },
    webPreviewRefreshTrigger: Int = 0
) {
    val htmlFile = remember(files) { 
        files.find { it.path.equals("index.html", ignoreCase = true) || it.path.endsWith("/index.html", ignoreCase = true) } 
    }
    val filesHolder = remember { FilesHolder(files) }
    filesHolder.filesList = files

    val isReactViteFramework = remember(files) {
        com.example.api.LocalHttpServer.isReactViteProject(files)
    }

    val instructionHtml = remember {
        """
        <!DOCTYPE html>
        <html lang="bn">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Compilation Required</title>
            <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700&display=swap" rel="stylesheet">
            <style>
                body {
                    font-family: 'Plus Jakarta Sans', sans-serif;
                    background-color: #0d0e15;
                    color: #ffffff;
                    margin: 0;
                    padding: 24px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    min-height: 100vh;
                    box-sizing: border-box;
                    text-align: center;
                }
                .card {
                    background-color: #151726;
                    border: 1px solid #2b2f4a;
                    border-radius: 16px;
                    padding: 32px 24px;
                    max-width: 420px;
                    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
                }
                .icon {
                    font-size: 48px;
                    margin-bottom: 16px;
                }
                h1 {
                    font-size: 22px;
                    font-weight: 700;
                    margin: 0 0 12px 0;
                    background: linear-gradient(135deg, #a855f7, #ec4899);
                    -webkit-background-clip: text;
                    -webkit-text-fill-color: transparent;
                }
                p {
                    font-size: 14px;
                    color: #94a3b8;
                    line-height: 1.6;
                    margin: 0 0 20px 0;
                }
                .bn-text {
                    border-top: 1px solid #23273f;
                    padding-top: 16px;
                    margin-top: 16px;
                    color: #cbd5e1;
                    font-size: 13.5px;
                }
                .highlight {
                    color: #38bdf8;
                    font-weight: 600;
                }
            </style>
        </head>
        <body>
            <div class="card">
                <div class="icon">🚀</div>
                <h1>Compilation Required</h1>
                <p>This is a <span class="highlight">React + Vite</span> project. It needs to be built before it can run in the live preview.</p>
                <p>Go to the <span class="highlight">Build Tab</span> (Android Build Pipeline), configure your GitHub repository, and click <span class="highlight">Build</span> to compile and load the preview.</p>
                
                <div class="bn-text">
                    <p>👋 এটি একটি <span class="highlight">React + Vite</span> প্রজেক্ট। লাইভ প্রিভিউ দেখতে প্রথমে প্রজেক্টটি কম্পাইল করা প্রয়োজন।</p>
                    <p>দয়া করে <span class="highlight">Build Tab</span> এ গিয়ে আপনার GitHub Repository সেটআপ করুন এবং <span class="highlight">Build</span> বাটনে ক্লিক করুন।</p>
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    var refreshTrigger by remember { mutableStateOf(0) }
    val localWebDir by com.example.api.LocalHttpServer.webDistDirFlow.collectAsState()
    val hasWebDist = remember(localWebDir, refreshTrigger, isReactViteFramework) {
        val dir = localWebDir
        if (isReactViteFramework && !dir.isNullOrBlank()) {
            java.io.File(dir).exists()
        } else {
            false
        }
    }

    LaunchedEffect(files) {
        com.example.api.LocalHttpServer.updateFiles(files)
    }

    LaunchedEffect(Unit) {
        com.example.api.LocalHttpServer.start()
    }

    LaunchedEffect(webPreviewRefreshTrigger) {
        if (webPreviewRefreshTrigger > 0) {
            refreshTrigger++
            onClearLogs()
        }
    }
    var showLogs by remember { mutableStateOf(false) }
    var isInspectorModeActive by remember { mutableStateOf(false) }
    val currentOnElementSelected by rememberUpdatedState(onInspectorElementSelected)
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isGeneratingLivePreview by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    LaunchedEffect(isInspectorModeActive, webViewRef) {
        webViewRef?.evaluateJavascript("window.isInspectorModeActive = $isInspectorModeActive;", null)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = Color(0xFF161B22),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Browser URL/Title bar
                Surface(
                    color = Color(0xFF0D1117),
                    border = BorderStroke(1.dp, Color(0xFF30363D)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF3FB950)))
                        Text(
                            text = if (isReactViteFramework && hasWebDist) "http://localhost:5173 (dist)" else "http://localhost:8080/index.html",
                            color = Color(0xFF8B949E),
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { isInspectorModeActive = !isInspectorModeActive },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isInspectorModeActive) Color(0xFF1F6FEB) else Color(0xFF21262D),
                        border = BorderStroke(1.dp, if (isInspectorModeActive) Color(0xFF388BFD) else Color(0xFF30363D))
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Toggle Inspector",
                                tint = if (isInspectorModeActive) Color.White else Color(0xFFC9D1D9),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { showLogs = !showLogs },
                        shape = RoundedCornerShape(6.dp),
                        color = if (showLogs) Color(0xFF1F6FEB) else Color(0xFF21262D),
                        border = BorderStroke(1.dp, if (showLogs) Color(0xFF388BFD) else Color(0xFF30363D))
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (showLogs) Icons.Default.Terminal else Icons.Default.Code,
                                contentDescription = "Toggle Logs",
                                tint = if (showLogs) Color.White else Color(0xFFC9D1D9),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { 
                            refreshTrigger++
                            onClearLogs()
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF21262D),
                        border = BorderStroke(1.dp, Color(0xFF30363D))
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload Preview",
                                tint = Color(0xFFC9D1D9),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            if (htmlFile == null) {
                                android.widget.Toast.makeText(context, "No index.html found", android.widget.Toast.LENGTH_SHORT).show()
                                return@Surface
                            }
                            com.example.api.LocalHttpServer.start()
                            com.example.api.LocalHttpServer.updateFiles(files)

                            val localUrl = "http://127.0.0.1:8080/index.html"
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(localUrl)).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    uriHandler.openUri(localUrl)
                                } catch (e2: Exception) {
                                    android.widget.Toast.makeText(context, "Failed to open browser: ${e2.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF238636),
                        border = BorderStroke(1.dp, Color(0xFF2EA043))
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open in Browser",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
        ) {
            key(refreshTrigger) {
                if (!hasWebDist && isReactViteFramework) {
                    // Show a beautiful, native-looking compilation required card
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                }
                                loadDataWithBaseURL(
                                    "https://virtual-app/",
                                    instructionHtml,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        },
                        update = { /* no-op */ },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (htmlFile == null && !hasWebDist) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF08080C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No HTML file found. Create index.html to preview.", color = Color.Gray)
                    }
                } else {
                    var lastLoadedHtml by remember { mutableStateOf(htmlFile?.content ?: "") }
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                }
                                isFocusable = true
                                isFocusableInTouchMode = true
                                scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
                                
                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        if (consoleMessage != null) {
                                            val level = when (consoleMessage.messageLevel()) {
                                                ConsoleMessage.MessageLevel.ERROR -> "error"
                                                ConsoleMessage.MessageLevel.WARNING -> "warning"
                                                else -> "log"
                                            }
                                            onConsoleLog(
                                                consoleMessage.message() ?: "",
                                                level,
                                                consoleMessage.sourceId() ?: "",
                                                consoleMessage.lineNumber()
                                            )
                                            if (level == "error") {
                                                onWebError(
                                                    consoleMessage.message() ?: "",
                                                    consoleMessage.sourceId() ?: "",
                                                    consoleMessage.lineNumber()
                                                )
                                            }
                                        }
                                        return true
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        if (url == "https://virtual-app/" || url?.startsWith("https://virtual-app/") == true) {
                                            onClearErrors()
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        val js = """
                                            window.isInspectorModeActive = false;
                                            if (!window.inspectorInitialized) {
                                                window.inspectorInitialized = true;
                                                document.addEventListener('click', function(e) {
                                                    if (window.isInspectorModeActive) {
                                                        e.preventDefault();
                                                        e.stopPropagation();
                                                        var el = e.target;
                                                        var tag = el.tagName.toLowerCase();
                                                        var id = el.id ? '#' + el.id : '';
                                                        var className = el.className ? '.' + el.className.split(' ').join('.') : '';
                                                        var identifier = tag + id + className;
                                                        window.AndroidInspector.onElementClicked(identifier, el.outerHTML);
                                                    }
                                                }, true);
                                                document.addEventListener('mouseover', function(e) {
                                                    if (window.isInspectorModeActive) {
                                                        e.target.dataset.oldOutline = e.target.style.outline;
                                                        e.target.style.outline = '2px solid #38BDF8';
                                                        e.target.style.cursor = 'crosshair';
                                                    }
                                                }, true);
                                                document.addEventListener('mouseout', function(e) {
                                                    if (window.isInspectorModeActive) {
                                                        e.target.style.outline = e.target.dataset.oldOutline || '';
                                                        e.target.style.cursor = '';
                                                    }
                                                }, true);
                                            }
                                        """.trimIndent()
                                        view?.evaluateJavascript(js, null)
                                    }

                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val urlString = request?.url?.toString() ?: return null
                                        if (urlString.startsWith("https://virtual-app/")) {
                                            var path = urlString.removePrefix("https://virtual-app/")
                                            if (path.contains("?")) {
                                                path = path.substringBefore("?")
                                            }
                                            if (path.contains("#")) {
                                                path = path.substringBefore("#")
                                            }
                                            try {
                                                path = java.net.URLDecoder.decode(path, "UTF-8")
                                            } catch (e: Exception) {
                                                // ignore
                                            }
                                            val cleanPath = path.removePrefix("/")
                                            
                                            fun getMimeTypeAndEncoding(nameOrPath: String): Pair<String, String?> {
                                                val clean = nameOrPath.lowercase()
                                                val mime = when {
                                                    clean.endsWith(".html") || clean.endsWith(".htm") -> "text/html"
                                                    clean.endsWith(".css") -> "text/css"
                                                    clean.endsWith(".js") || clean.endsWith(".mjs") || clean.endsWith(".cjs") || clean.endsWith(".jsx") || clean.endsWith(".ts") || clean.endsWith(".tsx") -> "text/javascript"
                                                    clean.endsWith(".json") -> "application/json"
                                                    clean.endsWith(".svg") -> "image/svg+xml"
                                                    clean.endsWith(".png") -> "image/png"
                                                    clean.endsWith(".jpg") || clean.endsWith(".jpeg") -> "image/jpeg"
                                                    clean.endsWith(".gif") -> "image/gif"
                                                    clean.endsWith(".webp") -> "image/webp"
                                                    clean.endsWith(".ico") -> "image/x-icon"
                                                    clean.endsWith(".woff2") -> "font/woff2"
                                                    clean.endsWith(".woff") -> "font/woff"
                                                    clean.endsWith(".ttf") -> "font/ttf"
                                                    clean.endsWith(".otf") -> "font/otf"
                                                    clean.endsWith(".eot") -> "application/vnd.ms-fontobject"
                                                    clean.endsWith(".wasm") -> "application/wasm"
                                                    clean.endsWith(".xml") -> "application/xml"
                                                    clean.endsWith(".txt") || clean.endsWith(".md") -> "text/plain"
                                                    else -> "application/octet-stream"
                                                }
                                                val isText = mime.startsWith("text/") || 
                                                             mime.contains("javascript") || 
                                                             mime.contains("json") || 
                                                             mime.contains("xml") ||
                                                             mime.contains("svg")
                                                return Pair(mime, if (isText) "UTF-8" else null)
                                            }

                                            fun createWebResponse(mime: String, enc: String?, stream: java.io.InputStream): WebResourceResponse {
                                                val resp = WebResourceResponse(mime, enc, stream)
                                                val headers = HashMap<String, String>()
                                                headers["Content-Type"] = if (enc != null) "$mime; charset=$enc" else mime
                                                headers["Access-Control-Allow-Origin"] = "*"
                                                headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
                                                headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
                                                headers["Cache-Control"] = "no-cache, no-store, must-revalidate"
                                                headers["Pragma"] = "no-cache"
                                                headers["Expires"] = "0"
                                                resp.responseHeaders = headers
                                                return resp
                                            }

                                            // Check disk web artifact files first
                                            val localDir = com.example.api.LocalHttpServer.webDistDir
                                            if (!localDir.isNullOrBlank()) {
                                                val dir = java.io.File(localDir)
                                                if (dir.exists()) {
                                                    val candidateFiles = listOf(
                                                        java.io.File(dir, cleanPath),
                                                        java.io.File(dir, "out/$cleanPath"),
                                                        java.io.File(dir, "dist/$cleanPath"),
                                                        java.io.File(dir, "build/$cleanPath")
                                                    )
                                                    var found: java.io.File? = candidateFiles.find { it.exists() && it.isFile }
                                                    if (found == null && (cleanPath.isEmpty() || !cleanPath.contains("."))) {
                                                        found = listOf(
                                                            java.io.File(dir, "index.html"),
                                                            java.io.File(dir, "out/index.html"),
                                                            java.io.File(dir, "dist/index.html")
                                                        ).find { it.exists() && it.isFile }
                                                    }
                                                    val targetFile = found
                                                    if (targetFile != null) {
                                                        val (mimeType, encoding) = getMimeTypeAndEncoding(targetFile.name)
                                                        return createWebResponse(mimeType, encoding, java.io.FileInputStream(targetFile))
                                                    }
                                                }
                                            }

                                            var matchingFile = filesHolder.filesList.find { 
                                                it.path.equals(path, ignoreCase = true) || 
                                                it.path.removePrefix("/").equals(cleanPath, ignoreCase = true) ||
                                                it.path.endsWith("/$cleanPath", ignoreCase = true)
                                            }
                                            if (matchingFile == null && (cleanPath.isEmpty() || !cleanPath.contains("."))) {
                                                matchingFile = filesHolder.filesList.find { it.path.equals("index.html", ignoreCase = true) || it.path.endsWith("/index.html", ignoreCase = true) }
                                            }
                                            if (matchingFile != null) {
                                                val (mimeType, encoding) = getMimeTypeAndEncoding(matchingFile.path)
                                                val isBinary = !encoding.equals("UTF-8") || matchingFile.content.startsWith("data:")
                                                val stream = if (isBinary) {
                                                    val rawContent = matchingFile.content
                                                    val bytes = if (rawContent.startsWith("data:") && rawContent.contains(";base64,")) {
                                                        try {
                                                            android.util.Base64.decode(rawContent.substringAfter(";base64,"), android.util.Base64.DEFAULT)
                                                        } catch (e: Exception) {
                                                            rawContent.toByteArray()
                                                        }
                                                    } else {
                                                        try {
                                                            android.util.Base64.decode(rawContent, android.util.Base64.DEFAULT)
                                                        } catch (e: Exception) {
                                                            rawContent.toByteArray()
                                                        }
                                                    }
                                                    java.io.ByteArrayInputStream(bytes)
                                                 } else {
                                                    java.io.ByteArrayInputStream((if (matchingFile.path.equals("index.html", ignoreCase = true) || matchingFile.path.endsWith("/index.html", ignoreCase = true)) preprocessHtmlForBabel(matchingFile.content) else matchingFile.content).toByteArray())
                                                 }
                                                 return createWebResponse(mimeType, encoding, stream)
                                            }
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }
                                }
                                addJavascriptInterface(WebAppInspectorInterface { identifier, outerHTML ->
                                    currentOnElementSelected(identifier, outerHTML)
                                }, "AndroidInspector")
                                
                                if (hasWebDist) {
                                    loadUrl("https://virtual-app/")
                                } else if (htmlFile != null) {
                                    loadDataWithBaseURL(
                                        "https://virtual-app/",
                                        preprocessHtmlForBabel(htmlFile.content),
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                }
                            }
                        },
                        update = { webView ->
                            webViewRef = webView
                            if (!hasWebDist && htmlFile != null) {
                                val currentContent = preprocessHtmlForBabel(htmlFile.content)
                                if (lastLoadedHtml != currentContent) {
                                    lastLoadedHtml = currentContent
                                    webView.loadDataWithBaseURL(
                                        "https://virtual-app/",
                                        currentContent,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (showLogs) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                color = Color(0xFF161B22),
                border = BorderStroke(1.dp, Color(0xFF30363D))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = Color(0xFF8B949E),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Console Output",
                                color = Color(0xFFE6EDF3),
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                        Surface(
                            onClick = onClearLogs,
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF21262D),
                            border = BorderStroke(1.dp, Color(0xFF30363D))
                        ) {
                            Text(
                                "Clear",
                                color = Color(0xFFF85149),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFF30363D), thickness = 1.dp)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (consoleLogs.isEmpty()) {
                            item {
                                Text(
                                    "No console logs recorded.",
                                    color = Color(0xFF6E7681),
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        } else {
                            items(consoleLogs) { log ->
                                val (badgeBg, badgeText) = when (log.level) {
                                    "error" -> Color(0x33F85149) to Color(0xFFF85149)
                                    "warning" -> Color(0x33D29922) to Color(0xFFD29922)
                                    else -> Color(0x333FB950) to Color(0xFF3FB950)
                                }
                                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = badgeBg,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                log.level.uppercase(),
                                                color = badgeText,
                                                fontSize = 9.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(log.message, color = Color(0xFFE6EDF3), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }
                                    if (log.sourceId.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                                        ) {
                                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color(0xFF58A6FF), modifier = Modifier.size(11.dp))
                                            Text("File: ${log.sourceId} (Line ${log.lineNumber})", color = Color(0xFF58A6FF), fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalTabContent(
    terminalOutput: String,
    onSendCommand: (String) -> Unit,
    onClear: () -> Unit
) {
    var commandInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(terminalOutput) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        // Terminal Title Header
        Surface(
            color = Color(0xFF161B22),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Window dots
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF85149)))
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFD29922)))
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF3FB950)))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "terminal • bash",
                        color = Color(0xFF8B949E),
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }

                Surface(
                    onClick = onClear,
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF21262D),
                    border = BorderStroke(1.dp, Color(0xFF30363D))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Terminal",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Clear",
                            color = Color(0xFF8B949E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Terminal Output Screen
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(14.dp)
                .verticalScroll(scrollState)
        ) {
            if (terminalOutput.isBlank()) {
                Text(
                    text = "Ready. Type a command below and press Enter.",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF6E7681),
                    lineHeight = 18.sp
                )
            } else {
                Text(
                    text = terminalOutput,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF58A6FF),
                    lineHeight = 18.sp
                )
            }
        }

        // Terminal Prompt Input Line
        Surface(
            color = Color(0xFF161B22),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color(0xFF3FB950),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    placeholder = {
                        Text(
                            "Type command (e.g. ls, npm build)...",
                            color = Color(0xFF6E7681),
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFE6EDF3),
                        unfocusedTextColor = Color(0xFFE6EDF3),
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0xFF30363D)
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                )

                Surface(
                    onClick = {
                        if (commandInput.isNotBlank()) {
                            onSendCommand(commandInput)
                            commandInput = ""
                        }
                    },
                    enabled = commandInput.isNotBlank(),
                    shape = RoundedCornerShape(6.dp),
                    color = if (commandInput.isNotBlank()) Color(0xFF238636) else Color(0xFF21262D),
                    border = BorderStroke(1.dp, if (commandInput.isNotBlank()) Color(0xFF2EA043) else Color(0xFF30363D))
                ) {
                    Box(
                        modifier = Modifier.size(38.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                            contentDescription = "Execute",
                            tint = if (commandInput.isNotBlank()) Color.White else Color(0xFF484F58),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AndroidBuildTabContent(
    project: ProjectEntity,
    githubRepo: String,
    githubToken: String,
    githubBranch: String,
    gitHubWorkflows: List<GitHubWorkflow> = emptyList(),
    onTriggerWorkflows: (Set<Long>?) -> Unit = {},
    buildStatus: String,
    buildSteps: List<BuildStep>,
    buildLogs: String,
    buildError: String?,
    isPollingBuild: Boolean,
    gitProgress: String,
    apkDownloadProgress: String = "",
    apkDownloadPercentage: Float? = null,
    webArtifactInfo: WebArtifactInfo? = null,
    onInstallApk: () -> Unit = {},
    onPreviewWebArtifact: () -> Unit = {},
    onFetchLatestArtifact: () -> Unit = {},
    onSaveRepo: (String) -> Unit,
    onSaveToken: (String) -> Unit,
    onSaveBranch: (String) -> Unit,
    onTriggerBuild: () -> Unit
) {
    var isEditingConfig by remember { mutableStateOf(githubRepo.isBlank() || githubToken.isBlank()) }
    var tempRepo by remember { mutableStateOf(githubRepo) }
    var tempToken by remember { mutableStateOf(githubToken) }
    var tempBranch by remember { mutableStateOf(githubBranch) }
    var expandArtifacts by remember { mutableStateOf(true) }
    var isWorkflowsExpanded by remember { mutableStateOf(false) }
    var selectedWorkflowIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val logsScrollState = rememberScrollState()

    var elapsedSeconds by remember { mutableStateOf(0) }
    val isBuildActive = isPollingBuild && (
        buildStatus.contains("in_progress", ignoreCase = true) ||
        buildStatus.contains("queued", ignoreCase = true) ||
        (buildStatus.contains("Run #", ignoreCase = true) && !buildStatus.contains("completed", ignoreCase = true))
    )

    LaunchedEffect(isBuildActive) {
        if (isBuildActive) {
            elapsedSeconds = 0
            while (true) {
                kotlinx.coroutines.delay(1000)
                elapsedSeconds++
            }
        }
    }

    LaunchedEffect(buildLogs) {
        if (buildLogs.isNotEmpty()) {
            logsScrollState.animateScrollTo(logsScrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050508))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tab Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = Color(0xFFEC4899),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Android Build Pipeline",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isEditingConfig && githubRepo.isNotBlank()) {
                IconButton(
                    onClick = { isEditingConfig = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Edit Config",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Divider(color = Color(0xFF1E2230))

        if (isEditingConfig) {
            // Configuration Setup UI
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E15)),
                border = BorderStroke(1.dp, Color(0xFF1E2230)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "GitHub Build Configuration",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configure your GitHub credentials and repository to enable automatic pushing and build tracking via GitHub Actions.",
                        color = Color(0xFF80809B),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    OutlinedTextField(
                        value = tempRepo,
                        onValueChange = { tempRepo = it },
                        label = { Text("GitHub Repository (owner/repo)", fontSize = 11.sp) },
                        placeholder = { Text("e.g. octocat/Hello-World", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFEC4899),
                            unfocusedBorderColor = Color(0xFF1E2230)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = tempToken,
                        onValueChange = { tempToken = it },
                        label = { Text("GitHub Access Token", fontSize = 11.sp) },
                        placeholder = { Text("ghp_xxxxxxxxxxxx", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFEC4899),
                            unfocusedBorderColor = Color(0xFF1E2230)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    val buildTabUriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Need a token? ",
                            color = Color(0xFF80809B),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Create one on GitHub",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                try {
                                    buildTabUriHandler.openUri("https://github.com/settings/tokens/new")
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = tempBranch,
                        onValueChange = { tempBranch = it },
                        label = { Text("Branch", fontSize = 11.sp) },
                        placeholder = { Text("main", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFEC4899),
                            unfocusedBorderColor = Color(0xFF1E2230)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (githubRepo.isNotBlank() && githubToken.isNotBlank()) {
                            TextButton(onClick = { isEditingConfig = false }) {
                                Text("Cancel", color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Button(
                            onClick = {
                                if (tempRepo.isNotBlank() && tempToken.isNotBlank()) {
                                    onSaveRepo(tempRepo)
                                    onSaveToken(tempToken)
                                    onSaveBranch(tempBranch.ifBlank { "main" })
                                    isEditingConfig = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEC4899),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = tempRepo.isNotBlank() && tempToken.isNotBlank()
                        ) {
                            Text("Save & Connect")
                        }
                    }
                }
            }
        } else {
            // Dashboard UI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = githubRepo,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Branch: $githubBranch",
                        color = Color(0xFF80809B),
                        fontSize = 11.sp
                    )
                }

                // Push and Trigger build button (forces push to trigger workflow)
                if (gitProgress.isNotEmpty()) {
                    val isSuccess = gitProgress.contains("complete", ignoreCase = true) || gitProgress.contains("success", ignoreCase = true)
                    val isFailure = gitProgress.contains("failed", ignoreCase = true) || gitProgress.contains("error", ignoreCase = true)
                    val indicatorColor = if (isSuccess) Color(0xFF2ED573) else if (isFailure) Color(0xFFEE5253) else Color(0xFFEC4899)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isSuccess) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = indicatorColor,
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (isFailure) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Failure",
                                tint = indicatorColor,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                color = indicatorColor,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp
                            )
                        }
                        Text(
                            text = gitProgress,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Button(
                        onClick = onTriggerBuild,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEC4899),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Build", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Status Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E15)),
                border = BorderStroke(1.dp, Color(0xFF1E2230)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Current Build Status", color = Color(0xFF80809B), fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = buildStatus,
                                color = if (buildStatus.contains("success", ignoreCase = true)) Color(0xFF2ED573)
                                        else if (buildStatus.contains("fail", ignoreCase = true) || buildStatus.contains("error", ignoreCase = true)) Color(0xFFEE5253)
                                        else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            if (elapsedSeconds > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(${elapsedSeconds}s)",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    if (isPollingBuild) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFEC4899),
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp
                            )
                            Text("Live Polling", color = Color(0xFF80809B), fontSize = 10.sp)
                        }
                    }
                }
            }

            // Workflows and Trigger section
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E15)),
                border = BorderStroke(1.dp, Color(0xFF1E2230)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isWorkflowsExpanded = !isWorkflowsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isWorkflowsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Workflows",
                                tint = Color(0xFFEC4899),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "GitHub Workflows (${gitHubWorkflows.size})",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { onTriggerWorkflows(if (selectedWorkflowIds.isNotEmpty()) selectedWorkflowIds else null) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEC4899),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            enabled = gitHubWorkflows.isNotEmpty()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (selectedWorkflowIds.isNotEmpty()) "Trigger Selected Workflows (${selectedWorkflowIds.size})" else "Trigger Non-Running Workflows",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isWorkflowsExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        if (gitHubWorkflows.isEmpty()) {
                            Text(
                                text = "No workflows detected. Please configure and poll your repository to load workflows.",
                                color = Color(0xFF80809B),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                gitHubWorkflows.forEach { wf ->
                                    val isChecked = selectedWorkflowIds.contains(wf.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF08090F), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFF141722), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    selectedWorkflowIds = if (checked) {
                                                        selectedWorkflowIds + wf.id
                                                    } else {
                                                        selectedWorkflowIds - wf.id
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color(0xFFEC4899),
                                                    uncheckedColor = Color(0xFF80809B),
                                                    checkmarkColor = Color.White
                                                )
                                            )
                                            Column(
                                                modifier = Modifier.padding(start = 4.dp)
                                            ) {
                                                Text(
                                                    text = wf.name,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = wf.path,
                                                    color = Color(0xFF80809B),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Real-time Status indicator
                                        if (wf.isTriggering) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    color = Color(0xFFEC4899),
                                                    modifier = Modifier.size(12.dp),
                                                    strokeWidth = 1.5.dp
                                                )
                                                Text("Triggering... 🚀", color = Color(0xFFEC4899), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            val status = wf.latestRunStatus.lowercase()
                                            val conclusion = wf.latestRunConclusion?.lowercase()

                                            when {
                                                status == "in_progress" || status == "queued" || status == "requested" -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        CircularProgressIndicator(
                                                            color = Color(0xFFEC4899),
                                                            modifier = Modifier.size(12.dp),
                                                            strokeWidth = 1.5.dp
                                                        )
                                                        Text("Running... ⚙️", color = Color(0xFFEC4899), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                status == "completed" && conclusion == "success" -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = "Success",
                                                            tint = Color(0xFF2ED573),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text("Success ✅", color = Color(0xFF2ED573), fontSize = 11.sp)
                                                    }
                                                }
                                                status == "completed" && (conclusion == "failure" || conclusion == "cancelled" || conclusion == "timed_out") -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Error,
                                                            contentDescription = "Failed",
                                                            tint = Color(0xFFEE5253),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text("Failed ❌", color = Color(0xFFEE5253), fontSize = 11.sp)
                                                    }
                                                }
                                                else -> {
                                                    Text(
                                                        text = "No recent run",
                                                        color = Color(0xFF80809B),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (buildError != null) {
                // Error Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1E21)),
                    border = BorderStroke(1.dp, Color(0xFFEE5253).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Error",
                            tint = Color(0xFFEE5253),
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text("Build Error Detected", color = Color(0xFFEE5253), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(buildError, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }

            // Steps and Logs Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Steps Column
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0D14)),
                    border = BorderStroke(1.dp, Color(0xFF1E2230)),
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Workflow Steps", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Divider(color = Color(0xFF1E2230), modifier = Modifier.padding(vertical = 4.dp))

                        if (buildSteps.isEmpty()) {
                            Text("No steps recorded.", color = Color.Gray, fontSize = 11.sp)
                        } else {
                            buildSteps.forEach { step ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = step.name,
                                        color = if (step.status == "in_progress") Color.White else Color(0xFF80809B),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Icon(
                                        imageVector = if (step.conclusion == "success") Icons.Default.CheckCircle
                                                      else if (step.conclusion == "failure") Icons.Default.Cancel
                                                      else Icons.Default.Info,
                                        contentDescription = step.status,
                                        tint = if (step.conclusion == "success") Color(0xFF2ED573)
                                               else if (step.conclusion == "failure") Color(0xFFEE5253)
                                               else if (step.status == "in_progress") Color(0xFFEC4899)
                                               else Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandArtifacts = !expandArtifacts }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Build Artifacts", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Icon(
                                imageVector = if (expandArtifacts) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand/Collapse",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Divider(color = Color(0xFF1E2230), modifier = Modifier.padding(vertical = 4.dp))

                        if (expandArtifacts) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (webArtifactInfo != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(
                                                    imageVector = Icons.Default.Public,
                                                    contentDescription = "Web Artifacts",
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Web Artifacts (${webArtifactInfo.name})",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = "${webArtifactInfo.fileCount} files (${webArtifactInfo.zipSizeBytes / 1024} KB)",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Extracted & running live in system background preview panel.",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 10.sp
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = onPreviewWebArtifact,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Language, contentDescription = "Preview", modifier = Modifier.size(14.dp), tint = Color.Black)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Open Web Preview", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (apkDownloadProgress.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1A1C29), RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = apkDownloadProgress,
                                            color = if (apkDownloadProgress.startsWith("Success")) Color(0xFF2ED573)
                                            else if (apkDownloadProgress.contains("fail", ignoreCase = true)) Color(0xFFEE5253)
                                            else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        
                                        if (apkDownloadPercentage != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LinearProgressIndicator(
                                                progress = { apkDownloadPercentage / 100f },
                                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                                color = Color(0xFFEC4899),
                                                trackColor = Color(0xFF1E2230)
                                            )
                                            Text(
                                                text = "${apkDownloadPercentage.toInt()}%",
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                                            )
                                        }
                                        
                                        if (apkDownloadProgress.startsWith("Success") && !apkDownloadProgress.contains("Web Artifacts")) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = onInstallApk,
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ED573)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(vertical = 8.dp)
                                            ) {
                                                Icon(Icons.Default.SystemUpdate, contentDescription = "Install", modifier = Modifier.size(14.dp), tint = Color.Black)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Install APK", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else if (webArtifactInfo == null) {
                                    Text("No artifacts generated yet.", color = Color.Gray, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = onFetchLatestArtifact,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = "Fetch Artifact", modifier = Modifier.size(14.dp), tint = Color(0xFF38BDF8))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fetch Latest Artifact", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Logs Column
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF050508)),
                    border = BorderStroke(1.dp, Color(0xFF1E2230)),
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Execution Logs", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Divider(color = Color(0xFF1E2230))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(8.dp)
                                .verticalScroll(logsScrollState)
                        ) {
                            Text(
                                text = buildLogs.ifEmpty { "Waiting for logs to compile..." },
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF8B949E),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExplorerPanel(
    files: List<ProjectFileEntity>,
    activeFile: ProjectFileEntity?,
    onSelectFile: (ProjectFileEntity) -> Unit,
    onCreateFile: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onRenameFile: (String, String) -> Unit,
    onMoveFile: (String, String) -> Unit,
    onImportFiles: (List<android.net.Uri>) -> Unit,
    onDecompileApk: (String) -> Unit = {},
    onPush: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var fileToRename by remember { mutableStateOf<String?>(null) }
    var fileToMove by remember { mutableStateOf<String?>(null) }
    var fileToDeleteConfirm by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        onImportFiles(uris)
    }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val rootNode = remember(files) {
        buildFileTree(files)
    }

    // Flatten the tree into a list of visible nodes for LazyColumn performance
    val visibleNodes = remember(rootNode, expandedFolders) {
        val list = mutableListOf<FlatFileNode>()
        fun flatten(node: FileNode, level: Int) {
            if (node.path.isNotEmpty()) {
                val isExpanded = expandedFolders.contains(node.path)
                val isActualFile = node.isFile && node.children.isEmpty()
                list.add(
                    FlatFileNode(
                        path = node.path,
                        name = node.name,
                        isFile = isActualFile,
                        level = level,
                        isExpanded = isExpanded,
                        fileEntity = node.fileEntity
                    )
                )
                if (isActualFile || !isExpanded) return
            }
            
            // Sort children: folders first, then alphabetically, deduplicated by path
            val sortedChildren = node.children
                .distinctBy { it.path }
                .sortedWith(
                    compareBy<FileNode> { if (!it.isFile || it.children.isNotEmpty()) 0 else 1 }
                        .thenBy { it.name.lowercase() }
                )
            
            sortedChildren.forEach { child ->
                flatten(child, if (node.path.isEmpty()) 0 else level + 1)
            }
        }
        flatten(rootNode, 0)
        list.distinctBy { it.path }
    }

    Column(
        modifier = modifier.fillMaxHeight().padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "EXPLORER",
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onSearch,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF8B949E), modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = "Import Files", tint = Color(0xFF8B949E), modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onPush,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(Icons.Default.Publish, contentDescription = "Push", tint = Color(0xFF8B949E), modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New File", tint = Color(0xFF8B949E), modifier = Modifier.size(16.dp))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            itemsIndexed(visibleNodes, key = { index, node -> "${node.path}_$index" }) { _, node ->
                FileNodeItem(
                    node = node,
                    isActive = node.isFile && node.path == activeFile?.path,
                    onToggleFolder = { path ->
                        expandedFolders = if (expandedFolders.contains(path)) {
                            expandedFolders - path
                        } else {
                            expandedFolders + path
                        }
                    },
                    onSelectFile = onSelectFile,
                    onRename = { fileToRename = it },
                    onMove = { fileToMove = it },
                    onDelete = { fileToDeleteConfirm = it },
                    onDecompileApk = onDecompileApk
                )
            }
        }

        if (showCreateDialog) {
            CreateFileDialog(onDismiss = { showCreateDialog = false }, onCreate = onCreateFile)
        }
        if (fileToRename != null) {
            RenameFileDialog(
                oldPath = fileToRename!!,
                onDismiss = { fileToRename = null },
                onRename = {
                    onRenameFile(fileToRename!!, it)
                    fileToRename = null
                }
            )
        }
        if (fileToMove != null) {
            MoveFileDialog(
                oldPath = fileToMove!!,
                onDismiss = { fileToMove = null },
                onMove = {
                    onMoveFile(fileToMove!!, it)
                    fileToMove = null
                }
            )
        }
        if (fileToDeleteConfirm != null) {
            val isProtectedYml = fileToDeleteConfirm?.lowercase()?.trim()?.let {
                it == "android.yml" || it.endsWith("/android.yml") || it.endsWith("\\android.yml")
            } ?: false
            AlertDialog(
                onDismissRequest = { fileToDeleteConfirm = null },
                title = { Text(if (isProtectedYml) "Protected File" else "Delete File", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { 
                    Text(
                        if (isProtectedYml) "The 'android.yml' file is protected and cannot be deleted."
                        else "Are you sure you want to delete '${fileToDeleteConfirm}'? This action cannot be undone.", 
                        color = Color(0xFF80809B)
                    ) 
                },
                confirmButton = {
                    if (!isProtectedYml) {
                        Button(
                            onClick = {
                                onDeleteFile(fileToDeleteConfirm!!)
                                fileToDeleteConfirm = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE5253))
                        ) {
                            Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { fileToDeleteConfirm = null }) {
                        Text(if (isProtectedYml) "OK" else "Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF12131A),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.border(BorderStroke(1.dp, Color(0xFF222533)), RoundedCornerShape(16.dp))
            )
        }
    }
}

data class FileNode(
    val name: String,
    val path: String,
    val isFile: Boolean,
    val children: MutableList<FileNode> = mutableListOf(),
    val fileEntity: ProjectFileEntity? = null
)

data class FlatFileNode(
    val path: String,
    val name: String,
    val isFile: Boolean,
    val level: Int,
    val isExpanded: Boolean,
    val fileEntity: ProjectFileEntity?
)

@Composable
fun FileNodeItem(
    node: FlatFileNode,
    isActive: Boolean,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (ProjectFileEntity) -> Unit,
    onRename: (String) -> Unit,
    onMove: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDecompileApk: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(if (isActive) Color(0xFF1E2130) else Color.Transparent)
            .padding(start = (node.level * 12 + 8).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable {
                    if (node.isFile) {
                        node.fileEntity?.let { onSelectFile(it) }
                    } else {
                        onToggleFolder(node.path)
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!node.isFile) {
                Icon(
                    imageVector = if (node.isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF8B949E),
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(14.dp))
            }

            FileIcon(name = node.name, isFile = node.isFile, isExpanded = node.isExpanded)

            Text(
                text = node.name,
                color = if (isActive) Color.White else Color(0xFFE6EDF3),
                fontSize = 13.sp,
                maxLines = 1,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
        }

        Box(
            modifier = Modifier.fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "File Menu",
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF12131A))
            ) {
                if (node.isFile && node.name.lowercase().endsWith(".apk")) {
                    DropdownMenuItem(
                        text = { Text("Decompile APK", color = Color(0xFF00FFCC), fontSize = 13.sp) },
                        onClick = {
                            showMenu = false
                            onDecompileApk(node.path)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename", color = Color.White, fontSize = 13.sp) },
                    onClick = {
                        showMenu = false
                        onRename(node.path)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Move", color = Color.White, fontSize = 13.sp) },
                    onClick = {
                        showMenu = false
                        onMove(node.path)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFEE5253), fontSize = 13.sp) },
                    onClick = {
                        showMenu = false
                        onDelete(node.path)
                    }
                )
            }
        }
    }
}

fun buildFileTree(files: List<ProjectFileEntity>): FileNode {
    val root = FileNode("", "", false)
    val pathNodeMap = mutableMapOf<String, FileNode>()
    pathNodeMap[""] = root

    // Normalize paths and filter out build artifacts and empty leading slash components
    val filteredFiles = files.filter { f ->
        val clean = f.path.replace('\\', '/').trimStart('/')
        !clean.startsWith("_next/") && !clean.startsWith(".next/") && !clean.startsWith("web-dist/")
    }
    val displayFiles = if (filteredFiles.size > 2000) filteredFiles.take(2000) else filteredFiles

    displayFiles.forEach { file ->
        val normPath = file.path.replace('\\', '/').trimStart('/').trimEnd('/')
        if (normPath.isBlank()) return@forEach
        val parts = normPath.split("/").filter { it.isNotBlank() }
        var currentPath = ""
        
        parts.forEachIndexed { index, part ->
            val parentPath = currentPath
            currentPath = if (parentPath.isEmpty()) part else "$parentPath/$part"
            val isLast = index == parts.size - 1
            val isFileNode = isLast
            val parentNode = pathNodeMap[parentPath] ?: root
            
            var existingNode = pathNodeMap[currentPath]
            if (existingNode == null) {
                existingNode = FileNode(
                    name = part,
                    path = currentPath,
                    isFile = isFileNode,
                    fileEntity = if (isLast) file else null
                )
                pathNodeMap[currentPath] = existingNode
                if (parentNode.children.none { it.path == currentPath }) {
                    parentNode.children.add(existingNode)
                }
            } else {
                if (isLast && existingNode.children.isEmpty()) {
                    val updated = existingNode.copy(isFile = true, fileEntity = file)
                    pathNodeMap[currentPath] = updated
                    val idx = parentNode.children.indexOfFirst { it.path == currentPath }
                    if (idx >= 0) {
                        parentNode.children[idx] = updated
                    }
                }
            }
        }
    }

    sortFileNodes(root)
    return root
}

fun sortFileNodes(node: FileNode) {
    val uniqueChildren = node.children.distinctBy { it.path }.toMutableList()
    uniqueChildren.forEach { sortFileNodes(it) }
    uniqueChildren.sortWith(
        compareBy<FileNode> { child ->
            if (!child.isFile || child.children.isNotEmpty()) 0 else 1
        }.thenBy { it.name.lowercase() }
    )
    node.children.clear()
    node.children.addAll(uniqueChildren)
}

@Composable
fun FileIcon(name: String, isFile: Boolean, isExpanded: Boolean) {
    if (!isFile) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
            contentDescription = null,
            tint = Color(0xFFFFCA28), // Golden Yellow folder tint for high-visibility and distinctiveness
            modifier = Modifier.size(16.dp)
        )
    } else {
        val extension = name.substringAfterLast(".", "").lowercase()
        val (icon, color) = when (extension) {
            "tsx", "ts" -> Icons.Default.Code to Color(0xFF38BDF8)
            "js", "jsx" -> Icons.Default.Code to Color(0xFFF1C40F)
            "html" -> Icons.Default.Language to Color(0xFFE67E22)
            "css" -> Icons.Default.Brush to Color(0xFF3498DB)
            "json" -> Icons.Default.DataObject to Color(0xFFF1C40F)
            "sql" -> Icons.Default.Storage to Color(0xFF95A5A6)
            "png", "jpg", "jpeg", "webp", "gif", "svg", "ico" -> Icons.Default.Image to Color(0xFF2ECC71) // Distinct green for image assets
            "apk" -> Icons.Default.Build to Color(0xFF3DDC84) // Green for build outputs like APKs
            "md", "txt" -> Icons.Default.Info to Color(0xFF9B59B6) // Purple for info/text/markdown files
            else -> Icons.Default.Description to Color(0xFF8B949E)
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun RenameFileDialog(
    oldPath: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newPath by remember { mutableStateOf(oldPath) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF12131A),
            border = BorderStroke(1.dp, Color(0xFF222533)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Rename File", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = newPath,
                    onValueChange = { newPath = it },
                    label = { Text("New file name/path") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { onRename(newPath) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                    ) { Text("Rename") }
                }
            }
        }
    }
}

@Composable
fun MoveFileDialog(
    oldPath: String,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit
) {
    var destPath by remember { mutableStateOf(oldPath) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF12131A),
            border = BorderStroke(1.dp, Color(0xFF222533)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Move File", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = destPath,
                    onValueChange = { destPath = it },
                    label = { Text("Destination path") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { onMove(destPath) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                    ) { Text("Move") }
                }
            }
        }
    }
}

@Composable
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var pathInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF12131A),
            border = BorderStroke(1.dp, Color(0xFF222533)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Create Virtual File",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    label = { Text("File Name (e.g. style.css, script.js)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedLabelColor = Color(0xFF38BDF8),
                        unfocusedLabelColor = Color(0xFF80809B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF80809B))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            if (pathInput.isNotBlank()) {
                                onCreate(pathInput)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = pathInput.isNotBlank()
                    ) {
                        Text("Create", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TextStyle(
    fontFamily: FontFamily,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color
) = androidx.compose.ui.text.TextStyle(
    fontFamily = fontFamily,
    fontSize = fontSize,
    color = color
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CustomSettingsDialog(
    provider: String,
    apiKey: String,
    baseUrl: String,
    modelId: String,
    useCustom: Boolean,
    customModels: List<CustomModelConfig> = emptyList(),
    selectedModelId: String = "",
    geminiModels: List<String> = emptyList(),
    openaiModels: List<String> = emptyList(),
    claudeModels: List<String> = emptyList(),
    mistralModels: List<String> = emptyList(),
    scannedModels: List<String> = emptyList(),
    isScanningModels: Boolean = false,
    scanError: String? = null,
    onScanModels: (String, String, String) -> Unit = { _, _, _ -> },
    onClearScannedModels: () -> Unit = {},
    maxActionSteps: Int = 80,
    allowBuildPush: Boolean = false,
    allowAutoFix: Boolean = false,
    allowBackgroundExecution: Boolean = false,
    onSaveAllowBuildPush: (Boolean) -> Unit = {},
    onSaveAllowAutoFix: (Boolean) -> Unit = {},
    onSaveAllowBackgroundExecution: (Boolean) -> Unit = {},
    onSaveMaxActionSteps: (Int) -> Unit = {},
    onAddCustomModel: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onDeleteCustomModel: (String) -> Unit = {},
    onSelectCustomModel: (String) -> Unit = {},
    onOpenAgentSkills: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean) -> Unit
) {
    var pInput by remember { mutableStateOf(provider) }
    var keyInput by remember { mutableStateOf(apiKey) }
    var baseInput by remember { mutableStateOf(baseUrl) }
    var modelInput by remember { mutableStateOf(modelId) }
    var aliasInput by remember { mutableStateOf("") }
    var ucToggle by remember { mutableStateOf(useCustom) }
    var stepsInput by remember { mutableStateOf(maxActionSteps.toString()) }
    var allowBuildPushState by remember { mutableStateOf(allowBuildPush) }
    var allowAutoFixState by remember { mutableStateOf(allowAutoFix) }
    var allowBackgroundExecutionState by remember { mutableStateOf(allowBackgroundExecution) }

    var showAddNewForm by remember { mutableStateOf(false) }
    var modelSearchQuery by remember { mutableStateOf("") }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "AI Orchestrator Settings",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE6EDF3)
                )

                Text(
                    text = "Register your own API keys and select models. Default AI is disabled for security.",
                    color = Color(0xFF8D96A0),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                // Agent Skills & Extensions Banner Card
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                    border = BorderStroke(1.dp, Color(0xFF30363D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAgentSkills() }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF21262D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = Color(0xFF2F81F7),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Agent Skills & Extensions",
                                    color = Color(0xFFE6EDF3),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Discover, install & toggle online agent skills",
                                    color = Color(0xFF8D96A0),
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF8D96A0)
                        )
                    }
                }

                // Max Action Steps Configuration
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, Color(0xFF30363D)), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Agent Step Execution Limit",
                        color = Color(0xFFE6EDF3),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Configure maximum tool calls and action perform steps. (Default is 80 steps)",
                        color = Color(0xFF8D96A0),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                    OutlinedTextField(
                        value = stepsInput,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) {
                                stepsInput = newValue
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE6EDF3),
                            unfocusedTextColor = Color(0xFFE6EDF3),
                            focusedBorderColor = Color(0xFF2F81F7),
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedContainerColor = Color(0xFF161B22),
                            unfocusedContainerColor = Color(0xFF161B22)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        singleLine = true
                    )
                }

                // Allow Build & Push Permission Toggle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, Color(0xFF30363D)), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allow Auto Build & Push Permission",
                                color = Color(0xFFE6EDF3),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Automatically push to GitHub and build Kotlin/Flutter projects when AI finishes, without asking for confirmation every time.",
                                color = Color(0xFF8D96A0),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = allowBuildPushState,
                            onCheckedChange = { allowBuildPushState = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF238636),
                                uncheckedThumbColor = Color(0xFF8D96A0),
                                uncheckedTrackColor = Color(0xFF21262D)
                            )
                        )
                    }
                }

                // Allow Auto Fix Permission Toggle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, Color(0xFF30363D)), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allow Auto Fix Build Errors",
                                color = Color(0xFFE6EDF3),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Automatically analyze and fix any compilation or web console errors as soon as they are detected, without asking for confirmation.",
                                color = Color(0xFF8D96A0),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = allowAutoFixState,
                            onCheckedChange = { allowAutoFixState = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF238636),
                                uncheckedThumbColor = Color(0xFF8D96A0),
                                uncheckedTrackColor = Color(0xFF21262D)
                            )
                        )
                    }
                }

                // Allow Background Agent Execution Toggle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, Color(0xFF30363D)), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Run AI Agent in Background",
                                color = Color(0xFFE6EDF3),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Keep the AI Agent and the application fully running and working in the background even if you minimize or close the app.",
                                color = Color(0xFF8D96A0),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = allowBackgroundExecutionState,
                            onCheckedChange = { allowBackgroundExecutionState = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF238636),
                                uncheckedThumbColor = Color(0xFF8D96A0),
                                uncheckedTrackColor = Color(0xFF21262D)
                            )
                        )
                    }
                }

                if (!showAddNewForm) {
                    Button(
                        onClick = { showAddNewForm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF21262D),
                            contentColor = Color(0xFF38BDF8)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add New Model Configuration", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    // Add New Model Form
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161822), RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, Color(0xFF222533)), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "New Configuration",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Provider Picker
                        val providers = listOf("opencode_zen", "gemini", "openai", "claude", "mistral", "groq", "cohere", "openrouter", "ollama_cloud", "cloudflare", "custom")
                        Text("Provider", color = Color(0xFF80809B), fontSize = 11.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            providers.forEach { p ->
                                val isSelected = pInput == p
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color(0xFF38BDF8) else Color(0xFF0D0E15))
                                        .border(BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF222533)), RoundedCornerShape(10.dp))
                                        .clickable { 
                                            pInput = p 
                                            // Set defaults based on provider but let user customize freely
                                            modelInput = when(p) {
                                                "opencode_zen" -> "opencode-zen"
                                                "gemini" -> "gemini-2.0-flash"
                                                "openai" -> "gpt-4o"
                                                "mistral" -> "mistral-large-latest"
                                                "groq" -> "llama-3.3-70b-versatile"
                                                "cohere" -> "command-r-plus"
                                                "openrouter" -> "google/gemini-2.5-flash"
                                                "ollama_cloud" -> "llama3.3"
                                                "cloudflare" -> "@cf/meta/llama-3.3-70b-instruct"
                                                else -> ""
                                            }
                                            baseInput = when(p) {
                                                "opencode_zen" -> "https://opencode.ai/zen/v1"
                                                "groq" -> "https://api.groq.com/openai"
                                                "cohere" -> "https://api.cohere.com"
                                                "openrouter" -> "https://openrouter.ai/api/v1"
                                                "ollama_cloud" -> "https://api.ollama.com"
                                                "cloudflare" -> "https://api.cloudflare.com/client/v4/accounts/YOUR_ACCOUNT_ID/ai/run"
                                                "custom" -> "https://api.example.com/v1"
                                                else -> ""
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val displayName = when(p) {
                                        "opencode_zen" -> "OpenCode Zen"
                                        "ollama_cloud" -> "Ollama Cloud"
                                        "cloudflare" -> "Cloudflare"
                                        "cohere" -> "Cohere"
                                        "groq" -> "Groq"
                                        "openrouter" -> "OpenRouter"
                                        else -> p.replaceFirstChar { it.uppercase() }
                                    }
                                    Text(
                                        text = displayName,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (pInput == "custom" || pInput == "opencode_zen" || pInput == "groq" || pInput == "cohere" || pInput == "openrouter" || pInput == "ollama_cloud" || pInput == "cloudflare") {
                            OutlinedTextField(
                                value = baseInput,
                                onValueChange = { baseInput = it },
                                label = { Text("API Base URL") },
                                placeholder = { Text(if (pInput == "opencode_zen") "https://opencode.ai/zen/v1" else "e.g. https://api.groq.com/openai/v1") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF222533)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = modelInput,
                            onValueChange = { modelInput = it },
                            label = { Text("Model ID") },
                            placeholder = { Text("e.g. opencode-zen or gpt-4o") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF222533)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            label = { Text("API Key") },
                            placeholder = { Text("Paste your key here") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF222533)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Create API Key Button / Link
                        val createKeyUrl = when (pInput) {
                            "opencode_zen" -> "https://opencode.ai/zen"
                            "gemini" -> "https://aistudio.google.com/app/apikey"
                            "openai" -> "https://platform.openai.com/api-keys"
                            "claude" -> "https://console.anthropic.com/settings/keys"
                            "mistral" -> "https://console.mistral.ai/api-keys/"
                            "groq" -> "https://console.groq.com/keys"
                            "cohere" -> "https://dashboard.cohere.com/api-keys"
                            "openrouter" -> "https://openrouter.ai/keys"
                            "cloudflare" -> "https://dash.cloudflare.com/profile/api-tokens"
                            "ollama_cloud" -> "https://ollama.com"
                            else -> "https://opencode.ai/zen"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (keyInput.isBlank()) "No API key? Create one here:" else "Key added. Generate new key:",
                                color = Color(0xFF80809B),
                                fontSize = 11.sp
                            )
                            OutlinedButton(
                                onClick = {
                                    try {
                                        uriHandler.openUri(createKeyUrl)
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                },
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Create API Key",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }

                        // Scan Models UI Section
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Button(
                                onClick = {
                                    onScanModels(pInput, keyInput, baseInput)
                                },
                                enabled = !isScanningModels,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B),
                                    contentColor = Color(0xFF38BDF8)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp))
                            ) {
                                if (isScanningModels) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFF38BDF8),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Scanning...", fontSize = 12.sp, color = Color(0xFF38BDF8))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Scan",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFF38BDF8)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan Active Models", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                }
                            }
                            
                            if (scannedModels.isNotEmpty()) {
                                Button(
                                    onClick = { onClearScannedModels() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = Color.Red
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text("Clear", fontSize = 12.sp, color = Color.Red)
                                }
                            }
                        }

                        if (!scanError.isNullOrEmpty()) {
                            Text(
                                text = scanError!!,
                                color = Color.Red,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            )
                        }

                        if (scannedModels.isNotEmpty()) {
                            val filteredScannedModels = remember(scannedModels, modelSearchQuery) {
                                if (modelSearchQuery.isBlank()) scannedModels
                                else scannedModels.filter { it.contains(modelSearchQuery.trim(), ignoreCase = true) }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Available Models (${filteredScannedModels.size}/${scannedModels.size}):",
                                    color = Color(0xFF80809B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Click to paste",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp
                                )
                            }

                            OutlinedTextField(
                                value = modelSearchQuery,
                                onValueChange = { modelSearchQuery = it },
                                placeholder = { Text("Search model ID or keyword...", fontSize = 11.sp, color = Color.Gray) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                },
                                trailingIcon = {
                                    if (modelSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { modelSearchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF222533)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .background(Color(0xFF0D0E15), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF222533), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (filteredScannedModels.isEmpty()) {
                                    Text(
                                        text = "No model found matching '$modelSearchQuery'",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        filteredScannedModels.forEach { scannedModel ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF1E293B))
                                                    .clickable {
                                                        modelInput = scannedModel
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = scannedModel,
                                                    color = Color.White,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = aliasInput,
                            onValueChange = { aliasInput = it },
                            label = { Text("Friendly Name (Optional)") },
                            placeholder = { Text("e.g. My Fast Llama") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF222533)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showAddNewForm = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFF80809B)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    if (modelInput.isNotBlank() && keyInput.isNotBlank()) {
                                        val finalAlias = if (aliasInput.isBlank()) {
                                            modelInput.split("-", "_", ".").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                                        } else {
                                            aliasInput
                                        }
                                        onAddCustomModel(finalAlias, pInput, keyInput, baseInput, modelInput)
                                        aliasInput = ""; keyInput = ""; baseInput = ""; modelInput = ""; showAddNewForm = false
                                    }
                                },
                                enabled = modelInput.isNotBlank() && keyInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Add Model", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (customModels.isNotEmpty()) {
                    Text(
                        text = "Saved Configurations",
                        color = Color(0xFFE6EDF3),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        customModels.forEach { model ->
                            val isSelected = model.id == selectedModelId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF21262D) else Color(0xFF0D1117))
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isSelected) Color(0xFF2F81F7) else Color(0xFF30363D)
                                        ),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectCustomModel(model.id) }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSelectCustomModel(model.id) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color(0xFF2F81F7),
                                            unselectedColor = Color(0xFF6E7681)
                                        )
                                    )
                                    Column {
                                        Text(
                                            text = model.alias,
                                            color = Color(0xFFE6EDF3),
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${model.provider.uppercase()} • ${model.modelId}",
                                            color = Color(0xFF8D96A0),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onDeleteCustomModel(model.id) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete model",
                                        tint = Color(0xFFF85149),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val steps = stepsInput.toIntOrNull() ?: 80
                            onSaveMaxActionSteps(steps)
                            onSaveAllowBuildPush(allowBuildPushState)
                            onSaveAllowAutoFix(allowAutoFixState)
                            onSaveAllowBackgroundExecution(allowBackgroundExecutionState)
                            onDismiss()
                        }
                    ) {
                        Text("Close", color = Color(0xFF8D96A0), fontSize = 12.5.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val activeConfig = customModels.find { it.id == selectedModelId }
                            if (activeConfig != null) {
                                onSave(
                                    activeConfig.provider,
                                    activeConfig.apiKey,
                                    activeConfig.baseUrl,
                                    activeConfig.modelId,
                                    true
                                )
                            }
                            val steps = stepsInput.toIntOrNull() ?: 80
                            onSaveMaxActionSteps(steps)
                            onSaveAllowBuildPush(allowBuildPushState)
                            onSaveAllowAutoFix(allowAutoFixState)
                            onSaveAllowBackgroundExecution(allowBackgroundExecutionState)
                            onDismiss()
                        },
                        enabled = selectedModelId.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF238636),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Apply Selection", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushProjectDialog(
    gitProgress: String,
    initialToken: String,
    initialRepo: String = "",
    initialBranch: String = "main",
    onDismiss: () -> Unit,
    onSaveToken: (String) -> Unit,
    onSaveRepo: (String) -> Unit = {},
    onSaveBranch: (String) -> Unit = {},
    onPush: (String, String, String, Boolean) -> Unit
) {
    var repo by remember { mutableStateOf(initialRepo) }
    var token by remember { mutableStateOf(initialToken) }
    var branch by remember { mutableStateOf(initialBranch) }
    var forcePush by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = { if (gitProgress.isEmpty() || gitProgress.contains("complete", ignoreCase = true) || gitProgress.contains("failed", ignoreCase = true)) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E15)),
            border = BorderStroke(1.dp, Color(0xFF1E2230)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Push to GitHub",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Deploy this current active workspace directly to a designated GitHub repository.",
                    color = Color(0xFF80809B),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = repo,
                    onValueChange = { 
                        repo = it 
                        onSaveRepo(it)
                    },
                    label = { Text("Repository (owner/repo or just repo-name)") },
                    placeholder = { Text("e.g. Hello-World") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF222533)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = "💡 আপনি চাইলে শুধুমাত্র রিপোজিটরির নাম (যেমন 'my-repo') দিতে পারেন। ইউজারনেমটি আপনার অ্যাক্সেস টোকেন থেকে স্বয়ংক্রিয়ভাবে খুঁজে নেওয়া হবে।",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = { 
                        token = it
                        onSaveToken(it)
                    },
                    label = { Text("GitHub Access Token") },
                    placeholder = { Text("ghp_xxxxxxxxxxxx") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF222533)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = branch,
                    onValueChange = { 
                        branch = it 
                        onSaveBranch(it)
                    },
                    label = { Text("Branch") },
                    placeholder = { Text("main") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF222533)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Force Overwrite Push", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Overwrite origin reference completely", color = Color(0xFFEE5253), fontSize = 11.sp)
                    }
                    Switch(
                        checked = forcePush,
                        onCheckedChange = { forcePush = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFEE5253),
                            checkedTrackColor = Color(0xFF1E2130)
                        )
                    )
                }

                if (gitProgress.isNotEmpty()) {
                    val isSuccess = gitProgress.contains("complete", ignoreCase = true) || gitProgress.contains("success", ignoreCase = true)
                    val isFailure = gitProgress.contains("failed", ignoreCase = true) || gitProgress.contains("error", ignoreCase = true)
                    val indicatorColor = if (isSuccess) Color(0xFF2ED573) else if (isFailure) Color(0xFFEE5253) else Color(0xFF38BDF8)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isSuccess) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = indicatorColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else if (isFailure) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Failure",
                                    tint = indicatorColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                CircularProgressIndicator(
                                    color = indicatorColor,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            Text(
                                text = gitProgress,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = gitProgress.isEmpty() || gitProgress.contains("complete", ignoreCase = true) || gitProgress.contains("failed", ignoreCase = true)
                    ) {
                        Text("Cancel", color = Color(0xFF80809B))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            if (repo.isNotBlank() && token.isNotBlank()) {
                                onPush(repo, token, branch.ifBlank { "main" }, forcePush)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF38BDF8),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = repo.isNotBlank() && token.isNotBlank() && (gitProgress.isEmpty() || gitProgress.contains("complete", ignoreCase = true) || gitProgress.contains("failed", ignoreCase = true))
                    ) {
                        Text("Push to origin", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickEditDialog(
    content: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var previewResult by remember { mutableStateOf("") }

    LaunchedEffect(search, replace) {
        if (search.isNotEmpty() && content.contains(search)) {
            previewResult = content.replace(search, replace)
        } else {
            previewResult = ""
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF12131A)),
            border = BorderStroke(1.dp, Color(0xFF222533)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Quick Find & Replace",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Find Text") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF8B5CF6)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = replace,
                    onValueChange = { replace = it },
                    label = { Text("Replace With") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (search.isNotEmpty()) {
                    val count = content.split(search).size - 1
                    Text(
                        text = if (count > 0) "$count occurrences found." else "Not found in file.",
                        color = if (count > 0) Color(0xFF2ED573) else Color(0xFFEE5253),
                        fontSize = 12.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (search.isNotEmpty() && content.contains(search)) {
                                onApply(previewResult)
                            }
                        },
                        enabled = search.isNotEmpty() && content.contains(search),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalSearchDialog(
    files: List<ProjectFileEntity>,
    onSelectFile: (ProjectFileEntity) -> Unit,
    onTabSelected: (WorkspaceTab) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF12131A),
            border = BorderStroke(1.dp, Color(0xFF222533)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Global Search",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Type search string...", color = Color.Gray) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF38BDF8)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF222533)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (query.length >= 2) {
                    val searchResults = remember(query, files) {
                        val list = mutableListOf<SearchMatch>()
                        files.forEach { file ->
                            val lines = file.content.lines()
                            lines.forEachIndexed { index, line ->
                                if (line.contains(query, ignoreCase = true)) {
                                    list.add(SearchMatch(file = file, lineNumber = index + 1, text = line.trim()))
                                }
                            }
                        }
                        list
                    }
                    
                    if (searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No matches found.", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(searchResults) { match ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                                    border = BorderStroke(1.dp, Color(0xFF2E324A)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectFile(match.file)
                                            onTabSelected(WorkspaceTab.CODE)
                                            onDismiss()
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = match.file.path,
                                                color = Color(0xFF38BDF8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "Line ${match.lineNumber}",
                                                color = Color(0xFF80809B),
                                                fontSize = 11.sp,
                                                
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = match.text,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Type at least 2 characters to search...", color = Color.Gray, fontSize = 14.sp)
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = Color.Gray)
                    }
                }
            }
        }
    }
}

data class SearchMatch(
    val file: ProjectFileEntity,
    val lineNumber: Int,
    val text: String
)

@Composable
fun WorkspaceOperationsTimeline(
    displayLogs: List<com.example.ui.AiActionLog>,
    isThinking: Boolean
) {
    var isExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    
    val currentMillis = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(isThinking) {
        while (true) {
            currentMillis.value = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(Color(0xFF161B22), RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, Color(0xFF30363D)), RoundedCornerShape(10.dp))
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(26.dp)
                        .background(Color(0xFF21262D), RoundedCornerShape(6.dp))
                        .border(BorderStroke(1.dp, Color(0xFF30363D)), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Operations",
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(15.dp)
                    )
                }
                androidx.compose.foundation.layout.Column {
                    Text(
                        text = "Agent Operations Timeline",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE6EDF3)
                    )
                    Text(
                        text = "${displayLogs.size} operations executed",
                        fontSize = 11.sp,
                        color = Color(0xFF8B949E)
                    )
                }
            }
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isThinking) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = Color(0xFF58A6FF),
                        strokeWidth = 1.5.dp
                    )
                    Text("Running", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF58A6FF))
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(if (isExpanded) "Hide" else "Show", fontSize = 12.sp, color = Color(0xFF8B949E))
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle",
                    tint = Color(0xFF8B949E),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        if (isExpanded) {
            HorizontalDivider(color = Color(0xFF21262D), thickness = 1.dp)
            
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                displayLogs.forEachIndexed { index, log ->
                    val isThought = log.title.contains("thinking", ignoreCase = true) || log.title.contains("formulating", ignoreCase = true) || log.title.contains("Thought process", ignoreCase = true)
                    val isEditOrPatch = log.title.startsWith("Edit:") || log.title.startsWith("Patch:") || log.title.contains("Modified file")
                    val isDelete = log.title.contains("delete", ignoreCase = true)
                    val isMove = log.title.contains("move", ignoreCase = true) || log.title.contains("extrac", ignoreCase = true)
                    val isCopy = log.title.contains("copy", ignoreCase = true)
                    val isSuccess = log.status == "success"
                    val isItemThinking = log.status == "thinking"
                    
                    val iconColor = when {
                        isDelete -> Color(0xFFF85149)
                        isMove -> Color(0xFFA371F7)
                        isCopy -> Color(0xFF388BFD)
                        log.title == "Read file" || log.title.startsWith("read :") || log.title.startsWith("read_file") -> Color(0xFF58A6FF)
                        isEditOrPatch || log.title == "Appended to file" || log.title.startsWith("Appended:") -> Color(0xFF3FB950)
                        log.title == "Executed shell command" || log.title.contains("command") || log.title.startsWith("Run:") || log.title.startsWith("Search:") -> Color(0xFFD29922)
                        log.title.contains("Thought process", ignoreCase = true) -> Color(0xFFD29922)
                        else -> if (isThought) Color(0xFFD29922) else Color(0xFF58A6FF)
                    }
                    
                    val icon = when {
                        isDelete -> Icons.Default.Delete
                        isMove -> Icons.Default.SwapHoriz
                        isCopy -> Icons.Default.ContentCopy
                        log.title == "Read file" || log.title.startsWith("read :") || log.title.startsWith("read_file") -> Icons.Default.Description
                        isEditOrPatch -> Icons.Default.Edit
                        log.title == "Appended to file" || log.title.startsWith("Appended:") -> Icons.Default.Add
                        log.title == "Executed shell command" || log.title.contains("command") || log.title.startsWith("Run:") || log.title.startsWith("Search:") -> Icons.Default.Terminal
                        log.title.contains("Thought process", ignoreCase = true) -> Icons.Default.TipsAndUpdates
                        else -> if (isThought) Icons.Default.Lightbulb else Icons.Default.Check
                    }
                    
                    androidx.compose.foundation.layout.Row(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Timeline line and icon
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = androidx.compose.ui.Modifier
                                    .size(24.dp)
                                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(6.dp))
                                    .background(Color(0xFF21262D), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isItemThinking) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(11.dp),
                                        color = iconColor,
                                        strokeWidth = 1.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = "Step icon",
                                        tint = iconColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            if (index < displayLogs.size - 1 || isThinking) {
                                androidx.compose.foundation.layout.Box(
                                    modifier = androidx.compose.ui.Modifier
                                        .width(1.dp)
                                        .weight(1f, fill = false)
                                        .heightIn(min = 24.dp)
                                        .background(Color(0xFF30363D))
                                )
                            }
                        }
                        
                        // Content
                        androidx.compose.foundation.layout.Column(
                            modifier = androidx.compose.ui.Modifier
                                .weight(1f)
                                .padding(bottom = 16.dp)
                        ) {
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = log.title,
                                    fontSize = 12.sp,
                                    color = Color(0xFFE6EDF3),
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (log.title.contains("Appwrite", ignoreCase = true) ||
                                    log.title.contains("Supabase", ignoreCase = true) ||
                                    log.title.contains("Cloudflare", ignoreCase = true) ||
                                    log.title.contains("Vercel", ignoreCase = true) ||
                                    log.title.contains("Stitch", ignoreCase = true) ||
                                    log.title.contains("MCP", ignoreCase = true)
                                ) {
                                    val platformName = when {
                                        log.title.contains("Appwrite", ignoreCase = true) -> "Appwrite"
                                        log.title.contains("Supabase", ignoreCase = true) -> "Supabase"
                                        log.title.contains("Cloudflare", ignoreCase = true) -> "Cloudflare"
                                        log.title.contains("Vercel", ignoreCase = true) -> "Vercel"
                                        log.title.contains("Stitch", ignoreCase = true) -> "Google Stitch"
                                        else -> "MCP"
                                    }
                                    val badgeColor = when (platformName) {
                                        "Appwrite" -> Color(0xFFFD366E)
                                        "Supabase" -> Color(0xFF3ECF8E)
                                        "Cloudflare" -> Color(0xFFF38020)
                                        "Vercel" -> Color(0xFFE0E0E0)
                                        "Google Stitch" -> Color(0xFF4285F4)
                                        else -> Color(0xFFA371F7)
                                    }
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(platformName, color = badgeColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                
                                if (isSuccess) {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .background(Color(0xFF238636).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Done", color = Color(0xFF3FB950), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                } else if (log.status == "failed") {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = Modifier
                                            .background(Color(0xFFDA3633).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Failed", color = Color(0xFFF85149), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            
                            if (log.details != null) {
                                val isEditPatchAppend = log.title.startsWith("edit", ignoreCase = true) || 
                                                       log.title.startsWith("patch", ignoreCase = true) || 
                                                       log.title.startsWith("append", ignoreCase = true) ||
                                                       log.title.startsWith("multi", ignoreCase = true) ||
                                                       log.title.contains("Modified", ignoreCase = true) ||
                                                       log.title.contains("Multi-edited", ignoreCase = true) ||
                                                       log.title.contains("Appended", ignoreCase = true)
                                                       
                                val isRead = log.title.startsWith("read", ignoreCase = true) || log.title.contains("read", ignoreCase = true)
                                val displayText = if (!log.lineRange.isNullOrBlank() && (isEditPatchAppend || isRead || log.title.contains("file", ignoreCase = true))) {
                                    val cleanFileName = log.details.substringAfterLast("/")
                                    val rawRange = log.lineRange.lowercase().replace("lines ", "").replace("line ", "").trim()
                                    val formattedRange = if (rawRange.startsWith("line")) rawRange else "line $rawRange"
                                    if (isEditPatchAppend) {
                                        val actionType = when {
                                            log.title.startsWith("patch", ignoreCase = true) -> "patch"
                                            log.title.startsWith("append", ignoreCase = true) -> "append"
                                            else -> "edit"
                                        }
                                        "$actionType $cleanFileName ($formattedRange)"
                                    } else if (isRead) {
                                        "read $cleanFileName ($formattedRange)"
                                    } else {
                                        "${log.details} ($formattedRange)"
                                    }
                                } else {
                                    log.details
                                }
                                
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .background(Color(0xFF0D1117), RoundedCornerShape(6.dp))
                                        .border(BorderStroke(1.dp, Color(0xFF21262D)), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = displayText,
                                        fontSize = 11.sp,
                                        color = Color(0xFF8B949E),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                // If thinking, show active step
                if (isThinking) {
                    androidx.compose.foundation.layout.Row(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = androidx.compose.ui.Modifier
                                    .size(24.dp)
                                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(6.dp))
                                    .background(Color(0xFF21262D), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(11.dp),
                                    color = Color(0xFF58A6FF),
                                    strokeWidth = 1.5.dp
                                )
                            }
                        }
                        androidx.compose.foundation.layout.Column(
                            modifier = androidx.compose.ui.Modifier
                                .weight(1f)
                                .padding(bottom = 6.dp)
                        ) {
                            Text(
                                "Formulating next operation...",
                                fontSize = 12.sp,
                                color = Color(0xFFE6EDF3),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Analyzing codebase context and executing steps",
                                fontSize = 11.sp,
                                color = Color(0xFF8B949E),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TokenMonitorCard(
    modelName: String,
    timeSeconds: Long,
    isLive: Boolean,
    systemTokens: Int,
    userTokens: Int,
    toolTokens: Int,
    totalInputTokens: Int,
    totalOutputTokens: Int,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1420)),
        border = BorderStroke(1.dp, if (isLive) Color(0xFF00F2FE).copy(alpha = 0.5f) else Color(0xFF262E42)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row: Model Name & Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Model",
                        tint = if (isLive) Color(0xFF00F2FE) else Color(0xFFFFB020),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isLive) "$modelName (running for ${timeSeconds}s)" else "$modelName (${timeSeconds}s)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Timer badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isLive) Color(0xFF00F2FE).copy(alpha = 0.15f) else Color(0xFF1E293B)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isLive) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = Color(0xFF00F2FE)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Timer",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = if (isLive) "Running for ${timeSeconds}s..." else "${timeSeconds}s",
                            color = if (isLive) Color(0xFF00F2FE) else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E2638), thickness = 0.8.dp)

            // Token Breakdown Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = "Token Monitor",
                    tint = Color(0xFFA855F7),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "TOKEN MONITOR" + if (isLive) " (LIVE)" else "",
                    color = Color(0xFFA855F7),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }

            // Detailed Breakdown List
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TokenMetricRow(label = "System Instruction", count = systemTokens, color = Color(0xFF38BDF8))
                TokenMetricRow(label = "User Prompt", count = userTokens, color = Color(0xFF4ADE80))
                TokenMetricRow(label = "Tool Usage", count = toolTokens, color = Color(0xFFFACC15))
            }

            HorizontalDivider(color = Color(0xFF1E2638), thickness = 0.8.dp)

            // Totals Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Total Input:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("${java.text.NumberFormat.getInstance().format(totalInputTokens)} tokens", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Total Output:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("${java.text.NumberFormat.getInstance().format(totalOutputTokens)} tokens", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TokenMetricRow(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            Text(label, color = Color(0xFFCBD5E1), fontSize = 11.sp)
        }
        Text("${java.text.NumberFormat.getInstance().format(count)} tokens", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
