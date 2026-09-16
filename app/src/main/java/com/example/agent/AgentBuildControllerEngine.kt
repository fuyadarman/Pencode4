package com.example.agent

import android.util.Log

/**
 * AgentBuildControllerEngine
 * 
 * Manages:
 * 1. AI-controlled Build button execution & GitHub push triggering
 * 2. Suppression of Build tab error notifications in ChatBar for client-side web stacks
 *    (React CDN, Vanilla JS, Vanilla Three.js)
 * 3. Prevention of duplicate/recurrent error prompts once dismissed or allowed by the user
 */
object AgentBuildControllerEngine {

    private const val TAG = "AgentBuildController"

    // Track workflow runs that the user has already dismissed or allowed/handled
    private val dismissedRunIds = mutableSetOf<Long>()
    private val handledRunIds = mutableSetOf<Long>()
    private var activeFailedRunId: Long? = null

    /**
     * Stacks where Build Tab workflow errors must NOT be shown in Chat / ChatBar:
     * - React CDN ("react")
     * - Vanilla JS ("vanilla")
     * - Vanilla Three.js ("vanilla_three")
     */
    fun shouldSuppressBuildErrorsInChat(templateKey: String?): Boolean {
        if (templateKey == null) return false
        val key = templateKey.lowercase().trim()
        return key == "react" || key == "vanilla" || key == "vanilla_three" || key.contains("cdn")
    }

    /**
     * Records the currently detected failing workflow run ID
     */
    fun setActiveFailedRunId(runId: Long?) {
        activeFailedRunId = runId
    }

    fun getActiveFailedRunId(): Long? = activeFailedRunId

    /**
     * User clicked "Cancel / Deny": marks this run as permanently dismissed
     * so it never pops up again during polling.
     */
    fun dismissRun(runId: Long? = activeFailedRunId) {
        if (runId != null && runId > 0) {
            dismissedRunIds.add(runId)
            Log.d(TAG, "Dismissed build run $runId - will not show again")
        }
    }

    /**
     * User clicked "Allow / Fix Selected": marks this run as handled
     * so it never pops up again during polling.
     */
    fun markRunHandled(runId: Long? = activeFailedRunId) {
        if (runId != null && runId > 0) {
            handledRunIds.add(runId)
            Log.d(TAG, "Marked build run $runId as handled - will not show again")
        }
    }

    /**
     * Checks if a run has already been resolved by user (either allowed or dismissed)
     */
    fun isRunResolved(runId: Long): Boolean {
        return dismissedRunIds.contains(runId) || handledRunIds.contains(runId)
    }

    /**
     * Resets tracking when a new build/push is manually or autonomously triggered
     */
    fun resetForNewBuild() {
        activeFailedRunId = null
    }

    /**
     * AI tool execution: AI triggers the Build button to push code to GitHub and start build
     */
    fun executeAiTriggerBuild(
        projectName: String,
        githubRepo: String,
        githubToken: String,
        githubBranch: String,
        commitMessage: String? = null,
        onPush: (projectName: String, repo: String, token: String, branch: String, force: Boolean, callback: (Result<Unit>) -> Unit) -> Unit
    ): String {
        if (githubRepo.isBlank() || githubToken.isBlank()) {
            return "Error: GitHub Repository or Token is not configured yet. Please configure GitHub credentials in the Build tab before triggering a build."
        }

        val branch = githubBranch.ifBlank { "main" }
        resetForNewBuild()

        var pushInitiated = false
        var errorResult: String? = null

        try {
            onPush(projectName, githubRepo, githubToken, branch, true) { result ->
                if (result.isFailure) {
                    errorResult = result.exceptionOrNull()?.localizedMessage ?: "Failed to push"
                }
            }
            pushInitiated = true
        } catch (e: Exception) {
            return "Error while triggering build button: ${e.localizedMessage}"
        }

        return if (pushInitiated && errorResult == null) {
            "Build button pressed successfully! Code pushed to GitHub repo '$githubRepo' on branch '$branch'. GitHub Actions build pipeline has been initiated."
        } else {
            "Build trigger encountered issue: ${errorResult ?: "Could not complete push operation"}."
        }
    }
}
