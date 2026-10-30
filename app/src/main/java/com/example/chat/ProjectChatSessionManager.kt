package com.example.chat

import android.content.Context
import com.example.data.ChatMessageEntity
import com.example.data.VibeRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class ChatSessionHeader(
    val id: String,
    val title: String,
    val timestamp: Long,
    val messageCount: Int = 0
)

typealias ProjectChatSession = ChatSessionHeader

/**
 * ProjectChatSessionManager
 * Manages multiple chat sessions within a single project.
 * Supports "Start new chat" and switching between recent chat sessions.
 */
object ProjectChatSessionManager {

    private const val PREFS_NAME = "project_chat_sessions_prefs"
    private const val SESSIONS_PREFIX = "chat_sessions_"
    private const val CURRENT_SESSION_PREFIX = "current_session_id_"

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, ChatSessionHeader::class.java)
    private val adapter = moshi.adapter<List<ChatSessionHeader>>(listType)

    private val _currentSessions = MutableStateFlow<List<ChatSessionHeader>>(emptyList())
    val currentSessions: StateFlow<List<ChatSessionHeader>> = _currentSessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private fun sanitize(projectName: String): String {
        return projectName.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
    }

    fun formatSessionDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun loadSessions(context: Context, projectName: String) {
        if (projectName.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(SESSIONS_PREFIX + sanitize(projectName), null)
        val activeId = prefs.getString(CURRENT_SESSION_PREFIX + sanitize(projectName), null)

        val list = if (!json.isNullOrBlank()) {
            try {
                adapter.fromJson(json) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        _currentSessions.value = list
        _activeSessionId.value = activeId
    }

    private fun saveSessions(context: Context, projectName: String, list: List<ChatSessionHeader>, activeId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = adapter.toJson(list)
        prefs.edit()
            .putString(SESSIONS_PREFIX + sanitize(projectName), json)
            .putString(CURRENT_SESSION_PREFIX + sanitize(projectName), activeId)
            .apply()
        _currentSessions.value = list
        _activeSessionId.value = activeId
    }

    /**
     * Start a new chat session for this project.
     * Archives current messages and returns new session ID.
     */
    suspend fun startNewChat(
        context: Context,
        projectName: String,
        repository: VibeRepository
    ): String = withContext(Dispatchers.IO) {
        val currentMessages = repository.getChatsForProject(projectName)
        val list = _currentSessions.value.toMutableList()

        // If there were existing messages, archive them as a session header if not already saved
        if (currentMessages.isNotEmpty()) {
            val firstUserPrompt = currentMessages.firstOrNull { it.role == "user" }?.content ?: "Conversation"
            val cleanTitle = firstUserPrompt.lines().firstOrNull()?.take(32)?.trim() ?: "Chat Session"
            val existingActiveId = _activeSessionId.value ?: UUID.randomUUID().toString()

            val existingIndex = list.indexOfFirst { it.id == existingActiveId }
            val header = ChatSessionHeader(
                id = existingActiveId,
                title = cleanTitle,
                timestamp = currentMessages.lastOrNull()?.timestamp ?: System.currentTimeMillis(),
                messageCount = currentMessages.size
            )
            if (existingIndex >= 0) {
                list[existingIndex] = header
            } else {
                list.add(0, header)
            }

            // Save archived messages file
            saveSessionMessagesToDisk(context, projectName, existingActiveId, currentMessages)
        }

        // Create new session ID and clear current active chat table
        val newSessionId = UUID.randomUUID().toString()
        repository.clearChatsForProject(projectName)

        saveSessions(context, projectName, list, newSessionId)
        newSessionId
    }

    /**
     * Switch to an existing chat session.
     */
    suspend fun switchSession(
        context: Context,
        projectName: String,
        targetSessionId: String,
        repository: VibeRepository
    ): Unit = withContext(Dispatchers.IO) {
        val currentActiveId = _activeSessionId.value
        if (currentActiveId == targetSessionId) return@withContext

        // Archive current chat messages
        val currentMessages = repository.getChatsForProject(projectName)
        if (currentMessages.isNotEmpty() && !currentActiveId.isNullOrBlank()) {
            saveSessionMessagesToDisk(context, projectName, currentActiveId, currentMessages)
        }

        // Load target session messages from disk
        val restoredMessages = loadSessionMessagesFromDisk(context, projectName, targetSessionId)
        repository.clearChatsForProject(projectName)
        for (msg in restoredMessages) {
            repository.insertChatMessage(msg)
        }

        val list = _currentSessions.value
        saveSessions(context, projectName, list, targetSessionId)
    }

    private fun saveSessionMessagesToDisk(context: Context, projectName: String, sessionId: String, messages: List<ChatMessageEntity>) {
        try {
            val dir = java.io.File(context.filesDir, "chat_sessions/${sanitize(projectName)}")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "session_$sessionId.json")
            val msgAdapter = moshi.adapter<List<ChatMessageEntity>>(Types.newParameterizedType(List::class.java, ChatMessageEntity::class.java))
            file.writeText(msgAdapter.toJson(messages))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSessionMessagesFromDisk(context: Context, projectName: String, sessionId: String): List<ChatMessageEntity> {
        return try {
            val file = java.io.File(context.filesDir, "chat_sessions/${sanitize(projectName)}/session_$sessionId.json")
            if (file.exists()) {
                val msgAdapter = moshi.adapter<List<ChatMessageEntity>>(Types.newParameterizedType(List::class.java, ChatMessageEntity::class.java))
                msgAdapter.fromJson(file.readText()) ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
