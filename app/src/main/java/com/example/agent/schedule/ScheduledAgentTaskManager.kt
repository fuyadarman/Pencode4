package com.example.agent.schedule

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ScheduledAgentTaskManager
 * Autonomous scheduler for PenCode AI tasks (shopping alerts, job searches, social posts, web jobs).
 */
object ScheduledAgentTaskManager {

    private const val PREFS_NAME = "pencode_scheduled_tasks_prefs"
    private const val KEY_TASKS = "scheduled_tasks"

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val taskListType = Types.newParameterizedType(List::class.java, ScheduledAgentTask::class.java)
    private val taskAdapter = moshi.adapter<List<ScheduledAgentTask>>(taskListType)

    private val _tasks = MutableStateFlow<List<ScheduledAgentTask>>(emptyList())
    val tasks: StateFlow<List<ScheduledAgentTask>> = _tasks.asStateFlow()

    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var tickerJob: Job? = null

    var onExecuteTaskCallback: ((ScheduledAgentTask) -> Unit)? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        loadTasks()
        startSchedulerTicker()
        scheduleNextAlarm()
    }

    private fun scheduleNextAlarm() {
        val ctx = appContext ?: return
        val nextTask = _tasks.value.filter { it.isActive && it.triggerTimeMillis > System.currentTimeMillis() }
            .minByOrNull { it.triggerTimeMillis }
        if (nextTask != null) {
            ScheduledTaskAlarmReceiver.scheduleAlarm(ctx, nextTask.triggerTimeMillis)
        }
    }

    private fun loadTasks() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TASKS, null)
        if (!json.isNullOrBlank()) {
            try {
                val list = taskAdapter.fromJson(json) ?: emptyList()
                _tasks.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveTasks() {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = taskAdapter.toJson(_tasks.value)
            prefs.edit().putString(KEY_TASKS, json).apply()
            scheduleNextAlarm()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addTask(task: ScheduledAgentTask) {
        val current = _tasks.value.toMutableList()
        current.add(0, task)
        _tasks.value = current
        saveTasks()
    }

    fun removeTask(taskId: String) {
        val current = _tasks.value.filter { it.id != taskId }
        _tasks.value = current
        saveTasks()
    }

    fun toggleTaskActive(taskId: String, isActive: Boolean) {
        val current = _tasks.value.map {
            if (it.id == taskId) it.copy(isActive = isActive) else it
        }
        _tasks.value = current
        saveTasks()
    }

    fun recordExecution(taskId: String, result: String) {
        val now = System.currentTimeMillis()
        val current = _tasks.value.map { task ->
            if (task.id == taskId) {
                if (task.isRecurring && (task.intervalMinutes ?: 0L) > 0) {
                    val nextTime = now + (task.intervalMinutes!! * 60 * 1000)
                    task.copy(
                        triggerTimeMillis = nextTime,
                        lastExecutedMillis = now,
                        lastExecutionResult = result
                    )
                } else {
                    task.copy(
                        isActive = false,
                        lastExecutedMillis = now,
                        lastExecutionResult = result
                    )
                }
            } else task
        }
        _tasks.value = current
        saveTasks()
    }

    private fun startSchedulerTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(15_000) // Check every 15 seconds
                checkAndRunDueTasks()
            }
        }
    }

    private suspend fun checkAndRunDueTasks() {
        val now = System.currentTimeMillis()
        val dueTasks = _tasks.value.filter { it.isActive && it.triggerTimeMillis <= now }
        for (task in dueTasks) {
            withContext(Dispatchers.Main) {
                onExecuteTaskCallback?.invoke(task)
            }
            recordExecution(task.id, "Executed at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(now))}")
        }
    }
}
