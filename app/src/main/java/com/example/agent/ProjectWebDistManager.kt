package com.example.agent

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.ui.WebArtifactInfo
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * ProjectWebDistManager
 * Manages isolated per-project build artifacts (web-dist), per-project GitHub configurations,
 * and ensures preview isolation across multiple projects (especially React + Vite projects).
 */
object ProjectWebDistManager {

    private const val TAG = "ProjectWebDistManager"
    private val lastDownloadedRunMap = ConcurrentHashMap<String, Long>()

    fun sanitizeProjectName(projectName: String): String {
        return projectName.trim().replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").ifBlank { "default_project" }
    }

    fun getProjectWebDistDir(context: Context, projectName: String): File {
        val safeName = sanitizeProjectName(projectName)
        return File(context.cacheDir, "web_dist_$safeName")
    }

    /**
     * Recursively searches for index.html in a directory and returns the parent directory
     * containing index.html. Returns null if not found.
     */
    fun findEffectiveWebDir(baseDir: File): File? {
        if (!baseDir.exists() || !baseDir.isDirectory) return null

        var foundIndexHtmlFile: File? = null

        fun search(dir: File, depth: Int = 0) {
            if (depth > 5 || foundIndexHtmlFile != null) return
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (f.isFile && f.name.equals("index.html", ignoreCase = true)) {
                    foundIndexHtmlFile = f
                    return
                }
            }
            for (f in list) {
                if (f.isDirectory) {
                    search(f, depth + 1)
                    if (foundIndexHtmlFile != null) return
                }
            }
        }

        search(baseDir)
        return foundIndexHtmlFile?.parentFile ?: if (File(baseDir, "index.html").exists()) baseDir else null
    }

    fun hasCompiledWebDist(context: Context, projectName: String): Boolean {
        val baseDir = getProjectWebDistDir(context, projectName)
        return findEffectiveWebDir(baseDir) != null
    }

    fun getWebArtifactInfo(context: Context, projectName: String): WebArtifactInfo? {
        val baseDir = getProjectWebDistDir(context, projectName)
        val effectiveDir = findEffectiveWebDir(baseDir) ?: return null
        val indexFile = File(effectiveDir, "index.html")
        val indexContent = if (indexFile.exists()) {
            try { indexFile.readText() } catch (e: Exception) { null }
        } else null

        var fileCount = 0
        var totalBytes = 0L
        fun countFiles(d: File) {
            d.listFiles()?.forEach { f ->
                if (f.isFile) {
                    fileCount++
                    totalBytes += f.length()
                } else if (f.isDirectory) {
                    countFiles(f)
                }
            }
        }
        countFiles(effectiveDir)

        return WebArtifactInfo(
            name = "web-dist.zip",
            fileCount = fileCount,
            zipSizeBytes = totalBytes,
            localDir = effectiveDir.absolutePath,
            indexHtmlContent = indexContent
        )
    }

    data class ExtractionResult(
        val success: Boolean,
        val effectiveWebDir: File?,
        val filesExtracted: Int,
        val zipSizeBytes: Long,
        val indexHtmlContent: String?,
        val errorMessage: String? = null
    )

    fun extractWebDist(context: Context, projectName: String, zipFile: File): ExtractionResult {
        val targetBaseDir = getProjectWebDistDir(context, projectName)
        try {
            if (targetBaseDir.exists()) {
                targetBaseDir.deleteRecursively()
            }
            targetBaseDir.mkdirs()

            var filesExtracted = 0
            val zipIn = ZipInputStream(BufferedInputStream(FileInputStream(zipFile)))
            var entry = zipIn.nextEntry

            while (entry != null) {
                val entryName = entry.name.replace('\\', '/')
                if (!entry.isDirectory && !entryName.endsWith(".apk", ignoreCase = true)) {
                    val destFile = File(targetBaseDir, entryName)
                    destFile.parentFile?.mkdirs()
                    val outStream = BufferedOutputStream(FileOutputStream(destFile), 262144)
                    val buffer = ByteArray(262144)
                    var len = zipIn.read(buffer)
                    while (len > 0) {
                        outStream.write(buffer, 0, len)
                        len = zipIn.read(buffer)
                    }
                    outStream.close()
                    filesExtracted++
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipIn.close()

            // Unpack any nested zip files (e.g. inner web-dist.zip)
            unpackNestedZips(targetBaseDir)

            val effectiveDir = findEffectiveWebDir(targetBaseDir) ?: targetBaseDir
            val indexFile = File(effectiveDir, "index.html")
            val indexHtml = if (indexFile.exists()) {
                try { indexFile.readText() } catch (e: Exception) { null }
            } else null

            Log.d(TAG, "Extracted $filesExtracted web files for project '$projectName' to ${effectiveDir.absolutePath}")

            return ExtractionResult(
                success = filesExtracted > 0 || indexFile.exists(),
                effectiveWebDir = effectiveDir,
                filesExtracted = filesExtracted,
                zipSizeBytes = zipFile.length(),
                indexHtmlContent = indexHtml
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting web-dist for project '$projectName': ${e.message}", e)
            return ExtractionResult(
                success = false,
                effectiveWebDir = null,
                filesExtracted = 0,
                zipSizeBytes = 0L,
                indexHtmlContent = null,
                errorMessage = e.localizedMessage
            )
        }
    }

    private fun unpackNestedZips(dir: File, depth: Int = 0) {
        if (depth > 5) return
        val zips = mutableListOf<File>()
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) unpackNestedZips(f, depth + 1)
            else if (f.isFile && f.name.endsWith(".zip", ignoreCase = true)) zips.add(f)
        }
        for (z in zips) {
            try {
                val destDir = z.parentFile ?: dir
                val zIn = ZipInputStream(BufferedInputStream(FileInputStream(z)))
                var zEntry = zIn.nextEntry
                while (zEntry != null) {
                    if (!zEntry.isDirectory) {
                        val eName = zEntry.name.replace('\\', '/')
                        val dFile = File(destDir, eName)
                        dFile.parentFile?.mkdirs()
                        val outStream = BufferedOutputStream(FileOutputStream(dFile))
                        val buf = ByteArray(131072)
                        var l = zIn.read(buf)
                        while (l > 0) {
                            outStream.write(buf, 0, l)
                            l = zIn.read(buf)
                        }
                        outStream.close()
                    }
                    zIn.closeEntry()
                    zEntry = zIn.nextEntry
                }
                zIn.close()
                z.delete()
            } catch (e: Exception) {
                z.delete()
            }
        }
    }

    // Per-project Run ID tracking
    fun getLastDownloadedRunId(projectName: String): Long {
        return lastDownloadedRunMap[projectName] ?: 0L
    }

    fun setLastDownloadedRunId(projectName: String, runId: Long) {
        lastDownloadedRunMap[projectName] = runId
    }

    fun clearLastDownloadedRunId(projectName: String) {
        lastDownloadedRunMap.remove(projectName)
    }

    // Per-project GitHub Repo & Branch persistence
    fun getProjectGithubRepo(prefs: SharedPreferences, projectName: String): String {
        val safeName = sanitizeProjectName(projectName)
        val projectSpecific = prefs.getString("github_repo_$safeName", "") ?: ""
        if (projectSpecific.isNotBlank()) return projectSpecific
        return prefs.getString("github_repo", "") ?: ""
    }

    fun saveProjectGithubRepo(prefs: SharedPreferences, projectName: String, repo: String) {
        val safeName = sanitizeProjectName(projectName)
        prefs.edit()
            .putString("github_repo_$safeName", repo)
            .putString("github_repo", repo)
            .apply()
    }

    fun getProjectGithubBranch(prefs: SharedPreferences, projectName: String): String {
        val safeName = sanitizeProjectName(projectName)
        val projectSpecific = prefs.getString("github_branch_$safeName", "") ?: ""
        if (projectSpecific.isNotBlank()) return projectSpecific
        return prefs.getString("github_branch", "main") ?: "main"
    }

    fun saveProjectGithubBranch(prefs: SharedPreferences, projectName: String, branch: String) {
        val safeName = sanitizeProjectName(projectName)
        prefs.edit()
            .putString("github_branch_$safeName", branch)
            .putString("github_branch", branch)
            .apply()
    }
}
