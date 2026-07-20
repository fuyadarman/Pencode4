    private fun runLs(workingDir: File, args: List<String>): String {
        val pathStr = if (args.size > 1 && !args[1].startsWith("-")) args[1] else "."
        val target = File(workingDir, pathStr).canonicalFile
        if (!target.exists()) return "ls: $pathStr: No such file or directory"
        if (!target.isDirectory) return target.name
        val files = target.listFiles() ?: return ""
        val isAll = args.contains("-a") || args.contains("-la") || args.contains("-al")
        val filtered = if (isAll) files.toList() else files.filter { !it.name.startsWith(".") }
        return filtered.joinToString("\n") { it.name }
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
        for (i in 1 until args.size) {
            if (args[i] == "-p") continue
            val target = File(workingDir, args[i]).canonicalFile
            target.mkdirs()
        }
        return ""
    }

    private fun runRm(workingDir: File, args: List<String>): String {
        if (args.size < 2) return "Usage: rm file"
        var recursive = false
        for (i in 1 until args.size) {
            if (args[i] == "-r" || args[i] == "-rf") {
                recursive = true
                continue
            }
            val target = File(workingDir, args[i]).canonicalFile
            if (target.exists()) {
                if (recursive) target.deleteRecursively() else target.delete()
            }
        }
        return ""
    }

    private fun runTouch(workingDir: File, args: List<String>): String {
        if (args.size < 2) return "Usage: touch file"
        for (i in 1 until args.size) {
            val target = File(workingDir, args[i]).canonicalFile
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                target.createNewFile()
            } else {
                target.setLastModified(System.currentTimeMillis())
            }
        }
        return ""
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
        } catch(e: Exception) {
            return "cp: error: ${e.message}"
        }
        return ""
    }

    private fun runMv(workingDir: File, args: List<String>): String {
        if (args.size < 3) return "Usage: mv source dest"
        val src = File(workingDir, args[1]).canonicalFile
        val dest = File(workingDir, args[2]).canonicalFile
        if (!src.exists()) return "mv: cannot stat '${args[1]}': No such file or directory"
        try {
            src.copyRecursively(dest, overwrite = true)
            src.deleteRecursively()
        } catch(e: Exception) {
             return "mv: error: ${e.message}"
        }
        return ""
    }

    private fun runEcho(args: List<String>): String {
        return args.drop(1).joinToString(" ")
    }
