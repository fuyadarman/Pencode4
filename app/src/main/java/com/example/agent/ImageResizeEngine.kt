package com.example.agent

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.data.VibeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * High-performance Image Resize and Transformation Engine.
 * Supports scaling, aspect ratio preservation, format conversion, and compression.
 */
object ImageResizeEngine {

    data class ImageDimensions(
        val width: Int,
        val height: Int,
        val mimeType: String,
        val sizeBytes: Int
    )

    suspend fun resizeImage(
        projectName: String,
        sourcePath: String,
        destinationPath: String?,
        targetWidth: Int?,
        targetHeight: Int?,
        format: String? = null,
        maintainAspectRatio: Boolean = true,
        quality: Int = 90,
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): String = withContext(Dispatchers.IO) {
        val cleanSource = normalizePath(sourcePath.trim())
        if (cleanSource.isBlank()) {
            return@withContext "Error: Source image path cannot be empty."
        }

        val cleanDest = if (!destinationPath.isNullOrBlank()) {
            normalizePath(destinationPath.trim())
        } else {
            cleanSource
        }

        val files = repository.getFilesForProject(projectName)
        val sourceFile = files.find { it.path.equals(cleanSource, ignoreCase = true) }
            ?: files.find { it.path.endsWith("/$cleanSource", ignoreCase = true) }
            ?: files.find { it.path.substringAfterLast("/").equals(cleanSource.substringAfterLast("/"), ignoreCase = true) }
            ?: return@withContext "Error: Source image file '$cleanSource' not found in project '$projectName'."

        try {
            val base64Content = sourceFile.content
            val isDataUri = base64Content.startsWith("data:") && base64Content.contains(";base64,")
            val rawBase64 = if (isDataUri) base64Content.substringAfter(";base64,") else base64Content
            val imageBytes = Base64.decode(rawBase64, Base64.DEFAULT)

            if (imageBytes.isEmpty()) {
                return@withContext "Error: Image file '$cleanSource' is empty or invalid base64."
            }

            // Decode bounds first to get original dimensions
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight

            if (origWidth <= 0 || origHeight <= 0) {
                return@withContext "Error: Unable to parse image dimensions from '$cleanSource'."
            }

            // Calculate final target dimensions
            val (finalWidth, finalHeight) = calculateDimensions(
                origWidth = origWidth,
                origHeight = origHeight,
                reqWidth = targetWidth,
                reqHeight = targetHeight,
                maintainAspectRatio = maintainAspectRatio
            )

            // Decode full bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
                ?: return@withContext "Error: Failed to decode image bitmap from '$cleanSource'."

            val scaledBitmap = if (originalBitmap.width == finalWidth && originalBitmap.height == finalHeight) {
                originalBitmap
            } else {
                Bitmap.createScaledBitmap(originalBitmap, finalWidth, finalHeight, true)
            }

            // Determine output compression format
            val outputFormatStr = (format ?: cleanDest.substringAfterLast(".", "png")).lowercase(Locale.ROOT)
            val compressFormat = when (outputFormatStr) {
                "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                "webp" -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.PNG
            }

            val clampedQuality = quality.coerceIn(1, 100)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(compressFormat, clampedQuality, outputStream)
            val outputBytes = outputStream.toByteArray()

            val destMimeType = when (outputFormatStr) {
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "ico" -> "image/x-icon"
                else -> "image/png"
            }

            val resultBase64 = "data:$destMimeType;base64," + Base64.encodeToString(outputBytes, Base64.NO_WRAP)
            repository.saveFile(projectName, cleanDest, resultBase64)

            // Clean up memory
            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()

            val origKb = imageBytes.size / 1024
            val destKb = outputBytes.size / 1024
            "Successfully resized image '$cleanSource' ($origWidth x $origHeight, ${origKb}KB) -> '$cleanDest' ($finalWidth x $finalHeight, ${destKb}KB, format: ${compressFormat.name})."
        } catch (e: Exception) {
            "Error resizing image '$cleanSource': ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    fun resizeBase64Image(
        base64Content: String,
        targetWidth: Int?,
        targetHeight: Int?,
        format: String? = null,
        maintainAspectRatio: Boolean = true,
        quality: Int = 90
    ): Pair<String, String>? {
        return try {
            val isDataUri = base64Content.startsWith("data:") && base64Content.contains(";base64,")
            val rawBase64 = if (isDataUri) base64Content.substringAfter(";base64,") else base64Content
            val imageBytes = Base64.decode(rawBase64, Base64.DEFAULT)
            if (imageBytes.isEmpty()) return null

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            val (finalWidth, finalHeight) = calculateDimensions(
                origWidth = origWidth,
                origHeight = origHeight,
                reqWidth = targetWidth,
                reqHeight = targetHeight,
                maintainAspectRatio = maintainAspectRatio
            )

            val decodeOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions) ?: return null
            val scaledBitmap = if (originalBitmap.width == finalWidth && originalBitmap.height == finalHeight) {
                originalBitmap
            } else {
                Bitmap.createScaledBitmap(originalBitmap, finalWidth, finalHeight, true)
            }

            val outputFormatStr = (format ?: "png").lowercase(Locale.ROOT)
            val compressFormat = when (outputFormatStr) {
                "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                "webp" -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.PNG
            }

            val clampedQuality = quality.coerceIn(1, 100)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(compressFormat, clampedQuality, outputStream)
            val outputBytes = outputStream.toByteArray()

            val destMimeType = when (outputFormatStr) {
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "ico" -> "image/x-icon"
                else -> "image/png"
            }

            val resultBase64 = "data:$destMimeType;base64," + Base64.encodeToString(outputBytes, Base64.NO_WRAP)
            if (scaledBitmap != originalBitmap) scaledBitmap.recycle()
            originalBitmap.recycle()

            val summary = "$finalWidth x $finalHeight (${outputBytes.size / 1024} KB, ${compressFormat.name})"
            Pair(resultBase64, summary)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateDimensions(
        origWidth: Int,
        origHeight: Int,
        reqWidth: Int?,
        reqHeight: Int?,
        maintainAspectRatio: Boolean
    ): Pair<Int, Int> {
        val w = reqWidth ?: 0
        val h = reqHeight ?: 0

        if (w <= 0 && h <= 0) {
            return Pair(origWidth, origHeight)
        }

        if (!maintainAspectRatio) {
            return Pair(if (w > 0) w else origWidth, if (h > 0) h else origHeight)
        }

        val aspectRatio = origWidth.toFloat() / origHeight.toFloat()
        return when {
            w > 0 && h > 0 -> {
                // Fit inside box preserving aspect ratio
                val targetAspect = w.toFloat() / h.toFloat()
                if (aspectRatio > targetAspect) {
                    Pair(w, (w / aspectRatio).toInt().coerceAtLeast(1))
                } else {
                    Pair((h * aspectRatio).toInt().coerceAtLeast(1), h)
                }
            }
            w > 0 -> Pair(w, (w / aspectRatio).toInt().coerceAtLeast(1))
            h > 0 -> Pair((h * aspectRatio).toInt().coerceAtLeast(1), h)
            else -> Pair(origWidth, origHeight)
        }
    }

    suspend fun getImageInfo(
        projectName: String,
        path: String,
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): ImageDimensions? = withContext(Dispatchers.IO) {
        val cleanPath = normalizePath(path.trim())
        val files = repository.getFilesForProject(projectName)
        val file = files.find { it.path.equals(cleanPath, ignoreCase = true) } ?: return@withContext null

        try {
            val base64Content = file.content
            val isDataUri = base64Content.startsWith("data:") && base64Content.contains(";base64,")
            val rawBase64 = if (isDataUri) base64Content.substringAfter(";base64,") else base64Content
            val bytes = Base64.decode(rawBase64, Base64.DEFAULT)

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            ImageDimensions(
                width = options.outWidth,
                height = options.outHeight,
                mimeType = options.outMimeType ?: "image/png",
                sizeBytes = bytes.size
            )
        } catch (_: Exception) {
            null
        }
    }
}
