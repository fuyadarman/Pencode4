package com.example.ui

import com.example.api.Content
import com.example.api.Part
import com.example.api.ToolArguments
import com.example.api.ToolCallResponse
import com.example.data.ProjectEntity
import com.example.data.VibeRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExtraToolHandlers {

    suspend fun handleExtraToolCall(
        tool: String,
        args: ToolArguments?,
        project: ProjectEntity,
        repository: VibeRepository,
        backgroundBrowser: BackgroundBrowser,
        todoList: List<TodoItem>,
        updateTodoList: (List<TodoItem>) -> Unit,
        createLog: (title: String, status: String, details: String, lineRange: String?) -> AiActionLog,
        addLog: (AiActionLog) -> Unit,
        updateLog: (id: String, status: String, details: String) -> Unit,
        setAgentStatus: (String) -> Unit,
        normalizePath: (String) -> String
    ): String {
        return when (tool) {
            "generate_image" -> {
                val filePath = normalizePath(args?.path ?: "image.png")
                val imagePrompt = args?.prompt ?: "beautiful abstract digital art"
                val width = args?.width ?: 1024
                val height = args?.height ?: 1024

                val genLog = createLog(
                    "Generate image",
                    "thinking",
                    "Generating: \"$imagePrompt\" ($width x $height)",
                    "pollinations"
                )
                addLog(genLog)
                setAgentStatus("Generating image using Pollinations AI...")

                val result = try {
                    withContext(Dispatchers.IO) {
                        val client = okhttp3.OkHttpClient()
                        val encodedPrompt = java.net.URLEncoder.encode(imagePrompt, "UTF-8")
                        val randomSeed = (1..1000000).random()
                        val url = "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$height&seed=$randomSeed&model=flux&nologo=true"

                        val request = okhttp3.Request.Builder()
                            .url(url)
                            .get()
                            .build()

                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val bytes = response.body?.bytes()
                            if (bytes != null) {
                                val mimeType = when (filePath.substringAfterLast(".", "").lowercase()) {
                                    "png" -> "image/png"
                                    "jpg", "jpeg" -> "image/jpeg"
                                    "webp" -> "image/webp"
                                    "gif" -> "image/gif"
                                    "ico" -> "image/x-icon"
                                    else -> "image/png"
                                }
                                val base64Content = "data:$mimeType;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                repository.saveFile(project.name, filePath, base64Content)
                                "Successfully generated and saved image to '$filePath'"
                            } else {
                                "Error: Image response body was empty."
                            }
                        } else {
                            "Error: Failed to fetch image from Pollinations (HTTP ${response.code})."
                        }
                    }
                } catch (e: Exception) {
                    val errorMsg = e.localizedMessage ?: e.javaClass.simpleName
                    "Error generating image: $errorMsg"
                }

                val isSuccess = result.startsWith("Successfully")
                updateLog(genLog.id, if (isSuccess) "success" else "failed", result)
                result
            }
            "resize_image" -> {
                val sourcePath = normalizePath(args?.path ?: "")
                val destPath = normalizePath(args?.destinationPath ?: "")
                val targetWidth = args?.width ?: 512
                val targetHeight = args?.height ?: 512
                val outputFormatStr = args?.format ?: destPath.substringAfterLast(".", "png")

                val resizeLog = createLog(
                    "Resize image",
                    "thinking",
                    "Resizing $sourcePath to $destPath ($targetWidth x $targetHeight, format: $outputFormatStr)",
                    "android-graphics"
                )
                addLog(resizeLog)

                val files = repository.getFilesForProject(project.name)
                val sourceFile = files.find { it.path == sourcePath }

                val result = if (sourceFile == null) {
                    "Error: Source image file '$sourcePath' not found."
                } else {
                    try {
                        val base64String = sourceFile.content
                        val isBase64Image = base64String.startsWith("data:") && base64String.contains(";base64,")
                        val cleanBase64 = if (isBase64Image) base64String.substringAfter(";base64,") else base64String
                        val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)

                        val originalBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (originalBitmap != null) {
                            val resizedBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
                            val stream = java.io.ByteArrayOutputStream()
                            val compressFormat = when (outputFormatStr.lowercase()) {
                                "jpg", "jpeg" -> android.graphics.Bitmap.CompressFormat.JPEG
                                "webp" -> android.graphics.Bitmap.CompressFormat.WEBP
                                else -> android.graphics.Bitmap.CompressFormat.PNG
                            }
                            resizedBitmap.compress(compressFormat, 100, stream)
                            val resizedBytes = stream.toByteArray()

                            val destMimeType = when (destPath.substringAfterLast(".", "").lowercase()) {
                                "png" -> "image/png"
                                "jpg", "jpeg" -> "image/jpeg"
                                "webp" -> "image/webp"
                                "gif" -> "image/gif"
                                "ico" -> "image/x-icon"
                                else -> "image/png"
                            }
                            val destBase64 = "data:$destMimeType;base64," + android.util.Base64.encodeToString(resizedBytes, android.util.Base64.NO_WRAP)
                            repository.saveFile(project.name, destPath, destBase64)

                            originalBitmap.recycle()
                            resizedBitmap.recycle()

                            "Successfully resized and saved image to '$destPath' ($targetWidth x $targetHeight, format: ${compressFormat.name})"
                        } else {
                            "Error: Failed to decode image bytes from '$sourcePath'."
                        }
                    } catch (e: Exception) {
                        "Error resizing image: ${e.localizedMessage}"
                    }
                }

                val isSuccess = result.startsWith("Successfully")
                updateLog(resizeLog.id, if (isSuccess) "success" else "failed", result)
                result
            }
            "browser_search" -> {
                val queryVal = args?.query ?: ""
                val searchLog = createLog(
                    "Browser search/navigate",
                    "thinking",
                    "Searching or loading: $queryVal",
                    "background-browser"
                )
                addLog(searchLog)
                setAgentStatus("Performing browser search/navigation for: $queryVal...")

                var lastUrl = ""
                var lastTitle = ""
                var lastExcerpt = ""

                val result = if (queryVal.isBlank()) {
                    "Error: 'query' argument cannot be empty. Please provide a search term or a URL."
                } else {
                    val isUrl = queryVal.startsWith("http://") || queryVal.startsWith("https://") || (queryVal.contains(".") && !queryVal.contains(" "))
                    val browserResult = if (isUrl) {
                        backgroundBrowser.navigate(queryVal)
                    } else {
                        backgroundBrowser.searchBrave(queryVal)
                    }
                    when (browserResult) {
                        is BrowserResult.Success -> {
                            lastUrl = browserResult.url
                            lastTitle = browserResult.title
                            lastExcerpt = browserResult.content.take(180)
                            "Successfully loaded page: ${browserResult.url}\nTitle: ${browserResult.title}\n\nContent Summary:\n${browserResult.content}"
                        }
                        is BrowserResult.Error -> {
                            "Error performing browser action: ${browserResult.message}"
                        }
                    }
                }

                val isSuccess = !result.startsWith("Error")
                val logDetails = if (isSuccess) {
                    "Navigated to: $lastUrl\nTitle: $lastTitle\nArticle Excerpt: $lastExcerpt..."
                } else result
                updateLog(searchLog.id, if (isSuccess) "success" else "failed", logDetails)
                result
            }
            "browser_click" -> {
                val selector = args?.search ?: ""
                val clickLog = createLog(
                    "Browser Click & Switch",
                    "thinking",
                    "Clicking button/element '$selector' in browser...",
                    "background-browser"
                )
                addLog(clickLog)
                setAgentStatus("Clicking element '$selector' and switching page...")

                var clickedUrl = ""
                var clickedTitle = ""

                val result = if (selector.isBlank()) {
                    "Error: 'search' argument (CSS selector or XPath) cannot be empty."
                } else {
                    when (val browserResult = backgroundBrowser.clickElement(selector)) {
                        is BrowserResult.Success -> {
                            clickedUrl = browserResult.url
                            clickedTitle = browserResult.title
                            browserResult.content
                        }
                        is BrowserResult.Error -> {
                            "Error clicking element: ${browserResult.message}"
                        }
                    }
                }

                val isSuccess = !result.startsWith("Error")
                val detailsText = if (isSuccess) {
                    "Clicked '$selector' -> Switched to: $clickedUrl\nPage Title: $clickedTitle"
                } else result
                updateLog(clickLog.id, if (isSuccess) "success" else "failed", detailsText)
                result
            }
            "browser_read" -> {
                val readLog = createLog(
                    "Browser Read Site Article",
                    "thinking",
                    "Reading current website article & page content...",
                    "background-browser"
                )
                addLog(readLog)
                setAgentStatus("Reading article & site content...")

                val result = when (val browserResult = backgroundBrowser.readPageContent()) {
                    is BrowserResult.Success -> {
                        val dataText = "Current URL: ${browserResult.url}\nTitle: ${browserResult.title}\n\nContent:\n${browserResult.content}"
                        try {
                            val files = repository.getFilesForProject(project.name)
                            val currentMem = files.find { it.path == "browser_memory.md" }?.content ?: ""
                            val updatedMem = if (currentMem.isBlank()) {
                                "# Browser Memory\n\n## ${browserResult.title}\nURL: ${browserResult.url}\n\n${browserResult.content}"
                            } else {
                                "$currentMem\n\n---\n\n## ${browserResult.title}\nURL: ${browserResult.url}\n\n${browserResult.content}"
                            }
                            repository.saveFile(project.name, "browser_memory.md", updatedMem)
                        } catch (e: Exception) {
                            // Handle silently
                        }
                        dataText
                    }
                    is BrowserResult.Error -> {
                        "Error reading content: ${browserResult.message}"
                    }
                }

                val isSuccess = !result.startsWith("Error")
                updateLog(readLog.id, if (isSuccess) "success" else "failed", result.take(200))
                result
            }
            "create_todo_list" -> {
                val queryVal = args?.query ?: ""
                val todoLog = createLog(
                    "Create TODO List",
                    "thinking",
                    "Initializing tasks: $queryVal",
                    "todo-list"
                )
                addLog(todoLog)
                setAgentStatus("Creating todo list...")

                val tasks = queryVal.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                updateTodoList(tasks.map { TodoItem(task = it) })

                val result = if (tasks.isEmpty()) {
                    "Error: No tasks provided to create todo list."
                } else {
                    "Successfully created todo list with ${tasks.size} tasks:\n" + tasks.mapIndexed { idx, t -> "$idx. [ ] $t" }.joinToString("\n")
                }

                val isSuccess = tasks.isNotEmpty()
                updateLog(todoLog.id, if (isSuccess) "success" else "failed", if (isSuccess) "Created todo list with ${tasks.size} items" else result)
                result
            }
            "complete_todo_task" -> {
                val queryVal = args?.query ?: ""
                setAgentStatus("Completing todo task...")

                val index = queryVal.toIntOrNull()
                val currentTodos = todoList
                val result = if (index != null && index >= 0 && index < currentTodos.size) {
                    val updated = currentTodos.toMutableList()
                    val task = updated[index]
                    updated[index] = task.copy(isCompleted = true)
                    updateTodoList(updated)
                    "Successfully marked task '${task.task}' as completed."
                } else {
                    "Error: Invalid task index '$queryVal'. Current todo list size is ${currentTodos.size}."
                }
                result
            }
            "delete_file" -> {
                val filePath = normalizePath(args?.path ?: "")
                val deleteLog = createLog(
                    "Deleted file",
                    "thinking",
                    filePath,
                    null
                )
                addLog(deleteLog)

                val result = try {
                    repository.deleteFile(project.name, filePath)
                    "Successfully deleted file '$filePath'"
                } catch (e: Exception) {
                    "Error deleting file: ${e.localizedMessage}"
                }

                updateLog(deleteLog.id, "success", result)
                result
            }
            "rename_file" -> {
                val oldPath = normalizePath(if (!args?.oldPath.isNullOrBlank()) args.oldPath else args?.path ?: "")
                val newPath = normalizePath(if (!args?.newPath.isNullOrBlank()) args.newPath else args?.destinationPath ?: "")
                val renameLog = createLog(
                    "Renamed file",
                    "thinking",
                    "Renaming '$oldPath' to '$newPath'",
                    null
                )
                addLog(renameLog)

                val result = try {
                    if (oldPath.isBlank() || newPath.isBlank()) {
                        "Error: both old path and new path are required for rename_file."
                    } else {
                        repository.renameFile(project.name, oldPath, newPath)
                        "Successfully renamed '$oldPath' to '$newPath'"
                    }
                } catch (e: Exception) {
                    "Error renaming file: ${e.localizedMessage}"
                }

                updateLog(renameLog.id, if (result.startsWith("Successfully")) "success" else "failed", result)
                result
            }
            "move_file" -> {
                val oldPath = normalizePath(if (!args?.oldPath.isNullOrBlank()) args.oldPath else args?.path ?: "")
                val newPath = normalizePath(if (!args?.newPath.isNullOrBlank()) args.newPath else args?.destinationPath ?: "")
                val moveLog = createLog(
                    "Moved file",
                    "thinking",
                    "Moving '$oldPath' to '$newPath'",
                    null
                )
                addLog(moveLog)

                val result = try {
                    if (oldPath.isBlank() || newPath.isBlank()) {
                        "Error: both source path and destination path are required for move_file."
                    } else {
                        repository.moveFile(project.name, oldPath, newPath)
                        "Successfully moved '$oldPath' to '$newPath'"
                    }
                } catch (e: Exception) {
                    "Error moving file: ${e.localizedMessage}"
                }

                updateLog(moveLog.id, if (result.startsWith("Successfully")) "success" else "failed", result)
                result
            }
            "open_url", "navigate", "browse_url" -> {
                val targetUrl = (if (!args?.url.isNullOrBlank()) args.url else if (!args?.query.isNullOrBlank()) args.query else args?.path ?: "").trim()
                val navLog = createLog(
                    "Open Web URL",
                    "thinking",
                    "Navigating to URL: $targetUrl",
                    "web-clone"
                )
                addLog(navLog)
                setAgentStatus("Navigating to URL: $targetUrl...")

                val result = if (targetUrl.isBlank()) {
                    "Error: 'url' parameter cannot be empty for open_url."
                } else {
                    when (val browserResult = backgroundBrowser.navigate(targetUrl)) {
                        is BrowserResult.Success -> {
                            "Successfully opened URL: ${browserResult.url}\nTitle: ${browserResult.title}\n\nContent Overview:\n${browserResult.content.take(800)}"
                        }
                        is BrowserResult.Error -> {
                            "Error opening URL: ${browserResult.message}"
                        }
                    }
                }

                val isSuccess = result.startsWith("Successfully")
                updateLog(navLog.id, if (isSuccess) "success" else "failed", result.take(300))
                result
            }
            "get_page_source" -> {
                val sourceLog = createLog(
                    "Get Page Source",
                    "thinking",
                    "Extracting complete HTML source code...",
                    "web-clone"
                )
                addLog(sourceLog)
                setAgentStatus("Extracting HTML page source...")

                val result = when (val browserResult = backgroundBrowser.getPageSource()) {
                    is BrowserResult.Success -> {
                        "HTML Source of ${browserResult.url} (Title: ${browserResult.title}):\n${browserResult.content}"
                    }
                    is BrowserResult.Error -> {
                        "Error extracting page source: ${browserResult.message}"
                    }
                }

                val isSuccess = !result.startsWith("Error")
                updateLog(sourceLog.id, if (isSuccess) "success" else "failed", if (isSuccess) "Extracted ${result.length} characters of HTML source" else result)
                result
            }
            "inspect_dom" -> {
                val targetSelector = args?.selector ?: args?.search ?: args?.query ?: "body"
                val domLog = createLog(
                    "Inspect DOM",
                    "thinking",
                    "Inspecting DOM structure for '$targetSelector'",
                    "web-clone"
                )
                addLog(domLog)
                setAgentStatus("Inspecting DOM element hierarchy: $targetSelector...")

                val result = when (val browserResult = backgroundBrowser.inspectDom(targetSelector)) {
                    is BrowserResult.Success -> {
                        "DOM Inspection for '$targetSelector':\n${browserResult.content}"
                    }
                    is BrowserResult.Error -> {
                        "Error inspecting DOM: ${browserResult.message}"
                    }
                }

                val isSuccess = !result.startsWith("Error")
                updateLog(domLog.id, if (isSuccess) "success" else "failed", if (isSuccess) "Inspected DOM node hierarchy for '$targetSelector'" else result)
                result
            }
            "inspect_css" -> {
                val targetSelector = args?.selector ?: args?.search ?: args?.query ?: "body"
                val cssLog = createLog(
                    "Inspect CSS",
                    "thinking",
                    "Extracting CSS rules and stylesheets for '$targetSelector'",
                    "web-clone"
                )
                addLog(cssLog)
                setAgentStatus("Inspecting CSS stylesheets & rules for $targetSelector...")

                val result = when (val browserResult = backgroundBrowser.inspectCss(targetSelector)) {
                    is BrowserResult.Success -> {
                        "CSS Inspection for '$targetSelector':\n${browserResult.content}"
                    }
                    is BrowserResult.Error -> {
                        "Error inspecting CSS: ${browserResult.message}"
                    }
                }

                val isSuccess = !result.startsWith("Error")
                updateLog(cssLog.id, if (isSuccess) "success" else "failed", if (isSuccess) "Extracted matched CSS rules for '$targetSelector'" else result)
                result
            }
            "get_computed_styles" -> {
                val targetSelector = args?.selector ?: args?.search ?: args?.query ?: "body"
                val properties = args?.properties ?: args?.query
                val styleLog = createLog(
                    "Get Computed Styles",
                    "thinking",
                    "Computing precise styles (colors, fonts, box model) for '$targetSelector'",
                    "web-clone"
                )
                addLog(styleLog)
                setAgentStatus("Computing exact CSS styles for $targetSelector...")

                val result = when (val browserResult = backgroundBrowser.getComputedStyles(targetSelector, properties)) {
                    is BrowserResult.Success -> {
                        "Computed Styles for '$targetSelector':\n${browserResult.content}"
                    }
                    is BrowserResult.Error -> {
                        "Error computing styles: ${browserResult.message}"
                    }
                }

                val isSuccess = !result.startsWith("Error")
                updateLog(styleLog.id, if (isSuccess) "success" else "failed", if (isSuccess) "Computed box model and design tokens for '$targetSelector'" else result)
                result
            }
            "take_screenshot" -> {
                val targetPath = normalizePath(if (!args?.path.isNullOrBlank()) args.path else "screenshots/web_preview.png")
                val shotLog = createLog(
                    "Take Web Screenshot",
                    "thinking",
                    "Capturing visual rendering of loaded web page -> $targetPath",
                    "web-clone"
                )
                addLog(shotLog)
                setAgentStatus("Capturing web page screenshot...")

                val bitmap = backgroundBrowser.captureScreenshot()
                val result = if (bitmap != null) {
                    try {
                        val stream = java.io.ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, stream)
                        val bytes = stream.toByteArray()
                        val base64Content = "data:image/png;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        repository.saveFile(project.name, targetPath, base64Content)
                        bitmap.recycle()
                        "Successfully captured screenshot (${bytes.size / 1024} KB) and saved to workspace: '$targetPath'"
                    } catch (e: Exception) {
                        "Error saving screenshot: ${e.localizedMessage}"
                    }
                } else {
                    "Error: Failed to capture WebView screenshot. Ensure page is loaded."
                }

                val isSuccess = result.startsWith("Successfully")
                updateLog(shotLog.id, if (isSuccess) "success" else "failed", result)
                result
            }
            "click" -> {
                val targetSelector = (args?.selector ?: args?.search ?: args?.query ?: "").trim()
                val clickLog = createLog(
                    "Click Element",
                    "thinking",
                    "Clicking element: '$targetSelector'",
                    "web-clone"
                )
                addLog(clickLog)
                setAgentStatus("Clicking element '$targetSelector'...")

                val result = if (targetSelector.isBlank()) {
                    "Error: 'selector' cannot be empty for click."
                } else {
                    when (val browserResult = backgroundBrowser.clickElement(targetSelector)) {
                        is BrowserResult.Success -> browserResult.content
                        is BrowserResult.Error -> "Error clicking element: ${browserResult.message}"
                    }
                }

                val isSuccess = !result.startsWith("Error")
                updateLog(clickLog.id, if (isSuccess) "success" else "failed", result.take(200))
                result
            }
            "type" -> {
                val targetSelector = (args?.selector ?: args?.search ?: args?.query ?: "").trim()
                val inputText = args?.text ?: args?.content ?: args?.message ?: ""
                val typeLog = createLog(
                    "Type Text",
                    "thinking",
                    "Typing into '$targetSelector': \"$inputText\"",
                    "web-clone"
                )
                addLog(typeLog)
                setAgentStatus("Typing into '$targetSelector'...")

                val result = if (targetSelector.isBlank()) {
                    "Error: 'selector' cannot be empty for type."
                } else {
                    when (val browserResult = backgroundBrowser.typeText(targetSelector, inputText)) {
                        is BrowserResult.Success -> browserResult.content
                        is BrowserResult.Error -> "Error typing: ${browserResult.message}"
                    }
                }

                val isSuccess = !result.startsWith("Error")
                updateLog(typeLog.id, if (isSuccess) "success" else "failed", result)
                result
            }
            "scroll" -> {
                val direction = args?.direction ?: args?.query ?: "down"
                val amount = args?.amount ?: 600
                val scrollLog = createLog(
                    "Scroll Web Page",
                    "thinking",
                    "Scrolling $direction by $amount px",
                    "web-clone"
                )
                addLog(scrollLog)
                setAgentStatus("Scrolling web page $direction...")

                val result = when (val browserResult = backgroundBrowser.scrollPage(direction, amount)) {
                    is BrowserResult.Success -> browserResult.content
                    is BrowserResult.Error -> "Error scrolling: ${browserResult.message}"
                }

                updateLog(scrollLog.id, "success", result)
                result
            }
            "get_links" -> {
                val linksLog = createLog(
                    "Get Links",
                    "thinking",
                    "Extracting all navigation and hyperlinks from page...",
                    "web-clone"
                )
                addLog(linksLog)
                setAgentStatus("Extracting links and navigation map...")

                val result = when (val browserResult = backgroundBrowser.getLinks()) {
                    is BrowserResult.Success -> {
                        "Page Links & Navigation Elements:\n${browserResult.content}"
                    }
                    is BrowserResult.Error -> {
                        "Error extracting links: ${browserResult.message}"
                    }
                }

                val isSuccess = !result.startsWith("Error")
                updateLog(linksLog.id, if (isSuccess) "success" else "failed", if (isSuccess) "Extracted links from active page" else result)
                result
            }
            "get_images" -> {
                val imagesLog = createLog(
                    "Get Images",
                    "thinking",
                    "Extracting image URLs, icons, SVGs, and visual assets...",
                    "web-clone"
                )
                addLog(imagesLog)
                setAgentStatus("Extracting images, icons & SVGs...")

                val result = when (val browserResult = backgroundBrowser.getImages()) {
                    is BrowserResult.Success -> {
                        "Page Images & Visual Assets:\n${browserResult.content}"
                    }
                    is BrowserResult.Error -> {
                        "Error extracting images: ${browserResult.message}"
                    }
                }

                val isSuccess = !result.startsWith("Error")
                updateLog(imagesLog.id, if (isSuccess) "success" else "failed", if (isSuccess) "Extracted images and visual media assets" else result)
                result
            }
            "get_fonts" -> {
                val fontsLog = createLog(
                    "Get Fonts & Typography",
                    "thinking",
                    "Extracting font families, weights, and Google Fonts...",
                    "web-clone"
                )
                addLog(fontsLog)
                setAgentStatus("Extracting fonts & typography tokens...")

                val result = when (val browserResult = backgroundBrowser.getFonts()) {
                    is BrowserResult.Success -> {
                        "Page Fonts & Typography Tokens:\n${browserResult.content}"
                    }
                    is BrowserResult.Error -> {
                        "Error extracting fonts: ${browserResult.message}"
                    }
                }

                val isSuccess = !result.startsWith("Error")
                updateLog(fontsLog.id, if (isSuccess) "success" else "failed", if (isSuccess) "Extracted font tokens" else result)
                result
            }
            "run_javascript", "execute_javascript", "eval_js" -> {
                val script = args?.script ?: args?.content ?: args?.command ?: args?.query ?: ""
                val jsLog = createLog(
                    "Run JavaScript",
                    "thinking",
                    "Executing JavaScript snippet in page context...",
                    "web-clone"
                )
                addLog(jsLog)
                setAgentStatus("Running JavaScript in browser context...")

                val result = if (script.isBlank()) {
                    "Error: 'script' argument cannot be empty."
                } else {
                    backgroundBrowser.runJavascript(script)
                }

                val isSuccess = !result.startsWith("Error")
                updateLog(jsLog.id, if (isSuccess) "success" else "failed", result.take(300))
                result
            }
            "compare_screenshot" -> {
                val targetImg = args?.targetImage ?: args?.path ?: args?.query ?: "screenshots/web_preview.png"
                val compLog = createLog(
                    "Compare Screenshot / UI Alignment",
                    "thinking",
                    "Comparing visual alignment with reference '$targetImg'",
                    "web-clone"
                )
                addLog(compLog)
                setAgentStatus("Comparing visual screenshots for UI/UX accuracy...")

                val files = repository.getFilesForProject(project.name)
                val targetFile = files.find { it.path == targetImg || it.path.endsWith(targetImg) }
                val result = if (targetFile != null) {
                    "Visual Reference '$targetImg' is loaded. Ready for UI/UX pixel-perfect comparison. Ensure color palette, font sizes, margins, responsive breakpoints and layout match the captured DOM and styles."
                } else {
                    "Reference screenshot '$targetImg' not found yet. Please run 'take_screenshot' first."
                }

                updateLog(compLog.id, "success", result)
                result
            }
            else -> "Error: Unknown tool '$tool'"
        }
    }
}
