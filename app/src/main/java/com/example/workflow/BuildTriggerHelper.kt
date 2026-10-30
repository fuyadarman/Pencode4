package com.example.workflow

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BuildTriggerHelper
 * 
 * Manages GitHub Actions workflow trigger state, notification chime,
 * and error handling so clicking "Trigger Workflow" never crashes the app.
 */
object BuildTriggerHelper {
    private const val TAG = "BuildTriggerHelper"

    // User requested: "build tab এর trigger workflow click করলে isBuildTrigger = true করো"
    private val _isBuildTrigger = MutableStateFlow(false)
    val isBuildTrigger: StateFlow<Boolean> = _isBuildTrigger.asStateFlow()

    // Prompt for workflow errors (Allow / Deny permission prompt)
    data class WorkflowErrorPrompt(
        val workflowName: String,
        val errorMessage: String,
        val onAllow: () -> Unit,
        val onDeny: () -> Unit
    )

    private val _activeErrorPrompt = MutableStateFlow<WorkflowErrorPrompt?>(null)
    val activeErrorPrompt: StateFlow<WorkflowErrorPrompt?> = _activeErrorPrompt.asStateFlow()

    fun setBuildTrigger(value: Boolean) {
        _isBuildTrigger.value = value
    }

    /**
     * Plays the system default notification sound safely.
     * Guaranteed never to crash the application regardless of volume, focus, or permissions.
     */
    fun playNotificationSound(context: Context) {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (notificationUri != null) {
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, notificationUri)
                if (ringtone != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    ringtone.play()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Notification sound played safely without crash: ${t.message}")
        }
    }

    /**
     * Entry point for triggering build workflows safely.
     * Sets isBuildTrigger = true, plays notification sound, and executes trigger without crashing.
     */
    fun onWorkflowTriggerClicked(context: Context, onTrigger: () -> Unit) {
        try {
            _isBuildTrigger.value = true
            playNotificationSound(context)
            onTrigger()
        } catch (t: Throwable) {
            Log.e(TAG, "Safely caught workflow trigger error: ${t.message}", t)
        }
    }

    /**
     * Show workflow error prompt in chat/UI with Allow/Deny actions per RULE[AGENTS_md].
     */
    fun showWorkflowErrorPrompt(
        workflowName: String,
        errorMessage: String,
        onAllow: () -> Unit,
        onDeny: () -> Unit
    ) {
        _activeErrorPrompt.value = WorkflowErrorPrompt(
            workflowName = workflowName,
            errorMessage = errorMessage,
            onAllow = {
                _activeErrorPrompt.value = null
                onAllow()
            },
            onDeny = {
                _activeErrorPrompt.value = null
                onDeny()
            }
        )
    }

    fun dismissErrorPrompt() {
        _activeErrorPrompt.value = null
    }
}
