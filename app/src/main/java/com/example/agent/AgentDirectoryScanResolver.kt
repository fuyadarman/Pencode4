package com.example.agent

import com.example.data.ProjectFileEntity
import com.example.data.VibeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AgentDirectoryScanResolver {

    data class DirectoryScanResult(
        val output: String,
        val isSuccess: Boolean,
        val fileCount: Int,
        val matchedFiles: List<String> = emptyList()
    )

    data class ResolvedFileResult(
        val path: String,
        val content: String,
        val linesCount: Int
    )

    suspend fun scanDirectory(
        rawPath: String,
        projectName: String,
        repository: VibeRepository
    ): DirectoryScanResult = withContext(Dispatchers.IO) {
        val cleanPath = rawPath.trim()
            .replace('\\', '/')
            .trimStart('.', '/')
            .trimEnd('/')

        val projectDir = repository.getProjectDir(projectName)

        // 1. Check if cleanPath points to project root or is empty
        val isProjectRootTarget = cleanPath.isEmpty() ||
                cleanPath == "." ||
                cleanPath.equals(projectName, ignoreCase = true) ||
                cleanPath.equals(projectDir.name, ignoreCase = true)

        // 2. Try physical disk directory search first
        val diskMatchedDir = findDirectoryOnDisk(projectDir, cleanPath, isProjectRootTarget)
        if (diskMatchedDir != null && diskMatchedDir.exists() && diskMatchedDir.isDirectory) {
            val (subdirs, filesOnDisk) = scanDiskDirectory(diskMatchedDir, projectDir)
            if (filesOnDisk.isNotEmpty()) {
                // Background sync to DB so future operations are fast
                try { repository.syncStorageToDatabase(projectName) } catch (_: Exception) {}

                val output = buildScanSuccessOutput(
                    targetDisplay = if (cleanPath.isEmpty()) projectDir.name else cleanPath,
                    subdirectories = subdirs,
                    files = filesOnDisk
                )
                return@withContext DirectoryScanResult(
                    output = output,
                    isSuccess = true,
                    fileCount = filesOnDisk.size,
                    matchedFiles = filesOnDisk.map { it.path }
                )
            }
        }

        // 3. Try database files lookup
        var dbFiles = repository.getFilesForProject(projectName)
        var matchedEntities = filterEntities(dbFiles, cleanPath, projectName, projectDir.name, isProjectRootTarget)

        // If not found in DB, try syncing disk to DB and re-querying
        if (matchedEntities.isEmpty()) {
            try {
                repository.syncStorageToDatabase(projectName)
                dbFiles = repository.getFilesForProject(projectName)
                matchedEntities = filterEntities(dbFiles, cleanPath, projectName, projectDir.name, isProjectRootTarget)
            } catch (_: Exception) {}
        }

        if (matchedEntities.isNotEmpty()) {
            val fileInfos = matchedEntities.sortedBy { it.path }.map { entity ->
                val lineCount = entity.content.lines().size
                val sizeInBytes = entity.content.toByteArray(Charsets.UTF_8).size
                val sizeStr = formatFileSize(sizeInBytes)
                FileInfo(path = entity.path, lineCount = lineCount, sizeStr = sizeStr)
            }

            val subdirs = matchedEntities.mapNotNull {
                val p = it.path.replace('\\', '/')
                val lastSlash = p.lastIndexOf('/')
                if (lastSlash > 0) p.substring(0, lastSlash) else null
            }.distinct().sorted()

            val output = buildScanSuccessOutput(
                targetDisplay = if (cleanPath.isEmpty()) projectName else cleanPath,
                subdirectories = subdirs,
                files = fileInfos
            )
            return@withContext DirectoryScanResult(
                output = output,
                isSuccess = true,
                fileCount = fileInfos.size,
                matchedFiles = fileInfos.map { it.path }
            )
        }

        // If still empty after all attempts
        val notFoundMsg = "No files or subdirectories found under '$rawPath'. Please verify the folder name or use 'list_directory' to see all available project files."
        DirectoryScanResult(
            output = notFoundMsg,
            isSuccess = false,
            fileCount = 0
        )
    }

    suspend fun resolveFile(
        rawPath: String,
        projectName: String,
        repository: VibeRepository
    ): ResolvedFileResult? = withContext(Dispatchers.IO) {
        val cleanPath = rawPath.trim().replace('\\', '/').trimStart('/')
        val projectDir = repository.getProjectDir(projectName)

        // 1. Check DB files
        val dbFiles = repository.getFilesForProject(projectName)
        val matchedDb = dbFiles.find { entity ->
            val p = entity.path.replace('\\', '/').trimStart('/')
            p.equals(cleanPath, ignoreCase = true) ||
                    p.endsWith("/$cleanPath", ignoreCase = true) ||
                    cleanPath.endsWith("/$p", ignoreCase = true)
        }
        if (matchedDb != null) {
            return@withContext ResolvedFileResult(
                path = matchedDb.path,
                content = matchedDb.content,
                linesCount = matchedDb.content.lines().size
            )
        }

        // 2. Check disk file
        val directDisk = File(projectDir, cleanPath)
        if (directDisk.exists() && directDisk.isFile) {
            val content = try { directDisk.readText().replace("\r\n", "\n") } catch (_: Exception) { "" }
            return@withContext ResolvedFileResult(
                path = directDisk.relativeTo(projectDir).path.replace('\\', '/'),
                content = content,
                linesCount = content.lines().size
            )
        }

        // 3. Search disk file by name
        val fileName = File(cleanPath).name
        val matchedDiskFile = projectDir.walkTopDown()
            .maxDepth(6)
            .filter { it.isFile && it.name.equals(fileName, ignoreCase = true) }
            .firstOrNull()

        if (matchedDiskFile != null) {
            val content = try { matchedDiskFile.readText().replace("\r\n", "\n") } catch (_: Exception) { "" }
            return@withContext ResolvedFileResult(
                path = matchedDiskFile.relativeTo(projectDir).path.replace('\\', '/'),
                content = content,
                linesCount = content.lines().size
            )
        }

        null
    }

    private fun findDirectoryOnDisk(
        projectDir: File,
        cleanPath: String,
        isProjectRootTarget: Boolean
    ): File? {
        if (!projectDir.exists()) return null
        if (isProjectRootTarget) return projectDir

        val direct = File(projectDir, cleanPath)
        if (direct.exists() && direct.isDirectory) return direct

        // Search ignoring case
        val targetName = File(cleanPath).name
        return projectDir.walkTopDown()
            .maxDepth(5)
            .filter { it.isDirectory && it.name.equals(targetName, ignoreCase = true) }
            .firstOrNull()
    }

    private data class FileInfo(
        val path: String,
        val lineCount: Int,
        val sizeStr: String
    )

    private fun scanDiskDirectory(
        targetDir: File,
        projectDir: File
    ): Pair<List<String>, List<FileInfo>> {
        val ignoreDirs = listOf(".git", ".gradle", ".dart_tool", "build", "node_modules", "bin", "obj")
        val subdirs = mutableListOf<String>()
        val files = mutableListOf<FileInfo>()

        targetDir.walkTopDown()
            .onEnter { dir ->
                val name = dir.name
                val ignore = ignoreDirs.any { name.equals(it, ignoreCase = true) } ||
                        (name.startsWith(".") && !name.equals(".github", ignoreCase = true))
                if (!ignore && dir != targetDir) {
                    val relPath = dir.relativeTo(projectDir).path.replace('\\', '/')
                    subdirs.add(relPath)
                }
                !ignore
            }
            .filter { it.isFile && !it.name.startsWith(".") }
            .forEach { file ->
                val relPath = file.relativeTo(projectDir).path.replace('\\', '/')
                val lineCount = try { file.readLines().size } catch (_: Exception) { 0 }
                val size = file.length()
                files.add(FileInfo(path = relPath, lineCount = lineCount, sizeStr = formatFileSize(size)))
            }

        return Pair(subdirs.sorted(), files.sortedBy { it.path })
    }

    private fun filterEntities(
        files: List<ProjectFileEntity>,
        cleanPath: String,
        projectName: String,
        projectDirName: String,
        isProjectRootTarget: Boolean
    ): List<ProjectFileEntity> {
        val targetLower = cleanPath.lowercase()
        return files.filter { entity ->
            val p = entity.path.replace('\\', '/').trimStart('/')
            val pLower = p.lowercase()

            when {
                isProjectRootTarget -> true
                pLower.startsWith("$targetLower/") -> true
                pLower == targetLower -> true
                pLower.contains("/$targetLower/") -> true
                pLower.contains(targetLower) -> true
                else -> false
            }
        }
    }

    private fun buildScanSuccessOutput(
        targetDisplay: String,
        subdirectories: List<String>,
        files: List<FileInfo>
    ): String {
        val sb = StringBuilder()
        val subdirCount = subdirectories.size
        sb.append("Recursive scan of directory '$targetDisplay' succeeded. Found ${files.size} files across $subdirCount subdirectories:\n\n")

        if (subdirectories.isNotEmpty()) {
            sb.append("Subdirectories ($subdirCount):\n")
            subdirectories.take(20).forEach {
                sb.append("  📁 $it\n")
            }
            if (subdirectories.size > 20) {
                sb.append("  ... and ${subdirectories.size - 20} more subdirectories\n")
            }
            sb.append("\n")
        }

        sb.append("Files (${files.size}):\n")
        files.take(200).forEach { file ->
            sb.append("  - ${file.path} (${file.lineCount} lines, ${file.sizeStr})\n")
        }
        if (files.size > 200) {
            sb.append("  ... and ${files.size - 200} more files (truncated for brevity)\n")
        }

        return sb.toString().trimEnd()
    }

    private fun formatFileSize(bytes: Long): String {
        return if (bytes >= 1024 * 1024) {
            String.format("%.2f MB", bytes.toDouble() / (1024 * 1024))
        } else {
            String.format("%.2f KB", bytes.toDouble() / 1024)
        }
    }

    private fun formatFileSize(bytes: Int): String = formatFileSize(bytes.toLong())
}
