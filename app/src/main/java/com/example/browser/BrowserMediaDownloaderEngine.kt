package com.example.browser

import android.util.Base64
import com.example.data.VibeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * BrowserMediaDownloaderEngine
 *
 * Downloads files, videos, audios, and images found on browser pages
 * and saves them directly into the current codebase/project so they appear in File Explorer.
 */
object BrowserMediaDownloaderEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class DownloadResult(
        val isSuccess: Boolean,
        val savedRelativePath: String,
        val fileName: String,
        val fileSizeFormatted: String,
        val message: String
    )

    suspend fun downloadMediaToProject(
        projectName: String,
        mediaUrl: String,
        suggestedName: String?,
        repository: VibeRepository
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val projectDir = repository.getProjectDir(projectName)
            val downloadsDir = File(projectDir, "downloads").apply { mkdirs() }

            val cleanUrl = mediaUrl.trim()
            val fileName = buildSafeFileName(cleanUrl, suggestedName)
            val targetFile = File(downloadsDir, fileName)

            if (cleanUrl.startsWith("data:")) {
                // Base64 data URL
                val commaIndex = cleanUrl.indexOf(",")
                if (commaIndex != -1) {
                    val base64Data = cleanUrl.substring(commaIndex + 1)
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    targetFile.writeBytes(bytes)
                    val relPath = "downloads/$fileName"
                    repository.saveFile(projectName, relPath, "[Downloaded Media: $fileName, ${bytes.size} bytes]")
                    return@withContext DownloadResult(
                        isSuccess = true,
                        savedRelativePath = relPath,
                        fileName = fileName,
                        fileSizeFormatted = "${bytes.size / 1024} KB",
                        message = "Saved to codebase: $relPath"
                    )
                }
            }

            // Normal HTTP/HTTPS download
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext DownloadResult(
                    isSuccess = false,
                    savedRelativePath = "",
                    fileName = fileName,
                    fileSizeFormatted = "0 KB",
                    message = "HTTP download failed with code ${response.code}"
                )
            }

            val body = response.body ?: return@withContext DownloadResult(
                isSuccess = false,
                savedRelativePath = "",
                fileName = fileName,
                fileSizeFormatted = "0 KB",
                message = "Response body was empty"
            )

            val bytes = body.bytes()
            targetFile.writeBytes(bytes)

            val relPath = "downloads/$fileName"
            val sizeStr = if (bytes.size >= 1024 * 1024) "${bytes.size / (1024 * 1024)} MB" else "${bytes.size / 1024} KB"
            repository.saveFile(projectName, relPath, "[Downloaded File: $fileName, $sizeStr]")

            DownloadResult(
                isSuccess = true,
                savedRelativePath = relPath,
                fileName = fileName,
                fileSizeFormatted = sizeStr,
                message = "Successfully downloaded and saved to $relPath"
            )
        } catch (e: Exception) {
            DownloadResult(
                isSuccess = false,
                savedRelativePath = "",
                fileName = suggestedName ?: "unknown",
                fileSizeFormatted = "0 KB",
                message = "Error: ${e.message}"
            )
        }
    }

    private fun buildSafeFileName(url: String, suggested: String?): String {
        val base = suggested?.replace("[^a-zA-Z0-9._-]".toRegex(), "_")?.take(40)
        if (!base.isNullOrBlank()) {
            return if (base.contains(".")) base else "$base.dat"
        }
        val urlPath = try {
            java.net.URI(url).path
        } catch (_: Exception) {
            url
        }
        val lastSegment = urlPath.substringAfterLast("/").substringBefore("?").replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return if (lastSegment.isNotBlank() && lastSegment.contains(".")) {
            lastSegment
        } else {
            "media_${System.currentTimeMillis()}.dat"
        }
    }
}
