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
            if (lowerPkg.contains("\"vite\"") || lowerPkg.contains("\"@vitejs/plugin-react\"") || lowerPkg.contains("\"build\": \"vite build\"")) {
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

                val bodyBytes = if (matchingFile.content.startsWith("data:") && matchingFile.content.contains(";base64,")) {
                    try {
                        android.util.Base64.decode(matchingFile.content.substringAfter(";base64,"), android.util.Base64.DEFAULT)
                    } catch (e: Exception) {
                        matchingFile.content.toByteArray(Charsets.UTF_8)
                    }
                } else {
                    var content = matchingFile.content
                    if (matchingFile.path.equals("index.html", ignoreCase = true) || matchingFile.path.endsWith("/index.html", ignoreCase = true)) {
                        content = preprocessHtmlForBabel(content)
                    }
                    content.toByteArray(Charsets.UTF_8)
                }
                
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

    private fun preprocessHtmlForBabel(html: String): String {
        var content = html

        // 1. Inject Babel setup to register 'react-classic' preset with classic runtime
        val babelCdnRegex = Regex("""(<script\s+[^>]*src=["'][^"']*babel\.min\.js["'][^>]*>\s*</script>)""", RegexOption.IGNORE_CASE)
        if (babelCdnRegex.containsMatchIn(content)) {
            content = babelCdnRegex.replace(content) { matchResult ->
                matchResult.value + "\n<script>\n" +
                        "if (window.Babel) {\n" +
                        "  Babel.registerPreset('react-classic', {\n" +
                        "    presets: [\n" +
                        "      [Babel.availablePresets['react'], { runtime: 'classic' }]\n" +
                        "    ]\n" +
                        "  });\n" +
                        "}\n" +
                        "</script>"
            }
        } else {
            val headRegex = Regex("""(<head>)""", RegexOption.IGNORE_CASE)
            if (headRegex.containsMatchIn(content)) {
                content = headRegex.replace(content) { matchResult ->
                    matchResult.value + "\n<script>\n" +
                            "window.addEventListener('DOMContentLoaded', () => {\n" +
                            "  if (window.Babel) {\n" +
                            "    Babel.registerPreset('react-classic', {\n" +
                            "      presets: [\n" +
                            "        [Babel.availablePresets['react'], { runtime: 'classic' }]\n" +
                            "      ]\n" +
                            "    });\n" +
                            "  }\n" +
                            "});\n" +
                            "</script>"
                }
            }
        }

        // 2. Replace 'react' preset with 'react-classic' in data-presets attribute
        val dataPresetsRegex = Regex("""data-presets\s*=\s*["']([^"']*)\breact\b([^"']*)["']""", RegexOption.IGNORE_CASE)
        content = dataPresetsRegex.replace(content) { matchResult ->
            val before = matchResult.groups[1]?.value ?: ""
            val after = matchResult.groups[2]?.value ?: ""
            "data-presets=\"${before}react-classic${after}\""
        }

        // 3. Keep data-type="module" additions to avoid 'Cannot use import statement outside a module'
        val babelScriptRegex = Regex("""<script\s+type\s*=\s*["']text/babel["'](?![^>]*data-type\s*=)([^>]*)>""", RegexOption.IGNORE_CASE)
        content = babelScriptRegex.replace(content) { matchResult ->
            val attrs = matchResult.groups[1]?.value ?: ""
            "<script type=\"text/babel\" data-type=\"module\"$attrs>"
        }

        return content
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
