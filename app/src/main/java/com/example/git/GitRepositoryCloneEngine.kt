package com.example.git

import com.example.data.ProjectFileEntity
import com.example.data.VibeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * GitRepositoryCloneEngine
 * Handles robust GitHub repository cloning and loading into project workspaces.
 * Supports public & private repositories, branch fallback (main, master, default branch resolution),
 * and agent-driven cloning with progress tracking.
 */
object GitRepositoryCloneEngine {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Clones a GitHub repository into an existing or new project workspace.
     */
    suspend fun cloneGitHubRepository(
        repoInput: String,
        projectName: String,
        branch: String? = null,
        token: String? = null,
        repository: VibeRepository,
        progressCallback: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        try {
            progressCallback("Parsing repository information...")
            val cleanRepo = parseGitHubRepoCoordinates(repoInput)
            if (cleanRepo == null) {
                return@withContext "Error: Invalid repository format '$repoInput'. Please provide 'owner/repo' or a GitHub URL (e.g. 'https://github.com/owner/repo')."
            }

            val (owner, repoName) = cleanRepo
            progressCallback("Checking repository metadata for $owner/$repoName...")

            // Try branches in priority order
            val requestedBranch = branch?.trim()?.ifEmpty { null }
            val branchesToTry: List<String?> = if (requestedBranch != null) {
                listOf(requestedBranch, "main", "master", null)
            } else {
                listOf(null, "main", "master")
            }

            var downloadSuccess = false
            var lastErrorMessage = ""
            var successBranch = requestedBranch ?: "default"

            for (targetBranch in branchesToTry.distinct()) {
                val branchLabel = targetBranch ?: "default"
                progressCallback("Downloading repository ZIP ($branchLabel branch)...")
                val downloadResult = downloadAndExtractRepoZip(
                    owner = owner,
                    repoName = repoName,
                    branch = targetBranch,
                    token = token,
                    projectName = projectName,
                    repository = repository
                )

                if (downloadResult.isSuccess) {
                    downloadSuccess = true
                    successBranch = branchLabel
                    break
                } else {
                    lastErrorMessage = downloadResult.exceptionOrNull()?.message ?: "Unknown download error"
                }
            }

            // If direct zipball fails, try resolving default branch from GitHub API
            if (!downloadSuccess) {
                progressCallback("Resolving default branch via GitHub API...")
                val defaultBranch = fetchDefaultBranch(owner, repoName, token)
                if (defaultBranch != null && !branchesToTry.contains(defaultBranch)) {
                    progressCallback("Downloading repository ZIP ($defaultBranch branch)...")
                    val retryResult = downloadAndExtractRepoZip(
                        owner = owner,
                        repoName = repoName,
                        branch = defaultBranch,
                        token = token,
                        projectName = projectName,
                        repository = repository
                    )
                    if (retryResult.isSuccess) {
                        downloadSuccess = true
                        successBranch = defaultBranch
                    } else {
                        lastErrorMessage = retryResult.exceptionOrNull()?.message ?: lastErrorMessage
                    }
                }
            }

            if (!downloadSuccess) {
                return@withContext "Error cloning repository '$owner/$repoName': $lastErrorMessage. (Make sure the repository exists, is public or your GitHub token is provided)."
            }

            progressCallback("Synchronizing database and workspace storage...")
            repository.syncDatabaseToStorage(projectName)

            val fileCount = repository.getFilesForProject(projectName).size
            "Successfully cloned GitHub repository '$owner/$repoName' (branch: $successBranch) with $fileCount files loaded into project '$projectName'."
        } catch (e: Exception) {
            "Error while cloning repository: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    /**
     * Parses owner and repository name from URL, git SSH string, or owner/repo format.
     */
    fun parseGitHubRepoCoordinates(input: String): Pair<String, String>? {
        var trimmed = input.trim()
        if (trimmed.startsWith("git clone", ignoreCase = true)) {
            trimmed = trimmed.substring(9).trim()
        }
        trimmed = trimmed
            .removePrefix("git@github.com:")
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .removeSuffix("/")

        val parts = trimmed.split("/").filter { it.isNotBlank() }
        return if (parts.size >= 2) {
            val owner = parts[0].trim()
            val repoName = parts[1].trim().removeSuffix(".git")
            Pair(owner, repoName)
        } else {
            null
        }
    }

    private suspend fun downloadAndExtractRepoZip(
        owner: String,
        repoName: String,
        branch: String?,
        token: String?,
        projectName: String,
        repository: VibeRepository
    ): Result<Int> {
        val trimmedBranch = branch?.trim()?.ifEmpty { null }
        // Strategy 1: GitHub API zipball endpoint
        // Strategy 2: GitHub codeload / archive direct download (works even without rate limit)
        val candidateUrls = mutableListOf<String>()
        if (trimmedBranch != null) {
            candidateUrls.add("https://api.github.com/repos/$owner/$repoName/zipball/$trimmedBranch")
            candidateUrls.add("https://codeload.github.com/$owner/$repoName/zip/refs/heads/$trimmedBranch")
            candidateUrls.add("https://codeload.github.com/$owner/$repoName/legacy.zip/refs/heads/$trimmedBranch")
        } else {
            // Default branch automatic detection via zipball & HEAD archive
            candidateUrls.add("https://api.github.com/repos/$owner/$repoName/zipball")
            candidateUrls.add("https://github.com/$owner/$repoName/archive/HEAD.zip")
            candidateUrls.add("https://codeload.github.com/$owner/$repoName/zip/refs/heads/main")
            candidateUrls.add("https://codeload.github.com/$owner/$repoName/zip/refs/heads/master")
        }

        var lastError = "Could not download repository archive."

        for (url in candidateUrls) {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "AI-Vibe-Studio-Android")

            if (url.contains("api.github.com")) {
                requestBuilder.header("Accept", "application/vnd.github.v3+json")
            }

            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "token ${token.trim()}")
            }

            try {
                val response = httpClient.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    val bodyStream = response.body?.byteStream()
                    if (bodyStream != null) {
                        val filesExtracted = extractZipToDatabase(projectName, bodyStream, repository)
                        if (filesExtracted > 0) {
                            return Result.success(filesExtracted)
                        }
                    }
                } else {
                    val code = response.code
                    lastError = when (code) {
                        404 -> "Repository or branch not found (HTTP 404). If this is a private repository, please provide a GitHub Personal Access Token."
                        401, 403 -> "GitHub API Rate limit reached or unauthorized (HTTP $code). Please configure a GitHub Token."
                        else -> "HTTP $code ${response.message}"
                    }
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Connection error"
            }
        }

        return Result.failure(Exception(lastError))
    }

    private suspend fun extractZipToDatabase(
        projectName: String,
        byteStream: java.io.InputStream,
        repository: VibeRepository
    ): Int {
        val zipIn = ZipInputStream(byteStream)
        var entry = zipIn.nextEntry
        var count = 0

        while (entry != null) {
            if (!entry.isDirectory) {
                val entryName = entry.name
                val pathParts = entryName.split("/")
                if (pathParts.size > 1) {
                    val relativePath = pathParts.drop(1).joinToString("/")
                    if (relativePath.isNotBlank() && !relativePath.startsWith(".git/")) {
                        val outStream = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        var len = zipIn.read(buffer)
                        while (len > 0) {
                            outStream.write(buffer, 0, len)
                            len = zipIn.read(buffer)
                        }
                        val contentStr = outStream.toString("UTF-8")
                        repository.saveFile(projectName, relativePath, contentStr)
                        count++
                    }
                }
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }
        zipIn.close()
        return count
    }

    private fun fetchDefaultBranch(owner: String, repoName: String, token: String?): String? {
        return try {
            val url = "https://api.github.com/repos/$owner/$repoName"
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "AI-Vibe-Studio-Android")
                .header("Accept", "application/vnd.github.v3+json")

            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "token ${token.trim()}")
            }

            val resp = httpClient.newCall(reqBuilder.build()).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val match = Regex(""""default_branch"\s*:\s*"([^"]+)"""").find(body)
                match?.groupValues?.get(1)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
