package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.HomeScreen
import com.example.ui.VibeViewModel
import com.example.ui.WorkspaceScreen
import com.example.ui.WebConsoleError
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.foundation.text.selection.SelectionContainer
import com.example.api.LocalHttpServer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var mainViewModel: VibeViewModel? = null

    private var pendingOAuthUri: android.net.Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            com.example.crash.AppCrashGuard.install(applicationContext)
            android.webkit.WebView.enableSlowWholeDocumentDraw()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onCreate(savedInstanceState)
        pendingOAuthUri = intent?.data
        
        // Handle initial intent if launched via deep link
        intent?.data?.let { uri ->
            mainViewModel?.handleMcpOAuthCallback(uri)
        }
        
        // Pre-create WebView HTTP Code Cache directories to prevent Chromium folder-not-found errors
        try {
            val cacheDir = cacheDir
            if (cacheDir != null) {
                val webViewDefaultCacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
                if (!webViewDefaultCacheDir.exists()) {
                    webViewDefaultCacheDir.mkdirs()
                }
                java.io.File(webViewDefaultCacheDir, "js").apply { if (!exists()) mkdirs() }
                java.io.File(webViewDefaultCacheDir, "wasm").apply { if (!exists()) mkdirs() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        enableEdgeToEdge()
        setContent {
            // Force true dark theme for the sleek professional Cursor/Lovable feel
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF08080C)
                ) {
                    SelectionContainer {
                        val viewModel: VibeViewModel = viewModel()
                        mainViewModel = viewModel
                        
                        LaunchedEffect(Unit) {
                            pendingOAuthUri?.let { uri ->
                                viewModel.handleMcpOAuthCallback(uri)
                                pendingOAuthUri = null
                            }
                        }
                        val currentProject by viewModel.currentProject.collectAsState()
                        val projectsList by viewModel.projectsList.collectAsState()
                        val projectFiles by viewModel.projectFiles.collectAsState()
                        val activeFile by viewModel.activeFile.collectAsState()
                        val editorContent by viewModel.editorContent.collectAsState()
                        val chatMessages by viewModel.chatMessages.collectAsState()
                        val isThinking by viewModel.isThinking.collectAsState()
                        val agentStatus by viewModel.agentStatus.collectAsState()
                        val currentTab by viewModel.currentTab.collectAsState()
                        val aiActionLogs by viewModel.aiActionLogs.collectAsState()
                        val terminalOutput by viewModel.terminalOutput.collectAsState()
                        val gitProgress by viewModel.gitProgress.collectAsState()
                        val customProvider by viewModel.customProvider.collectAsState()
                        val customApiKey by viewModel.customApiKey.collectAsState()
                        val customBaseUrl by viewModel.customBaseUrl.collectAsState()
                        val customModelId by viewModel.customModelId.collectAsState()
                        val useCustomModel by viewModel.useCustomModel.collectAsState()
                        val githubToken by viewModel.githubToken.collectAsState()
                        val githubRepo by viewModel.githubRepo.collectAsState()
                        val githubBranch by viewModel.githubBranch.collectAsState()
                        val explorerGithubToken by viewModel.explorerGithubToken.collectAsState()
                        val explorerGithubRepo by viewModel.explorerGithubRepo.collectAsState()
                        val explorerGithubBranch by viewModel.explorerGithubBranch.collectAsState()
                        val gitHubWorkflows by viewModel.gitHubWorkflows.collectAsState()
                        val buildStatus by viewModel.buildStatus.collectAsState()
                        val buildSteps by viewModel.buildSteps.collectAsState()
                        val buildLogs by viewModel.buildLogs.collectAsState()
                        val buildError by viewModel.buildError.collectAsState()
                        val isPollingBuild by viewModel.isPollingBuild.collectAsState()
                        val apkDownloadProgress by viewModel.apkDownloadProgress.collectAsState()
                        val apkDownloadPercentage by viewModel.apkDownloadPercentage.collectAsState()
                        val detectedWebErrors by viewModel.detectedWebErrors.collectAsState()
                        val detectedAndroidBuildErrors by viewModel.detectedAndroidBuildErrors.collectAsState()
                        val customModels by viewModel.customModels.collectAsState()
                        val selectedModelId by viewModel.selectedModelId.collectAsState()
                        val showGithubPushPrompt by viewModel.showGithubPushPrompt.collectAsState()
                        val writeFileConfirmInfo by viewModel.writeFileConfirmInfo.collectAsState()
                        val webPreviewRefreshTrigger by viewModel.webPreviewRefreshTrigger.collectAsState()
                        val detectedFramework by viewModel.detectedFramework.collectAsState()
                        val todoList by viewModel.todoList.collectAsState()
                        val isTodoListExpanded by viewModel.isTodoListExpanded.collectAsState()
                        val chatInputText by viewModel.chatInputText.collectAsState()
                        val attachedFiles by viewModel.attachedFiles.collectAsState()
                        val maxActionSteps by viewModel.maxActionSteps.collectAsState()
                        val allowBuildPush by viewModel.allowBuildPush.collectAsState()
                        val allowAutoFix by viewModel.allowAutoFix.collectAsState()
                        val allowBackgroundExecution by viewModel.allowBackgroundExecution.collectAsState()
                        val isLoadingWorkspace by viewModel.isLoadingWorkspace.collectAsState()
                        val scannedModels by viewModel.scannedModels.collectAsState()
                        val isScanningModels by viewModel.isScanningModels.collectAsState()
                        val scanError by viewModel.scanError.collectAsState()
                        val agentSkills by viewModel.agentSkills.collectAsState()
                        val isFetchingSkills by viewModel.isFetchingSkills.collectAsState()
                        val webArtifactInfo by viewModel.webArtifactInfo.collectAsState()
                        val isImportingFiles by viewModel.isImportingFiles.collectAsState()
                        val importProgress by viewModel.importProgress.collectAsState()
                        val importProgressMessage by viewModel.importProgressMessage.collectAsState()
                        val backupsList by viewModel.backupsList.collectAsState()
                        val executionElapsedTimeSeconds by viewModel.executionElapsedTimeSeconds.collectAsState()
                        val currentRunningModelName by viewModel.currentRunningModelName.collectAsState()
                        val liveSystemTokens by viewModel.liveSystemTokens.collectAsState()
                        val liveUserTokens by viewModel.liveUserTokens.collectAsState()
                        val liveToolTokens by viewModel.liveToolTokens.collectAsState()
                        val liveTotalInputTokens by viewModel.liveTotalInputTokens.collectAsState()
                        val liveTotalOutputTokens by viewModel.liveTotalOutputTokens.collectAsState()
                        val reasoningEffort by viewModel.reasoningEffort.collectAsState()

                        if (currentProject == null) {
                            HomeScreen(
                                projects = projectsList,
                                gitProgress = gitProgress,
                                onCreateProject = { name, desc, template, uris ->
                                    viewModel.createProject(name, desc, template, uris)
                                },
                                onUpdateProject = { oldName, newName, newDesc ->
                                    viewModel.updateProject(oldName, newName, newDesc)
                                },
                                onDeleteProject = { name ->
                                    viewModel.deleteProject(name)
                                },
                                onSelectProject = { project ->
                                    viewModel.selectProject(project)
                                },
                                onCloneProject = { name, repo, token, branch, onComplete ->
                                    viewModel.cloneGitRepo(name, repo, token, branch, onComplete)
                                }
                            )
                        } else {
                            WorkspaceScreen(
                                project = currentProject!!,
                                files = projectFiles,
                                activeFile = activeFile,
                                editorContent = editorContent,
                                chatMessages = chatMessages,
                                isThinking = isThinking,
                                agentStatus = agentStatus,
                                currentTab = currentTab,
                                aiActionLogs = aiActionLogs,
                                mcpServers = viewModel.mcpServers.collectAsState().value,
                                selectedMcpServerIds = viewModel.selectedMcpServerIds.collectAsState().value,
                                onToggleSelectMcpServer = { viewModel.toggleSelectMcpServer(it) },
                                onSelectAllConnectedMcpServers = { viewModel.selectAllConnectedMcpServers() },
                                onClearSelectedMcpServers = { viewModel.clearSelectedMcpServers() },
                                onAddMcpServer = { name, url, platform, apiKey -> viewModel.mcpManager.addServer(name, url, platform, apiKey) },
                                onToggleMcpWorkspace = { serverId, enabled -> viewModel.mcpManager.toggleWorkspaceForServer(serverId, currentProject?.name ?: "", enabled) },
                                onTestConnectMcp = { serverId -> viewModel.mcpManager.testAndConnectServer(serverId) },
                                onStartMcpOAuthFlow = { context, serverId, clientId -> viewModel.startMcpOAuthFlow(context, serverId, clientId) },
                                onDeleteMcpServer = { serverId -> viewModel.mcpManager.deleteServer(serverId) },
                                terminalOutput = terminalOutput,
                                isLoadingWorkspace = isLoadingWorkspace,
                                gitProgress = gitProgress,
                                customProvider = customProvider,
                                customApiKey = customApiKey,
                                customBaseUrl = customBaseUrl,
                                customModelId = customModelId,
                                useCustomModel = useCustomModel,
                                githubToken = githubToken,
                                githubRepo = githubRepo,
                                githubBranch = githubBranch,
                                gitHubWorkflows = gitHubWorkflows,
                                onTriggerWorkflows = { selectedIds -> viewModel.triggerWorkflows(selectedIds) },
                                explorerGithubToken = explorerGithubToken,
                                explorerGithubRepo = explorerGithubRepo,
                                explorerGithubBranch = explorerGithubBranch,
                                buildStatus = buildStatus,
                                buildSteps = buildSteps,
                                buildLogs = buildLogs,
                                buildError = buildError,
                                isPollingBuild = isPollingBuild,
                                apkDownloadProgress = apkDownloadProgress,
                                apkDownloadPercentage = apkDownloadPercentage,
                                onInstallApk = { viewModel.installApk() },
                                onSaveGithubRepo = { viewModel.saveGithubRepo(it) },
                                onSaveGithubBranch = { viewModel.saveGithubBranch(it) },
                                onSaveExplorerGithubRepo = { viewModel.saveExplorerGithubRepo(it) },
                                onSaveExplorerGithubBranch = { viewModel.saveExplorerGithubBranch(it) },
                                detectedWebErrors = detectedWebErrors,
                                detectedAndroidBuildErrors = detectedAndroidBuildErrors,
                                scannedModels = scannedModels,
                                isScanningModels = isScanningModels,
                                scanError = scanError,
                                onScanModels = { provider, apiKey, baseUrl -> viewModel.scanModels(provider, apiKey, baseUrl) },
                                onClearScannedModels = { viewModel.clearScannedModels() },
                                customModels = customModels,
                                selectedModelId = selectedModelId,
                                geminiModels = viewModel.geminiModels,
                                openaiModels = viewModel.openaiModels,
                                claudeModels = viewModel.claudeModels,
                                mistralModels = viewModel.mistralModels,
                                onAddCustomModel = { alias, provider, apiKey, baseUrl, modelId ->
                                    viewModel.addCustomModel(alias, provider, apiKey, baseUrl, modelId)
                                },
                                onDeleteCustomModel = { id ->
                                    viewModel.deleteCustomModel(id)
                                },
                                onSelectCustomModel = { id ->
                                    viewModel.selectModel(id)
                                },
                                showGithubPushPrompt = showGithubPushPrompt,
                                webPreviewRefreshTrigger = webPreviewRefreshTrigger,
                                detectedFramework = detectedFramework,
                                onDismissGithubPushPrompt = { viewModel.dismissGithubPushPrompt() },
                                onAcceptGithubPushPrompt = { viewModel.acceptGithubPushPrompt() },
                                todoList = todoList,
                                isTodoListExpanded = isTodoListExpanded,
                                onToggleTodoListExpanded = { viewModel.toggleTodoListExpanded() },
                                chatInputText = chatInputText,
                                onUpdateChatInputText = { viewModel.updateChatInputText(it) },
                                attachedFiles = attachedFiles,
                                onAddAttachedFile = { viewModel.addAttachedFile(it) },
                                onRemoveAttachedFile = { viewModel.removeAttachedFile(it) },
                                onClearAttachedFiles = { viewModel.clearAttachedFiles() },
                                agentSkills = agentSkills,
                                onToggleAgentSkill = { id, enabled -> viewModel.toggleAgentSkill(id, enabled) },
                                onInstallAgentSkill = { id -> viewModel.installAgentSkill(id) },
                                onUninstallAgentSkill = { id -> viewModel.uninstallAgentSkill(id) },
                                onAddCustomAgentSkill = { skill -> viewModel.addCustomAgentSkill(skill) },
                                onFetchOnlineAgentSkills = { viewModel.fetchOnlineAgentSkills() },
                                isFetchingSkills = isFetchingSkills,
                                onUpdateSkillContent = { id, content -> viewModel.updateSkillContent(id, content) },
                                onFetchSkillFileContent = { id, cb -> viewModel.fetchSkillFileContent(id, cb) },
                                webArtifactInfo = webArtifactInfo,
                                onPreviewWebArtifact = { viewModel.previewWebArtifact() },
                                onFetchLatestArtifact = { viewModel.fetchLatestArtifact() },
                                onToggleError = { id -> viewModel.toggleWebErrorSelection(id) },
                                onToggleAllErrors = { selectAll -> viewModel.toggleAllWebErrors(selectAll) },
                                onClearErrors = { viewModel.clearWebErrors() },
                                onFixErrors = { viewModel.fixSelectedWebErrors() },
                                onToggleAndroidBuildError = { id -> viewModel.toggleAndroidBuildErrorSelection(id) },
                                onToggleAllAndroidBuildErrors = { selectAll -> viewModel.toggleAllAndroidBuildErrors(selectAll) },
                                onClearAndroidBuildErrors = { viewModel.clearAndroidBuildErrors() },
                                onFixAndroidBuildErrors = { viewModel.fixSelectedAndroidBuildErrors() },
                                onWebError = { message, sourceId, lineNumber ->
                                    viewModel.addWebError(message, sourceId, lineNumber)
                                },
                                onSaveSettings = { provider, key, base, model, useCustom ->
                                    viewModel.saveCustomSettings(provider, key, base, model, useCustom)
                                },
                                maxActionSteps = maxActionSteps,
                                allowBuildPush = allowBuildPush,
                                allowAutoFix = allowAutoFix,
                                allowBackgroundExecution = allowBackgroundExecution,
                                onSaveAllowBuildPush = { viewModel.saveAllowBuildPush(it) },
                                onSaveAllowAutoFix = { viewModel.saveAllowAutoFix(it) },
                                onSaveAllowBackgroundExecution = { viewModel.saveAllowBackgroundExecution(it) },
                                onSaveMaxActionSteps = { viewModel.saveMaxActionSteps(it) },
                                executionElapsedTimeSeconds = executionElapsedTimeSeconds,
                                currentRunningModelName = currentRunningModelName,
                                liveSystemTokens = liveSystemTokens,
                                liveUserTokens = liveUserTokens,
                                liveToolTokens = liveToolTokens,
                                liveTotalInputTokens = liveTotalInputTokens,
                                liveTotalOutputTokens = liveTotalOutputTokens,
                                onSaveGithubToken = { token ->
                                    viewModel.saveGithubToken(token)
                                },
                                onSaveExplorerGithubToken = { token ->
                                    viewModel.saveExplorerGithubToken(token)
                                },
                                onPushGitRepo = { repo, token, branch, force, onComplete ->
                                    viewModel.pushGitRepo(currentProject!!.name, repo, token, branch, force, onComplete)
                                },
                                onTabSelected = { tab ->
                                    viewModel.changeTab(tab)
                                },
                                onBack = {
                                    viewModel.exitProject()
                                },
                                onSelectFile = { file ->
                                    viewModel.selectActiveFile(file)
                                },
                                onUpdateEditor = { updatedContent ->
                                    viewModel.updateEditorContent(updatedContent)
                                },
                                onSaveFile = {
                                    viewModel.saveActiveFile()
                                },
                                onCreateFile = { path ->
                                    viewModel.createNewFile(path)
                                },
                                onDeleteFile = { path ->
                                    viewModel.deleteCurrentFile(path)
                                },
                                onRenameFile = { old, new ->
                                    viewModel.renameFile(old, new)
                                },
                                onMoveFile = { old, new ->
                                    viewModel.moveFile(old, new)
                                },
                                onSendPrompt = { prompt, attachments ->
                                    viewModel.sendPrompt(prompt, attachments)
                                },
                                onImportFiles = { uris ->
                                    viewModel.importFilesFromDevice(uris)
                                },
                                onRefreshProjectFiles = {
                                    viewModel.refreshProjectFiles()
                                },
                                isImportingFiles = isImportingFiles,
                                importProgress = importProgress,
                                importProgressMessage = importProgressMessage,
                                backupsList = backupsList,
                                onRestoreBackup = { backup -> viewModel.restoreProjectVersion(backup) },
                                onDecompileApk = { apkPath ->
                                    viewModel.decompileApk(apkPath)
                                },
                                onTerminalCommand = { cmd ->
                                    viewModel.runTerminalCommand(cmd)
                                },
                                onClearTerminal = {
                                    viewModel.clearTerminal()
                                },
                                onDeleteMessage = { msg ->
                                    viewModel.deleteMessage(msg)
                                },
                                onEditMessage = { msg, content ->
                                    viewModel.editMessage(msg, content)
                                },
                                onRegenerate = { msg ->
                                    viewModel.regenerateResponse(msg)
                                },
                                isInterrupted = viewModel.isInterrupted.collectAsStateWithLifecycle().value,
                                interruptionReason = viewModel.interruptionReason.collectAsStateWithLifecycle().value,
                                onContinue = {
                                    viewModel.continuePrompt()
                                },
                                onSkip = {
                                    viewModel.skipInterruption()
                                },
                                onStopAI = {
                                    viewModel.stopThinking()
                                },
                                webConsoleLogs = viewModel.webConsoleLogs.collectAsState().value,
                                onAddWebConsoleLog = { msg, lvl, src, line ->
                                    viewModel.addWebConsoleLog(msg, lvl, src, line)
                                },
                                onClearWebConsoleLogs = {
                                    viewModel.clearWebConsoleLogs()
                                },
                                reasoningEffort = reasoningEffort,
                                onSelectReasoningEffort = { viewModel.setReasoningEffort(it) }
                            )
                        }

                        if (writeFileConfirmInfo != null) {
                            AlertDialog(
                                onDismissRequest = { viewModel.rejectWriteFile() },
                                title = { Text("Overwrite Confirmation") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("The AI is trying to use the 'write_file' tool to overwrite/recreate the file:")
                                        Text(
                                            text = writeFileConfirmInfo!!.path,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text("This file is large (${writeFileConfirmInfo!!.existingLinesCount} lines).")
                                        Text("Are you sure you want to allow the AI to completely overwrite and recreate this file?")
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = { viewModel.approveWriteFile() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Allow (অনুমতি দিন)", color = Color.White)
                                    }
                                },
                                dismissButton = {
                                    OutlinedButton(onClick = { viewModel.rejectWriteFile() }) {
                                        Text("Deny (প্রত্যাখ্যান করুন)")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            mainViewModel?.handleMcpOAuthCallback(uri)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalHttpServer.stop()
    }
}
