package com.example.workflow

import android.content.Context
import android.content.SharedPreferences
import com.example.agent.ProjectWebDistManager

/**
 * ProjectBuildSeparationManager
 *
 * Manages per-project separation of the Build Tab / GitHub Repository pipeline:
 * 1. Allows users to toggle a 'Separate' switch/button in the Build Tab.
 * 2. When separate is ON: This specific project uses its own isolated GitHub repository & branch,
 *    allowing custom repo selection or creation without impacting other projects.
 * 3. When separate is OFF: Reverts to the global shared GitHub repository.
 * 4. Supports AI execution so the user can instruct AI to separate the build pipeline or configure repo.
 */
object ProjectBuildSeparationManager {

    private const val PREFS_NAME = "project_build_separation_prefs"
    private const val KEY_SEPARATED_PREFIX = "is_separated_"
    private const val KEY_REPO_PREFIX = "repo_"
    private const val KEY_BRANCH_PREFIX = "branch_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun sanitize(name: String): String {
        return name.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
    }

    /**
     * Checks if the given project has an independent/separated build tab repository configuration.
     */
    fun isBuildSeparated(context: Context, projectName: String): Boolean {
        if (projectName.isBlank()) return false
        val key = KEY_SEPARATED_PREFIX + sanitize(projectName)
        return getPrefs(context).getBoolean(key, false)
    }

    /**
     * Toggles or sets whether the project uses a separate repository.
     */
    fun setBuildSeparated(context: Context, projectName: String, separated: Boolean) {
        if (projectName.isBlank()) return
        val key = KEY_SEPARATED_PREFIX + sanitize(projectName)
        getPrefs(context).edit().putBoolean(key, separated).apply()
    }

    /**
     * Gets the project-specific repository if one has been assigned.
     */
    fun getProjectSpecificRepo(context: Context, projectName: String): String {
        if (projectName.isBlank()) return ""
        val key = KEY_REPO_PREFIX + sanitize(projectName)
        return getPrefs(context).getString(key, "") ?: ""
    }

    /**
     * Gets the effective GitHub repository for the current project.
     */
    fun getEffectiveRepo(context: Context, projectName: String, globalRepo: String): String {
        if (projectName.isBlank()) return globalRepo
        val isSep = isBuildSeparated(context, projectName)
        if (isSep) {
            val key = KEY_REPO_PREFIX + sanitize(projectName)
            return getPrefs(context).getString(key, "") ?: ""
        }
        return globalRepo
    }

    /**
     * Saves a project-specific repository. Automatically enables separation for this project.
     */
    fun saveProjectRepo(context: Context, projectName: String, repo: String) {
        if (projectName.isBlank()) return
        val s = sanitize(projectName)
        getPrefs(context).edit()
            .putBoolean(KEY_SEPARATED_PREFIX + s, true)
            .putString(KEY_REPO_PREFIX + s, repo.trim())
            .apply()

        // Also sync with ProjectWebDistManager for backward compatibility
        val defaultPrefs = context.getSharedPreferences("vibe_coder_prefs", Context.MODE_PRIVATE)
        ProjectWebDistManager.saveProjectGithubRepo(defaultPrefs, projectName, repo.trim())
    }

    /**
     * Gets the effective branch for the project.
     */
    fun getEffectiveBranch(context: Context, projectName: String, globalBranch: String): String {
        if (projectName.isBlank()) return globalBranch
        val isSep = isBuildSeparated(context, projectName)
        if (!isSep) return globalBranch

        val key = KEY_BRANCH_PREFIX + sanitize(projectName)
        val branch = getPrefs(context).getString(key, "") ?: ""
        return if (branch.isNotBlank()) branch else globalBranch
    }

    /**
     * Saves a project-specific branch.
     */
    fun saveProjectBranch(context: Context, projectName: String, branch: String) {
        if (projectName.isBlank()) return
        val s = sanitize(projectName)
        getPrefs(context).edit()
            .putString(KEY_BRANCH_PREFIX + s, branch.trim())
            .apply()

        val defaultPrefs = context.getSharedPreferences("vibe_coder_prefs", Context.MODE_PRIVATE)
        ProjectWebDistManager.saveProjectGithubBranch(defaultPrefs, projectName, branch.trim())
    }

    /**
     * Handles tool calls when AI is requested to separate or configure project build repo.
     */
    fun handleAiSeparationRequest(
        context: Context,
        projectName: String,
        repo: String?,
        branch: String?,
        separate: Boolean = true
    ): String {
        if (projectName.isBlank()) return "Error: No active project found."
        setBuildSeparated(context, projectName, separate)
        val sb = StringBuilder()
        if (separate) {
            sb.append("Build tab separated for project '$projectName'. ")
            if (!repo.isNullOrBlank()) {
                saveProjectRepo(context, projectName, repo)
                sb.append("Configured isolated repository: '$repo'. ")
            }
            if (!branch.isNullOrBlank()) {
                saveProjectBranch(context, projectName, branch)
                sb.append("Branch set to: '$branch'. ")
            }
            sb.append("This project now has its own independent CI/CD build pipeline.")
        } else {
            sb.append("Build tab un-separated for project '$projectName'. Reverted to global shared repository.")
        }
        return sb.toString()
    }
}
