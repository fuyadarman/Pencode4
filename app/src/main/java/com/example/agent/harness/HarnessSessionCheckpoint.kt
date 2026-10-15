package com.example.agent.harness

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Persistent Session & Harness State Checkpoint Manager.
 * Preserves agent context snapshots, harness configuration, and execution metadata across app lifecycle events.
 */
object HarnessSessionCheckpoint {
    private const val TAG = "HarnessSession"

    data class SessionSnapshot(
        val sessionId: String,
        val projectName: String,
        val lastUserPrompt: String,
        val totalTurnsExecuted: Int,
        val activeMemoryCount: Int,
        val lastKnownStatus: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun saveSnapshot(context: Context, snapshot: SessionSnapshot) {
        try {
            val file = File(context.cacheDir, "harness_session_${snapshot.projectName}.json")
            val json = JSONObject().apply {
                put("sessionId", snapshot.sessionId)
                put("projectName", snapshot.projectName)
                put("lastUserPrompt", snapshot.lastUserPrompt)
                put("totalTurnsExecuted", snapshot.totalTurnsExecuted)
                put("activeMemoryCount", snapshot.activeMemoryCount)
                put("lastKnownStatus", snapshot.lastKnownStatus)
                put("timestamp", snapshot.timestamp)
            }
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving session snapshot: ${e.message}")
        }
    }

    fun loadSnapshot(context: Context, projectName: String): SessionSnapshot? {
        try {
            val file = File(context.cacheDir, "harness_session_$projectName.json")
            if (!file.exists()) return null
            val json = JSONObject(file.readText())
            return SessionSnapshot(
                sessionId = json.optString("sessionId", ""),
                projectName = json.optString("projectName", projectName),
                lastUserPrompt = json.optString("lastUserPrompt", ""),
                totalTurnsExecuted = json.optInt("totalTurnsExecuted", 0),
                activeMemoryCount = json.optInt("activeMemoryCount", 0),
                lastKnownStatus = json.optString("lastKnownStatus", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading session snapshot: ${e.message}")
            return null
        }
    }
}
