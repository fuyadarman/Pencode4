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
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                extractedCount = extractFromStream(input, destDir, onProgress)
            } ?: return@withContext Result.failure(Exception("Unable to open input stream from URI"))
            Result.success(extractedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}", e)
            Result.failure(e)
        }
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
