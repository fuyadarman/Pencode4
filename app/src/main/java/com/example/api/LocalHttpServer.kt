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

    var appContext: android.content.Context? = null
    var onOAuthCallback: ((android.net.Uri) -> Unit)? = null

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
            val rawPath = parts[1]

            // Intercept OAuth callback (e.g. from Vercel MCP or loopback redirect)
            if (rawPath.startsWith("/callback") || rawPath.startsWith("/oauth/callback")) {
                val fullUri = android.net.Uri.parse("http://localhost:$PORT$rawPath")
                try {
                    onOAuthCallback?.invoke(fullUri)
                } catch (e: Exception) {
                    Log.e(TAG, "Error invoking OAuth callback", e)
                }
                
                val queryPart = if (rawPath.contains("?")) "?" + rawPath.substringAfter("?") else ""
                val appDeepLink = "pencode://mcp/oauth/callback$queryPart"
                val pkgName = appContext?.packageName ?: "com.example"
                val intentUri = "intent://mcp/oauth/callback$queryPart#Intent;scheme=pencode;package=$pkgName;action=android.intent.action.VIEW;end;"

                // Auto-launch the app back to foreground immediately
                appContext?.let { ctx ->
                    try {
                        val launchIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(appDeepLink)).apply {
                            setPackage(pkgName)
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        ctx.startActivity(launchIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch intent from LocalHttpServer", e)
                    }
                }

                val responseHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <title>MCP Authorization Successful</title>
                        <meta http-equiv="refresh" content="0; url=$intentUri">
                        <style>
                            body {
                                background: #0B0F17;
                                color: #F0F6FC;
                                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                                display: flex;
                                align-items: center;
                                justify-content: center;
                                min-height: 100vh;
                                margin: 0;
                                padding: 20px;
                                box-sizing: border-box;
                                text-align: center;
                            }
                            .card {
                                background: #161B22;
                                border: 1px solid #30363D;
                                border-radius: 16px;
                                max-width: 420px;
                                width: 100%;
                                padding: 32px 24px;
                                box-shadow: 0 12px 32px rgba(0,0,0,0.5);
                            }
                            .icon { font-size: 48px; margin-bottom: 16px; }
                            h2 { margin: 0 0 8px 0; color: #58A6FF; font-size: 20px; }
                            p { color: #8B949E; font-size: 14px; margin: 0 0 24px 0; line-height: 1.5; }
                            .btn {
                                display: inline-block;
                                background: #238636;
                                color: #ffffff;
                                text-decoration: none;
                                font-weight: 600;
                                font-size: 15px;
                                padding: 12px 28px;
                                border-radius: 8px;
                                cursor: pointer;
                            }
                        </style>
                    </head>
                    <body>
                        <div class="card">
                            <div class="icon">✅</div>
                            <h2>MCP Authorization Successful</h2>
                            <p>Connected to MCP! Returning to Pencode app...</p>
                            <a class="btn" href="$intentUri">Return to App</a>
                        </div>
                        <script>
                            function openApp() {
                                window.location.href = "$intentUri";
                                setTimeout(function() {
                                    window.location.href = "$appDeepLink";
                                }, 300);
                            }
                            openApp();
                        </script>
                    </body>
                    </html>
                """.trimIndent()
                
                val bytes = responseHtml.toByteArray(Charsets.UTF_8)
                val responseHeader = "HTTP/1.1 200 OK\r\n" +
                                     "Content-Type: text/html; charset=utf-8\r\n" +
                                     "Content-Length: ${bytes.size}\r\n" +
                                     "Connection: close\r\n\r\n"
                output.write(responseHeader.toByteArray(Charsets.US_ASCII))
                output.write(bytes)
                output.flush()
                return
            }

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
        return com.example.ui.preview.BabelCompatibilityEngine.sanitizeAndConfigureBabel(html)
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
