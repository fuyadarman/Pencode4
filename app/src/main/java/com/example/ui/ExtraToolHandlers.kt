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
            else -> "Error: Unknown tool '$tool'"
        }
    }
}
