package com.example.ui.build

import com.example.ui.AndroidBuildError

/**
 * Manages GitHub Actions / Android build pipeline error prompt lifecycle.
 * Prevents repeating/recurring error prompts in the chat bar after the user
 * has explicitly chosen to "Allow / Fix" or "Cancel / Deny" the error.
 */
class BuildWorkflowErrorManager {

    private val dismissedRunIds = mutableSetOf<Long>()
    private val handledRunIds = mutableSetOf<Long>()
    private val dismissedErrorSignatures = mutableSetOf<String>()
    
    @Volatile
    var currentTrackingRunId: Long? = null
        private set

    /**
     * Updates the current run ID being polled.
     */
    fun updateCurrentRunId(runId: Long?) {
        currentTrackingRunId = runId
    }

    /**
     * Checks whether an error prompt should be presented to the user.
     * Returns false if the run or the errors have already been dismissed or handled.
     */
    fun shouldPromptErrors(runId: Long?, errors: List<AndroidBuildError>): Boolean {
        if (errors.isEmpty()) return false
        if (runId != null) {
            if (dismissedRunIds.contains(runId)) return false
            if (handledRunIds.contains(runId)) return false
        }

        // Check if all individual errors have already been dismissed
        val unhandled = errors.filter { err ->
            val sig = "${err.filePath}:${err.lineNumber}:${err.message}"
            !dismissedErrorSignatures.contains(sig)
        }
        return unhandled.isNotEmpty()
    }

    /**
     * Called when the user clicks "Cancel / Deny" on the error prompt.
     * Permanently suppresses this run's errors from popping back up.
     */
    fun onUserDismissErrors(runId: Long?, errors: List<AndroidBuildError>) {
        if (runId != null) {
            dismissedRunIds.add(runId)
        }
        for (err in errors) {
            dismissedErrorSignatures.add("${err.filePath}:${err.lineNumber}:${err.message}")
        }
    }

    /**
     * Called when the user clicks "Allow / Fix Selected" or auto-fix is initiated.
     * Suppresses this run from re-prompting while fixing or awaiting next build.
     */
    fun onUserAllowFixErrors(runId: Long?, errors: List<AndroidBuildError>) {
        if (runId != null) {
            handledRunIds.add(runId)
        }
        for (err in errors) {
            dismissedErrorSignatures.add("${err.filePath}:${err.lineNumber}:${err.message}")
        }
    }

    /**
     * Called when a new workflow run is triggered (e.g. by new push or trigger button).
     * Clears history for newly scheduled runs while maintaining protection for older ones.
     */
    fun onNewWorkflowTriggered() {
        currentTrackingRunId = null
    }

    /**
     * Completely resets all dismissal cache if requested.
     */
    fun resetAll() {
        dismissedRunIds.clear()
        handledRunIds.clear()
        dismissedErrorSignatures.clear()
        currentTrackingRunId = null
    }
}
