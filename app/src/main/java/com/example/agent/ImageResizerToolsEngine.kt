package com.example.agent

import android.util.Log
import com.example.data.ProjectEntity
import com.example.data.VibeRepository
import org.json.JSONObject

/**
 * ImageResizerToolsEngine
 * Dedicated engine providing autonomous AI image resizing, cropping, compression,
 * format conversion, and asset dimension inspection.
 */
object ImageResizerToolsEngine {

    private const val TAG = "ImageResizerTools"

    fun isImageResizerTool(tool: String): Boolean {
        val t = tool.lowercase().trim()
        return t in setOf(
            "resize_image", "scale_image", "image_resize",
            "crop_image", "image_crop",
            "compress_image", "image_compress", "optimize_image",
            "get_image_info", "image_info", "inspect_image"
        )
    }

    suspend fun executeImageTool(
        tool: String,
        rawPath: String?,
        rawDestPath: String?,
        width: Int?,
        height: Int?,
        format: String?,
        quality: Int?,
        projectName: String,
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): String {
        val cleanPath = normalizePath(rawPath?.trim() ?: "")
        if (cleanPath.isBlank()) {
            return "Error: Image path cannot be empty. Please specify 'path' or 'sourcePath'."
        }

        val cleanDestPath = if (!rawDestPath.isNullOrBlank()) {
            normalizePath(rawDestPath.trim())
        } else {
            cleanPath
        }

        val actionName = tool.lowercase().trim()

        return try {
            when (actionName) {
                "get_image_info", "image_info", "inspect_image" -> {
                    val info = ImageResizeEngine.getImageInfo(projectName, cleanPath, repository, normalizePath)
                    if (info != null) {
                        "Image Info for '$cleanPath': Dimensions: ${info.width}x${info.height}px, MIME: ${info.mimeType}, Size: ${info.sizeBytes / 1024} KB (${info.sizeBytes} bytes)"
                    } else {
                        "Error: Could not retrieve image info for '$cleanPath'. File not found or invalid image format."
                    }
                }

                "crop_image", "image_crop" -> {
                    ImageResizeEngine.resizeImage(
                        projectName = projectName,
                        sourcePath = cleanPath,
                        destinationPath = cleanDestPath,
                        targetWidth = width,
                        targetHeight = height,
                        format = format,
                        maintainAspectRatio = false,
                        quality = quality ?: 95,
                        repository = repository,
                        normalizePath = normalizePath
                    )
                }

                "compress_image", "image_compress", "optimize_image" -> {
                    ImageResizeEngine.resizeImage(
                        projectName = projectName,
                        sourcePath = cleanPath,
                        destinationPath = cleanDestPath,
                        targetWidth = width,
                        targetHeight = height,
                        format = format ?: "webp",
                        maintainAspectRatio = true,
                        quality = quality ?: 80,
                        repository = repository,
                        normalizePath = normalizePath
                    )
                }

                else -> {
                    // Default: resize_image / scale_image
                    ImageResizeEngine.resizeImage(
                        projectName = projectName,
                        sourcePath = cleanPath,
                        destinationPath = cleanDestPath,
                        targetWidth = width,
                        targetHeight = height,
                        format = format,
                        maintainAspectRatio = true,
                        quality = quality ?: 90,
                        repository = repository,
                        normalizePath = normalizePath
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing image tool '$tool'", e)
            "Error executing $tool for '$cleanPath': ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }
}
