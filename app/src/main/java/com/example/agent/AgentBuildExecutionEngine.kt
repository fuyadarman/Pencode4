package com.example.agent

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * AgentBuildExecutionEngine
 * 
 * Handles the complete lifecycle when the AI triggers 'trigger_build':
 * 1. Verifies stack compatibility (React Vite, Android App, Chrome Extension, Flutter App only).
 * 2. Pushes project changes.
 * 3. Actively waits/polls the build run until completion (success, failure, or timeout).
 * 4. Yields live progress updates to AI logs and returns comprehensive build results.
 */
object AgentBuildExecutionEngine {

    private const val TAG = "AgentBuildExecution"
    private const val MAX_WAIT_SECONDS = 480 // Up to 8 minutes for Android/Flutter builds

    /**
     * Stacks allowed for compiling with 'trigger_build'.
     */
    private val SUPPORTED_BUILD_STACKS = setOf(
        "react_vite", "vite", "android", "android_compose", "android_xml",
        "chrome_extension", "chrome_ext", "flutter", "flutter_app"
    )

    private val UNSUPPORTED_STATIC_STACKS = setOf(
        "react", "react_cdn", "vanilla", "vanilla_js", "vanilla_three", "static", "html"
    )

    fun isStaticClientStack(templateKey: String?): Boolean {
        if (templateKey == null) return false
        val key = templateKey.lowercase().trim()
        return UNSUPPORTED_STATIC_STACKS.any { key == it || key.contains("cdn") }
    }

    /**
     * Executes the build action and waits for completion.
     */
    suspend fun executeAndAwaitBuild(
        projectName: String,
        templateKey: String?,
        githubRepo: String,
        githubToken: String,
        githubBranch: String,
        commitMessage: String? = null,
        onPush: suspend (projectName: String, repo: String, token: String, branch: String, force: Boolean, progressCallback: (String) -> Unit) -> Result<Unit>,
        onStatusUpdate: (status: String, details: String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        // 1. Stack check & Warning for Vanilla JS, React CDN, Vanilla Three.js
        if (isStaticClientStack(templateKey)) {
            val warningMsg = "Warning: 'trigger_build' is ONLY supported for compile-target projects (React Vite, Android App, Chrome Extension, Flutter App). The current project stack ('$templateKey') runs directly and instantly in the live Preview tab without needing compilation. Do NOT invoke 'trigger_build' for static web stacks."
            Log.w(TAG, warningMsg)
            return@withContext warningMsg
        }

        // 2. Validate configuration
        val repoVal = githubRepo.trim()
        val tokenVal = githubToken.trim()
        if (repoVal.isEmpty() || tokenVal.isEmpty()) {
            return@withContext "Error: GitHub Repository or Token is not configured yet. Please configure credentials in the Build tab before triggering a build."
        }

        val branch = githubBranch.ifBlank { "main" }
        val cleanRepo = repoVal.removePrefix("https://github.com/").removePrefix("http://github.com/").removeSuffix(".git")
        val parts = cleanRepo.split("/").filter { it.isNotBlank() }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        var owner = ""
        var repoName = ""

        if (parts.size >= 2) {
            owner = parts[0]
            repoName = parts[1]
        } else if (parts.size == 1) {
            repoName = parts[0]
            // Fetch username
            try {
                val userReq = okhttp3.Request.Builder()
                    .url("https://api.github.com/user")
                    .header("Authorization", "token $tokenVal")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                val userResp = client.newCall(userReq).execute()
                if (userResp.isSuccessful) {
                    val body = userResp.body?.string() ?: ""
                    val map = moshi.adapter(Map::class.java).fromJson(body) as? Map<*, *>
                    owner = (map?.get("login") as? String) ?: ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed resolving user: ${e.message}")
            }
        }

        if (owner.isBlank() || repoName.isBlank()) {
            return@withContext "Error: Invalid repository format ($repoVal). Must be 'owner/repo'."
        }

        // Record initial latest run ID before pushing so we can target the new run
        var baselineRunId: Long = 0L
        try {
            val checkUrl = "https://api.github.com/repos/$owner/$repoName/actions/runs?per_page=3"
            val checkReq = okhttp3.Request.Builder()
                .url(checkUrl)
                .header("Authorization", "token $tokenVal")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            val checkResp = client.newCall(checkReq).execute()
            if (checkResp.isSuccessful) {
                val b = checkResp.body?.string() ?: ""
                val rMap = moshi.adapter(Map::class.java).fromJson(b) as? Map<*, *>
                val runsList = rMap?.get("workflow_runs") as? List<*>
                val topRun = runsList?.firstOrNull() as? Map<*, *>
                baselineRunId = (topRun?.get("id") as? Number)?.toLong() ?: 0L
            }
        } catch (e: Exception) {
            Log.d(TAG, "Baseline run check exception: ${e.message}")
        }

        // 3. Initiate push & trigger
        onStatusUpdate("thinking", "Preparing project files and triggering build...")
        AgentBuildControllerEngine.resetForNewBuild()

        val pushResult = try {
            onPush(projectName, repoVal, tokenVal, branch, true) { progress ->
                onStatusUpdate("thinking", progress)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

        if (pushResult.isFailure) {
            val err = pushResult.exceptionOrNull()?.localizedMessage ?: "Failed to trigger build"
            return@withContext "Build trigger failed: $err"
        }

        onStatusUpdate("thinking", "Build triggered! Waiting for build process to complete...")

        // 4. Actively wait and poll while build is running
        val startTime = System.currentTimeMillis()
        var trackedRunId: Long? = null
        var lastStatus = "queued"
        var lastStepName = ""

        while (System.currentTimeMillis() - startTime < MAX_WAIT_SECONDS * 1000L) {
            delay(3500)

            try {
                val runsUrl = "https://api.github.com/repos/$owner/$repoName/actions/runs?per_page=5"
                val runsReq = okhttp3.Request.Builder()
                    .url(runsUrl)
                    .header("Authorization", "token $tokenVal")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                val runsResp = client.newCall(runsReq).execute()
                if (!runsResp.isSuccessful) {
                    continue
                }

                val bodyStr = runsResp.body?.string() ?: ""
                val runsMap = moshi.adapter(Map::class.java).fromJson(bodyStr) as? Map<*, *>
                val workflowRuns = runsMap?.get("workflow_runs") as? List<*> ?: emptyList<Any>()

                if (workflowRuns.isEmpty()) {
                    continue
                }

                // Pick the candidate run
                val candidate = workflowRuns.mapNotNull { it as? Map<*, *> }.firstOrNull { r ->
                    val rId = (r["id"] as? Number)?.toLong() ?: 0L
                    val rPath = (r["path"] as? String) ?: ""
                    val rName = (r["name"] as? String) ?: ""
                    val isCommand = rPath.lowercase().endsWith("command.yml") || rName.contains("Command", ignoreCase = true)
                    !isCommand && (trackedRunId == null || rId == trackedRunId || rId > baselineRunId)
                } ?: (workflowRuns[0] as? Map<*, *>)

                if (candidate == null) continue

                val currentRunId = (candidate["id"] as? Number)?.toLong() ?: continue
                if (trackedRunId == null) {
                    trackedRunId = currentRunId
                }

                val status = (candidate["status"] as? String) ?: "unknown"
                val conclusion = candidate["conclusion"] as? String
                val runName = (candidate["name"] as? String) ?: "Build Pipeline"
                lastStatus = status

                // Fetch jobs & steps for live status
                var activeStep = ""
                var failedStepName: String? = null
                var primaryJobId: Long? = null

                try {
                    val jobsUrl = "https://api.github.com/repos/$owner/$repoName/actions/runs/$currentRunId/jobs"
                    val jobsReq = okhttp3.Request.Builder()
                        .url(jobsUrl)
                        .header("Authorization", "token $tokenVal")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val jobsResp = client.newCall(jobsReq).execute()
                    if (jobsResp.isSuccessful) {
                        val jobsBody = jobsResp.body?.string() ?: ""
                        val jobsJson = moshi.adapter(Map::class.java).fromJson(jobsBody) as? Map<*, *>
                        val jobsList = jobsJson?.get("jobs") as? List<*>
                        val firstJob = jobsList?.firstOrNull() as? Map<*, *>
                        primaryJobId = (firstJob?.get("id") as? Number)?.toLong()
                        val stepsList = firstJob?.get("steps") as? List<*>

                        stepsList?.forEach { sObj ->
                            val sMap = sObj as? Map<*, *>
                            val sName = sMap?.get("name") as? String ?: ""
                            val sStatus = sMap?.get("status") as? String ?: ""
                            val sConclusion = sMap?.get("conclusion") as? String

                            if (sStatus == "in_progress") {
                                activeStep = sName
                            }
                            if (sConclusion == "failure" && failedStepName == null) {
                                failedStepName = sName
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Jobs polling error: ${e.message}")
                }

                if (activeStep.isNotBlank() && activeStep != lastStepName) {
                    lastStepName = activeStep
                    onStatusUpdate("thinking", "Build in progress: $activeStep ($status)")
                } else if (status != "completed") {
                    onStatusUpdate("thinking", "Build running ($status)...")
                }

                // Check completion
                if (status == "completed") {
                    if (conclusion == "success") {
                        onStatusUpdate("success", "Build completed successfully!")
                        return@withContext "Build completed successfully! Status: success. Artifact/APK packaged without errors."
                    } else if (conclusion == "failure") {
                        // Extract error logs snippet
                        var errorDetails = ""
                        if (primaryJobId != null) {
                            try {
                                val logsUrl = "https://api.github.com/repos/$owner/$repoName/actions/jobs/$primaryJobId/logs"
                                val logsReq = okhttp3.Request.Builder()
                                    .url(logsUrl)
                                    .header("Authorization", "token $tokenVal")
                                    .header("Accept", "application/vnd.github.v3+json")
                                    .build()
                                val logsResp = client.newCall(logsReq).execute()
                                if (logsResp.isSuccessful) {
                                    val fullLogs = logsResp.body?.string() ?: ""
                                    val lines = fullLogs.lines()
                                    val errorLines = lines.filter { line ->
                                        line.contains("error:", ignoreCase = true) ||
                                        line.contains("FAILURE:", ignoreCase = true) ||
                                        line.contains("FAILED", ignoreCase = true) ||
                                        line.contains("Exception", ignoreCase = true)
                                    }.takeLast(25)

                                    errorDetails = if (errorLines.isNotEmpty()) {
                                        errorLines.joinToString("\n")
                                    } else {
                                        lines.takeLast(30).joinToString("\n")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching job logs: ${e.message}")
                            }
                        }

                        val stepInfo = if (!failedStepName.isNullOrBlank()) " at step '$failedStepName'" else ""
                        val finalErr = "Build failed$stepInfo.\n\nKey Error Details:\n${errorDetails.ifBlank { "Compilation error encountered during build." }}"
                        onStatusUpdate("error", finalErr)
                        return@withContext finalErr
                    } else {
                        // Cancelled, timed out, or skipped
                        return@withContext "Build completed with conclusion: $conclusion."
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Polling exception: ${e.message}")
            }
        }

        return@withContext "Build is still in progress (status: $lastStatus). Execution wait time reached limit."
    }
}
