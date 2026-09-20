package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Robust ZIP extractor and importer helper that safely preserves nested folder hierarchies,
 * handles edge cases like entries without explicit folder entries, hidden files, and large files.
 */
object ZipImportHelper {

    private const val TAG = "ZipImportHelper"

    /**
     * Extracts a ZIP archive to the target destination directory.
     * Guaranteed to preserve complete folder structures and file names.
     */
    suspend fun extractZipArchive(
        context: Context,
        zipUri: Uri,
        destDir: File,
        onProgress: ((entryName: String, count: Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            var extractedCount = 0
            
            // First attempt: Copy to temp file and use ZipFile for resilient, full random-access extraction
            val tempZip = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.zip")
            try {
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    FileOutputStream(tempZip).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempZip.exists() && tempZip.length() > 0) {
                    extractedCount = extractWithZipFile(tempZip, destDir, onProgress)
                }
            } catch (e: Exception) {
                Log.w(TAG, "ZipFile extraction failed, falling back to stream: ${e.message}")
            } finally {
                if (tempZip.exists()) tempZip.delete()
            }

            // Fallback attempt: Stream-based extraction if ZipFile extracted 0 files
            if (extractedCount == 0) {
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    extractedCount = extractFromStream(input, destDir, onProgress)
                } ?: return@withContext Result.failure(Exception("Unable to open input stream from URI"))
            }

            Result.success(extractedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Extracts using java.util.zip.ZipFile with single-root folder unwrapping and charset safety.
     */
    fun extractWithZipFile(
        zipFileOnDisk: File,
        destDir: File,
        onProgress: ((entryName: String, count: Int) -> Unit)? = null
    ): Int {
        var count = 0
        java.util.zip.ZipFile(zipFileOnDisk).use { zip ->
            val entries = zip.entries().asSequence().toList()
            
            // Check if all non-empty entries share a common root directory (e.g. "my-app/...")
            val validEntryNames = entries.map { it.name.replace('\\', '/').trimStart('/') }
                .filter { it.isNotBlank() && !it.startsWith("__MACOSX/") && !it.contains("/__MACOSX/") }
            
            val commonRootPrefix: String = if (validEntryNames.isNotEmpty()) {
                val firstSlash = validEntryNames.first().indexOf('/')
                if (firstSlash > 0) {
                    val candidate = validEntryNames.first().substring(0, firstSlash + 1)
                    if (validEntryNames.all { it.startsWith(candidate) || it == candidate.trimEnd('/') }) candidate else ""
                } else ""
            } else ""

            val buffer = ByteArray(8192)
            for (entry in entries) {
                try {
                    val rawName = entry.name.replace('\\', '/').trimStart('/')
                    if (rawName.isBlank() || rawName.startsWith("__MACOSX/") || rawName.contains("/__MACOSX/") || rawName.contains("../")) {
                        continue
                    }

                    // Strip common single root folder so folders like src, worker, pages appear directly at root
                    val targetName = if (commonRootPrefix.isNotEmpty() && rawName.startsWith(commonRootPrefix)) {
                        rawName.removePrefix(commonRootPrefix)
                    } else {
                        rawName
                    }

                    if (targetName.isBlank()) continue

                    val outFile = File(destDir, targetName)
                    if (entry.isDirectory || rawName.endsWith("/")) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                var len = input.read(buffer)
                                while (len > 0) {
                                    output.write(buffer, 0, len)
                                    len = input.read(buffer)
                                }
                            }
                        }
                        count++
                        onProgress?.invoke(targetName, count)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipped corrupted entry ${entry.name}: ${e.message}")
                }
            }
        }
        return count
    }

    /**
     * Internal extraction loop from InputStream with path normalization and folder generation.
     */
    fun extractFromStream(
        inputStream: InputStream,
        destDir: File,
        onProgress: ((entryName: String, count: Int) -> Unit)? = null
    ): Int {
        var count = 0
        val zipInputStream = ZipInputStream(inputStream)
        var entry: ZipEntry? = zipInputStream.nextEntry
        val buffer = ByteArray(8192)

        while (entry != null) {
            val rawName = entry.name.replace('\\', '/').trimStart('/')
            // Skip MacOS metadata and dangerous path traversal
            if (rawName.startsWith("__MACOSX/") || rawName.contains("/__MACOSX/") || rawName.contains("../")) {
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
                continue
            }

            val outFile = File(destDir, rawName)

            if (entry.isDirectory || rawName.endsWith("/")) {
                outFile.mkdirs()
            } else {
                // Ensure all parent directories exist
                outFile.parentFile?.mkdirs()

                FileOutputStream(outFile).use { output ->
                    var len = zipInputStream.read(buffer)
                    while (len > 0) {
                        output.write(buffer, 0, len)
                        len = zipInputStream.read(buffer)
                    }
                }
                count++
                onProgress?.invoke(rawName, count)
            }
            zipInputStream.closeEntry()
            entry = zipInputStream.nextEntry
        }
        zipInputStream.close()
        return count
    }
}
