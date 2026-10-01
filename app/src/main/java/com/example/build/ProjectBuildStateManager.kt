package com.example.build

import com.example.ui.AndroidBuildError
import com.example.ui.BuildStep
import com.example.ui.GitHubWorkflow
import com.example.ui.WebArtifactInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * ProjectBuildStateManager
 * 
 * Provides strict per-project isolation for all Build Tab state.
 * Guarantees that processing status, build logs, error lists, step progress,
 * GitHub workflows, and generated artifacts (APKs/Web artifacts) for one project
 * never leak into, contaminate, or mix with another project.
 */
object ProjectBuildStateManager {

    data class ProjectBuildSnapshot(
        val buildStatus: String = "Idle",
        val buildSteps: List<BuildStep> = emptyList(),
        val buildLogs: String = "",
        val buildError: String? = null,
        val isPollingBuild: Boolean = false,
        val gitHubWorkflows: List<GitHubWorkflow> = emptyList(),
        val apkDownloadProgress: String = "",
        val apkDownloadPercentage: Float? = null,
        val localApkPath: String? = null,
        val webArtifactInfo: WebArtifactInfo? = null,
        val detectedAndroidBuildErrors: List<AndroidBuildError> = emptyList(),
        val lastFailedRunId: Long? = null
    )

    private val projectBuildCache = ConcurrentHashMap<String, ProjectBuildSnapshot>()

    fun saveSnapshot(
        projectName: String,
        buildStatus: String,
        buildSteps: List<BuildStep>,
        buildLogs: String,
        buildError: String?,
        isPollingBuild: Boolean,
        gitHubWorkflows: List<GitHubWorkflow>,
        apkDownloadProgress: String,
        apkDownloadPercentage: Float?,
        localApkPath: String?,
        webArtifactInfo: WebArtifactInfo?,
        detectedAndroidBuildErrors: List<AndroidBuildError>,
        lastFailedRunId: Long? = null
    ) {
        if (projectName.isBlank()) return
        projectBuildCache[projectName] = ProjectBuildSnapshot(
            buildStatus = buildStatus,
            buildSteps = buildSteps,
            buildLogs = buildLogs,
            buildError = buildError,
            isPollingBuild = isPollingBuild,
            gitHubWorkflows = gitHubWorkflows,
            apkDownloadProgress = apkDownloadProgress,
            apkDownloadPercentage = apkDownloadPercentage,
            localApkPath = localApkPath,
            webArtifactInfo = webArtifactInfo,
            detectedAndroidBuildErrors = detectedAndroidBuildErrors,
            lastFailedRunId = lastFailedRunId
        )
    }

    fun getSnapshot(projectName: String): ProjectBuildSnapshot {
        if (projectName.isBlank()) return ProjectBuildSnapshot()
        return projectBuildCache.getOrPut(projectName) { ProjectBuildSnapshot() }
    }

    fun clearProjectBuild(projectName: String) {
        if (projectName.isNotBlank()) {
            projectBuildCache.remove(projectName)
        }
    }
}
