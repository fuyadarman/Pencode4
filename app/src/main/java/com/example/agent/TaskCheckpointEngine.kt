package com.example.agent

import android.util.Log
import com.example.data.VibeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * TaskCheckpointEngine
 *
 * Implements Structured Task State / Checkpoint persistence via `task.json`.
 * Automatically updates completed steps, active blockers, next actions, and modified files
 * after every tool execution. Enables reliable resumption after app crashes, interruptions,
 * or user pauses.
 */
object TaskCheckpointEngine {

    private const val TASK_FILE_NAME = "task.json"

    data class CheckpointState(
        val goal: String = "",
        val status: String = "in_progress", // "in_progress", "paused", "blocked", "completed", "interrupted"
        val phase: String = "implementation",
        val completed: List<String> = emptyList(),
        val currentStep: String = "",
        val filesChanged: List<String> = emptyList(),
        val blocker: String? = null,
        val nextAction: String = "",
        val lastTool: String? = null,
        val lastResult: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Initializes or updates an active task checkpoint at the start of a prompt.
     */
    fun startOrUpdateTask(
        projectName: String,
        projectDir: File,
        repository: VibeRepository,
        goal: String,
        isContinuation: Boolean = false
    ): CheckpointState {
        val existing = loadCheckpoint(projectName, projectDir)
        val state = if (isContinuation && existing != null) {
            existing.copy(
                status = "in_progress",
                timestamp = System.currentTimeMillis()
            )
        } else {
            val existingCompleted = if (isContinuation) existing?.completed ?: emptyList() else emptyList()
            val existingFiles = if (isContinuation) existing?.filesChanged ?: emptyList() else emptyList()
            CheckpointState(
                goal = goal.take(400),
                status = "in_progress",
                phase = "implementation",
                completed = existingCompleted,
                currentStep = "Initiating execution",
                filesChanged = existingFiles,
                blocker = null,
                nextAction = "Inspect project and begin operations",
                timestamp = System.currentTimeMillis()
            )
        }
        persistCheckpoint(projectName, projectDir, repository, state)
        return state
    }

    /**
     * Records a completed or attempted tool operation into task.json.
     */
    fun recordOperation(
        projectName: String,
        projectDir: File,
        repository: VibeRepository,
        tool: String,
        targetFile: String?,
        isSuccess: Boolean,
        summary: String,
        nextActionHint: String? = null
    ): CheckpointState {
        val current = loadCheckpoint(projectName, projectDir) ?: CheckpointState(
            goal = "Active coding task",
            status = "in_progress",
            timestamp = System.currentTimeMillis()
        )

        val updatedFiles = current.filesChanged.toMutableList()
        if (!targetFile.isNullOrBlank()) {
            val cleanPath = targetFile.trim().removePrefix("/")
            if (!updatedFiles.contains(cleanPath)) {
                updatedFiles.add(cleanPath)
            }
        }

        val updatedCompleted = current.completed.toMutableList()
        val completedEntry = when (tool.lowercase()) {
            "create_file", "write_file" -> "Created file: ${targetFile ?: "file"}"
            "edit_file", "multi_edit_file", "patch_file" -> "Modified file: ${targetFile ?: "file"}"
            "delete_file" -> "Deleted file: ${targetFile ?: "file"}"
            "run_command", "shell_exec" -> "Ran command: ${summary.take(80)}"
            "trigger_build" -> "Build completed: ${summary.take(80)}"
            else -> "$tool: ${summary.take(80)}"
        }

        if (isSuccess && !updatedCompleted.contains(completedEntry)) {
            updatedCompleted.add(completedEntry)
        }

        val blockerVal = if (!isSuccess) summary.take(300) else current.blocker
        val statusVal = if (!isSuccess) "blocked" else "in_progress"
        val nextActionVal = nextActionHint?.ifBlank { null }
            ?: if (!isSuccess) "Fix error: ${summary.take(100)}" else "Proceed with next implementation step"

        val updatedState = current.copy(
            status = statusVal,
            completed = updatedCompleted,
            currentStep = completedEntry,
            filesChanged = updatedFiles,
            blocker = blockerVal,
            nextAction = nextActionVal,
            lastTool = tool,
            lastResult = if (isSuccess) "success" else "error: ${summary.take(150)}",
            timestamp = System.currentTimeMillis()
        )

        persistCheckpoint(projectName, projectDir, repository, updatedState)
        return updatedState
    }

    /**
     * Records a task interruption or manual pause.
     */
    fun recordInterruption(
        projectName: String,
        projectDir: File,
        repository: VibeRepository,
        reason: String
    ): CheckpointState {
        val current = loadCheckpoint(projectName, projectDir) ?: CheckpointState(
            goal = "Active coding task",
            status = "interrupted",
            timestamp = System.currentTimeMillis()
        )

        val updatedState = current.copy(
            status = "interrupted",
            blocker = reason.take(200),
            nextAction = "Resume remaining tasks and resolve blocker",
            timestamp = System.currentTimeMillis()
        )

        persistCheckpoint(projectName, projectDir, repository, updatedState)
        return updatedState
    }

    /**
     * Records task pause when user asks a side question.
     */
    fun pauseTaskForQuestion(
        projectName: String,
        projectDir: File,
        repository: VibeRepository,
        question: String
    ): CheckpointState {
        val current = loadCheckpoint(projectName, projectDir) ?: CheckpointState(
            goal = "Active coding task",
            status = "paused",
            timestamp = System.currentTimeMillis()
        )

        val updatedState = current.copy(
            status = "paused",
            currentStep = "Paused: Answering user question (${question.take(60)})",
            nextAction = "Resume task after answering question",
            timestamp = System.currentTimeMillis()
        )

        persistCheckpoint(projectName, projectDir, repository, updatedState)
        return updatedState
    }

    /**
     * Records full completion of the task.
     */
    fun recordComplete(
        projectName: String,
        projectDir: File,
        repository: VibeRepository,
        summary: String
    ): CheckpointState {
        val current = loadCheckpoint(projectName, projectDir) ?: CheckpointState(
            goal = "Task completed",
            timestamp = System.currentTimeMillis()
        )

        val updatedState = current.copy(
            status = "completed",
            currentStep = "Completed",
            blocker = null,
            nextAction = "None (Task finished)",
            lastResult = summary.take(200),
            timestamp = System.currentTimeMillis()
        )

        persistCheckpoint(projectName, projectDir, repository, updatedState)
        return updatedState
    }

    /**
     * Loads the current task checkpoint from task.json on disk or in repository.
     */
    fun loadCheckpoint(projectName: String, projectDir: File): CheckpointState? {
        val file = File(projectDir, TASK_FILE_NAME)
        if (!file.exists() || !file.isFile) return null

        return try {
            val jsonStr = file.readText()
            parseCheckpointJson(jsonStr)
        } catch (e: Exception) {
            Log.e("TaskCheckpointEngine", "Failed reading $TASK_FILE_NAME", e)
            null
        }
    }

    /**
     * Persists the checkpoint data to task.json on disk and into the database via VibeRepository.
     */
    fun persistCheckpoint(
        projectName: String,
        projectDir: File,
        repository: VibeRepository,
        state: CheckpointState
    ) {
        try {
            val jsonObject = JSONObject().apply {
                put("goal", state.goal)
                put("status", state.status)
                put("phase", state.phase)
                put("current_step", state.currentStep)
                put("blocker", state.blocker ?: JSONObject.NULL)
                put("next_action", state.nextAction)
                put("timestamp", state.timestamp)

                val completedArr = JSONArray()
                state.completed.forEach { completedArr.put(it) }
                put("completed", completedArr)

                val filesArr = JSONArray()
                state.filesChanged.forEach { filesArr.put(it) }
                put("files_changed", filesArr)

                val lastActionObj = JSONObject().apply {
                    put("tool", state.lastTool ?: JSONObject.NULL)
                }
                put("last_action", lastActionObj)

                val lastResultObj = JSONObject().apply {
                    put("result", state.lastResult ?: JSONObject.NULL)
                }
                put("last_result", lastResultObj)
            }

            val jsonString = jsonObject.toString(2)
            
            // 1. Write physically to disk
            val file = File(projectDir, TASK_FILE_NAME)
            file.parentFile?.mkdirs()
            file.writeText(jsonString)

            // 2. Persist asynchronously to Room repository so project files stay in sync
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launchSilently {
                repository.saveFile(projectName, TASK_FILE_NAME, jsonString)
            }
        } catch (e: Exception) {
            Log.e("TaskCheckpointEngine", "Failed persisting $TASK_FILE_NAME", e)
        }
    }

    /**
     * Builds structured prompt text for resuming a task from checkpoint.
     */
    fun buildResumptionPrompt(checkpoint: CheckpointState): String {
        val completedList = if (checkpoint.completed.isNotEmpty()) {
            checkpoint.completed.joinToString("\n") { "  ✓ $it" }
        } else {
            "  (No completed operations recorded yet)"
        }

        val filesList = if (checkpoint.filesChanged.isNotEmpty()) {
            checkpoint.filesChanged.joinToString("\n") { "  • $it" }
        } else {
            "  (None)"
        }

        val blockerInfo = checkpoint.blocker?.let { "• Active Blocker / Issue: $it\n" } ?: ""

        return """
            [TASK CHECKPOINT RESUMPTION]
            Resuming task from structured checkpoint ($TASK_FILE_NAME):
            Goal: "${checkpoint.goal}"
            Status: ${checkpoint.status}
            $blockerInfo
            Completed Milestones:
            $completedList

            Files Modified So Far:
            $filesList

            Next Required Action:
            ${checkpoint.nextAction.ifBlank { "Inspect state and complete remaining implementation requirements" }}

            INSTRUCTIONS:
            - Do not recreate or overwrite already completed files.
            - Pick up directly from '${checkpoint.nextAction}'.
            - Fulfill all remaining requirements and call 'complete' when done.
        """.trimIndent()
    }

    private fun parseCheckpointJson(json: String): CheckpointState? {
        return try {
            val obj = JSONObject(json)
            val completedList = mutableListOf<String>()
            val compArr = obj.optJSONArray("completed")
            if (compArr != null) {
                for (i in 0 until compArr.length()) {
                    completedList.add(compArr.getString(i))
                }
            }

            val filesList = mutableListOf<String>()
            val filesArr = obj.optJSONArray("files_changed")
            if (filesArr != null) {
                for (i in 0 until filesArr.length()) {
                    filesList.add(filesArr.getString(i))
                }
            }

            val blockerStr = if (obj.isNull("blocker")) null else obj.optString("blocker")
            val lastActionObj = obj.optJSONObject("last_action")
            val lastToolStr = lastActionObj?.optString("tool")

            val lastResultObj = obj.optJSONObject("last_result")
            val lastResultStr = lastResultObj?.optString("result")

            CheckpointState(
                goal = obj.optString("goal", ""),
                status = obj.optString("status", "in_progress"),
                phase = obj.optString("phase", "implementation"),
                completed = completedList,
                currentStep = obj.optString("current_step", ""),
                filesChanged = filesList,
                blocker = blockerStr,
                nextAction = obj.optString("next_action", ""),
                lastTool = lastToolStr,
                lastResult = lastResultStr,
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun CoroutineScope.launchSilently(block: suspend () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                block()
            } catch (e: Exception) {
                Log.w("TaskCheckpointEngine", "Async save failed: ${e.message}")
            }
        }
    }
}
