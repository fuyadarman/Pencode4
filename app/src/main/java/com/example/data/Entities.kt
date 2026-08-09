package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val name: String,
    val description: String,
    val createdAt: Long,
    val templateKey: String? = null
)

@Entity(
    tableName = "project_files",
    indices = [Index(value = ["projectName", "path"], unique = true)]
)
data class ProjectFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectName: String,
    val path: String,
    val content: String
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectName: String,
    val role: String, // "user", "assistant"
    val content: String,
    val timestamp: Long,
    val isThinking: Boolean = false,
    val fileActionsJson: String? = null, // Stored as JSON array: [{"type":"create","path":"index.html"}]
    val aiActionLogsJson: String? = null, // Stored as JSON array of AiActionLog
    val modelName: String? = null,
    val executionTimeSeconds: Long = 0,
    val systemTokens: Int = 0,
    val userTokens: Int = 0,
    val toolTokens: Int = 0,
    val historyTokens: Int = 0,
    val skillTokens: Int = 0,
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0
)
