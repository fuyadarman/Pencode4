package com.example.api

import android.util.Log
import com.example.data.ProjectFileEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object LocalHttpServer {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var activeFiles: List<ProjectFileEntity> = emptyList()
    
    private val _webDistDir = MutableStateFlow<String?>(null)
    val webDistDirFlow = _webDistDir.asStateFlow()

    val webDistDir: String?
        get() = _webDistDir.value
        
    private const val TAG = "LocalHttpServer"
    const val PORT = 8080

    fun isReactViteProject(files: List<ProjectFileEntity>): Boolean {
        if (files.isEmpty()) return false

        val hasViteConfig = files.any { 
            val p = it.path.lowercase()
            p == "vite.config.js" || p == "vite.config.ts" || p == "vite.config.mjs" || p == "vite.config.cjs" ||
            p.endsWith("/vite.config.js") || p.endsWith("/vite.config.ts")
        }
        if (hasViteConfig) return true

        val packageJson = files.find { it.path.equals("package.json", ignoreCase = true) }?.content ?: ""
        if (packageJson.isNotBlank()) {
            val lowerPkg = packageJson.lowercase()
            if (lowerPkg.contains("\"vite\"") || lowerPkg.contains("'vite'") || lowerPkg.contains("@vitejs/plugin-react")) {
                return true
            }
        }

        val indexHtml = files.find { 
            val p = it.path.lowercase()
            p == "index.html" || p.endsWith("/index.html") 
        }?.content ?: ""

        if (indexHtml.isNotBlank()) {
            val lowerHtml = indexHtml.lowercase()
            if (lowerHtml.contains("@vite") || lowerHtml.contains("/@vite/client")) {
                return true
            }
        }

        return false
    }

    fun setWebDistDir(dir: String?) {
        _webDistDir.value = dir
        Log.d(TAG, "Updated web dist dir: $dir")
    }

    fun updateFiles(files: List<ProjectFileEntity>) {
        activeFiles = files
        Log.d(TAG, "Updated server files: ${files.size} files")
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        thread(name = "LocalHttpServerThread") {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "Server started on port $PORT")
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    thread {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in server socket: ${e.message}", e)
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverSocket = null
        Log.d(TAG, "Server stopped")
    }

    private fun handleClient(socket: Socket) {
        var reader: BufferedReader? = null
        var output: OutputStream? = null
        try {
            reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            output = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return
            Log.d(TAG, "Request: $requestLine")

            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            var path = parts[1]

            if (method != "GET") {
                sendError(output, 405, "Method Not Allowed")
                return
            }

            // Clean path (remove query params and hash)
            if (path.contains("?")) {
                path = path.substringBefore("?")
            }
            if (path.contains("#")) {
                path = path.substringBefore("#")
            }
            try {
                path = java.net.URLDecoder.decode(path, "UTF-8")
            } catch (e: Exception) {
                // ignore
            }

            // Remove leading slash for matching
            var cleanPath = path.removePrefix("/")
            if (cleanPath.isEmpty()) {
                cleanPath = "index.html"
            }

            fun getMimeTypeAndContentType(nameOrPath: String): Pair<String, String> {
                val clean = nameOrPath.lowercase()
                val mime = when {
                    clean.endsWith(".html") || clean.endsWith(".htm") -> "text/html"
                    clean.endsWith(".css") -> "text/css"
                    clean.endsWith(".js") || clean.endsWith(".mjs") || clean.endsWith(".cjs") || clean.endsWith(".jsx") || clean.endsWith(".ts") || clean.endsWith(".tsx") -> "text/javascript"
                    clean.endsWith(".json") -> "application/json"
                    clean.endsWith(".svg") -> "image/svg+xml"
                    clean.endsWith(".png") -> "image/png"
                    clean.endsWith(".jpg") || clean.endsWith(".jpeg") -> "image/jpeg"
                    clean.endsWith(".gif") -> "image/gif"
                    clean.endsWith(".webp") -> "image/webp"
                    clean.endsWith(".ico") -> "image/x-icon"
                    clean.endsWith(".woff2") -> "font/woff2"
                    clean.endsWith(".woff") -> "font/woff"
                    clean.endsWith(".ttf") -> "font/ttf"
                    clean.endsWith(".otf") -> "font/otf"
                    clean.endsWith(".eot") -> "application/vnd.ms-fontobject"
                    clean.endsWith(".wasm") -> "application/wasm"
                    clean.endsWith(".xml") -> "application/xml"
                    clean.endsWith(".txt") || clean.endsWith(".md") -> "text/plain"
                    else -> "application/octet-stream"
                }
                val isText = mime.startsWith("text/") || 
                             mime.contains("javascript") || 
                             mime.contains("json") || 
                             mime.contains("xml") ||
                             mime.contains("svg")
                val contentType = if (isText) "$mime; charset=utf-8" else mime
                return Pair(mime, contentType)
            }

            // Check disk files if webDistDir is configured
            var diskFileBytes: ByteArray? = null
            var resolvedPath = cleanPath
            val localDir = webDistDir
            val isReactVite = isReactViteProject(activeFiles)
            if (!localDir.isNullOrBlank() && isReactVite) {
                val dir = java.io.File(localDir)
                if (dir.exists()) {
                    val candidateFiles = listOf(
                        java.io.File(dir, cleanPath),
                        java.io.File(dir, "out/$cleanPath"),
                        java.io.File(dir, "dist/$cleanPath"),
                        java.io.File(dir, "build/$cleanPath")
                    )
                    var found = candidateFiles.find { it.exists() && it.isFile }
                    if (found == null && !cleanPath.contains(".")) {
                        found = listOf(
                            java.io.File(dir, "index.html"),
                            java.io.File(dir, "out/index.html"),
                            java.io.File(dir, "dist/index.html")
                        ).find { it.exists() && it.isFile }
                    }
                    if (found != null) {
                        try {
                            diskFileBytes = found.readBytes()
                            resolvedPath = found.name
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading disk file: ${e.message}")
                        }
                    }
                }
            }

            if (diskFileBytes != null) {
                val (mimeType, contentType) = getMimeTypeAndContentType(resolvedPath)

                output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                output.write("Content-Type: $contentType\r\n".toByteArray())
                output.write("Content-Length: ${diskFileBytes.size}\r\n".toByteArray())
                output.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
                output.write("Connection: close\r\n\r\n".toByteArray())
                output.write(diskFileBytes)
                output.flush()
                return
            }

            // Find matching file in activeFiles
            var matchingFile = activeFiles.find { 
                it.path.equals(cleanPath, ignoreCase = true) || 
                it.path.removePrefix("/").equals(cleanPath, ignoreCase = true) ||
                it.path.endsWith("/$cleanPath", ignoreCase = true)
            }
            if (matchingFile == null && !cleanPath.contains(".")) {
                matchingFile = activeFiles.find { it.path.equals("index.html", ignoreCase = true) || it.path.endsWith("/index.html", ignoreCase = true) }
            }

            if (matchingFile != null) {
                val (mimeType, contentType) = getMimeTypeAndContentType(matchingFile.path)

                var fileText = matchingFile.content
                if (fileText.startsWith("data:") && fileText.contains(";base64,")) {
                    val bodyBytes = try {
                        android.util.Base64.decode(fileText.substringAfter(";base64,"), android.util.Base64.DEFAULT)
                    } catch (e: Exception) {
                        fileText.toByteArray(Charsets.UTF_8)
                    }
                    output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                    output.write("Content-Type: $contentType\r\n".toByteArray())
                    output.write("Content-Length: ${bodyBytes.size}\r\n".toByteArray())
                    output.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
                    output.write("Connection: close\r\n\r\n".toByteArray())
                    output.write(bodyBytes)
                    output.flush()
                    return
                }

                // If it's a React CDN or HTML/JSX/JS file, apply CDN import compatibility
                if (!isReactVite) {
                    val lowerPath = matchingFile.path.lowercase()
                    if (lowerPath.endsWith(".html") || lowerPath.endsWith(".htm")) {
                        // Ensure script tags loading JS/JSX/TSX use type="text/babel" for Babel standalone
                        fileText = fileText.replace(Regex("""<script\s+(?:type=["']module["']\s+)?src=["']([^"']+\.(?:jsx?|tsx?))["']""")) { match ->
                            val src = match.groupValues[1]
                            """<script type="text/babel" data-presets="react,stage-3" src="$src""""
                        }

                        if (!fileText.contains("babel.min.js")) {
                            val cdnScripts = """
                            <script src="https://cdn.tailwindcss.com"></script>
                            <script src="https://unpkg.com/react@18/umd/react.development.js" crossorigin></script>
                            <script src="https://unpkg.com/react-dom@18/umd/react-dom.development.js" crossorigin></script>
                            <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
                            <script>
                              window.React = window.React || React;
                              window.ReactDOM = window.ReactDOM || ReactDOM;
                              window.exports = window.exports || {};
                              window.module = window.module || { exports: window.exports };
                            </script>
                            """.trimIndent()
                            fileText = if (fileText.contains("<head>", ignoreCase = true)) {
                                fileText.replace("(?i)<head>".toRegex(), "<head>\n$cdnScripts\n")
                            } else {
                                "$cdnScripts\n$fileText"
                            }
                        }
                    } else if (lowerPath.endsWith(".js") || lowerPath.endsWith(".jsx") || lowerPath.endsWith(".ts") || lowerPath.endsWith(".tsx")) {
                        // Prepend window globals for Babel standalone
                        val prependHeader = "var exports = window.exports = window.exports || {}; var React = window.React || React; var ReactDOM = window.ReactDOM || ReactDOM;\n"
                        // Transform ES module imports for React CDN compatibility
                        var transformed = fileText
                            .replace(Regex("""import\s+React\s*,\s*\{([^}]+)\}\s+from\s+['"]react['"];?""")) { match ->
                                val destructured = match.groupValues[1]
                                "const React = window.React || {}; const {$destructured} = window.React || React || {};"
                            }
                            .replace(Regex("""import\s+React\s+from\s+['"]react['"];?""")) {
                                "const React = window.React || {};"
                            }
                            .replace(Regex("""import\s+\{([^}]+)\}\s+from\s+['"]react['"];?""")) { match ->
                                val destructured = match.groupValues[1]
                                "const {$destructured} = window.React || {};"
                            }
                            .replace(Regex("""import\s+ReactDOM\s+from\s+['"]react-dom/(?:client|server)['"];?""")) {
                                "const ReactDOM = window.ReactDOM || {};"
                            }
                            .replace(Regex("""import\s+ReactDOM\s+from\s+['"]react-dom['"];?""")) {
                                "const ReactDOM = window.ReactDOM || {};"
                            }
                            .replace(Regex("""import\s+\{([^}]+)\}\s+from\s+['"]react-dom/(?:client|server)['"];?""")) { match ->
                                val destructured = match.groupValues[1]
                                "const {$destructured} = window.ReactDOM || {};"
                            }
                            .replace(Regex("""import\s+\{([^}]+)\}\s+from\s+['"]react-dom['"];?""")) { match ->
                                val destructured = match.groupValues[1]
                                "const {$destructured} = window.ReactDOM || {};"
                            }
                            .replace(Regex("""import\s+['"][^'"]+\.css['"];?""")) {
                                "/* CSS import omitted in CDN mode */"
                            }
                        fileText = prependHeader + transformed
                    }
                }

                val bodyBytes = fileText.toByteArray(Charsets.UTF_8)
                
                output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                output.write("Content-Type: $contentType\r\n".toByteArray())
                output.write("Content-Length: ${bodyBytes.size}\r\n".toByteArray())
                output.write("Access-Control-Allow-Origin: *\r\n".toByteArray()) // Allow CORS
                output.write("Connection: close\r\n\r\n".toByteArray())
                output.write(bodyBytes)
                output.flush()
            } else {
                sendError(output, 404, "Not Found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client: ${e.message}")
        } finally {
            try { reader?.close() } catch (e: Exception) {}
            try { output?.close() } catch (e: Exception) {}
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun sendError(output: OutputStream, code: Int, message: String) {
        try {
            val body = "<h1>$code $message</h1>"
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            output.write("HTTP/1.1 $code $message\r\n".toByteArray())
            output.write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
            output.write("Content-Length: ${bodyBytes.size}\r\n".toByteArray())
            output.write("Connection: close\r\n\r\n".toByteArray())
            output.write(bodyBytes)
            output.flush()
        } catch (e: Exception) {
            // ignore
        }
    }
}
