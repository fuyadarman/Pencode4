package com.example.build

import com.example.ui.AndroidBuildError
import com.example.ui.BuildStep
import com.example.ui.GitHubWorkflow
import com.example.ui.WebArtifactInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * ProjectBuildIsolationManager
 * 
 * Guarantees strict per-project isolation for all Build Tab contents:
 * processing, status, steps, execution logs, detected errors, GitHub workflows,
 * and built artifacts (APK/Web).
 * Ensures that one project's build tab data NEVER leaks into another project.
 */
object ProjectBuildIsolationManager {

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
        val lastFailedRunId: Long = -1L,
        val projectRunId: Long? = null,
        val hasBuildInitiated: Boolean = false,
        val buildInitiatedTime: Long = 0L
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
        lastFailedRunId: Long = -1L,
        projectRunId: Long? = null,
        hasBuildInitiated: Boolean = false,
        buildInitiatedTime: Long = 0L
    ) {
        if (projectName.isBlank()) return
        val existing = projectBuildCache[projectName]
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
            lastFailedRunId = lastFailedRunId,
            projectRunId = projectRunId ?: existing?.projectRunId,
            hasBuildInitiated = hasBuildInitiated || (existing?.hasBuildInitiated == true),
            buildInitiatedTime = if (buildInitiatedTime > 0L) buildInitiatedTime else (existing?.buildInitiatedTime ?: 0L)
        )
    }

    fun markBuildInitiated(projectName: String) {
        if (projectName.isBlank()) return
        val current = getSnapshot(projectName)
        projectBuildCache[projectName] = current.copy(
            hasBuildInitiated = true,
            buildInitiatedTime = System.currentTimeMillis()
        )
    }

    fun setProjectRunId(projectName: String, runId: Long) {
        if (projectName.isBlank()) return
        val current = getSnapshot(projectName)
        projectBuildCache[projectName] = current.copy(
            projectRunId = runId,
            hasBuildInitiated = true
        )
    }

    fun getSnapshot(projectName: String): ProjectBuildSnapshot {
        if (projectName.isBlank()) return ProjectBuildSnapshot()
        return projectBuildCache.getOrPut(projectName) { ProjectBuildSnapshot() }
    }

    fun initCleanProjectBuild(projectName: String) {
        if (projectName.isNotBlank()) {
            projectBuildCache[projectName] = ProjectBuildSnapshot(
                buildStatus = "Idle",
                buildSteps = emptyList(),
                buildLogs = "",
                buildError = null,
                isPollingBuild = false,
                gitHubWorkflows = emptyList(),
                apkDownloadProgress = "",
                apkDownloadPercentage = null,
                localApkPath = null,
                webArtifactInfo = null,
                detectedAndroidBuildErrors = emptyList(),
                lastFailedRunId = -1L,
                projectRunId = null,
                hasBuildInitiated = false,
                buildInitiatedTime = 0L
            )
        }
    }

    fun clearProjectBuild(projectName: String) {
        if (projectName.isNotBlank()) {
            projectBuildCache.remove(projectName)
        }
    }
}
