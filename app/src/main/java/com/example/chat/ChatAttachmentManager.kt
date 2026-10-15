package com.example.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.data.VibeRepository
import com.example.ui.AttachedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * ChatAttachmentManager
 *
 * Efficiently processes user attachments (images, audio, video, documents) without
 * bloating prompt context into 100k+ tokens.
 * - Saves binary files (video, audio, docs) to project workspace (`chat_attachments/`).
 * - Optimizes and resizes images to compact dimensions before converting to lightweight Base64.
 * - Avoids converting binary raw bytes into broken UTF-8 strings.
 */
object ChatAttachmentManager {

    private const val MAX_IMAGE_DIMENSION = 1024
    private const val MAX_TEXT_EMBED_CHARS = 10_000

    suspend fun processAttachmentUri(
        context: Context,
        uri: Uri,
        projectName: String?,
        repository: VibeRepository?
    ): AttachedFile? = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        var displayName = "attachment_${System.currentTimeMillis()}"

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx != -1) {
                    displayName = cursor.getString(nameIdx) ?: displayName
                }
            }
        } catch (_: Exception) {}

        val isImage = mimeType.startsWith("image/")
        val isVideo = mimeType.startsWith("video/")
        val isAudio = mimeType.startsWith("audio/")
        val isPlainText = isTextMimeType(mimeType, displayName)

        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val fileSizeStr = formatFileSize(bytes.size)

            if (isImage) {
                // Compress & downscale image to prevent 100k+ token base64 bloating
                val compressedBase64 = compressImageToBase64(bytes)
                // Also save image to project workspace if project exists
                saveFileToProjectWorkspace(projectName, repository, displayName, bytes)
                return@withContext AttachedFile(
                    uri = uri,
                    name = displayName,
                    mimeType = mimeType,
                    isImage = true,
                    contentAsBase64 = compressedBase64,
                    contentAsText = "Image: $displayName ($fileSizeStr, saved to chat_attachments/$displayName)"
                )
            } else if (isPlainText && bytes.size <= 256_000) {
                // Legitimate text file (code, json, markdown, txt)
                val textContent = String(bytes, Charsets.UTF_8)
                saveFileToProjectWorkspace(projectName, repository, displayName, bytes)
                val truncatedText = if (textContent.length > MAX_TEXT_EMBED_CHARS) {
                    textContent.take(MAX_TEXT_EMBED_CHARS) + "\n... [Truncated for context efficiency. Full file saved to chat_attachments/$displayName]"
                } else {
                    textContent
                }
                return@withContext AttachedFile(
                    uri = uri,
                    name = displayName,
                    mimeType = mimeType,
                    isImage = false,
                    contentAsText = truncatedText
                )
            } else {
                // Video, Audio, PDF, ZIP, or large binary files: NEVER convert to UTF-8 string!
                saveFileToProjectWorkspace(projectName, repository, displayName, bytes)
                val typeLabel = when {
                    isVideo -> "Video"
                    isAudio -> "Audio"
                    mimeType.contains("pdf") -> "PDF Document"
                    mimeType.contains("zip") || mimeType.contains("tar") -> "Archive"
                    else -> "Binary File"
                }
                val summaryNotice = "[$typeLabel: $displayName ($fileSizeStr)\n" +
                        "MIME: $mimeType\n" +
                        "Saved to Workspace: chat_attachments/$displayName\n" +
                        "Status: File available in codebase for AI tools/read_file.]"

                return@withContext AttachedFile(
                    uri = uri,
                    name = displayName,
                    mimeType = mimeType,
                    isImage = false,
                    contentAsText = summaryNotice
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isTextMimeType(mime: String, name: String): Boolean {
        if (mime.startsWith("text/")) return true
        if (mime.contains("json") || mime.contains("javascript") || mime.contains("xml")) return true
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".kt") || lower.endsWith(".java") || lower.endsWith(".ts") ||
                lower.endsWith(".tsx") || lower.endsWith(".js") || lower.endsWith(".jsx") ||
                lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".json") ||
                lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".xml") ||
                lower.endsWith(".gradle") || lower.endsWith(".kts") || lower.endsWith(".py")
    }

    private fun compressImageToBase64(rawBytes: ByteArray): String {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
            val origWidth = options.outWidth
            val origHeight = options.outHeight

            var sampleSize = 1
            if (origWidth > MAX_IMAGE_DIMENSION || origHeight > MAX_IMAGE_DIMENSION) {
                val halfWidth = origWidth / 2
                val halfHeight = origHeight / 2
                while ((halfWidth / sampleSize) >= MAX_IMAGE_DIMENSION || (halfHeight / sampleSize) >= MAX_IMAGE_DIMENSION) {
                    sampleSize *= 2
                }
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return Base64.encodeToString(rawBytes.take(50000).toByteArray(), Base64.NO_WRAP)

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            val compressedBytes = stream.toByteArray()
            Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            Base64.encodeToString(rawBytes.take(50000).toByteArray(), Base64.NO_WRAP)
        }
    }

    private suspend fun saveFileToProjectWorkspace(
        projectName: String?,
        repository: VibeRepository?,
        fileName: String,
        bytes: ByteArray
    ) {
        if (projectName.isNullOrBlank() || repository == null) return
        try {
            val targetRelativePath = "chat_attachments/$fileName"
            val projectDir = repository.getProjectDir(projectName)
            val targetFile = File(projectDir, targetRelativePath)
            targetFile.parentFile?.mkdirs()
            targetFile.writeBytes(bytes)
            repository.saveFile(projectName, targetRelativePath, "[Binary/Attachment: $fileName, ${bytes.size} bytes]")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun formatFileSize(bytes: Int): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes bytes"
        }
    }
}
