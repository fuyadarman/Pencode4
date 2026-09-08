package com.example.agent

import com.example.data.ChatMessageEntity
import com.example.data.ProjectEntity
import com.example.data.VibeRepository
import com.example.ui.AiActionLog
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File
import java.util.UUID

object AgentTaskFinalizer {

    suspend fun finalizeTask(
        projectName: String,
        finishMsg: String,
        repository: VibeRepository,
        moshi: Moshi,
        currentLogs: List<AiActionLog>,
        hasCodeChanges: Boolean,
        allowBuildPush: Boolean,
        onAddLog: (AiActionLog) -> Unit,
        onUpdateStatus: (String) -> Unit,
        onUpdateChats: (List<ChatMessageEntity>) -> Unit,
        onTriggerBuildPush: (framework: String, autoAccept: Boolean) -> Unit,
        onHideBuildPushPrompt: () -> Unit
    ) {
        val logEntry = AiActionLog(
            id = UUID.randomUUID().toString(),
            title = "AI finished task execution",
            status = "success",
            details = finishMsg,
            timestamp = System.currentTimeMillis()
        )
        onAddLog(logEntry)

        val updatedLogs = currentLogs + logEntry
        val logsJson = try {
            val listType = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
            moshi.adapter<List<AiActionLog>>(listType).toJson(updatedLogs)
        } catch (e: Exception) {
            null
        }

        val cleanMessage = finishMsg.replace(Regex("(?i)</?tool_call>"), "")
            .replace(Regex("(?i)</?function_call>"), "")
            .trim()
        val agentMsg = ChatMessageEntity(
            projectName = projectName,
            role = "assistant",
            content = if (cleanMessage.isNotBlank()) cleanMessage else "Task completed successfully!",
            timestamp = System.currentTimeMillis(),
            aiActionLogsJson = logsJson
        )
        repository.insertChatMessage(agentMsg)
        onUpdateChats(repository.getChatsForProject(projectName))

        onUpdateStatus("Changes applied successfully!")

        if (hasCodeChanges) {
            val projectDir = repository.getProjectDir(projectName)
            val pFiles = repository.getFilesForProject(projectName)
            val isKotlin = File(projectDir, "build.gradle.kts").exists() || File(projectDir, "build.gradle").exists() || pFiles.any { it.path.endsWith("build.gradle.kts") || it.path.endsWith("build.gradle") }
            val isFlutter = File(projectDir, "pubspec.yaml").exists() || pFiles.any { it.path.endsWith("pubspec.yaml") }
            val isNextJs = File(projectDir, "next.config.js").exists() || File(projectDir, "next.config.mjs").exists() || pFiles.any { it.path.contains("next.config") }
            val isReactVite = File(projectDir, "vite.config.js").exists() || File(projectDir, "vite.config.ts").exists() || pFiles.any { it.path.contains("vite.config") || (it.path.endsWith("package.json") && it.content.contains("vite", ignoreCase = true)) }
            val isWebPackage = File(projectDir, "package.json").exists() || pFiles.any { it.path.endsWith("package.json") }

            when {
                isKotlin -> onTriggerBuildPush("Kotlin/Android", allowBuildPush)
                isFlutter -> onTriggerBuildPush("Flutter", allowBuildPush)
                isNextJs -> onTriggerBuildPush("Next.js", allowBuildPush)
                isReactVite -> onTriggerBuildPush("React Vite", allowBuildPush)
                isWebPackage -> onTriggerBuildPush("Web App", allowBuildPush)
            }
        } else {
            onHideBuildPushPrompt()
        }
    }
}
