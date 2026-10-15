package com.example.browser

import android.util.Base64
import android.util.Log
import com.example.data.ProjectFileEntity
import com.example.data.VibeRepository
import com.example.ui.BackgroundBrowser
import com.example.ui.BrowserResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * BrowserTransferEngine
 * Autonomous file upload and download engine for the AI Browser Controller.
 * Enables AI to:
 * 1. Download any file (images, PDFs, ZIPs, source code, data blobs, web assets) from browser
 *    pages or direct download links directly into the project workspace.
 * 2. Upload any project workspace file into browser forms and <input type="file"> elements
 *    using simulated File and DataTransfer synthesis.
 */
object BrowserTransferEngine {

    private const val TAG = "BrowserTransferEngine"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Downloads an asset or file from the browser page or a given URL/selector into the project workspace.
     */
    suspend fun downloadFromBrowser(
        urlOrSelector: String?,
        destinationPath: String?,
        projectName: String,
        repository: VibeRepository,
        backgroundBrowser: BackgroundBrowser,
        normalizePath: (String) -> String
    ): String = withContext(Dispatchers.IO) {
        val target = urlOrSelector?.trim() ?: ""

        // 1. Determine if target is a direct URL or a DOM selector
        var downloadUrl: String? = null
        var inlineDataUri: String? = null
        var suggestedFilename: String? = null

        val isHttpUrl = target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)
        val isDataUri = target.startsWith("data:", ignoreCase = true)
        val isBlobUri = target.startsWith("blob:", ignoreCase = true)

        if (isHttpUrl) {
            downloadUrl = target
        } else if (isDataUri) {
            inlineDataUri = target
        } else if (isBlobUri) {
            // Blob URL: Read via JavaScript inside WebView
            val extractBlobJs = """
                (function() {
                    return new Promise((resolve) => {
                        fetch('$target')
                            .then(r => r.blob())
                            .then(blob => {
                                var reader = new FileReader();
                                reader.onloadend = function() { resolve(reader.result); };
                                reader.onerror = function() { resolve('ERROR: ' + reader.error); };
                                reader.readAsDataURL(blob);
                            })
                            .catch(err => resolve('ERROR: ' + err.message));
                    });
                })()
            """.trimIndent()
            val rawRes = withContext(Dispatchers.Main) { backgroundBrowser.runJavascript(extractBlobJs) }
            val cleanRes = rawRes.removeSurrounding("\"").trim()
            if (cleanRes.startsWith("data:")) {
                inlineDataUri = cleanRes
            } else {
                return@withContext "Error reading blob URL from browser: $cleanRes"
            }
        } else {
            // DOM Selector or Element Index: Inspect element in browser DOM to locate href, src, or download link
            val escapedSel = JSONObject.quote(target.ifBlank { "a[href], [download], img[src]" })
            val inspectJs = """
                (function() {
                    try {
                        var sel = $escapedSel;
                        var el = null;
                        if (sel && sel.length > 0) {
                            try { el = document.querySelector(sel); } catch(e) {}
                            if (!el) {
                                try { el = document.getElementById(sel); } catch(e) {}
                            }
                            if (!el) {
                                try {
                                    var xp = document.evaluate(sel, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                                    el = xp.singleNodeValue;
                                } catch(e) {}
                            }
                        }
                        if (!el) {
                            el = document.querySelector('a[href]:not([href^="#"]):not([href^="javascript:"]), a[download], img[src]');
                        }
                        if (!el) return JSON.stringify({ error: 'Element not found on page' });

                        var url = el.getAttribute('href') || el.src || el.currentSrc || el.getAttribute('data-url') || '';
                        var downloadAttr = el.getAttribute('download') || '';
                        
                        // Handle relative URLs
                        if (url && !url.startsWith('http://') && !url.startsWith('https://') && !url.startsWith('data:') && !url.startsWith('blob:')) {
                            var a = document.createElement('a');
                            a.href = url;
                            url = a.href;
                        }

                        // Canvas support: export as DataURL
                        if (el.tagName === 'CANVAS') {
                            try { url = el.toDataURL('image/png'); } catch(ce) {}
                        }

                        return JSON.stringify({
                            url: url,
                            downloadName: downloadAttr,
                            tagName: el.tagName.toLowerCase()
                        });
                    } catch(err) {
                        return JSON.stringify({ error: err.message || err.toString() });
                    }
                })()
            """.trimIndent()

            val rawRes = withContext(Dispatchers.Main) { backgroundBrowser.runJavascript(inspectJs) }
            val jsonStr = rawRes.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\").trim()
            try {
                val json = JSONObject(jsonStr)
                if (json.has("error") && !json.isNull("error")) {
                    return@withContext "Error locating download element in browser: ${json.getString("error")}"
                }
                val extractedUrl = json.optString("url", "")
                suggestedFilename = json.optString("downloadName", "").ifBlank { null }

                if (extractedUrl.startsWith("data:")) {
                    inlineDataUri = extractedUrl
                } else if (extractedUrl.isNotBlank()) {
                    downloadUrl = extractedUrl
                } else {
                    return@withContext "Error: Found element <${json.optString("tagName", "")}> but it does not contain a downloadable URL (href or src)."
                }
            } catch (e: Exception) {
                return@withContext "Error parsing element download info: ${e.localizedMessage} (raw: $rawRes)"
            }
        }

        // 2. Process data: URI
        if (!inlineDataUri.isNullOrBlank()) {
            return@withContext saveInlineDataUri(
                dataUri = inlineDataUri,
                destinationPath = destinationPath,
                suggestedFilename = suggestedFilename,
                projectName = projectName,
                repository = repository,
                normalizePath = normalizePath
            )
        }

        // 3. Process HTTP/HTTPS download
        val finalUrl = downloadUrl ?: return@withContext "Error: No downloadable URL could be identified."
        try {
            val request = Request.Builder()
                .url(finalUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept", "*/*")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext "Error downloading file from '$finalUrl': HTTP ${response.code} ${response.message}"
            }

            val body = response.body ?: return@withContext "Error: Download response body was empty for '$finalUrl'."
            val bytes = body.bytes()
            if (bytes.isEmpty()) {
                return@withContext "Error: Downloaded file content was 0 bytes."
            }

            // Determine filename & extension
            val headerDisposition = response.header("Content-Disposition")
            var filenameFromHeader: String? = null
            if (!headerDisposition.isNullOrBlank() && headerDisposition.contains("filename=")) {
                filenameFromHeader = headerDisposition.substringAfter("filename=").removeSurrounding("\"").substringBefore(";")
            }

            val contentType = response.header("Content-Type") ?: "application/octet-stream"
            val urlFileName = try {
                val parsed = URL(finalUrl)
                File(parsed.path).name.ifBlank { null }
            } catch (e: Exception) {
                null
            }

            val computedName = filenameFromHeader ?: suggestedFilename ?: urlFileName ?: "download_${System.currentTimeMillis()}.${guessExtension(contentType)}"
            val finalDest = if (!destinationPath.isNullOrBlank()) {
                normalizePath(destinationPath)
            } else {
                "downloads/$computedName"
            }

            // Check if binary or text
            val isBinary = isBinaryContent(contentType, computedName)
            val fileContent = if (isBinary) {
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                String(bytes, Charsets.UTF_8)
            }

            repository.saveFile(projectName, finalDest, fileContent)

            val sizeKb = bytes.size / 1024
            "Successfully downloaded '$computedName' ($sizeKb KB, $contentType) to workspace '$finalDest'."
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for '$finalUrl'", e)
            "Error downloading from '$finalUrl': ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    /**
     * Uploads a project workspace file into an HTML <input type="file"> on the browser page.
     */
    suspend fun uploadToBrowser(
        filePath: String,
        selector: String?,
        elementIndex: Int?,
        projectName: String,
        repository: VibeRepository,
        backgroundBrowser: BackgroundBrowser,
        normalizePath: (String) -> String
    ): String = withContext(Dispatchers.IO) {
        val cleanPath = normalizePath(filePath.trim())
        if (cleanPath.isBlank()) {
            return@withContext "Error: Please specify the 'filePath' of the project file to upload."
        }

        val projectFiles = repository.getFilesForProject(projectName)
        val fileEntity = projectFiles.find { it.path.equals(cleanPath, ignoreCase = true) }
            ?: projectFiles.find { it.path.endsWith("/$cleanPath", ignoreCase = true) }
            ?: projectFiles.find { it.path.substringAfterLast("/").equals(cleanPath.substringAfterLast("/"), ignoreCase = true) }
            ?: return@withContext "Error: Workspace file '$cleanPath' not found in project '$projectName'."

        val fileName = File(cleanPath).name
        val extension = fileName.substringAfterLast(".", "").lowercase()
        val mimeType = guessMimeType(extension)

        // Extract base64 representation of file content
        val rawContent = fileEntity.content
        val base64Data = if (rawContent.startsWith("data:") && rawContent.contains(";base64,")) {
            rawContent.substringAfter(";base64,")
        } else if (extension in setOf("png", "jpg", "jpeg", "webp", "gif", "ico", "pdf", "zip", "mp3", "mp4", "wasm", "apk", "bin")) {
            // Already base64 encoded or binary
            rawContent.replace("\n", "").replace("\r", "")
        } else {
            // Text or code file: encode UTF-8 to Base64
            Base64.encodeToString(rawContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }

        // Clean selector
        val cleanSelector = selector?.trim()?.let {
            if (it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true) || it.isBlank()) null else it
        }

        val targetSelector = when {
            elementIndex != null && elementIndex > 0 -> "[data-agent-index='$elementIndex']"
            cleanSelector != null -> cleanSelector
            else -> ""
        }

        val escapedSelector = JSONObject.quote(targetSelector)
        val escapedFileName = JSONObject.quote(fileName)
        val escapedMime = JSONObject.quote(mimeType)
        val escapedBase64 = JSONObject.quote(base64Data)

        val uploadScript = """
            (function() {
                try {
                    var sel = $escapedSelector;
                    var fileName = $escapedFileName;
                    var mimeType = $escapedMime;
                    var b64Data = $escapedBase64;

                    var inputEl = null;

                    // 1. Selector search
                    if (sel && sel.length > 0 && sel !== 'null' && sel !== 'undefined') {
                        try { inputEl = document.querySelector(sel); } catch(e) {}
                        if (!inputEl) {
                            try { inputEl = document.getElementById(sel); } catch(e) {}
                        }
                    }

                    // 2. If selector is a button or label, find associated file input
                    if (inputEl && inputEl.tagName !== 'INPUT') {
                        var forId = inputEl.getAttribute('for');
                        if (forId) {
                            inputEl = document.getElementById(forId);
                        } else {
                            var nested = inputEl.querySelector('input[type="file"]');
                            if (nested) {
                                inputEl = nested;
                            } else {
                                // Search next sibling or parent
                                var parentInput = inputEl.parentElement ? inputEl.parentElement.querySelector('input[type="file"]') : null;
                                if (parentInput) inputEl = parentInput;
                            }
                        }
                    }

                    // 3. Fallback to first available file input on page
                    if (!inputEl || inputEl.type !== 'file') {
                        inputEl = document.querySelector('input[type="file"]');
                    }

                    if (!inputEl) {
                        return JSON.stringify({ error: 'No <input type=\"file\"> element found on current webpage.' });
                    }

                    // Decode base64 to binary byte array
                    var byteCharacters = atob(b64Data);
                    var byteNumbers = new Array(byteCharacters.length);
                    for (var i = 0; i < byteCharacters.length; i++) {
                        byteNumbers[i] = byteCharacters.charCodeAt(i);
                    }
                    var byteArray = new Uint8Array(byteNumbers);
                    var blob = new Blob([byteArray], { type: mimeType });
                    var file = new File([blob], fileName, { type: mimeType, lastModified: Date.now() });

                    // Set files list via DataTransfer API
                    var dt = new DataTransfer();
                    dt.items.add(file);
                    inputEl.files = dt.files;

                    // Trigger input & change events
                    inputEl.dispatchEvent(new Event('input', { bubbles: true }));
                    inputEl.dispatchEvent(new Event('change', { bubbles: true }));

                    // If form exists and has auto-upload, submit
                    var form = inputEl.form;
                    var tagDesc = inputEl.id ? '#' + inputEl.id : (inputEl.name ? '[name=' + inputEl.name + ']' : 'input[type=file]');

                    return JSON.stringify({
                        success: true,
                        input: tagDesc,
                        fileName: fileName,
                        size: byteArray.length
                    });
                } catch(fatal) {
                    return JSON.stringify({ error: fatal.message || fatal.toString() });
                }
            })()
        """.trimIndent()

        val rawRes = withContext(Dispatchers.Main) { backgroundBrowser.runJavascript(uploadScript) }
        val jsonStr = rawRes.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\").trim()

        try {
            val json = JSONObject(jsonStr)
            if (json.optBoolean("success", false)) {
                val inputDesc = json.optString("input", "file input")
                val sizeKb = json.optInt("size", base64Data.length * 3 / 4) / 1024
                "Successfully uploaded workspace file '$cleanPath' ($sizeKb KB, $mimeType) to browser element $inputDesc."
            } else {
                val err = json.optString("error", "Unknown upload error")
                "Error uploading file to browser: $err"
            }
        } catch (e: Exception) {
            "Error executing browser file upload: ${e.localizedMessage} (raw output: $rawRes)"
        }
    }

    private suspend fun saveInlineDataUri(
        dataUri: String,
        destinationPath: String?,
        suggestedFilename: String?,
        projectName: String,
        repository: VibeRepository,
        normalizePath: (String) -> String
    ): String {
        return try {
            val mimeType = dataUri.substringAfter("data:").substringBefore(";").ifBlank { "application/octet-stream" }
            val isBase64 = dataUri.contains(";base64,")
            val rawData = if (isBase64) dataUri.substringAfter(";base64,") else dataUri.substringAfter(",")
            
            val ext = guessExtension(mimeType)
            val name = suggestedFilename ?: "download_${System.currentTimeMillis()}.$ext"
            val finalDest = if (!destinationPath.isNullOrBlank()) normalizePath(destinationPath) else "downloads/$name"

            val cleanContent = if (isBase64) rawData else Base64.encodeToString(rawData.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            repository.saveFile(projectName, finalDest, cleanContent)
            
            val sizeKb = cleanContent.length * 3 / 4 / 1024
            "Successfully saved inline browser data ($sizeKb KB, $mimeType) to workspace '$finalDest'."
        } catch (e: Exception) {
            "Error saving inline data URI: ${e.localizedMessage}"
        }
    }

    private fun guessExtension(mimeType: String): String {
        return when {
            mimeType.contains("png") -> "png"
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("gif") -> "gif"
            mimeType.contains("svg") -> "svg"
            mimeType.contains("pdf") -> "pdf"
            mimeType.contains("json") -> "json"
            mimeType.contains("zip") -> "zip"
            mimeType.contains("html") -> "html"
            mimeType.contains("javascript") -> "js"
            mimeType.contains("css") -> "css"
            mimeType.contains("text/plain") -> "txt"
            else -> "bin"
        }
    }

    private fun guessMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            "zip" -> "application/zip"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "txt", "md" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun isBinaryContent(mimeType: String, filename: String): Boolean {
        val ext = filename.substringAfterLast(".", "").lowercase()
        if (ext in setOf("png", "jpg", "jpeg", "webp", "gif", "ico", "pdf", "zip", "mp3", "mp4", "apk", "jar", "bin", "wasm")) {
            return true
        }
        val lowerMime = mimeType.lowercase()
        return lowerMime.startsWith("image/") || lowerMime.startsWith("audio/") || lowerMime.startsWith("video/") ||
               lowerMime.contains("pdf") || lowerMime.contains("zip") || lowerMime.contains("octet-stream")
    }
}
