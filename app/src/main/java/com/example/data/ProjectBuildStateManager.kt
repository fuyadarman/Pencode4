package com.example.data

import com.example.ui.AndroidBuildError
import com.example.ui.BuildStep
import com.example.ui.GitHubWorkflow
import com.example.ui.WebArtifactInfo

data class ProjectBuildSnapshot(
    val buildStatus: String = "IDLE",
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

object ProjectBuildStateManager {
    private val projectSnapshots = java.util.concurrent.ConcurrentHashMap<String, ProjectBuildSnapshot>()

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
        lastFailedRunId: Long?
    ) {
        projectSnapshots[projectName] = ProjectBuildSnapshot(
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
        return projectSnapshots[projectName] ?: ProjectBuildSnapshot()
    }

    fun clearProjectBuild(projectName: String) {
        projectSnapshots.remove(projectName)
    }
}
