package com.example.chat

import android.content.Context
import com.example.data.ChatMessageEntity
import com.example.ui.AiActionLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Data model for a distinct chat session / thread within a project.
 */
data class ProjectChatSession(
    val id: String = UUID.randomUUID().toString(),
    val projectName: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val previewSnippet: String = ""
)

/**
 * Thread-safe persistent manager for multiple chat sessions inside a project.
 * Allows users to create, switch, persist, and delete independent chat sessions per project.
 */
object ProjectChatSessionManager {

    private fun getChatDir(context: Context, projectName: String): File {
        val sanitized = projectName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val dir = File(context.filesDir, "project_chats/$sanitized")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getMetadataFile(context: Context, projectName: String): File {
        return File(getChatDir(context, projectName), "sessions.json")
    }

    private fun getSessionDataFile(context: Context, projectName: String, sessionId: String): File {
        return File(getChatDir(context, projectName), "$sessionId.json")
    }

    /**
     * Formats timestamp into clean user-friendly format matching screenshot: "28 Oct, 14:52"
     */
    fun formatSessionDate(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("d MMM, HH:mm", Locale.ENGLISH)
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "Recent"
        }
    }

    /**
     * Retrieves all chat sessions for the specified project.
     * If no session exists yet, bootstraps an initial session with existing messages (if provided).
     */
    @Synchronized
    fun getSessions(
        context: Context,
        projectName: String,
        fallbackMessages: List<ChatMessageEntity> = emptyList()
    ): List<ProjectChatSession> {
        val file = getMetadataFile(context, projectName)
        if (!file.exists()) {
            // First time initialization: create initial session
            val initialTitle = fallbackMessages.firstOrNull { it.role == "user" }?.content?.take(30)
                ?: "Main Chat"
            val initialSession = ProjectChatSession(
                id = UUID.randomUUID().toString(),
                projectName = projectName,
                title = initialTitle,
                timestamp = System.currentTimeMillis(),
                messageCount = fallbackMessages.size,
                previewSnippet = fallbackMessages.lastOrNull()?.content?.take(60) ?: ""
            )
            saveSessionsList(context, projectName, listOf(initialSession), initialSession.id)
            if (fallbackMessages.isNotEmpty()) {
                saveSessionData(context, projectName, initialSession.id, fallbackMessages, emptyList())
            }
            return listOf(initialSession)
        }

        return try {
            val jsonStr = file.readText()
            val obj = JSONObject(jsonStr)
            val arr = obj.optJSONArray("sessions") ?: JSONArray()
            val list = mutableListOf<ProjectChatSession>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                list.add(
                    ProjectChatSession(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        projectName = projectName,
                        title = item.optString("title", "Chat"),
                        timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                        messageCount = item.optInt("messageCount", 0),
                        previewSnippet = item.optString("previewSnippet", "")
                    )
                )
            }
            // Sort by most recently active
            list.sortedByDescending { it.timestamp }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /**
     * Retrieves currently active session ID for project.
     */
    @Synchronized
    fun getActiveSessionId(context: Context, projectName: String): String? {
        val file = getMetadataFile(context, projectName)
        if (!file.exists()) return null
        return try {
            val jsonStr = file.readText()
            val obj = JSONObject(jsonStr)
            obj.optString("activeSessionId", null)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Sets active session ID for project.
     */
    @Synchronized
    fun setActiveSessionId(context: Context, projectName: String, sessionId: String) {
        val currentSessions = getSessions(context, projectName)
        saveSessionsList(context, projectName, currentSessions, sessionId)
    }

    /**
     * Creates a new chat session for this project and sets it as active.
     */
    @Synchronized
    fun createNewSession(context: Context, projectName: String, initialTitle: String? = null): ProjectChatSession {
        val existing = getSessions(context, projectName).toMutableList()
        val title = initialTitle?.take(30)?.trim()?.ifBlank { null } ?: "New Chat"
        val newSession = ProjectChatSession(
            id = UUID.randomUUID().toString(),
            projectName = projectName,
            title = title,
            timestamp = System.currentTimeMillis(),
            messageCount = 0,
            previewSnippet = ""
        )
        existing.add(0, newSession)
        saveSessionsList(context, projectName, existing, newSession.id)
        // Write empty session data
        saveSessionData(context, projectName, newSession.id, emptyList(), emptyList())
        return newSession
    }

    /**
     * Updates session title, usually when user inputs the first prompt.
     */
    @Synchronized
    fun updateSessionTitle(context: Context, projectName: String, sessionId: String, prompt: String) {
        val sessions = getSessions(context, projectName).map { s ->
            if (s.id == sessionId && (s.title == "New Chat" || s.title == "Main Chat" || s.title.isBlank())) {
                val cleanTitle = prompt.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: prompt.trim()
                val display = if (cleanTitle.length > 28) cleanTitle.take(28) + "..." else cleanTitle
                s.copy(title = display, timestamp = System.currentTimeMillis())
            } else s
        }
        val activeId = getActiveSessionId(context, projectName) ?: sessionId
        saveSessionsList(context, projectName, sessions, activeId)
    }

    /**
     * Saves messages and logs of a session to disk.
     */
    @Synchronized
    fun saveSessionData(
        context: Context,
        projectName: String,
        sessionId: String,
        messages: List<ChatMessageEntity>,
        logs: List<AiActionLog>
    ) {
        try {
            val file = getSessionDataFile(context, projectName, sessionId)
            val root = JSONObject()
            
            val msgsArr = JSONArray()
            for (msg in messages) {
                val m = JSONObject()
                m.put("id", msg.id)
                m.put("projectName", msg.projectName)
                m.put("role", msg.role)
                m.put("content", msg.content)
                m.put("timestamp", msg.timestamp)
                m.put("modelName", msg.modelName)
                m.put("aiActionLogsJson", msg.aiActionLogsJson)
                msgsArr.put(m)
            }
            root.put("messages", msgsArr)

            val logsArr = JSONArray()
            for (log in logs) {
                val l = JSONObject()
                l.put("id", log.id)
                l.put("title", log.title)
                l.put("status", log.status)
                l.put("timestamp", log.timestamp)
                l.put("details", log.details)
                l.put("lineRange", log.lineRange)
                l.put("modelName", log.modelName)
                l.put("durationMillis", log.durationMillis)
                logsArr.put(l)
            }
            root.put("logs", logsArr)

            file.writeText(root.toString())

            // Update session count & snippet in sessions list
            val sessions = getSessions(context, projectName).map { s ->
                if (s.id == sessionId) {
                    s.copy(
                        messageCount = messages.size,
                        previewSnippet = messages.lastOrNull()?.content?.take(60) ?: "",
                        timestamp = messages.lastOrNull()?.timestamp ?: s.timestamp
                    )
                } else s
            }
            val activeId = getActiveSessionId(context, projectName) ?: sessionId
            saveSessionsList(context, projectName, sessions, activeId)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    /**
     * Loads messages and logs for a session from disk.
     */
    @Synchronized
    fun loadSessionData(
        context: Context,
        projectName: String,
        sessionId: String
    ): Pair<List<ChatMessageEntity>, List<AiActionLog>> {
        val file = getSessionDataFile(context, projectName, sessionId)
        if (!file.exists()) return Pair(emptyList(), emptyList())
        return try {
            val root = JSONObject(file.readText())
            val msgsArr = root.optJSONArray("messages") ?: JSONArray()
            val msgs = mutableListOf<ChatMessageEntity>()
            for (i in 0 until msgsArr.length()) {
                val m = msgsArr.getJSONObject(i)
                msgs.add(
                    ChatMessageEntity(
                        id = m.optInt("id", 0),
                        projectName = m.optString("projectName", projectName),
                        role = m.optString("role", "user"),
                        content = m.optString("content", ""),
                        timestamp = m.optLong("timestamp", System.currentTimeMillis()),
                        modelName = if (m.has("modelName")) m.optString("modelName") else null,
                        aiActionLogsJson = if (m.has("aiActionLogsJson")) m.optString("aiActionLogsJson") else null
                    )
                )
            }

            val logsArr = root.optJSONArray("logs") ?: JSONArray()
            val logs = mutableListOf<AiActionLog>()
            for (i in 0 until logsArr.length()) {
                val l = logsArr.getJSONObject(i)
                logs.add(
                    AiActionLog(
                        id = l.optString("id", UUID.randomUUID().toString()),
                        title = l.optString("title", ""),
                        status = l.optString("status", "success"),
                        timestamp = l.optLong("timestamp", System.currentTimeMillis()),
                        details = if (l.has("details")) l.optString("details") else null,
                        lineRange = if (l.has("lineRange")) l.optString("lineRange") else null,
                        modelName = if (l.has("modelName")) l.optString("modelName") else null,
                        durationMillis = if (l.has("durationMillis")) l.optLong("durationMillis") else null
                    )
                )
            }
            Pair(msgs, logs)
        } catch (t: Throwable) {
            Pair(emptyList(), emptyList())
        }
    }

    /**
     * Deletes a session and its associated storage file.
     */
    @Synchronized
    fun deleteSession(context: Context, projectName: String, sessionId: String): String? {
        try {
            val file = getSessionDataFile(context, projectName, sessionId)
            if (file.exists()) file.delete()

            val sessions = getSessions(context, projectName).filter { it.id != sessionId }
            var activeId = getActiveSessionId(context, projectName)
            if (activeId == sessionId) {
                activeId = sessions.firstOrNull()?.id
            }
            saveSessionsList(context, projectName, sessions, activeId)
            return activeId
        } catch (t: Throwable) {
            return null
        }
    }

    private fun saveSessionsList(
        context: Context,
        projectName: String,
        sessions: List<ProjectChatSession>,
        activeSessionId: String?
    ) {
        try {
            val root = JSONObject()
            root.put("activeSessionId", activeSessionId ?: "")
            val arr = JSONArray()
            for (s in sessions) {
                val obj = JSONObject()
                obj.put("id", s.id)
                obj.put("title", s.title)
                obj.put("timestamp", s.timestamp)
                obj.put("messageCount", s.messageCount)
                obj.put("previewSnippet", s.previewSnippet)
                arr.put(obj)
            }
            root.put("sessions", arr)
            getMetadataFile(context, projectName).writeText(root.toString())
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
