package com.example.api

import android.util.Log
import com.example.data.ProjectFileEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

object LocalHttpServer {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var activeFiles: List<ProjectFileEntity> = emptyList()
    var webDistDir: String? = null
        private set
    private const val TAG = "LocalHttpServer"
    const val PORT = 8080

    fun setWebDistDir(dir: String?) {
        webDistDir = dir
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

            // Check disk files if webDistDir is configured
            var diskFileBytes: ByteArray? = null
            var resolvedPath = cleanPath
            val localDir = webDistDir
            if (!localDir.isNullOrBlank()) {
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
                val mimeType = when {
                    cleanPath.endsWith(".css", ignoreCase = true) || resolvedPath.endsWith(".css", ignoreCase = true) -> "text/css"
                    cleanPath.endsWith(".js", ignoreCase = true) || cleanPath.endsWith(".mjs", ignoreCase = true) -> "application/javascript"
                    cleanPath.endsWith(".html", ignoreCase = true) || resolvedPath.endsWith(".html", ignoreCase = true) -> "text/html"
                    cleanPath.endsWith(".png", ignoreCase = true) -> "image/png"
                    cleanPath.endsWith(".jpg", ignoreCase = true) || cleanPath.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                    cleanPath.endsWith(".gif", ignoreCase = true) -> "image/gif"
                    cleanPath.endsWith(".webp", ignoreCase = true) -> "image/webp"
                    cleanPath.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
                    cleanPath.endsWith(".ico", ignoreCase = true) -> "image/x-icon"
                    cleanPath.endsWith(".json", ignoreCase = true) -> "application/json"
                    cleanPath.endsWith(".wasm", ignoreCase = true) -> "application/wasm"
                    cleanPath.endsWith(".woff2", ignoreCase = true) -> "font/woff2"
                    cleanPath.endsWith(".woff", ignoreCase = true) -> "font/woff"
                    cleanPath.endsWith(".ttf", ignoreCase = true) -> "font/ttf"
                    else -> "text/plain"
                }

                output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                output.write("Content-Type: $mimeType; charset=utf-8\r\n".toByteArray())
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
                val mimeType = when {
                    cleanPath.endsWith(".css", ignoreCase = true) -> "text/css"
                    cleanPath.endsWith(".js", ignoreCase = true) ||
                    cleanPath.endsWith(".jsx", ignoreCase = true) ||
                    cleanPath.endsWith(".ts", ignoreCase = true) ||
                    cleanPath.endsWith(".tsx", ignoreCase = true) ||
                    cleanPath.endsWith(".mjs", ignoreCase = true) ||
                    cleanPath.endsWith(".cjs", ignoreCase = true) -> "application/javascript"
                    cleanPath.endsWith(".html", ignoreCase = true) -> "text/html"
                    cleanPath.endsWith(".png", ignoreCase = true) -> "image/png"
                    cleanPath.endsWith(".jpg", ignoreCase = true) || cleanPath.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                    cleanPath.endsWith(".gif", ignoreCase = true) -> "image/gif"
                    cleanPath.endsWith(".webp", ignoreCase = true) -> "image/webp"
                    cleanPath.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
                    cleanPath.endsWith(".ico", ignoreCase = true) -> "image/x-icon"
                    cleanPath.endsWith(".json", ignoreCase = true) -> "application/json"
                    cleanPath.endsWith(".wasm", ignoreCase = true) -> "application/wasm"
                    else -> "text/plain"
                }

                val bodyBytes = if (matchingFile.content.startsWith("data:") && matchingFile.content.contains(";base64,")) {
                    try {
                        android.util.Base64.decode(matchingFile.content.substringAfter(";base64,"), android.util.Base64.DEFAULT)
                    } catch (e: Exception) {
                        matchingFile.content.toByteArray(Charsets.UTF_8)
                    }
                } else {
                    matchingFile.content.toByteArray(Charsets.UTF_8)
                }
                
                output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                output.write("Content-Type: $mimeType; charset=utf-8\r\n".toByteArray())
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
