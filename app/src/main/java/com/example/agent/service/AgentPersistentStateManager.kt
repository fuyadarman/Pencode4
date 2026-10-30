package com.example.agent.service

import android.content.Context
import com.example.ui.AiActionLog
import com.example.ui.TodoItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AgentPersistentStateManager
 * Persists running AI action logs, active todo list, and background agent execution
 * state directly to device storage so state is never wiped when leaving the app.
 */
object AgentPersistentStateManager {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val logListType = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
    private val logAdapter = moshi.adapter<List<AiActionLog>>(logListType)

    private val todoListType = Types.newParameterizedType(List::class.java, TodoItem::class.java)
    private val todoAdapter = moshi.adapter<List<TodoItem>>(todoListType)

    private fun sanitize(projectName: String): String {
        return projectName.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
    }

    private fun getStorageDir(context: Context, projectName: String): File {
        val dir = File(context.filesDir, "agent_state/${sanitize(projectName)}")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun saveState(
        context: Context,
        projectName: String,
        logs: List<AiActionLog>,
        todos: List<TodoItem>,
        agentStatus: String
    ) = withContext(Dispatchers.IO) {
        try {
            val dir = getStorageDir(context, projectName)
            File(dir, "action_logs.json").writeText(logAdapter.toJson(logs))
            File(dir, "todo_list.json").writeText(todoAdapter.toJson(todos))
            File(dir, "status.txt").writeText(agentStatus)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun restoreLogs(context: Context, projectName: String): List<AiActionLog> = withContext(Dispatchers.IO) {
        try {
            val file = File(getStorageDir(context, projectName), "action_logs.json")
            if (file.exists()) {
                logAdapter.fromJson(file.readText()) ?: emptyList()
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun restoreTodos(context: Context, projectName: String): List<TodoItem> = withContext(Dispatchers.IO) {
        try {
            val file = File(getStorageDir(context, projectName), "todo_list.json")
            if (file.exists()) {
                todoAdapter.fromJson(file.readText()) ?: emptyList()
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun restoreStatus(context: Context, projectName: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File(getStorageDir(context, projectName), "status.txt")
            if (file.exists()) file.readText() else "Ready"
        } catch (_: Exception) {
            "Ready"
        }
    }
}
