package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.zip.ZipInputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.net.URL
import android.util.Base64

class VibeRepository(private val dao: VibeDao, private val context: Context) {

    private fun isDirWritable(dir: File): Boolean {
        return try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            if (dir.exists() && dir.isDirectory) {
                val tempFile = File(dir, ".write_test_${System.currentTimeMillis()}")
                val success = tempFile.createNewFile()
                if (success) {
                    tempFile.delete()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // Helper to get physical directory for project on device memory
    fun getProjectDir(projectName: String): File {
        val publicDocBase = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)?.resolve("pencode")
        val base = if (publicDocBase != null && isDirWritable(publicDocBase)) {
            publicDocBase
        } else {
            context.getExternalFilesDir(null)?.resolve("pencode") 
                ?: context.filesDir.resolve("pencode")
        }
        val sanitizedName = projectName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val projectDir = File(base, sanitizedName)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        return projectDir
    }

    fun isBinaryExtension(path: String): Boolean {
        val ext = path.substringAfterLast(".", "").lowercase()
        return ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "ico", "obj", "gltf", "glb", "fbx", "3ds", "stl", "dex", "arsc", "so", "jar", "apk", "zip")
    }

    fun isPhysicalFileBinary(file: File, relativePath: String): Boolean {
        if (isBinaryExtension(relativePath)) return true
        val ext = file.extension.lowercase()
        if (ext in setOf("dex", "arsc", "so", "jar", "apk", "zip", "class", "bin", "exe", "dll")) return true
        if (ext in setOf("java", "kt", "xml", "json", "txt", "properties", "gradle", "kts", "md", "html", "js", "css", "smali")) {
            return false
        }
        if (file.exists() && file.length() > 0) {
            try {
                val bytes = file.inputStream().use { stream ->
                    val buf = ByteArray(1024)
                    val read = stream.read(buf)
                    if (read > 0) buf.copyOf(read) else ByteArray(0)
                }
                return isBinaryBytes(bytes)
            } catch (e: Exception) {
                return true
            }
        }
        return false
    }

    fun decodeBase64Content(content: String): ByteArray? {
        if (content.startsWith("data:") && content.contains(";base64,")) {
            val base64Data = content.substringAfter(";base64,")
            return try {
                Base64.decode(base64Data, Base64.DEFAULT)
            } catch (e: Exception) {
                null
            }
        }
        return try {
            Base64.decode(content, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    // Sync database files to physical storage
    suspend fun syncDatabaseToStorage(projectName: String) = withContext(Dispatchers.IO) {
        try {
            val projectDir = getProjectDir(projectName)
            val dbFiles = dao.getFilesForProject(projectName)
            dbFiles.forEach { dbFile ->
                val normPath = dbFile.path.replace("\\", "/")
                val file = File(projectDir, normPath)
                file.parentFile?.mkdirs()
                if (isBinaryExtension(normPath) || dbFile.content.startsWith("data:")) {
                    val bytes = decodeBase64Content(dbFile.content)
                    if (bytes != null && bytes.isNotEmpty()) {
                        file.writeBytes(bytes)
                    } else if (file.length() == 0L) {
                        file.writeText(dbFile.content)
                    }
                } else {
                    file.writeText(dbFile.content)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Sync physical files back to database safely
    suspend fun syncStorageToDatabase(projectName: String) = withContext(Dispatchers.IO) {
        try {
            val projectDir = getProjectDir(projectName)
            if (!projectDir.exists()) return@withContext
            val diskFiles = try {
                projectDir.walkTopDown()
                    .onEnter { dir ->
                        val name = dir.name
                        val ignoreDirs = listOf(".git", ".gradle", ".dart_tool", "build", "node_modules", "bin", "obj")
                        if (ignoreDirs.any { name.equals(it, ignoreCase = true) }) {
                            false
                        } else if (name.startsWith(".") && !name.equals(".github", ignoreCase = true)) {
                            false
                        } else {
                            true
                        }
                    }
                    .filter { it.isFile && !it.name.startsWith(".") }
                    .toList()
            } catch (e: Exception) {
                emptyList()
            }
            
            val dbFiles = dao.getFilesForProject(projectName)
            val dbFilesMap = dbFiles.associateBy { it.path }
            val diskPaths = mutableSetOf<String>()
            
            diskFiles.take(1500).forEach { file ->
                val relativePath = file.relativeTo(projectDir).path.replace("\\", "/")
                diskPaths.add(relativePath)
                
                val isBinary = isPhysicalFileBinary(file, relativePath)
                val content = if (isBinary) {
                    if (file.length() <= 500_000L) {
                        val bytes = try { file.readBytes() } catch (e: Exception) { ByteArray(0) }
                        val mimeType = when (file.extension.lowercase()) {
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            "webp" -> "image/webp"
                            "gif" -> "image/gif"
                            "ico" -> "image/x-icon"
                            else -> "application/octet-stream"
                        }
                        "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                    } else {
                        "[Binary file: ${file.length()} bytes]"
                    }
                } else {
                    try { file.readText().replace("\r\n", "\n") } catch (e: Exception) { "" }
                }
                
                val existing = dbFilesMap[relativePath]
                if (existing != null) {
                    if (existing.content != content && !content.startsWith("[Binary file:")) {
                        dao.updateFile(existing.copy(content = content))
                    }
                } else {
                    dao.insertFile(ProjectFileEntity(projectName = projectName, path = relativePath, content = content))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getAllProjects(): List<ProjectEntity> = withContext(Dispatchers.IO) {
        dao.getAllProjects()
    }

    suspend fun createProject(name: String, description: String, templateKey: String?) = withContext(Dispatchers.IO) {
        val project = ProjectEntity(name, description, System.currentTimeMillis(), templateKey)
        dao.insertProject(project)

        val starterFiles = getStarterFilesForTemplate(name, templateKey)
        dao.insertFiles(starterFiles)

        // Sync files to physical storage immediately so they exist as real files
        syncDatabaseToStorage(name)

        // Insert initial system/assistant message welcoming the user
        val welcomeMessage = ChatMessageEntity(
            projectName = name,
            role = "assistant",
            content = "Hello! I am your AI Vibe Coding Agent. I have initialized the **${templateKey ?: "Empty"}** template for you. What would you like to build today? Feel free to write prompts or use the terminal!",
            timestamp = System.currentTimeMillis()
        )
        dao.insertChatMessage(welcomeMessage)
    }

    suspend fun deleteProject(name: String) = withContext(Dispatchers.IO) {
        dao.deleteProject(name)
        dao.deleteAllFilesForProject(name)
        dao.deleteAllChatsForProject(name)
        // Clean physical directory too
        val projectDir = getProjectDir(name)
        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        }
    }

    suspend fun updateProject(oldName: String, newName: String, newDescription: String) = withContext(Dispatchers.IO) {
        if (oldName != newName) {
            dao.updateProject(oldName, newName, newDescription)
            dao.updateProjectFilesProjectName(oldName, newName)
            dao.updateChatMessagesProjectName(oldName, newName)
            
            // Rename directory if name changed
            val oldDir = getProjectDir(oldName)
            val newDir = getProjectDir(newName)
            if (oldDir.exists() && oldDir.absolutePath != newDir.absolutePath) {
                oldDir.renameTo(newDir)
            }
        } else {
            dao.updateProject(oldName, newName, newDescription)
        }
    }

    suspend fun getFilesForProject(projectName: String): List<ProjectFileEntity> = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(projectName)
        val dbFiles = dao.getFilesForProject(projectName).filter { 
            !it.path.startsWith("agent-skills/") && !it.path.startsWith("skills/") && !it.path.startsWith(".skills/") 
        }
        dbFiles.map { entity ->
            val normPath = entity.path.replace("\\", "/")
            if (entity.content.startsWith("[Binary file:") || (entity.content.isEmpty() && File(projectDir, normPath).exists())) {
                val diskFile = File(projectDir, normPath)
                if (diskFile.exists() && diskFile.length() < 200_000L && !isBinaryExtension(normPath)) {
                    val readText = try { diskFile.readText().replace("\r\n", "\n") } catch (e: Exception) { entity.content }
                    entity.copy(path = normPath, content = readText)
                } else {
                    entity.copy(path = normPath)
                }
            } else {
                entity.copy(path = normPath)
            }
        }
    }

    suspend fun saveFile(projectName: String, path: String, content: String) = withContext(Dispatchers.IO) {
        val normPath = path.replace("\\", "/")
        val normalizedContent = if (isBinaryExtension(normPath) || content.startsWith("data:")) content else content.replace("\r\n", "\n")
        
        val projectDir = getProjectDir(projectName)
        val file = File(projectDir, normPath)
        file.parentFile?.mkdirs()
        if (isBinaryExtension(normPath) || normalizedContent.startsWith("data:")) {
            val bytes = decodeBase64Content(normalizedContent)
            if (bytes != null && bytes.isNotEmpty()) {
                file.writeBytes(bytes)
            } else {
                file.writeText(normalizedContent)
            }
        } else {
            file.writeText(normalizedContent)
        }

        val dbContent = if (normalizedContent.length > 200_000) {
            if (isBinaryExtension(normPath) || normalizedContent.startsWith("data:")) "[Binary file: ${file.length()} bytes]"
            else normalizedContent
        } else {
            normalizedContent
        }

        val existing = dao.getFileByPath(projectName, normPath)
        if (existing != null) {
            dao.updateFile(existing.copy(content = dbContent))
        } else {
            dao.insertFile(ProjectFileEntity(projectName = projectName, path = normPath, content = dbContent))
        }
    }

    suspend fun deleteFile(projectName: String, path: String) = withContext(Dispatchers.IO) {
        val lowerPath = path.lowercase().trim()
        if (lowerPath == "android.yml" || lowerPath.endsWith("/android.yml") || lowerPath.endsWith("\\android.yml")) {
            throw IllegalArgumentException("The 'android.yml' workflow file is protected and cannot be deleted.")
        }
        dao.deleteFile(projectName, path)
        
        // Delete from physical storage
        val projectDir = getProjectDir(projectName)
        val file = File(projectDir, path)
        if (file.exists()) {
            file.delete()
        }
    }

    suspend fun renameFile(projectName: String, oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(projectName)
        val oldFile = File(projectDir, oldPath)
        val newFile = File(projectDir, newPath)
        
        val dbFile = dao.getFileByPath(projectName, oldPath)
        if (!oldFile.exists() && dbFile == null) {
            throw java.io.FileNotFoundException("Source file '$oldPath' does not exist.")
        }

        if (oldFile.exists()) {
            newFile.parentFile?.mkdirs()
            val renameResult = oldFile.renameTo(newFile)
            if (!renameResult) {
                try {
                    oldFile.copyTo(newFile, overwrite = true)
                    oldFile.delete()
                } catch (e: Exception) {
                    throw Exception("Failed to rename file on disk: ${e.message}")
                }
            }
        }
        
        if (dbFile != null) {
            dao.deleteFile(projectName, newPath)
            dao.updateFile(dbFile.copy(path = newPath))
        } else {
            syncStorageToDatabase(projectName)
        }
    }

    suspend fun moveFile(projectName: String, oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        renameFile(projectName, oldPath, newPath)
    }

    suspend fun importFilesToProject(projectName: String, uris: List<android.net.Uri>) = withContext(Dispatchers.IO) {
        uris.forEach { uri ->
            try {
                val fileName = getFileNameFromUri(uri) ?: "imported_${System.currentTimeMillis()}"
                if (fileName.lowercase().endsWith(".zip")) {
                    extractZipToProject(projectName, uri)
                } else if (fileName.lowercase().endsWith(".apk")) {
                    // Save APK file first
                    val projectDir = getProjectDir(projectName)
                    val apkFile = File(projectDir, fileName)
                    apkFile.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(apkFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    syncStorageToDatabase(projectName)
                    // Auto decompile if imported as APK
                    decompileApkInProject(projectName, fileName)
                } else {
                    val contentBytes = try {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } catch (e: Exception) {
                        null
                    }
                    if (contentBytes != null) {
                        if (isBinaryExtension(fileName) || isBinaryBytes(contentBytes)) {
                            val base64 = "data:application/octet-stream;base64," + Base64.encodeToString(contentBytes, Base64.NO_WRAP)
                            saveFile(projectName, fileName, base64)
                        } else {
                            val textContent = String(contentBytes, Charsets.UTF_8)
                            saveFile(projectName, fileName, textContent)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isBinaryBytes(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        var nonPrintable = 0
        val checkLen = minOf(bytes.size, 1024)
        for (i in 0 until checkLen) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return true
            if (b < 9 || (b in 14..31)) nonPrintable++
        }
        return (nonPrintable.toFloat() / checkLen) > 0.3f
    }

    suspend fun decompileApkInProject(projectName: String, apkPath: String) = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(projectName)
        val apkFile = File(projectDir, apkPath)
        if (!apkFile.exists()) return@withContext

        try {
            java.util.zip.ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val entryName = entry.name
                    // Ignore signature metadata
                    if (entryName.startsWith("META-INF/")) continue

                    val outFile = File(projectDir, entryName)
                    outFile.parentFile?.mkdirs()

                    zip.getInputStream(entry).use { input ->
                        val bytes = input.readBytes()
                        if (entryName.equals("AndroidManifest.xml", ignoreCase = true)) {
                            val decompiledXml = com.example.api.Decompiler.decodeAxml(bytes)
                            outFile.writeText(decompiledXml)
                        } else if (entryName.endsWith(".xml", ignoreCase = true) || entryName.endsWith(".json", ignoreCase = true) || entryName.endsWith(".txt", ignoreCase = true) || entryName.endsWith(".properties", ignoreCase = true)) {
                            if (bytes.isNotEmpty() && bytes[0] == '<'.toByte()) {
                                outFile.writeText(String(bytes, Charsets.UTF_8))
                            } else {
                                val decoded = com.example.api.Decompiler.decodeAxml(bytes)
                                outFile.writeText(decoded)
                            }
                        } else if (entryName.endsWith(".dex")) {
                            outFile.writeBytes(bytes)
                            // Parse & decompile DEX bytecode classes into editable, readable text .java files
                            com.example.api.Decompiler.decompileDex(bytes, projectDir)
                        } else if (isBinaryExtension(entryName) || entryName.endsWith(".arsc") || entryName.endsWith(".so")) {
                            outFile.writeBytes(bytes)
                        } else {
                            try {
                                val text = String(bytes, Charsets.UTF_8)
                                if (!isBinaryBytes(bytes)) {
                                    outFile.writeText(text)
                                } else {
                                    outFile.writeBytes(bytes)
                                }
                            } catch (e: Exception) {
                                outFile.writeBytes(bytes)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Re-sync storage to DB so all decompiled files appear in editor tree
        syncStorageToDatabase(projectName)
    }

    private fun decodeAxml(bytes: ByteArray): String {
        return com.example.api.Decompiler.decodeAxml(bytes)
    }

    suspend fun extractZipToProject(projectName: String, zipUri: android.net.Uri) = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(projectName)
        context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
            val zipInputStream = ZipInputStream(inputStream)
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = entry.name
                    val outFile = File(projectDir, entryName)
                    outFile.parentFile?.mkdirs()
                    
                    val outputStream = FileOutputStream(outFile)
                    val buffer = ByteArray(4096)
                    var len = zipInputStream.read(buffer)
                    while (len > 0) {
                        outputStream.write(buffer, 0, len)
                        len = zipInputStream.read(buffer)
                    }
                    outputStream.close()
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()
        }
        // After unzipping, sync storage back to DB
        syncStorageToDatabase(projectName)
    }

    fun getFileNameFromUri(uri: android.net.Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name ?: uri.path?.substringAfterLast('/')
    }

    private var terminalWorkingDirMap = mutableMapOf<String, String>()

    suspend fun executeCommand(projectName: String, rawCommand: String): String = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(projectName)
        val currentDirName = terminalWorkingDirMap[projectName] ?: ""
        var workingDir = if (currentDirName.isEmpty()) projectDir else File(projectDir, currentDirName)
        if (!workingDir.exists() || !workingDir.isDirectory) {
            workingDir = projectDir
            terminalWorkingDirMap[projectName] = ""
        }

        val trimmed = rawCommand.trim()
        val hasShellOperators = trimmed.contains("&&") || 
                trimmed.contains("||") || 
                trimmed.contains(";") || 
                trimmed.contains("|") || 
                trimmed.contains(">") || 
                trimmed.contains("<")

        val args = splitCommand(trimmed)
        if (args.isNotEmpty() && !hasShellOperators) {
            val cmd = args[0]
            if (cmd == "grep") {
                return@withContext runGrep(workingDir, args)
            } else if (cmd == "find") {
                return@withContext runFind(workingDir, args)
            } else if (cmd == "ls") {
                return@withContext runLs(workingDir, args)
            } else if (cmd == "cat") {
                return@withContext runCat(workingDir, args)
            } else if (cmd == "pwd") {
                return@withContext workingDir.canonicalPath
            } else if (cmd == "mkdir") {
                val res = runMkdir(workingDir, args)
                syncStorageToDatabase(projectName)
                return@withContext res
            } else if (cmd == "rm") {
                val res = runRm(workingDir, args)
                syncStorageToDatabase(projectName)
                return@withContext res
            } else if (cmd == "touch") {
                val res = runTouch(workingDir, args)
                syncStorageToDatabase(projectName)
                return@withContext res
            } else if (cmd == "cp") {
                val res = runCp(workingDir, args)
                syncStorageToDatabase(projectName)
                return@withContext res
            } else if (cmd == "mv") {
                val res = runMv(workingDir, args)
                syncStorageToDatabase(projectName)
                return@withContext res
            } else if (cmd == "echo") {
                return@withContext runEcho(args)
            }
        }
        
        // Custom interactive cd implementation to support stateful navigation
        if (trimmed.startsWith("cd ") && !hasShellOperators) {
            val targetDirName = trimmed.substring(3).trim()
            if (targetDirName == "..") {
                val parent = workingDir.parentFile
                return@withContext if (parent != null && parent.canonicalPath.startsWith(projectDir.canonicalPath)) {
                    val relative = parent.relativeTo(projectDir).path
                    terminalWorkingDirMap[projectName] = relative
                    "Moved to ${relative.ifEmpty { "." }}"
                } else {
                    terminalWorkingDirMap[projectName] = ""
                    "Moved to ."
                }
            } else {
                val targetDir = File(workingDir, targetDirName)
                if (targetDir.exists() && targetDir.isDirectory) {
                    val relative = targetDir.relativeTo(projectDir).path
                    terminalWorkingDirMap[projectName] = relative
                    "Moved to $relative"
                } else {
                    "cd: no such file or directory: $targetDirName"
                }
            }
        } else if (trimmed == "cd" && !hasShellOperators) {
            terminalWorkingDirMap[projectName] = ""
            "Moved to ."
        } else {
            // Run general POSIX shell commands
            try {
                var finalCommand = trimmed
                if (finalCommand.contains("src/") && !File(workingDir, "src").exists() && File(workingDir, "app/src").exists()) {
                    finalCommand = finalCommand.replace("src/", "app/src/")
                }
                if (finalCommand.endsWith(" src") && !File(workingDir, "src").exists() && File(workingDir, "app/src").exists()) {
                    finalCommand = finalCommand.substring(0, finalCommand.length - 4) + " app/src"
                }

                val shellPath = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
                val process = ProcessBuilder()
                    .command(shellPath, "-c", finalCommand)
                    .directory(workingDir)
                    .redirectErrorStream(true)
                    .start()
                
                val output = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()
                
                // Sync any modified files back into Database
                syncStorageToDatabase(projectName)
                
                val exitCode = process.exitValue()
                val isGrep = finalCommand.contains("grep ") || finalCommand.startsWith("grep")

                if (output.isEmpty()) {
                    if (exitCode == 0) {
                        ""
                    } else if (isGrep && exitCode == 1) {
                        "(No matches found)"
                    } else {
                        "Command failed with exit status: $exitCode"
                    }
                } else {
                    if (isGrep && exitCode == 1) {
                        output.trimEnd() + "\n(No matches found)"
                    } else {
                        output.trimEnd()
                    }
                }
            } catch (e: Exception) {
                "Error executing shell command: ${e.localizedMessage}"
            }
        }
    }

    private fun splitCommand(command: String): List<String> {
        val list = mutableListOf<String>()
        val current = StringBuilder()
        var inDoubleQuotes = false
        var inSingleQuotes = false
        var i = 0
        while (i < command.length) {
            val c = command[i]
            if (c == '\"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes
            } else if (c == ' ' && !inDoubleQuotes && !inSingleQuotes) {
                if (current.isNotEmpty()) {
                    list.add(current.toString())
                    current.setLength(0)
                }
            } else {
                current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) {
            list.add(current.toString())
        }
        return list
    }

    private fun globToRegex(glob: String): Regex {
        val out = StringBuilder("^")
        for (i in 0 until glob.length) {
            val c = glob[i]
            when (c) {
                '*' -> out.append(".*")
                '?' -> out.append('.')
                '.' -> out.append("\\.")
                '\\' -> out.append("\\\\")
                else -> out.append(c)
            }
        }
        out.append("$")
        return Regex(out.toString(), RegexOption.IGNORE_CASE)
    }

    private fun runGrep(workingDir: File, args: List<String>): String {
        val options = mutableSetOf<Char>()
        val nonOptions = mutableListOf<String>()
        for (i in 1 until args.size) {
            val arg = args[i]
            if (arg.startsWith("-") && arg.length > 1) {
                for (j in 1 until arg.length) {
                    options.add(arg[j])
                }
            } else {
                nonOptions.add(arg)
            }
        }
        if (nonOptions.isEmpty()) {
            return "Usage: grep [options] pattern [path...]"
        }
        val pattern = nonOptions[0]
        val paths = if (nonOptions.size > 1) nonOptions.subList(1, nonOptions.size) else listOf(".")

        val recursive = options.contains('r') || options.contains('R')
        val ignoreCase = options.contains('i')
        val invertMatch = options.contains('v')
        val wholeWord = options.contains('w')
        val isFixedString = options.contains('F')
        val useRegex = options.contains('E') || !isFixedString

        // Compile regex if allowed and possible
        val regex = if (useRegex) {
            try {
                val flags = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                val regexPattern = if (wholeWord) "\\b$pattern\\b" else pattern
                Regex(regexPattern, flags)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val results = mutableListOf<String>()

        fun searchFile(file: File) {
            val canonicalPath = file.canonicalPath
            if (canonicalPath.contains("/.git/") || canonicalPath.contains("/build/") || canonicalPath.contains("/.gradle/")) {
                return
            }
            try {
                var lineNum = 1
                file.forEachLine { line ->
                    var matched = if (regex != null) {
                        regex.containsMatchIn(line)
                    } else {
                        if (wholeWord) {
                            val wordRegex = if (ignoreCase) {
                                Regex("\\b${Regex.escape(pattern)}\\b", RegexOption.IGNORE_CASE)
                            } else {
                                Regex("\\b${Regex.escape(pattern)}\\b")
                            }
                            wordRegex.containsMatchIn(line)
                        } else {
                            line.contains(pattern, ignoreCase = ignoreCase)
                        }
                    }

                    if (invertMatch) {
                        matched = !matched
                    }

                    if (matched) {
                        val relativePath = file.relativeTo(workingDir).path
                        results.add("$relativePath:$lineNum:$line")
                    }
                    lineNum++
                }
            } catch (e: Exception) {
                // Ignore binary or unreadable files
            }
        }

        fun searchDir(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    if (recursive) {
                        searchDir(file)
                    }
                } else {
                    searchFile(file)
                }
            }
        }

        for (pathStr in paths) {
            // Support wildcards/globbing (e.g. *.kt)
            if (pathStr.contains('*') || pathStr.contains('?')) {
                val parentDir = if (pathStr.contains('/')) {
                    File(workingDir, pathStr.substringBeforeLast('/')).canonicalFile
                } else {
                    workingDir
                }
                val filePattern = pathStr.substringAfterLast('/')
                if (parentDir.exists() && parentDir.isDirectory) {
                    val regexPattern = globToRegex(filePattern)
                    parentDir.listFiles()?.forEach { file ->
                        if (file.isFile && regexPattern.matches(file.name)) {
                            searchFile(file)
                        } else if (file.isDirectory && recursive) {
                            searchDir(file)
                        }
                    }
                }
            } else {
                var targetFile = File(workingDir, pathStr).canonicalFile
                if (!targetFile.exists()) {
                    if (pathStr == "src" || pathStr == "src/") {
                        val fallback = File(workingDir, "app/src").canonicalFile
                        if (fallback.exists()) {
                            targetFile = fallback
                        }
                    } else if (pathStr.startsWith("src/")) {
                        val fallback = File(workingDir, "app/" + pathStr).canonicalFile
                        if (fallback.exists()) {
                            targetFile = fallback
                        }
                    }
                }
                if (!targetFile.exists()) {
                    results.add("grep: $pathStr: No such file or directory")
                    continue
                }
                if (targetFile.isDirectory) {
                    if (recursive) {
                        searchDir(targetFile)
                    } else {
                        results.add("grep: $pathStr: Is a directory")
                    }
                } else {
                    searchFile(targetFile)
                }
            }
        }

        return if (results.isEmpty()) "" else results.joinToString("\n")
    }

    private fun runFind(workingDir: File, args: List<String>): String {
        var pathStr = "."
        var namePattern: String? = null
        
        var i = 1
        if (i < args.size && !args[i].startsWith("-")) {
            pathStr = args[i]
            i++
        }
        
        while (i < args.size) {
            if (args[i] == "-name" && i + 1 < args.size) {
                namePattern = args[i + 1]
                i += 2
            } else {
                i++
            }
        }
        
        val targetFile = File(workingDir, pathStr).canonicalFile
        if (!targetFile.exists()) {
            return "find: $pathStr: No such file or directory"
        }
        
        val nameRegex = namePattern?.let { globToRegex(it) }
        val results = mutableListOf<String>()
        
        fun findFiles(file: File) {
            val canonicalPath = file.canonicalPath
            if (canonicalPath.contains("/.git/") || canonicalPath.contains("/build/") || canonicalPath.contains("/.gradle/")) {
                return
            }
            
            val relativePath = file.relativeTo(workingDir).path
            val displayPath = if (relativePath.isEmpty()) "." else if (pathStr.startsWith("./")) "./$relativePath" else relativePath
            
            if (nameRegex == null || nameRegex.matches(file.name)) {
                results.add(displayPath)
            }
            
            if (file.isDirectory) {
                file.listFiles()?.forEach { findFiles(it) }
            }
        }
        
        findFiles(targetFile)
        return results.joinToString("\n")
    }

    suspend fun getChatsForProject(projectName: String): List<ChatMessageEntity> = withContext(Dispatchers.IO) {
        val chats = dao.getChatsForProject(projectName)
        val cleaned = mutableListOf<ChatMessageEntity>()
        var i = 0
        while (i < chats.size) {
            val curr = chats[i]
            if (curr.role == "user" && i + 1 < chats.size) {
                val next = chats[i + 1]
                if (next.role == "user" && next.content == curr.content && Math.abs(next.timestamp - curr.timestamp) < 300000) {
                    val stale = if (curr.modelName.isNullOrEmpty() && !next.modelName.isNullOrEmpty()) curr else next
                    val keep = if (stale == curr) next else curr
                    try { dao.deleteChatMessage(stale) } catch (_: Exception) {}
                    cleaned.add(keep)
                    i += 2
                    continue
                }
            }
            cleaned.add(curr)
            i++
        }
        cleaned
    }

    suspend fun insertChatMessage(message: ChatMessageEntity): Long = withContext(Dispatchers.IO) {
        dao.insertChatMessage(message)
    }

    suspend fun deleteChatMessage(message: ChatMessageEntity) = withContext(Dispatchers.IO) {
        dao.deleteChatMessage(message)
    }

    private fun getStarterFilesForTemplate(projectName: String, templateKey: String?): List<ProjectFileEntity> {
        return when (templateKey) {
            "android_kotlin" -> listOf(
                ProjectFileEntity(
                    projectName = projectName,
                    path = ".github/workflows/android.yml",
                    content = """name: Android Build

on:
  push:
    branches: [ "main", "master" ]

env:
  ACTIONS_AUDIT_NODE_VERSION: 'false'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - name: Checkout Code
      uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Setup Gradle
      uses: gradle/actions/setup-gradle@v3
      with:
        gradle-version: '8.2'

    - name: Build Debug APK
      run: gradle assembleDebug

    - name: Upload APK Artifact
      uses: actions/upload-artifact@v4
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = ".github/workflows/cleanup.yml",
                    content = """name: Cleanup Old Workflows and Artifacts

on:
  schedule:
    - cron: '0 0 * * *' # Run every day at midnight UTC
  workflow_dispatch: # Enable manual trigger from the GitHub Actions UI

jobs:
  cleanup:
    name: Delete Runs & Artifacts Older Than 1 Day
    runs-on: ubuntu-latest
    permissions:
      actions: write
    steps:
      - name: Clean up Artifacts
        uses: actions/github-script@v7
        with:
          script: |
            const daysToKeep = 1;
            const cutoffDate = new Date();
            cutoffDate.setDate(cutoffDate.getDate() - daysToKeep);

            console.log(`Searching for artifacts older than: ${"$"}{cutoffDate.toISOString()}`);

            try {
              let page = 1;
              let hasMore = true;
              while (hasMore) {
                const response = await github.rest.actions.listArtifactsForRepo({
                  owner: context.repo.owner,
                  repo: context.repo.repo,
                  per_page: 100,
                  page: page
                });

                const artifacts = response.data.artifacts;
                if (!artifacts || artifacts.length === 0) {
                  hasMore = false;
                  break;
                }

                console.log(`Page ${"$"}{page}: Found ${"$"}{artifacts.length} artifacts.`);

                for (const artifact of artifacts) {
                  const createdAt = new Date(artifact.created_at);
                  if (createdAt < cutoffDate) {
                    console.log(`Deleting artifact: ${"$"}{artifact.name} (${"$"}{artifact.id}), created at ${"$"}{artifact.created_at}`);
                    try {
                      await github.rest.actions.deleteArtifact({
                        owner: context.repo.owner,
                        repo: context.repo.repo,
                        artifact_id: artifact.id
                      });
                    } catch (e) {
                      console.error(`Error deleting artifact ${"$"}{artifact.id}: ${"$"}{e.message}`);
                    }
                  } else {
                    console.log(`Keeping artifact: ${"$"}{artifact.name} (${"$"}{artifact.id}), created at ${"$"}{artifact.created_at}`);
                  }
                }

                if (artifacts.length < 100) {
                  hasMore = false;
                } else {
                  page++;
                }
              }
            } catch (error) {
              core.setFailed(`Artifact cleanup failed: ${"$"}{error.message}`);
            }

      - name: Clean up Workflow Runs
        uses: actions/github-script@v7
        with:
          script: |
            const daysToKeep = 1;
            const cutoffDate = new Date();
            cutoffDate.setDate(cutoffDate.getDate() - daysToKeep);

            console.log(`Searching for workflow runs older than: ${"$"}{cutoffDate.toISOString()}`);

            try {
              let page = 1;
              let hasMore = true;
              while (hasMore) {
                const response = await github.rest.actions.listWorkflowRunsForRepo({
                  owner: context.repo.owner,
                  repo: context.repo.repo,
                  per_page: 100,
                  page: page
                });

                const runs = response.data.workflow_runs;
                if (!runs || runs.length === 0) {
                  hasMore = false;
                  break;
                }

                console.log(`Page ${"$"}{page}: Found ${"$"}{runs.length} workflow runs.`);

                for (const run of runs) {
                  const createdAt = new Date(run.created_at);
                  // DO NOT delete the currently running workflow run!
                  if (run.id === context.runId) {
                    continue;
                  }
                  if (createdAt < cutoffDate) {
                    console.log(`Deleting workflow run: ${"$"}{run.name} #${"$"}{run.run_number} (${"$"}{run.id}), created at ${"$"}{run.created_at}`);
                    try {
                      await github.rest.actions.deleteWorkflowRun({
                        owner: context.repo.owner,
                        repo: context.repo.repo,
                        run_id: run.id
                      });
                    } catch (e) {
                      console.error(`Error deleting run ${"$"}{run.id}: ${"$"}{e.message}`);
                    }
                  } else {
                    console.log(`Keeping workflow run: ${"$"}{run.name} #${"$"}{run.run_number} (${"$"}{run.id}), created at ${"$"}{run.created_at}`);
                  }
                }

                if (runs.length < 100) {
                  hasMore = false;
                } else {
                  page++;
                }
              }
            } catch (error) {
              core.setFailed(`Workflow run cleanup failed: ${"$"}{error.message}`);
            }
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "gradle.properties",
                    content = """org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "build.gradle.kts",
                    content = """plugins {
    id("com.android.application") version "8.1.1" apply false
    id("com.android.library") version "8.1.1" apply false
    id("org.jetbrains.kotlin.android") version "1.8.10" apply false
}
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "settings.gradle.kts",
                    content = """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MyAndroidApp"
include(":app")
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "app/build.gradle.kts",
                    content = """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.myandroidapp"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.myandroidapp"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.0")
    implementation(platform("androidx.compose:compose-bom:2023.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
}
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "app/src/main/AndroidManifest.xml",
                    content = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="My Android App"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "app/src/main/java/com/example/myandroidapp/MainActivity.kt",
                    content = """package com.example.myandroidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android Kotlin")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello ${"$"}name!",
        modifier = modifier
    )
}
"""
                )
            )
            "flutter" -> listOf(
                ProjectFileEntity(
                    projectName = projectName,
                    path = ".github/workflows/android.yml",
                    content = """name: Flutter Build

on:
  push:
    branches: [ "main", "master" ]

env:
  ACTIONS_AUDIT_NODE_VERSION: 'false'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - name: Checkout Code
      uses: actions/checkout@v4

    - name: Set up Java
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Set up Flutter
      uses: subosito/flutter-action@v2
      with:
        channel: 'stable'

    - name: Setup Gradle
      uses: gradle/actions/setup-gradle@v4
      with:
        gradle-version: '8.5'

    - name: Install Dependencies
      run: flutter pub get

    - name: Build APK
      run: flutter build apk --debug

    - name: Upload APK Artifact
      uses: actions/upload-artifact@v4
      with:
        name: app-debug
        path: build/app/outputs/flutter-apk/app-debug.apk
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = ".github/workflows/cleanup.yml",
                    content = """name: Cleanup Old Workflows and Artifacts

on:
  schedule:
    - cron: '0 0 * * *' # Run every day at midnight UTC
  workflow_dispatch: # Enable manual trigger from the GitHub Actions UI

jobs:
  cleanup:
    name: Delete Runs & Artifacts Older Than 1 Day
    runs-on: ubuntu-latest
    permissions:
      actions: write
    steps:
      - name: Clean up Artifacts
        uses: actions/github-script@v7
        with:
          script: |
            const daysToKeep = 1;
            const cutoffDate = new Date();
            cutoffDate.setDate(cutoffDate.getDate() - daysToKeep);

            console.log(`Searching for artifacts older than: ${"$"}{cutoffDate.toISOString()}`);

            try {
              let page = 1;
              let hasMore = true;
              while (hasMore) {
                const response = await github.rest.actions.listArtifactsForRepo({
                  owner: context.repo.owner,
                  repo: context.repo.repo,
                  per_page: 100,
                  page: page
                });

                const artifacts = response.data.artifacts;
                if (!artifacts || artifacts.length === 0) {
                  hasMore = false;
                  break;
                }

                console.log(`Page ${"$"}{page}: Found ${"$"}{artifacts.length} artifacts.`);

                for (const artifact of artifacts) {
                  const createdAt = new Date(artifact.created_at);
                  if (createdAt < cutoffDate) {
                    console.log(`Deleting artifact: ${"$"}{artifact.name} (${"$"}{artifact.id}), created at ${"$"}{artifact.created_at}`);
                    try {
                      await github.rest.actions.deleteArtifact({
                        owner: context.repo.owner,
                        repo: context.repo.repo,
                        artifact_id: artifact.id
                      });
                    } catch (e) {
                      console.error(`Error deleting artifact ${"$"}{artifact.id}: ${"$"}{e.message}`);
                    }
                  } else {
                    console.log(`Keeping artifact: ${"$"}{artifact.name} (${"$"}{artifact.id}), created at ${"$"}{artifact.created_at}`);
                  }
                }

                if (artifacts.length < 100) {
                  hasMore = false;
                } else {
                  page++;
                }
              }
            } catch (error) {
              core.setFailed(`Artifact cleanup failed: ${"$"}{error.message}`);
            }

      - name: Clean up Workflow Runs
        uses: actions/github-script@v7
        with:
          script: |
            const daysToKeep = 1;
            const cutoffDate = new Date();
            cutoffDate.setDate(cutoffDate.getDate() - daysToKeep);

            console.log(`Searching for workflow runs older than: ${"$"}{cutoffDate.toISOString()}`);

            try {
              let page = 1;
              let hasMore = true;
              while (hasMore) {
                const response = await github.rest.actions.listWorkflowRunsForRepo({
                  owner: context.repo.owner,
                  repo: context.repo.repo,
                  per_page: 100,
                  page: page
                });

                const runs = response.data.workflow_runs;
                if (!runs || runs.length === 0) {
                  hasMore = false;
                  break;
                }

                console.log(`Page ${"$"}{page}: Found ${"$"}{runs.length} workflow runs.`);

                for (const run of runs) {
                  const createdAt = new Date(run.created_at);
                  // DO NOT delete the currently running workflow run!
                  if (run.id === context.runId) {
                    continue;
                  }
                  if (createdAt < cutoffDate) {
                    console.log(`Deleting workflow run: ${"$"}{run.name} #${"$"}{run.run_number} (${"$"}{run.id}), created at ${"$"}{run.created_at}`);
                    try {
                      await github.rest.actions.deleteWorkflowRun({
                        owner: context.repo.owner,
                        repo: context.repo.repo,
                        run_id: run.id
                      });
                    } catch (e) {
                      console.error(`Error deleting run ${"$"}{run.id}: ${"$"}{e.message}`);
                    }
                  } else {
                    console.log(`Keeping workflow run: ${"$"}{run.name} #${"$"}{run.run_number} (${"$"}{run.id}), created at ${"$"}{run.created_at}`);
                  }
                }

                if (runs.length < 100) {
                  hasMore = false;
                } else {
                  page++;
                }
              }
            } catch (error) {
              core.setFailed(`Workflow run cleanup failed: ${"$"}{error.message}`);
            }
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "pubspec.yaml",
                    content = """name: my_flutter_app
description: A new Flutter project.
publish_to: 'none'
version: 1.0.0+1

environment:
  sdk: '>=3.0.0 <4.0.0'

dependencies:
  flutter:
    sdk: flutter
  cupertino_icons: ^1.0.2

dev_dependencies:
  flutter_test:
    sdk: flutter
  flutter_lints: ^2.0.0

flutter:
  uses-material-design: true
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "lib/main.dart",
                    content = """import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const MyHomePage(title: 'Flutter Home Page'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  int _counter = 0;

  void _incrementCounter() {
    setState(() {
      _counter++;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: Text(widget.title),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            const Text(
              'You have pushed the button this many times:',
            ),
            Text(
              '${"$"}_counter',
              style: Theme.of(context).textTheme.headlineMedium,
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _incrementCounter,
        tooltip: 'Increment',
        child: const Icon(Icons.add),
      ),
    );
  }
}
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "android/app/build.gradle",
                    content = """plugins {
    id "com.android.application"
    id "dev.flutter.flutter-gradle-plugin"
}

def localProperties = new Properties()
def localPropertiesFile = rootProject.file('local.properties')
if (localPropertiesFile.exists()) {
    localPropertiesFile.withReader('UTF-8') { reader ->
        localProperties.load(reader)
    }
}

def flutterVersionCode = localProperties.getProperty('flutter.versionCode')
if (flutterVersionCode == null) {
    flutterVersionCode = '1'
}

def flutterVersionName = localProperties.getProperty('flutter.versionName')
if (flutterVersionName == null) {
    flutterVersionName = '1.0'
}

android {
    namespace "com.example.my_flutter_app"
    compileSdkVersion flutter.compileSdkVersion
    ndkVersion flutter.ndkVersion

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId "com.example.my_flutter_app"
        minSdkVersion flutter.minSdkVersion
        targetSdkVersion flutter.targetSdkVersion
        versionCode flutterVersionCode.toInteger()
        versionName flutterVersionName
    }

    buildTypes {
        release {
            signingConfig signingConfigs.debug
        }
    }
}

flutter {
    source '../..'
}
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "android/build.gradle",
                    content = """allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.buildDir = '../build'
subprojects {
    project.buildDir = "${"$"}{rootProject.buildDir}/${"$"}{project.name}"
}
subprojects {
    project.evaluationDependsOn(':app')
}

tasks.register("clean", Delete) {
    delete rootProject.buildDir
}
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "android/settings.gradle",
                    content = """pluginManagement {
    def flutterSdkPath = {
        def properties = new Properties()
        def propertiesFile = new File(settingsDir, "local.properties")
        if (propertiesFile.exists()) {
            propertiesFile.withReader("UTF-8") { reader -> properties.load(reader) }
        }
        def sdkPath = properties.getProperty("flutter.sdk")
        assert sdkPath != null, "flutter.sdk not set in local.properties"
        return sdkPath
    }()

    includeBuild "${"$"}{flutterSdkPath}/packages/flutter_tools/gradle"

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id "dev.flutter.flutter-gradle-plugin" version "1.0.0" apply false
    id "com.android.application" version "8.6.0" apply false
}

include ":app"
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "android/gradle.properties",
                    content = """org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "android/gradle/wrapper/gradle-wrapper.properties",
                    content = """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.8-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "android/app/src/main/AndroidManifest.xml",
                    content = """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="my_flutter_app"
        android:name="${"$"}{applicationName}">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:configChanges="orientation|keyboardHidden|keyboard|screenSize|smallestScreenSize|locale|layoutDirection|fontScale|screenLayout|density|uiMode"
            android:hardwareAccelerated="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
        <meta-data
            android:name="flutterEmbedding"
            android:value="2" />
    </application>
</manifest>"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "android/app/src/main/java/com/example/my_flutter_app/MainActivity.java",
                    content = """package com.example.my_flutter_app;

import io.flutter.embedding.android.FlutterActivity;

public class MainActivity extends FlutterActivity {
}"""
                )
            )
            "react_vite" -> listOf(
                ProjectFileEntity(
                    projectName = projectName,
                    path = ".github/workflows/android.yml",
                    content = """name: React Vite Web Build

on:
  push:
    branches: [ "main", "master" ]

env:
  ACTIONS_AUDIT_NODE_VERSION: 'false'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - name: Checkout Code
      uses: actions/checkout@v4

    - name: Set up Node.js
      uses: actions/setup-node@v4
      with:
        node-version: '20'

    - name: Install Dependencies
      run: npm install || npm install --legacy-peer-deps

    - name: Build React Vite App (npm run esbuild / build)
      run: |
        if grep -q '"esbuild"' package.json; then
          npm run esbuild
        else
          npm run build
        fi

    - name: Prepare Web Dist Artifact
      run: |
        mkdir -p web-dist
        if [ -d "dist" ]; then
          cp -r dist/* web-dist/
        elif [ -d "build" ]; then
          cp -r build/* web-dist/
        else
          cp -r * web-dist/ 2>/dev/null || true
        fi
        cd web-dist && zip -r ../web-dist.zip ./* && cd ..

    - name: Upload Web Dist Artifact
      uses: actions/upload-artifact@v4
      with:
        name: web-dist
        path: web-dist.zip
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = ".github/workflows/cleanup.yml",
                    content = """name: Cleanup Old Workflows and Artifacts

on:
  schedule:
    - cron: '0 0 * * *'
  workflow_dispatch:

jobs:
  cleanup:
    name: Delete Runs & Artifacts Older Than 1 Day
    runs-on: ubuntu-latest
    permissions:
      actions: write
    steps:
      - name: Clean up Artifacts
        uses: actions/github-script@v7
        with:
          script: |
            try {
              const response = await github.rest.actions.listWorkflowRunsForRepo({
                owner: context.repo.owner,
                repo: context.repo.repo,
                per_page: 30
              });
              const runs = response.data.workflow_runs;
              const completedRuns = runs.filter(run => run.status === 'completed');
              for (let i = 5; i < completedRuns.length; i++) {
                await github.rest.actions.deleteWorkflowRun({
                  owner: context.repo.owner,
                  repo: context.repo.repo,
                  run_id: completedRuns[i].id
                });
              }
            } catch (error) {
              core.setFailed(`Workflow run cleanup failed: ${"$"}{error.message}`);
            }
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "package.json",
                    content = """{
  "name": "react-vite-app",
  "private": true,
  "version": "0.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "esbuild": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0"
  },
  "devDependencies": {
    "@types/react": "^18.2.55",
    "@types/react-dom": "^18.2.19",
    "@vitejs/plugin-react": "^4.2.1",
    "autoprefixer": "^10.4.17",
    "postcss": "^8.4.35",
    "tailwindcss": "^3.4.1",
    "vite": "^5.1.0"
  }
}"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "vite.config.js",
                    content = """import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
})"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "tailwind.config.js",
                    content = """/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {},
  },
  plugins: [],
}"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "postcss.config.js",
                    content = """export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
}"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "index.html",
                    content = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>React Vite App</title>
</head>
<body class="bg-slate-950 text-white min-h-screen">
    <div id="root"></div>
    <script type="module" src="/src/main.jsx"></script>
</body>
</html>"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "src/main.jsx",
                    content = """import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "src/index.css",
                    content = """@tailwind base;
@tailwind components;
@tailwind utilities;"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "src/App.jsx",
                    content = """export default function App() {
  return (
    <div className="min-h-screen bg-slate-950 text-white flex flex-col items-center justify-center p-6">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-8 max-w-md w-full text-center shadow-2xl">
        <div className="w-16 h-16 bg-purple-600/20 border border-purple-500/40 rounded-full flex items-center justify-center mx-auto mb-4">
          <span className="text-2xl font-black text-purple-400">👋</span>
        </div>
        <h1 className="text-3xl font-bold mb-2 bg-gradient-to-r from-purple-400 via-pink-400 to-cyan-400 bg-clip-text text-transparent">Hello World</h1>
        <p className="text-slate-400 text-sm">Welcome to your clean React + Vite + Tailwind application.</p>
      </div>
    </div>
  )
}"""
                )
            )
            "react" -> listOf(
                ProjectFileEntity(
                    projectName = projectName,
                    path = "index.html",
                    content = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>React Hello World</title>
    <!-- Tailwind CSS CDN -->
    <script src="https://cdn.tailwindcss.com"></script>
    <!-- React & ReactDOM CDN -->
    <script src="https://unpkg.com/react@18/umd/react.development.js" crossorigin></script>
    <script src="https://unpkg.com/react-dom@18/umd/react-dom.development.js" crossorigin></script>
    <!-- Babel CDN to compile JSX -->
    <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700&display=swap" rel="stylesheet">
    <style>
        body {
            font-family: 'Plus Jakarta Sans', sans-serif;
            background-color: #0d0e15;
            margin: 0;
            padding: 0;
        }
    </style>
</head>
<body class="text-white min-h-screen flex items-center justify-center">
    <div id="root"></div>

    <script type="text/babel" data-presets="react,stage-3">
        const { useState } = React;

        function App() {
            const [count, setCount] = useState(0);
            return (
                <div className="bg-[#151726] border border-[#2b2f4a] p-8 rounded-2xl shadow-2xl max-w-md text-center">
                    <h1 className="text-3xl font-extrabold bg-gradient-to-r from-cyan-400 to-blue-500 bg-clip-text text-transparent mb-4">
                        React Hello World
                    </h1>
                    <p className="text-gray-400 mb-6">
                        Powered by React 18 & ReactDOM directly from unpkg CDN.
                    </p>
                    <div className="p-6 bg-[#0d0e15] rounded-xl border border-[#23273f] mb-6">
                        <p className="text-sm font-semibold text-cyan-400 mb-2">Interactive Counter</p>
                        <span className="text-4xl font-bold text-white">{count}</span>
                    </div>
                    <button 
                        onClick={() => setCount(count + 1)}
                        className="bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-600 hover:to-blue-700 text-white font-bold py-3 px-8 rounded-xl transition duration-300 transform hover:scale-105 shadow-lg shadow-cyan-500/20"
                    >
                        Click Me!
                    </button>
                </div>
            );
        }

        const container = document.getElementById('root');
        const root = ReactDOM.createRoot(container);
        root.render(<App />);
    </script>
</body>
</html>"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "src/main.tsx",
                    content = """import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './app';

const container = document.getElementById('root');
if (container) {
  const root = ReactDOM.createRoot(container);
  root.render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
}"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "src/app.tsx",
                    content = """import React, { useState } from 'react';

export default function App() {
    const [count, setCount] = useState(0);
    return (
        <div className="bg-[#151726] border border-[#2b2f4a] p-8 rounded-2xl shadow-2xl max-w-md text-center">
            <h1 className="text-3xl font-extrabold bg-gradient-to-r from-cyan-400 to-blue-500 bg-clip-text text-transparent mb-4">
                React Hello World
            </h1>
            <p className="text-gray-400 mb-6">
                Powered by React 18 & ReactDOM directly from unpkg CDN.
            </p>
            <div className="p-6 bg-[#0d0e15] rounded-xl border border-[#23273f] mb-6">
                <p className="text-sm font-semibold text-cyan-400 mb-2">Interactive Counter</p>
                <span className="text-4xl font-bold text-white">{count}</span>
            </div>
            <button 
                onClick={() => setCount(count + 1)}
                className="bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-600 hover:to-blue-700 text-white font-bold py-3 px-8 rounded-xl transition duration-300 transform hover:scale-105 shadow-lg shadow-cyan-500/20"
            >
                Click Me!
            </button>
        </div>
    );
}"""
                )
            )
            "vanilla" -> listOf(
                ProjectFileEntity(
                    projectName = projectName,
                    path = "index.html",
                    content = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Hello World</title>
    <link rel="stylesheet" href="style.css">
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700&display=swap" rel="stylesheet">
</head>
<body>
    <h1>Hello World</h1>
    <script src="script.js"></script>
</body>
</html>"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "style.css",
                    content = """* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
    font-family: 'Plus Jakarta Sans', sans-serif;
}
body {
    background-color: #0d0e15;
    color: #ffffff;
    height: 100vh;
    width: 100vw;
    display: flex;
    justify-content: center;
    align-items: center;
    overflow: hidden;
}
h1 {
    font-size: 3.5rem;
    font-weight: 800;
    background: linear-gradient(135deg, #00ffcc, #6c5ce7);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    text-align: center;
}"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "script.js",
                    content = """console.log('Hello World');"""
                )
            )
            "vanilla_three" -> listOf(
                ProjectFileEntity(
                    projectName = projectName,
                    path = "index.html",
                    content = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <title>3D Globe World</title>
    <link rel="stylesheet" href="style.css">
    <script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js"></script>
</head>
<body>
    <canvas id="bg"></canvas>
    <script src="main.js"></script>
</body>
</html>"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "style.css",
                    content = """* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}
body, html {
    width: 100%;
    height: 100%;
    overflow: hidden;
    background-color: #030408;
}
#bg {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 1;
    touch-action: none;
}"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "main.js",
                    content = """// Initialize Three.js Scene
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x030408);

// Camera Setup
const camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 1000);
camera.position.set(0, 0, 4.2);

// Renderer Setup
const renderer = new THREE.WebGLRenderer({
    canvas: document.querySelector('#bg'),
    antialias: true
});
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(window.innerWidth, window.innerHeight);

// Orbit Controls for smooth drag, rotate & zoom
const controls = new THREE.OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
controls.dampingFactor = 0.05;
controls.rotateSpeed = 0.8;
controls.zoomSpeed = 1.0;
controls.autoRotate = true;
controls.autoRotateSpeed = 1.2;

// Lighting
const ambientLight = new THREE.AmbientLight(0xffffff, 0.7);
scene.add(ambientLight);

const dirLight = new THREE.DirectionalLight(0x00f2fe, 1.8);
dirLight.position.set(5, 3, 5);
scene.add(dirLight);

const blueGlowLight = new THREE.PointLight(0x38bdf8, 2, 50);
blueGlowLight.position.set(-5, -3, -5);
scene.add(blueGlowLight);

// Interactive 3D Globe Mesh
const globeGroup = new THREE.Group();
scene.add(globeGroup);

// Core Sphere
const sphereGeo = new THREE.SphereGeometry(1.5, 64, 64);
const sphereMat = new THREE.MeshPhongMaterial({
    color: 0x0a1128,
    emissive: 0x051937,
    specular: 0x00f2fe,
    shininess: 25,
    wireframe: false
});
const globe = new THREE.Mesh(sphereGeo, sphereMat);
globeGroup.add(globe);

// Latitude & Longitude Grid Overlay
const gridMat = new THREE.MeshBasicMaterial({
    color: 0x00f2fe,
    wireframe: true,
    transparent: true,
    opacity: 0.18
});
const gridMesh = new THREE.Mesh(new THREE.SphereGeometry(1.505, 32, 16), gridMat);
globeGroup.add(gridMesh);

// Outer Atmosphere Glow Ring
const atmosphereGeo = new THREE.SphereGeometry(1.68, 64, 64);
const atmosphereMat = new THREE.MeshBasicMaterial({
    color: 0x38bdf8,
    transparent: true,
    opacity: 0.08,
    side: THREE.BackSide
});
const atmosphere = new THREE.Mesh(atmosphereGeo, atmosphereMat);
globeGroup.add(atmosphere);

// Starfield Background Particles
const starCount = 800;
const starGeo = new THREE.BufferGeometry();
const starCoords = new Float32Array(starCount * 3);
for (let i = 0; i < starCount * 3; i++) {
    starCoords[i] = (Math.random() - 0.5) * 50;
}
starGeo.setAttribute('position', new THREE.BufferAttribute(starCoords, 3));
const starMat = new THREE.PointsMaterial({
    color: 0xffffff,
    size: 0.08,
    transparent: true,
    opacity: 0.6
});
const starField = new THREE.Points(starGeo, starMat);
scene.add(starField);

// Handle Window Resize
window.addEventListener('resize', () => {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
});

// Animation Loop
function animate() {
    requestAnimationFrame(animate);
    controls.update();
    renderer.render(scene, camera);
}

animate();"""
                )
            )
            "apk_decompile" -> listOf(
                ProjectFileEntity(
                    projectName = projectName,
                    path = "README.md",
                    content = """# APK Reverse Engineer & Recompiler Workspace

This workspace allows you to decompile Android APK files, inspect & edit source code, manifest, XML resources, and rebuild using GitHub Actions.

## How to Decompile an APK:
1. Import or drag an `.apk` file into this project.
2. In the File Explorer, click the three dots (`...`) menu next to the `.apk` file and select **"Decompile APK"**.
3. All resources, manifests, and source structures will be extracted into this workspace for editing!
4. Edit files, update code, and click **Build** to recompile via GitHub Actions.
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = ".github/workflows/build.yml",
                    content = """name: Build APK

on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

env:
  ACTIONS_AUDIT_NODE_VERSION: 'false'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - name: Checkout Code
      uses: actions/checkout@v4

    - name: Set up Java
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Setup Gradle
      uses: gradle/actions/setup-gradle@v4
      with:
        gradle-version: '8.5'

    - name: Make Gradle Wrapper Executable
      run: chmod +x gradlew || true

    - name: Build Debug APK
      run: ./gradlew assembleDebug --stacktrace || gradle assembleDebug

    - name: Upload APK Artifact
      uses: actions/upload-artifact@v4
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = ".github/workflows/cleanup.yml",
                    content = """name: Cleanup Old Workflows and Artifacts

on:
  schedule:
    - cron: '0 0 * * *' # Run every day at midnight UTC
  workflow_dispatch: # Enable manual trigger from the GitHub Actions UI

jobs:
  cleanup:
    name: Delete Runs & Artifacts Older Than 1 Day
    runs-on: ubuntu-latest
    permissions:
      actions: write
    steps:
      - name: Clean up Artifacts
        uses: actions/github-script@v7
        with:
          script: |
            const daysToKeep = 1;
            const cutoffDate = new Date();
            cutoffDate.setDate(cutoffDate.getDate() - daysToKeep);

            console.log(`Searching for artifacts older than: ${"$"}{cutoffDate.toISOString()}`);

            try {
              let page = 1;
              let hasMore = true;
              while (hasMore) {
                const response = await github.rest.actions.listArtifactsForRepo({
                  owner: context.repo.owner,
                  repo: context.repo.repo,
                  per_page: 100,
                  page: page,
                });

                const artifacts = response.data.artifacts;
                if (artifacts.length === 0) {
                  hasMore = false;
                  break;
                }

                for (const artifact of artifacts) {
                  const createdAt = new Date(artifact.created_at);
                  if (createdAt < cutoffDate) {
                    console.log(`Deleting artifact: ${"$"}{artifact.name} (ID: ${"$"}{artifact.id}) created on ${"$"}{artifact.created_at}`);
                    await github.rest.actions.deleteArtifact({
                      owner: context.repo.owner,
                      repo: context.repo.repo,
                      artifact_id: artifact.id,
                    });
                  }
                }
                page++;
              }
            } catch (error) {
              console.error(`Artifact cleanup failed: ${"$"}{error.message}`);
            }

      - name: Clean up Workflow Runs
        uses: actions/github-script@v7
        with:
          script: |
            const daysToKeep = 1;
            const cutoffDate = new Date();
            cutoffDate.setDate(cutoffDate.getDate() - daysToKeep);

            console.log(`Searching for workflow runs older than: ${"$"}{cutoffDate.toISOString()}`);

            try {
              let page = 1;
              let hasMore = true;
              while (hasMore) {
                const response = await github.rest.actions.listWorkflowRunsForRepo({
                  owner: context.repo.owner,
                  repo: context.repo.repo,
                  per_page: 100,
                  page: page,
                });

                const runs = response.data.workflow_runs;
                if (runs.length === 0) {
                  hasMore = false;
                  break;
                }

                for (const run of runs) {
                  const createdAt = new Date(run.created_at);
                  if (createdAt < cutoffDate) {
                    console.log(`Deleting workflow run: ${"$"}{run.name} (ID: ${"$"}{run.id}) created on ${"$"}{run.created_at}`);
                    await github.rest.actions.deleteWorkflowRun({
                      owner: context.repo.owner,
                      repo: context.repo.repo,
                      run_id: run.id,
                    });
                  }
                }
                page++;
              }
            } catch (error) {
              core.setFailed(`Workflow run cleanup failed: ${"$"}{error.message}`);
            }
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "settings.gradle.kts",
                    content = """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "DecompiledApp"
include(":app")
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "app/build.gradle.kts",
                    content = """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.decompiledapp"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.decompiledapp"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.0")
    implementation(platform("androidx.compose:compose-bom:2023.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
}
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "app/src/main/AndroidManifest.xml",
                    content = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="Decompiled App"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "app/src/main/java/com/example/decompiledapp/MainActivity.kt",
                    content = """package com.example.decompiledapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Decompiled App")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello ${"$"}name!",
        modifier = modifier
    )
}
"""
                )
            )
            "chrome_extension" -> com.example.data.generators.ChromeExtensionGenerator.getStarterFiles(projectName)
            else -> listOf(
                ProjectFileEntity(
                    projectName = projectName,
                    path = "index.html",
                    content = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Awesome AI App</title>
    <link rel="stylesheet" href="style.css">
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700&display=swap" rel="stylesheet">
</head>
<body>
    <div class="card">
        <h1>Vibe Workspace</h1>
        <p>I have created an empty project files workspace for you.</p>
        <button id="actionBtn">Touch the Future</button>
    </div>
    <script src="script.js"></script>
</body>
</html>"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "style.css",
                    content = """body {
    background-color: #0b0c10;
    color: #ffffff;
    font-family: 'Plus Jakarta Sans', sans-serif;
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    margin: 0;
}
.card {
    background-color: #1f2833;
    padding: 40px;
    border-radius: 16px;
    text-align: center;
    box-shadow: 0 4px 30px rgba(0, 0, 0, 0.5);
    max-width: 400px;
}
h1 {
    color: #66fcf1;
    margin-bottom: 10px;
}
button {
    background-color: #45f248;
    color: #000;
    border: none;
    padding: 12px 24px;
    border-radius: 8px;
    font-weight: bold;
    cursor: pointer;
    margin-top: 20px;
}"""
                ),
                ProjectFileEntity(
                    projectName = projectName,
                    path = "script.js",
                    content = """document.getElementById('actionBtn').addEventListener('click', () => {
    alert('Welcome to your Vibe Workspace! Ask your AI Agent to build whatever you imagine.');
});"""
                )
            )
        }
    }

    suspend fun cloneRepository(
        projectName: String,
        repo: String,
        token: String?,
        branch: String = "main",
        progressCallback: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            progressCallback("Initializing workspace...")
            val cleanRepo = repo.trim().removePrefix("https://github.com/").removePrefix("http://github.com/").removeSuffix(".git")
            val parts = cleanRepo.split("/")
            if (parts.size < 2) {
                return@withContext Result.failure(Exception("Invalid repository format. Please use 'owner/repo' or GitHub URL."))
            }
            val owner = parts[0]
            val repoName = parts[1]

            // Create project entry
            val project = ProjectEntity(projectName, "Cloned from $owner/$repoName", System.currentTimeMillis())
            dao.insertProject(project)

            progressCallback("Downloading repository ZIP...")
            val client = OkHttpClient()
            val url = "https://api.github.com/repos/$owner/$repoName/zipball/$branch"
            
            val requestBuilder = Request.Builder().url(url)
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "token ${token.trim()}")
            }
            requestBuilder.header("Accept", "application/vnd.github.v3+json")

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                // If main branch fails, try master
                if (branch == "main") {
                    progressCallback("Main branch failed, trying master branch...")
                    val fallbackUrl = "https://api.github.com/repos/$owner/$repoName/zipball/master"
                    val fallbackReq = Request.Builder().url(fallbackUrl)
                    if (!token.isNullOrBlank()) {
                        fallbackReq.header("Authorization", "token ${token.trim()}")
                    }
                    val fallbackResp = client.newCall(fallbackReq.build()).execute()
                    if (!fallbackResp.isSuccessful) {
                        return@withContext Result.failure(Exception("Failed to download ZIP: ${fallbackResp.code} ${fallbackResp.message}"))
                    }
                    unzipAndLoad(projectName, fallbackResp.body?.byteStream() ?: throw Exception("Empty response body"))
                } else {
                    return@withContext Result.failure(Exception("Failed to download ZIP: ${response.code} ${response.message}"))
                }
            } else {
                unzipAndLoad(projectName, response.body?.byteStream() ?: throw Exception("Empty response body"))
            }

            // Sync database files to physical storage
            progressCallback("Synchronizing database and local files...")
            syncDatabaseToStorage(projectName)

            // Insert initial assistant message
            val welcomeMessage = ChatMessageEntity(
                projectName = projectName,
                role = "assistant",
                content = "Successfully cloned repository **$owner/$repoName**! All files have been loaded into your local storage. Let's start vibe coding!",
                timestamp = System.currentTimeMillis()
            )
            dao.insertChatMessage(welcomeMessage)

            progressCallback("Clone complete!")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun unzipAndLoad(projectName: String, byteStream: java.io.InputStream) {
        val zipIn = ZipInputStream(byteStream)
        var entry = zipIn.nextEntry

        while (entry != null) {
            if (!entry.isDirectory) {
                // Skip the top level github generated directory (e.g. owner-repo-sha/)
                val entryName = entry.name
                val pathParts = entryName.split("/")
                if (pathParts.size > 1) {
                    val relativePath = pathParts.drop(1).joinToString("/")
                    if (relativePath.isNotBlank() && !relativePath.startsWith(".")) {
                        // Read content
                        val outStream = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        var len = zipIn.read(buffer)
                        while (len > 0) {
                            outStream.write(buffer, 0, len)
                            len = zipIn.read(buffer)
                        }
                        val contentStr = outStream.toString("UTF-8")
                        dao.insertFile(ProjectFileEntity(projectName = projectName, path = relativePath, content = contentStr))
                    }
                }
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }
        zipIn.close()
    }

    suspend fun pushToGitHub(
        projectName: String,
        repo: String,
        token: String,
        branch: String = "main",
        force: Boolean = false,
        progressCallback: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            progressCallback("Preparing files for GitHub...")
            val client = OkHttpClient()
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val mediaType = "application/json; charset=utf-8".toMediaType()

            val cleanRepo = repo.trim().removePrefix("https://github.com/").removePrefix("http://github.com/").removeSuffix(".git")
            val parts = cleanRepo.split("/").filter { it.isNotBlank() }
            
            var owner = ""
            var repoName = ""
            
            if (parts.size == 1) {
                progressCallback("Fetching GitHub username from token...")
                val userRequest = Request.Builder()
                    .url("https://api.github.com/user")
                    .header("Authorization", "token ${token.trim()}")
                    .header("Accept", "application/vnd.github.v3+json")
                    .get()
                    .build()
                val userResponse = client.newCall(userRequest).execute()
                if (userResponse.isSuccessful) {
                    val bodyStr = userResponse.body?.string() ?: ""
                    val userMap = moshi.adapter(Map::class.java).fromJson(bodyStr) as? Map<*, *>
                    val login = userMap?.get("login") as? String
                    if (!login.isNullOrBlank()) {
                        owner = login
                        repoName = parts[0]
                    } else {
                        return@withContext Result.failure(Exception("Could not retrieve username from token. Please specify 'owner/repo' format."))
                    }
                } else {
                    return@withContext Result.failure(Exception("Failed to fetch GitHub username (${userResponse.code}). Please check your token or use 'owner/repo' format."))
                }
            } else if (parts.size >= 2) {
                owner = parts[0]
                repoName = parts[1]
            } else {
                return@withContext Result.failure(Exception("Invalid repository format. Please use 'owner/repo' or GitHub URL."))
            }

            // 1. Get all files in the project
            val projectFiles = dao.getFilesForProject(projectName)
            if (projectFiles.isEmpty()) {
                return@withContext Result.failure(Exception("Cannot push an empty workspace. Please add files first."))
            }

            // 1.5 Verify repository existence & get metadata
            progressCallback("Verifying repository with GitHub...")
            val repoUrl = "https://api.github.com/repos/$owner/$repoName"
            val repoRequest = Request.Builder()
                .url(repoUrl)
                .header("Authorization", "token ${token.trim()}")
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build()
            
            val repoResponse = client.newCall(repoRequest).execute()
            if (!repoResponse.isSuccessful) {
                val code = repoResponse.code
                val bodyStr = repoResponse.body?.string() ?: ""
                return@withContext Result.failure(Exception("GitHub repository verification failed ($code). Check your URL, token, and permissions.\nDetails: $bodyStr"))
            }
            
            val repoBody = repoResponse.body?.string() ?: ""
            val repoMap = moshi.adapter(Map::class.java).fromJson(repoBody) as? Map<*, *>
            val defaultBranch = (repoMap?.get("default_branch") as? String) ?: "main"

            // 2. Check if branch reference exists
            progressCallback("Checking remote branch status...")
            val refUrl = "https://api.github.com/repos/$owner/$repoName/git/refs/heads/$branch"
            val refRequest = Request.Builder()
                .url(refUrl)
                .header("Authorization", "token ${token.trim()}")
                .header("Accept", "application/vnd.github.v3+json")
                .get()
                .build()

            var refResponse = client.newCall(refRequest).execute()
            var lastCommitSha: String? = null
            var baseTreeSha: String? = null

            if (!refResponse.isSuccessful && refResponse.code == 404) {
                // Branch does not exist. Let's see if the repository is completely empty or if we should branch off the default branch.
                progressCallback("Branch '$branch' not found. Checking default branch '$defaultBranch'...")
                val defaultRefUrl = "https://api.github.com/repos/$owner/$repoName/git/refs/heads/$defaultBranch"
                val defaultRefRequest = Request.Builder()
                    .url(defaultRefUrl)
                    .header("Authorization", "token ${token.trim()}")
                    .header("Accept", "application/vnd.github.v3+json")
                    .get()
                    .build()
                
                val defaultRefResponse = client.newCall(defaultRefRequest).execute()
                if (defaultRefResponse.isSuccessful) {
                    // Default branch exists! Let's create our branch off of it.
                    progressCallback("Creating branch '$branch' off of '$defaultBranch'...")
                    val defaultRefBody = defaultRefResponse.body?.string() ?: ""
                    val defaultRefMap = moshi.adapter(Map::class.java).fromJson(defaultRefBody) as? Map<*, *>
                    val defaultObjMap = defaultRefMap?.get("object") as? Map<*, *>
                    val defaultCommitSha = defaultObjMap?.get("sha") as? String
                    
                    if (defaultCommitSha != null) {
                        val createRefUrl = "https://api.github.com/repos/$owner/$repoName/git/refs"
                        val createRefBodyMap = mapOf(
                            "ref" to "refs/heads/$branch",
                            "sha" to defaultCommitSha
                        )
                        val createRefBodyJson = moshi.adapter(Map::class.java).toJson(createRefBodyMap)
                        val createRefRequest = Request.Builder()
                            .url(createRefUrl)
                            .header("Authorization", "token ${token.trim()}")
                            .header("Accept", "application/vnd.github.v3+json")
                            .post(createRefBodyJson.toRequestBody(mediaType))
                            .build()
                        
                        val createRefResponse = client.newCall(createRefRequest).execute()
                        if (createRefResponse.isSuccessful) {
                            // Fetch ref again
                            refResponse = client.newCall(refRequest).execute()
                        } else {
                            val errBody = createRefResponse.body?.string() ?: ""
                            return@withContext Result.failure(Exception("Failed to create branch '$branch': ${createRefResponse.code} $errBody"))
                        }
                    }
                } else {
                    // Default branch also 404! The repository is completely empty. Let's initialize it.
                    progressCallback("Repository is completely empty. Initializing repository with default files...")
                    
                    val initUrl = "https://api.github.com/repos/$owner/$repoName/contents/README.md"
                    val initBodyMap = mapOf(
                        "message" to "Initial commit from PenCode AI",
                        "content" to "IyBQZW5Db2RlIEFJIFByb2plY3QK", // "# PenCode AI Project" in Base64
                        "branch" to branch
                    )
                    val initBodyJson = moshi.adapter(Map::class.java).toJson(initBodyMap)
                    val initRequest = Request.Builder()
                        .url(initUrl)
                        .header("Authorization", "token ${token.trim()}")
                        .header("Accept", "application/vnd.github.v3+json")
                        .put(initBodyJson.toRequestBody(mediaType))
                        .build()
                    
                    val initResponse = client.newCall(initRequest).execute()
                    if (initResponse.isSuccessful) {
                        progressCallback("Initialized empty repository successfully.")
                        // Query the newly created branch reference
                        refResponse = client.newCall(refRequest).execute()
                    } else {
                        val errBody = initResponse.body?.string() ?: ""
                        return@withContext Result.failure(Exception("Failed to initialize empty repository: ${initResponse.code} $errBody"))
                    }
                }
            }

            if (refResponse.isSuccessful) {
                val refBody = refResponse.body?.string()
                val refMap = moshi.adapter(Map::class.java).fromJson(refBody ?: "") as? Map<*, *>
                val objMap = refMap?.get("object") as? Map<*, *>
                lastCommitSha = objMap?.get("sha") as? String

                if (lastCommitSha != null) {
                    // Get latest commit's tree SHA
                    val commitUrl = "https://api.github.com/repos/$owner/$repoName/git/commits/$lastCommitSha"
                    val commitReq = Request.Builder()
                        .url(commitUrl)
                        .header("Authorization", "token ${token.trim()}")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    val commitResp = client.newCall(commitReq).execute()
                    if (commitResp.isSuccessful) {
                        val commitMap = moshi.adapter(Map::class.java).fromJson(commitResp.body?.string() ?: "") as? Map<*, *>
                        val treeMap = commitMap?.get("tree") as? Map<*, *>
                        baseTreeSha = treeMap?.get("sha") as? String
                    }
                }
            } else {
                val errBody = refResponse.body?.string() ?: ""
                return@withContext Result.failure(Exception("Failed to locate or initialize branch ref '$branch': ${refResponse.code} $errBody"))
            }

            // 3. Create tree object
            progressCallback("Generating Git Tree...")
            val treeItems = mutableListOf<Map<String, Any>>()
            projectFiles.forEach { file ->
                val isBinary = isBinaryExtension(file.path) || (file.content.startsWith("data:") && file.content.contains(";base64,"))
                if (isBinary) {
                    val cleanBase64 = if (file.content.startsWith("data:") && file.content.contains(";base64,")) {
                        file.content.substringAfter(";base64,")
                    } else {
                        file.content
                    }
                    try {
                        val blobBodyMap = mapOf(
                            "content" to cleanBase64,
                            "encoding" to "base64"
                        )
                        val blobBodyJson = moshi.adapter(Map::class.java).toJson(blobBodyMap)
                        val blobUrl = "https://api.github.com/repos/$owner/$repoName/git/blobs"
                        val blobReq = Request.Builder()
                            .url(blobUrl)
                            .header("Authorization", "token ${token.trim()}")
                            .header("Accept", "application/vnd.github.v3+json")
                            .post(blobBodyJson.toRequestBody(mediaType))
                            .build()

                        val blobResp = client.newCall(blobReq).execute()
                        if (blobResp.isSuccessful) {
                            val blobRespBody = blobResp.body?.string() ?: ""
                            val blobResultMap = moshi.adapter(Map::class.java).fromJson(blobRespBody) as? Map<*, *>
                            val blobSha = blobResultMap?.get("sha") as? String
                            if (blobSha != null) {
                                treeItems.add(mapOf(
                                    "path" to file.path,
                                    "mode" to "100644",
                                    "type" to "blob",
                                    "sha" to blobSha
                                ))
                            } else {
                                treeItems.add(mapOf(
                                    "path" to file.path,
                                    "mode" to "100644",
                                    "type" to "blob",
                                    "content" to file.content
                                ))
                            }
                        } else {
                            treeItems.add(mapOf(
                                "path" to file.path,
                                "mode" to "100644",
                                "type" to "blob",
                                "content" to file.content
                            ))
                        }
                    } catch (e: Exception) {
                        treeItems.add(mapOf(
                            "path" to file.path,
                            "mode" to "100644",
                            "type" to "blob",
                            "content" to file.content
                        ))
                    }
                } else {
                    treeItems.add(mapOf(
                        "path" to file.path,
                        "mode" to "100644",
                        "type" to "blob",
                        "content" to file.content
                    ))
                }
            }

            val treeBodyMap = mutableMapOf<String, Any>()
            treeBodyMap["tree"] = treeItems
            // For normal push (non-force), set base_tree to support diffs
            if (!force && baseTreeSha != null) {
                treeBodyMap["base_tree"] = baseTreeSha
            }

            val treeBodyJson = moshi.adapter(Map::class.java).toJson(treeBodyMap)
            val treeUrl = "https://api.github.com/repos/$owner/$repoName/git/trees"
            val treeReq = Request.Builder()
                .url(treeUrl)
                .header("Authorization", "token ${token.trim()}")
                .header("Accept", "application/vnd.github.v3+json")
                .post(treeBodyJson.toRequestBody(mediaType))
                .build()

            val treeResp = client.newCall(treeReq).execute()
            val treeRespBody = treeResp.body?.string() ?: ""
            if (!treeResp.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to create tree: ${treeResp.code} $treeRespBody"))
            }

            val treeResultMap = moshi.adapter(Map::class.java).fromJson(treeRespBody) as? Map<*, *>
            val newTreeSha = treeResultMap?.get("sha") as? String
                ?: return@withContext Result.failure(Exception("No tree SHA returned from GitHub"))

            // 4. Create commit object
            progressCallback("Creating Commit on GitHub...")
            val commitBodyMap = mutableMapOf<String, Any>()
            commitBodyMap["message"] = "PenCode AI auto-commit: Syncing local project"
            commitBodyMap["tree"] = newTreeSha
            // For normal push or non-empty initializations, specify parent commits
            if (!force && lastCommitSha != null) {
                commitBodyMap["parents"] = listOf(lastCommitSha)
            }

            val commitBodyJson = moshi.adapter(Map::class.java).toJson(commitBodyMap)
            val commitUrl = "https://api.github.com/repos/$owner/$repoName/git/commits"
            val commitReq = Request.Builder()
                .url(commitUrl)
                .header("Authorization", "token ${token.trim()}")
                .header("Accept", "application/vnd.github.v3+json")
                .post(commitBodyJson.toRequestBody(mediaType))
                .build()

            val commitResp = client.newCall(commitReq).execute()
            val commitRespBody = commitResp.body?.string() ?: ""
            if (!commitResp.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to create commit: ${commitResp.code} $commitRespBody"))
            }

            val commitResultMap = moshi.adapter(Map::class.java).fromJson(commitRespBody) as? Map<*, *>
            val newCommitSha = commitResultMap?.get("sha") as? String
                ?: return@withContext Result.failure(Exception("No commit SHA returned from GitHub"))

            // 5. Update or create reference
            progressCallback("Updating remote branch reference...")
            val updateRefUrl: String
            val updateRefBodyMap = mutableMapOf<String, Any>()
            val updateRefReq: Request

            if (lastCommitSha == null) {
                // Create ref from scratch
                updateRefUrl = "https://api.github.com/repos/$owner/$repoName/git/refs"
                updateRefBodyMap["ref"] = "refs/heads/$branch"
                updateRefBodyMap["sha"] = newCommitSha
                val updateRefBodyJson = moshi.adapter(Map::class.java).toJson(updateRefBodyMap)
                updateRefReq = Request.Builder()
                    .url(updateRefUrl)
                    .header("Authorization", "token ${token.trim()}")
                    .header("Accept", "application/vnd.github.v3+json")
                    .post(updateRefBodyJson.toRequestBody(mediaType))
                    .build()
            } else {
                // Update existing ref
                updateRefUrl = "https://api.github.com/repos/$owner/$repoName/git/refs/heads/$branch"
                updateRefBodyMap["sha"] = newCommitSha
                updateRefBodyMap["force"] = force // Set force flag based on user choice!
                val updateRefBodyJson = moshi.adapter(Map::class.java).toJson(updateRefBodyMap)
                updateRefReq = Request.Builder()
                    .url(updateRefUrl)
                    .header("Authorization", "token ${token.trim()}")
                    .header("Accept", "application/vnd.github.v3+json")
                    .patch(updateRefBodyJson.toRequestBody(mediaType))
                    .build()
            }

            val updateRefResp = client.newCall(updateRefReq).execute()
            val updateRefRespBody = updateRefResp.body?.string() ?: ""
            if (!updateRefResp.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to update branch reference: ${updateRefResp.code} $updateRefRespBody"))
            }

            progressCallback("Push complete!")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun runLs(workingDir: File, args: List<String>): String {
        val pathStr = if (args.size > 1 && !args[1].startsWith("-")) args[1] else "."
        val target = File(workingDir, pathStr).canonicalFile
        if (!target.exists()) return "ls: $pathStr: No such file or directory"
        if (!target.isDirectory) {
            val linesCount = try { if (target.isFile) " (${target.readLines().size} lines)" else "" } catch (e: Exception) { "" }
            return "${target.name}$linesCount"
        }
        val files = target.listFiles() ?: return ""
        val isAll = args.contains("-a") || args.contains("-la") || args.contains("-al")
        val filtered = if (isAll) files.toList() else files.filter { !it.name.startsWith(".") }
        return filtered.joinToString("\n") { file ->
            val linesCount = try {
                if (file.isFile) {
                    " (${file.readLines().size} lines)"
                } else if (file.isDirectory) {
                    " (directory)"
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
            "${file.name}$linesCount"
        }
    }

    private fun runCat(workingDir: File, args: List<String>): String {
        if (args.size < 2) return "Usage: cat file"
        val sb = java.lang.StringBuilder()
        for (i in 1 until args.size) {
            val target = File(workingDir, args[i]).canonicalFile
            if (!target.exists()) sb.append("cat: ${args[i]}: No such file or directory\n")
            else if (target.isDirectory) sb.append("cat: ${args[i]}: Is a directory\n")
            else sb.append(target.readText()).append("\n")
        }
        return sb.toString().trimEnd()
    }

    private fun runMkdir(workingDir: File, args: List<String>): String {
        if (args.size < 2) return "Usage: mkdir directory"
        val createdDirs = mutableListOf<String>()
        for (i in 1 until args.size) {
            if (args[i] == "-p") continue
            val target = File(workingDir, args[i]).canonicalFile
            if (target.mkdirs()) {
                createdDirs.add(args[i])
            } else if (target.exists()) {
                createdDirs.add("${args[i]} (already exists)")
            } else {
                return "mkdir: cannot create directory '${args[i]}'"
            }
        }
        return if (createdDirs.isNotEmpty()) "Created directory: ${createdDirs.joinToString(", ")}" else "Directory already exists or could not be created"
    }

    private fun runRm(workingDir: File, args: List<String>): String {
        if (args.size < 2) return "Usage: rm file"
        var recursive = false
        val deletedItems = mutableListOf<String>()
        for (i in 1 until args.size) {
            if (args[i] == "-r" || args[i] == "-rf") {
                recursive = true
                continue
            }
            val target = File(workingDir, args[i]).canonicalFile
            if (target.exists()) {
                val name = args[i]
                val deleted = if (recursive) target.deleteRecursively() else target.delete()
                if (deleted) {
                    deletedItems.add(name)
                }
            } else {
                deletedItems.add("${args[i]} (does not exist)")
            }
        }
        return if (deletedItems.isNotEmpty()) "Removed: ${deletedItems.joinToString(", ")}" else "No files or directories were deleted"
    }

    private fun runTouch(workingDir: File, args: List<String>): String {
        if (args.size < 2) return "Usage: touch file"
        val touchedFiles = mutableListOf<String>()
        for (i in 1 until args.size) {
            val target = File(workingDir, args[i]).canonicalFile
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                if (target.createNewFile()) {
                    touchedFiles.add("${args[i]} (created)")
                }
            } else {
                target.setLastModified(System.currentTimeMillis())
                touchedFiles.add("${args[i]} (updated timestamp)")
            }
        }
        return if (touchedFiles.isNotEmpty()) "Touched: ${touchedFiles.joinToString(", ")}" else "No files were touched"
    }

    private fun runCp(workingDir: File, args: List<String>): String {
        if (args.size < 3) return "Usage: cp source dest"
        var recursive = false
        var srcIdx = 1
        if (args[1] == "-r" || args[1] == "-R") {
            recursive = true
            srcIdx = 2
        }
        if (args.size <= srcIdx + 1) return "Usage: cp source dest"
        val src = File(workingDir, args[srcIdx]).canonicalFile
        val dest = File(workingDir, args[srcIdx + 1]).canonicalFile
        if (!src.exists()) return "cp: cannot stat '${args[srcIdx]}': No such file or directory"
        
        try {
            if (recursive) src.copyRecursively(dest, overwrite = true)
            else src.copyTo(dest, overwrite = true)
            return "Copied ${args[srcIdx]} to ${args[srcIdx + 1]} successfully"
        } catch(e: Exception) {
            return "cp: error: ${e.message}"
        }
    }

    private fun runMv(workingDir: File, args: List<String>): String {
        if (args.size < 3) return "Usage: mv source dest"
        val src = File(workingDir, args[1]).canonicalFile
        val dest = File(workingDir, args[2]).canonicalFile
        if (!src.exists()) return "mv: cannot stat '${args[1]}': No such file or directory"
        try {
            src.copyRecursively(dest, overwrite = true)
            src.deleteRecursively()
            return "Moved ${args[1]} to ${args[2]} successfully"
        } catch(e: Exception) {
             return "mv: error: ${e.message}"
        }
    }

    private fun runEcho(args: List<String>): String {
        return args.drop(1).joinToString(" ")
    }
}
