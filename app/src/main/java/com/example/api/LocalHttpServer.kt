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
    private const val TAG = "LocalHttpServer"
    const val PORT = 8080

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

            // Find matching file
            val matchingFile = activeFiles.find { 
                it.path.equals(cleanPath, ignoreCase = true) || 
                it.path.removePrefix("/").equals(cleanPath, ignoreCase = true)
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

                val bodyBytes = matchingFile.content.toByteArray(Charsets.UTF_8)
                
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
