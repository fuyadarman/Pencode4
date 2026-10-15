package com.example.terminal

import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * High-performance Android Local Terminal & Shell Engine.
 * Supports all standard POSIX and Android built-in utilities (/system/bin, /vendor/bin)
 * with robust Kotlin fallbacks when binaries are sandboxed or unavailable.
 */
class AndroidTerminalEngine(private val appContext: Context) {

    private val workingDirMap = mutableMapOf<String, String>()
    private val sessionEnv = mutableMapOf<String, String>()

    init {
        sessionEnv["HOME"] = appContext.filesDir.absolutePath
        sessionEnv["TMPDIR"] = appContext.cacheDir.absolutePath
        sessionEnv["USER"] = "u0_a" + (android.os.Process.myUid() % 100000)
        sessionEnv["SHELL"] = "/system/bin/sh"
        sessionEnv["TERM"] = "xterm-256color"
    }

    private val systemPaths = listOf(
        "/system/bin",
        "/system/xbin",
        "/vendor/bin",
        "/system_ext/bin",
        "/product/bin",
        "/apex/com.android.runtime/bin",
        "/apex/com.android.art/bin",
        appContext.applicationInfo.nativeLibraryDir
    ).filter { File(it).exists() }

    val fullPathString: String = (systemPaths + listOf("/data/local/tmp")).joinToString(":")

    suspend fun execute(
        projectName: String,
        projectDir: File,
        rawCommand: String,
        onFileModified: suspend () -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return@withContext ""

        // Resolve current working directory for the project
        val currentRel = workingDirMap[projectName] ?: ""
        var workingDir = if (currentRel.isEmpty()) projectDir else File(projectDir, currentRel)
        if (!workingDir.exists() || !workingDir.isDirectory) {
            workingDir = projectDir
            workingDirMap[projectName] = ""
        }

        // 1. Interactive built-in 'cd'
        if (trimmed == "cd" || trimmed.startsWith("cd ")) {
            val res = handleCd(projectName, projectDir, workingDir, trimmed)
            return@withContext res
        }

        // 2. Interactive built-in 'pwd'
        if (trimmed == "pwd") {
            return@withContext workingDir.canonicalPath
        }

        // 3. Built-in 'clear'
        if (trimmed == "clear") {
            return@withContext ""
        }

        // 4. Built-in 'export' / 'env'
        if (trimmed == "env") {
            return@withContext (sessionEnv + mapOf("PWD" to workingDir.canonicalPath, "PATH" to fullPathString))
                .map { "${it.key}=${it.value}" }
                .sorted()
                .joinToString("\n")
        }
        if (trimmed.startsWith("export ")) {
            val assign = trimmed.removePrefix("export ").trim()
            if (assign.contains("=")) {
                val key = assign.substringBefore("=").trim()
                val value = assign.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                sessionEnv[key] = value
                return@withContext ""
            }
        }

        // 5. Check if command has shell operators (pipes, redirection, chains)
        val hasShellOperators = trimmed.contains("&&") ||
                trimmed.contains("||") ||
                trimmed.contains(";") ||
                trimmed.contains("|") ||
                trimmed.contains(">") ||
                trimmed.contains("<")

        // Format command for non-blocking execution on Android
        val sanitizedCommand = sanitizeAndroidCommand(trimmed)

        // 6. Execute via Android native /system/bin/sh
        val nativeResult = executeNativeShell(workingDir, sanitizedCommand)

        // 7. If native shell succeeded or produced valid output, return it
        if (nativeResult.isSuccess) {
            val output = nativeResult.getOrNull() ?: ""
            if (isModifyingCommand(trimmed)) {
                onFileModified()
            }
            return@withContext output
        }

        // 8. If native shell command was not found or failed, try pure-Kotlin fallback handlers
        val args = splitCommand(trimmed)
        if (args.isNotEmpty() && !hasShellOperators) {
            val cmd = args[0]
            val fallbackRes = executeKotlinFallback(workingDir, projectDir, cmd, args)
            if (fallbackRes != null) {
                if (isModifyingCommand(trimmed)) {
                    onFileModified()
                }
                return@withContext fallbackRes
            }
        }

        // Return native error or standard exit message
        val err = nativeResult.exceptionOrNull()?.localizedMessage ?: "Command failed"
        return@withContext err
    }

    private fun handleCd(projectName: String, projectDir: File, workingDir: File, cmd: String): String {
        val target = if (cmd == "cd" || cmd == "cd ~") {
            ""
        } else {
            cmd.removePrefix("cd").trim().removeSurrounding("\"").removeSurrounding("'")
        }

        if (target.isEmpty() || target == "~" || target == ".") {
            workingDirMap[projectName] = ""
            return "Moved to ."
        }

        if (target == "..") {
            val parent = workingDir.parentFile
            return if (parent != null && parent.canonicalPath.startsWith(projectDir.canonicalPath)) {
                val rel = parent.relativeTo(projectDir).path
                workingDirMap[projectName] = rel
                "Moved to ${rel.ifEmpty { "." }}"
            } else {
                workingDirMap[projectName] = ""
                "Moved to ."
            }
        }

        val dest = if (target.startsWith("/")) File(target) else File(workingDir, target)
        val canonicalDest = dest.canonicalFile

        return if (canonicalDest.exists() && canonicalDest.isDirectory) {
            if (canonicalDest.canonicalPath.startsWith(projectDir.canonicalPath)) {
                val rel = canonicalDest.relativeTo(projectDir).path
                workingDirMap[projectName] = rel
                "Moved to ${rel.ifEmpty { "." }}"
            } else {
                // Outside project navigation (e.g. system dirs)
                workingDirMap[projectName] = canonicalDest.absolutePath
                "Moved to ${canonicalDest.absolutePath}"
            }
        } else {
            "cd: no such file or directory: $target"
        }
    }

    private fun sanitizeAndroidCommand(command: String): String {
        var cmd = command.trim()
        
        // Prevent logcat from hanging indefinitely by defaulting to dump mode (-d)
        if (cmd == "logcat" || cmd.startsWith("logcat ")) {
            if (!cmd.contains("-d") && !cmd.contains("-c") && !cmd.contains("-g")) {
                cmd = cmd.replaceFirst("logcat", "logcat -d")
            }
        }
        
        // Prevent top from running indefinitely by defaulting to 1 iteration batch mode (-n 1 -b)
        if (cmd == "top" || cmd.startsWith("top ")) {
            if (!cmd.contains("-n")) {
                cmd = cmd.replaceFirst("top", "top -n 1 -b")
            }
        }

        // Prevent ping from infinite loop by adding count -c 3 if not present
        if (cmd == "ping" || cmd.startsWith("ping ")) {
            if (!cmd.contains("-c")) {
                cmd = cmd.replaceFirst("ping", "ping -c 3")
            }
        }

        return cmd
    }

    private suspend fun executeNativeShell(workingDir: File, command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val shellPath = when {
                File("/system/bin/sh").exists() -> "/system/bin/sh"
                File("/bin/sh").exists() -> "/bin/sh"
                else -> "sh"
            }

            val pb = ProcessBuilder(shellPath, "-c", command)
            pb.directory(workingDir)
            pb.redirectErrorStream(true)

            // Inject full PATH and session environment
            val env = pb.environment()
            env["PATH"] = fullPathString + ":" + (env["PATH"] ?: "")
            for ((k, v) in sessionEnv) {
                env[k] = v
            }
            env["PWD"] = workingDir.canonicalPath

            val process = pb.start()

            // Read with timeout (12 seconds)
            val output = withTimeoutOrNull(12_000L) {
                val text = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()
                text
            }

            if (output == null) {
                process.destroyForcibly()
                return@withContext Result.failure(Exception("Command timed out after 12s"))
            }

            val exitCode = process.exitValue()
            val trimmedOut = output.trimEnd()

            if (exitCode == 0) {
                Result.success(trimmedOut)
            } else {
                // If grep returned 1, it means no matches found
                if (command.contains("grep") && exitCode == 1 && trimmedOut.isEmpty()) {
                    Result.success("(No matches found)")
                } else if (trimmedOut.isNotEmpty()) {
                    Result.success(trimmedOut)
                } else {
                    Result.failure(Exception("Command exited with status $exitCode"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pure Kotlin Command Fallbacks (100% Reliable on all Android versions)
     */
    private fun executeKotlinFallback(workingDir: File, projectDir: File, cmd: String, args: List<String>): String? {
        return when (cmd) {
            "ls" -> runLs(workingDir, args)
            "cat" -> runCat(workingDir, args)
            "head" -> runHead(workingDir, args)
            "tail" -> runTail(workingDir, args)
            "wc" -> runWc(workingDir, args)
            "mkdir" -> runMkdir(workingDir, args)
            "rmdir" -> runRmdir(workingDir, args)
            "touch" -> runTouch(workingDir, args)
            "rm" -> runRm(workingDir, args)
            "cp" -> runCp(workingDir, args)
            "mv" -> runMv(workingDir, args)
            "stat" -> runStat(workingDir, args)
            "du" -> runDu(workingDir, args)
            "df" -> runDf(workingDir)
            "file" -> runFile(workingDir, args)
            "basename" -> if (args.size > 1) File(args[1]).name else "Usage: basename path"
            "dirname" -> if (args.size > 1) (File(args[1]).parent ?: ".") else "Usage: dirname path"
            "realpath", "readlink" -> if (args.size > 1) resolveTarget(workingDir, args[1]).canonicalPath else "Usage: $cmd path"
            "whoami" -> sessionEnv["USER"] ?: "u0_a" + (android.os.Process.myUid() % 100000)
            "id" -> "uid=${android.os.Process.myUid()}(${sessionEnv["USER"]}) gid=${android.os.Process.myUid()} groups=${android.os.Process.myUid()}"
            "uname" -> runUname(args)
            "uptime" -> runUptime()
            "getprop" -> runGetprop(args)
            "echo" -> if (args.size > 1) args.drop(1).joinToString(" ") else ""
            "printf" -> if (args.size > 1) args.drop(1).joinToString(" ").replace("\\n", "\n").replace("\\t", "\t") else ""
            "chmod" -> runChmod(workingDir, args)
            "gzip" -> runGzip(workingDir, args)
            "gunzip" -> runGunzip(workingDir, args)
            "zip" -> runZip(workingDir, args)
            "unzip" -> runUnzip(workingDir, args)
            else -> null
        }
    }

    private fun resolveTarget(workingDir: File, pathStr: String): File {
        return if (pathStr.startsWith("/")) File(pathStr) else File(workingDir, pathStr)
    }

    private fun runLs(workingDir: File, args: List<String>): String {
        val showAll = args.any { it.startsWith("-") && it.contains("a") }
        val showLong = args.any { it.startsWith("-") && it.contains("l") }
        val targetPath = args.lastOrNull { !it.startsWith("-") && it != "ls" }
        val targetDir = if (targetPath != null) resolveTarget(workingDir, targetPath) else workingDir

        if (!targetDir.exists()) return "ls: ${targetDir.name}: No such file or directory"
        if (targetDir.isFile) return targetDir.name

        val files = targetDir.listFiles() ?: return "(Empty directory)"
        val sorted = files.filter { showAll || !it.name.startsWith(".") }.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        )

        if (sorted.isEmpty()) return "(Empty directory)"

        return if (showLong) {
            val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.US)
            sorted.joinToString("\n") { f ->
                val type = if (f.isDirectory) "d" else "-"
                val r = if (f.canRead()) "r" else "-"
                val w = if (f.canWrite()) "w" else "-"
                val x = if (f.canExecute()) "x" else "-"
                val perms = "$type$r$w$x------"
                val size = if (f.isDirectory) "4096" else f.length().toString().padStart(8)
                val date = sdf.format(Date(f.lastModified()))
                val name = if (f.isDirectory) "${f.name}/" else f.name
                "$perms  1 u0_a0  u0_a0  $size $date $name"
            }
        } else {
            sorted.joinToString("  ") { if (it.isDirectory) "${it.name}/" else it.name }
        }
    }

    private fun runCat(workingDir: File, args: List<String>): String {
        val files = args.drop(1).filter { !it.startsWith("-") }
        if (files.isEmpty()) return "Usage: cat [file...]"
        val sb = StringBuilder()
        for (f in files) {
            val target = resolveTarget(workingDir, f)
            if (!target.exists()) return "cat: $f: No such file or directory"
            if (target.isDirectory) return "cat: $f: Is a directory"
            sb.append(target.readText())
        }
        return sb.toString().trimEnd()
    }

    private fun runHead(workingDir: File, args: List<String>): String {
        var lines = 10
        val files = mutableListOf<String>()
        var i = 1
        while (i < args.size) {
            val arg = args[i]
            if (arg.startsWith("-n")) {
                val nStr = if (arg.length > 2) arg.substring(2) else args.getOrNull(++i) ?: "10"
                lines = nStr.toIntOrNull() ?: 10
            } else if (!arg.startsWith("-")) {
                files.add(arg)
            }
            i++
        }
        if (files.isEmpty()) return "Usage: head [-n lines] file"
        val target = resolveTarget(workingDir, files[0])
        if (!target.exists()) return "head: ${files[0]}: No such file or directory"
        return target.bufferedReader().useLines { it.take(lines).joinToString("\n") }
    }

    private fun runTail(workingDir: File, args: List<String>): String {
        var lines = 10
        val files = mutableListOf<String>()
        var i = 1
        while (i < args.size) {
            val arg = args[i]
            if (arg.startsWith("-n")) {
                val nStr = if (arg.length > 2) arg.substring(2) else args.getOrNull(++i) ?: "10"
                lines = nStr.toIntOrNull() ?: 10
            } else if (!arg.startsWith("-")) {
                files.add(arg)
            }
            i++
        }
        if (files.isEmpty()) return "Usage: tail [-n lines] file"
        val target = resolveTarget(workingDir, files[0])
        if (!target.exists()) return "tail: ${files[0]}: No such file or directory"
        val allLines = target.readLines()
        return allLines.takeLast(lines).joinToString("\n")
    }

    private fun runWc(workingDir: File, args: List<String>): String {
        val files = args.drop(1).filter { !it.startsWith("-") }
        if (files.isEmpty()) return "Usage: wc [file...]"
        val sb = StringBuilder()
        for (f in files) {
            val target = resolveTarget(workingDir, f)
            if (!target.exists()) return "wc: $f: No such file or directory"
            val text = target.readText()
            val l = text.lines().size
            val w = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            val c = target.length()
            sb.appendLine("  $l  $w  $c  $f")
        }
        return sb.toString().trimEnd()
    }

    private fun runMkdir(workingDir: File, args: List<String>): String {
        val targets = args.drop(1).filter { !it.startsWith("-") }
        if (targets.isEmpty()) return "Usage: mkdir directory..."
        for (t in targets) {
            val dir = resolveTarget(workingDir, t)
            if (!dir.exists()) dir.mkdirs()
        }
        return ""
    }

    private fun runRmdir(workingDir: File, args: List<String>): String {
        val targets = args.drop(1).filter { !it.startsWith("-") }
        if (targets.isEmpty()) return "Usage: rmdir directory..."
        for (t in targets) {
            val dir = resolveTarget(workingDir, t)
            if (!dir.exists()) return "rmdir: $t: No such file or directory"
            if (!dir.isDirectory) return "rmdir: $t: Not a directory"
            if ((dir.listFiles()?.size ?: 0) > 0) return "rmdir: $t: Directory not empty"
            dir.delete()
        }
        return ""
    }

    private fun runTouch(workingDir: File, args: List<String>): String {
        val targets = args.drop(1).filter { !it.startsWith("-") }
        if (targets.isEmpty()) return "Usage: touch file..."
        for (t in targets) {
            val f = resolveTarget(workingDir, t)
            if (!f.exists()) {
                f.parentFile?.mkdirs()
                f.createNewFile()
            } else {
                f.setLastModified(System.currentTimeMillis())
            }
        }
        return ""
    }

    private fun runRm(workingDir: File, args: List<String>): String {
        val recursive = args.any { it.startsWith("-") && (it.contains("r") || it.contains("R")) }
        val targets = args.drop(1).filter { !it.startsWith("-") }
        if (targets.isEmpty()) return "Usage: rm [-r] file..."
        for (t in targets) {
            val f = resolveTarget(workingDir, t)
            if (!f.exists()) return "rm: $t: No such file or directory"
            if (f.isDirectory) {
                if (!recursive) return "rm: $t: is a directory"
                f.deleteRecursively()
            } else {
                f.delete()
            }
        }
        return ""
    }

    private fun runCp(workingDir: File, args: List<String>): String {
        val nonOptions = args.drop(1).filter { !it.startsWith("-") }
        if (nonOptions.size < 2) return "Usage: cp src dest"
        val src = resolveTarget(workingDir, nonOptions[0])
        val dest = resolveTarget(workingDir, nonOptions[1])
        if (!src.exists()) return "cp: ${nonOptions[0]}: No such file or directory"
        val targetDest = if (dest.isDirectory) File(dest, src.name) else dest
        if (src.isDirectory) src.copyRecursively(targetDest, overwrite = true)
        else src.copyTo(targetDest, overwrite = true)
        return ""
    }

    private fun runMv(workingDir: File, args: List<String>): String {
        val nonOptions = args.drop(1).filter { !it.startsWith("-") }
        if (nonOptions.size < 2) return "Usage: mv src dest"
        val src = resolveTarget(workingDir, nonOptions[0])
        val dest = resolveTarget(workingDir, nonOptions[1])
        if (!src.exists()) return "mv: ${nonOptions[0]}: No such file or directory"
        val targetDest = if (dest.isDirectory) File(dest, src.name) else dest
        if (src.isDirectory) {
            src.copyRecursively(targetDest, overwrite = true)
            src.deleteRecursively()
        } else {
            src.copyTo(targetDest, overwrite = true)
            src.delete()
        }
        return ""
    }

    private fun runStat(workingDir: File, args: List<String>): String {
        val targets = args.drop(1).filter { !it.startsWith("-") }
        if (targets.isEmpty()) return "Usage: stat file"
        val target = resolveTarget(workingDir, targets[0])
        if (!target.exists()) return "stat: cannot stat '${targets[0]}': No such file or directory"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
        return """
          File: ${target.name}
          Size: ${target.length()}       Blocks: ${(target.length() + 511) / 512}   IO Block: 4096   ${if (target.isDirectory) "directory" else "regular file"}
        Device: 0/0       Inode: ${target.hashCode()}   Links: 1
        Access: (0755/${if (target.isDirectory) "drwxr-xr-x" else "-rw-r--r--"})  Uid: (${android.os.Process.myUid()})   Gid: (${android.os.Process.myUid()})
        Access: ${sdf.format(Date(target.lastModified()))}
        Modify: ${sdf.format(Date(target.lastModified()))}
        Change: ${sdf.format(Date(target.lastModified()))}
        """.trimIndent()
    }

    private fun runDu(workingDir: File, args: List<String>): String {
        val targetPath = args.drop(1).firstOrNull { !it.startsWith("-") }
        val target = if (targetPath != null) resolveTarget(workingDir, targetPath) else workingDir
        if (!target.exists()) return "du: cannot access '${targetPath ?: "."}': No such file or directory"
        
        fun calculateSize(file: File): Long {
            if (file.isFile) return file.length()
            return file.listFiles()?.sumOf { calculateSize(it) } ?: 0L
        }

        val sizeKb = (calculateSize(target) + 1023) / 1024
        return "$sizeKb\t${targetPath ?: "."}"
    }

    private fun runDf(workingDir: File): String {
        return try {
            val stat = StatFs(workingDir.canonicalPath)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val freeBlocks = stat.availableBlocksLong
            val totalKb = (totalBlocks * blockSize) / 1024
            val freeKb = (freeBlocks * blockSize) / 1024
            val usedKb = totalKb - freeKb
            val usePercent = if (totalKb > 0) ((usedKb * 100) / totalKb) else 0

            """
            Filesystem     1K-blocks      Used Available Use% Mounted on
            /data          $totalKb  $usedKb  $freeKb  $usePercent% /data
            """.trimIndent()
        } catch (e: Exception) {
            "df: failed to read filesystem: ${e.localizedMessage}"
        }
    }

    private fun runFile(workingDir: File, args: List<String>): String {
        val targets = args.drop(1).filter { !it.startsWith("-") }
        if (targets.isEmpty()) return "Usage: file filename"
        val target = resolveTarget(workingDir, targets[0])
        if (!target.exists()) return "${targets[0]}: cannot open (No such file or directory)"
        if (target.isDirectory) return "${targets[0]}: directory"
        val ext = target.extension.lowercase()
        return when (ext) {
            "kt", "java", "js", "ts", "json", "html", "css", "xml", "txt", "md" -> "${targets[0]}: ASCII text, with very long lines"
            "png", "jpg", "jpeg", "webp" -> "${targets[0]}: image data"
            "zip", "apk", "jar" -> "${targets[0]}: Zip archive data"
            else -> "${targets[0]}: data"
        }
    }

    private fun runUname(args: List<String>): String {
        val showAll = args.any { it.contains("a") }
        val arch = System.getProperty("os.arch") ?: Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val osVersion = System.getProperty("os.version") ?: "Linux"
        return if (showAll) {
            "Linux localhost $osVersion #1 SMP PREEMPT android-$arch GNU/Linux"
        } else {
            "Linux"
        }
    }

    private fun runUptime(): String {
        val elapsedMillis = SystemClock.elapsedRealtime()
        val seconds = (elapsedMillis / 1000) % 60
        val minutes = (elapsedMillis / (1000 * 60)) % 60
        val hours = (elapsedMillis / (1000 * 60 * 60)) % 24
        val days = elapsedMillis / (1000 * 60 * 60 * 24)
        return if (days > 0) "up $days days, $hours:$minutes" else "up $hours:$minutes"
    }

    private fun runGetprop(args: List<String>): String {
        if (args.size > 1) {
            val key = args[1].trim()
            val value = getSystemProperty(key)
            return value
        }

        // Return rich property list
        return """
        [ro.product.model]: [${Build.MODEL}]
        [ro.product.brand]: [${Build.BRAND}]
        [ro.product.manufacturer]: [${Build.MANUFACTURER}]
        [ro.build.version.release]: [${Build.VERSION.RELEASE}]
        [ro.build.version.sdk]: [${Build.VERSION.SDK_INT}]
        [ro.product.cpu.abi]: [${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"}]
        [ro.build.id]: [${Build.ID}]
        [ro.board.platform]: [${Build.HARDWARE}]
        """.trimIndent()
    }

    private fun getSystemProperty(propName: String): String {
        return when (propName) {
            "ro.product.model" -> Build.MODEL
            "ro.product.brand" -> Build.BRAND
            "ro.product.manufacturer" -> Build.MANUFACTURER
            "ro.build.version.release" -> Build.VERSION.RELEASE
            "ro.build.version.sdk" -> Build.VERSION.SDK_INT.toString()
            "ro.product.cpu.abi" -> Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            "ro.build.id" -> Build.ID
            "ro.board.platform" -> Build.HARDWARE
            else -> {
                try {
                    val c = Class.forName("android.os.SystemProperties")
                    val get = c.getMethod("get", String::class.java)
                    val res = get.invoke(null, propName) as? String ?: ""
                    res
                } catch (e: Exception) {
                    ""
                }
            }
        }
    }

    private fun runChmod(workingDir: File, args: List<String>): String {
        val nonOptions = args.drop(1).filter { !it.startsWith("-") }
        if (nonOptions.size < 2) return "Usage: chmod mode file..."
        val mode = nonOptions[0]
        val files = nonOptions.drop(1)
        for (f in files) {
            val target = resolveTarget(workingDir, f)
            if (target.exists()) {
                if (mode.contains("x") || mode.endsWith("7") || mode.endsWith("5")) {
                    target.setExecutable(true, false)
                }
                if (mode.contains("w") || mode.endsWith("6") || mode.endsWith("7")) {
                    target.setWritable(true, false)
                }
                if (mode.contains("r") || mode.endsWith("4") || mode.endsWith("5") || mode.endsWith("6") || mode.endsWith("7")) {
                    target.setReadable(true, false)
                }
            }
        }
        return ""
    }

    private fun runGzip(workingDir: File, args: List<String>): String {
        val targets = args.drop(1).filter { !it.startsWith("-") }
        if (targets.isEmpty()) return "Usage: gzip file"
        val file = resolveTarget(workingDir, targets[0])
        if (!file.exists()) return "gzip: ${targets[0]}: No such file or directory"
        val gzFile = File(file.parentFile, "${file.name}.gz")
        FileInputStream(file).use { input ->
            GZIPOutputStream(FileOutputStream(gzFile)).use { output ->
                input.copyTo(output)
            }
        }
        file.delete()
        return ""
    }

    private fun runGunzip(workingDir: File, args: List<String>): String {
        val targets = args.drop(1).filter { !it.startsWith("-") }
        if (targets.isEmpty()) return "Usage: gunzip file.gz"
        val gzFile = resolveTarget(workingDir, targets[0])
        if (!gzFile.exists()) return "gunzip: ${targets[0]}: No such file or directory"
        val outFile = File(gzFile.parentFile, gzFile.name.removeSuffix(".gz"))
        GZIPInputStream(FileInputStream(gzFile)).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        gzFile.delete()
        return ""
    }

    private fun runZip(workingDir: File, args: List<String>): String {
        val nonOptions = args.drop(1).filter { !it.startsWith("-") }
        if (nonOptions.size < 2) return "Usage: zip archive.zip file..."
        val zipFile = resolveTarget(workingDir, nonOptions[0])
        val filesToZip = nonOptions.drop(1)
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for (fStr in filesToZip) {
                val f = resolveTarget(workingDir, fStr)
                if (f.exists()) {
                    zos.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return "added files to ${zipFile.name}"
    }

    private fun runUnzip(workingDir: File, args: List<String>): String {
        val nonOptions = args.drop(1).filter { !it.startsWith("-") }
        if (nonOptions.isEmpty()) return "Usage: unzip archive.zip"
        val zipFile = resolveTarget(workingDir, nonOptions[0])
        if (!zipFile.exists()) return "unzip: cannot find ${nonOptions[0]}"
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outFile = File(workingDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
        return "Extracted ${zipFile.name}"
    }

    private fun isModifyingCommand(cmd: String): Boolean {
        val first = cmd.trim().split(" ").firstOrNull() ?: ""
        return first in listOf("mkdir", "rm", "rmdir", "touch", "cp", "mv", "chmod", "gzip", "gunzip", "zip", "unzip", "tar", "git") ||
                cmd.contains(">") || cmd.contains(">>")
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
}
