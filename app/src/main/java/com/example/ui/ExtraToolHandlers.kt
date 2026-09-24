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
        normalizePath: (String) -> String,
        activeSkills: List<AgentSkill> = emptyList(),
        gitToken: String? = null
    ): String {
        return when (tool) {
            "generate_image", "pollinations_image", "create_image", "generate_logo", "create_logo" -> {
                val isLogo = tool == "generate_logo" || tool == "create_logo" || args?.isLogo == true
                val filePath = normalizePath(if (!args?.path.isNullOrBlank()) args.path else if (!args?.targetFile.isNullOrBlank()) args.targetFile else if (isLogo) "assets/logo.png" else "assets/image.png")
                val imagePrompt = args?.prompt ?: args?.query ?: args?.message ?: if (isLogo) "modern sleek app logo icon" else "beautiful abstract digital art"
                val width = args?.width ?: if (isLogo) 512 else 1024
                val height = args?.height ?: if (isLogo) 512 else 1024

                val genLog = createLog(
                    if (isLogo) "Generate Logo" else "Generate image",
                    "thinking",
                    "Generating: \"$imagePrompt\" ($width x $height)",
                    "pollinations"
                )
                addLog(genLog)
                setAgentStatus("Generating ${if (isLogo) "logo" else "image"} with Pollinations AI...")

                val result = com.example.agent.PollinationsImageGenerationEngine.generateImageOrLogo(
                    projectName = project.name,
                    prompt = imagePrompt,
                    targetPath = filePath,
                    width = width,
                    height = height,
                    isLogo = isLogo,
                    repository = repository,
                    normalizePath = normalizePath
                )

                val isSuccess = result.startsWith("Successfully")
                updateLog(genLog.id, if (isSuccess) "success" else "failed", result)
                result
            }
            "copy_file", "duplicate_code" -> {
                val srcPath = args?.path ?: args?.sourcePath ?: args?.oldPath ?: ""
                val dstPath = args?.destinationPath ?: args?.targetFile ?: args?.newPath ?: ""
                val copyLog = createLog(
                    "Copy File",
                    "thinking",
                    "Copying '$srcPath' to '$dstPath'",
                    null
                )
                addLog(copyLog)
                setAgentStatus("Copying file to $dstPath...")

                val result = com.example.agent.ExtendedFileOperationsEngine.copyFile(
                    projectName = project.name,
                    sourcePath = srcPath,
                    destinationPath = dstPath,
                    repository = repository,
                    normalizePath = normalizePath
                )

                val isSuccess = result.startsWith("Successfully")
                updateLog(copyLog.id, if (isSuccess) "success" else "failed", result)
                result
            }
            "duplicate_file" -> {
                val srcPath = args?.path ?: args?.sourcePath ?: ""
                val dupLog = createLog(
                    "Duplicate File",
                    "thinking",
                    "Creating duplicates of '$srcPath'",
                    null
                )
                addLog(dupLog)
                setAgentStatus("Duplicating file $srcPath...")

                val result = com.example.agent.ExtendedFileOperationsEngine.duplicateFile(
                    projectName = project.name,
                    sourcePath = srcPath,
                    targetPaths = args?.targetPaths,
                    count = args?.count,
                    repository = repository,
                    normalizePath = normalizePath
                )

                val isSuccess = result.startsWith("Successfully")
                updateLog(dupLog.id, if (isSuccess) "success" else "failed", result)
                result
            }
            "clone_git_repo", "clone_github_repo", "clone_repo", "git_clone" -> {
                val rawInput = (args?.url ?: args?.query ?: args?.message ?: args?.path ?: args?.search ?: "").trim()
                // Extract GitHub URL or owner/repo from potential natural language instructions
                val repoUrl = if (rawInput.contains("github.com/")) {
                    val urlMatch = Regex("""https?://github\.com/[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+""").find(rawInput)?.value
                    urlMatch ?: rawInput
                } else if (rawInput.contains("/") && !rawInput.contains(" ")) {
                    rawInput
                } else {
                    val slugMatch = Regex("""\b([a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+)\b""").find(rawInput)?.value
                    slugMatch ?: rawInput
                }
                val targetBranch = args?.theme ?: args?.category ?: args?.solution ?: args?.issue ?: args?.filter ?: args?.instructions
                val cloneLog = createLog(
                    "Clone GitHub Repository",
                    "thinking",
                    "Cloning repository: $repoUrl",
                    "git-clone"
                )
                addLog(cloneLog)
                setAgentStatus("Cloning GitHub repository $repoUrl...")

                val result = com.example.git.GitRepositoryCloneEngine.cloneGitHubRepository(
                    repoInput = repoUrl,
                    projectName = project.name,
                    branch = targetBranch,
                    token = gitToken,
                    repository = repository,
                    progressCallback = { status ->
                        setAgentStatus(status)
                    }
                )

                val isSuccess = result.startsWith("Successfully")
                updateLog(cloneLog.id, if (isSuccess) "success" else "failed", result)
                result
            }
            "clone_web_ui", "scrape_web_ui" -> {
                val targetUrl = (args?.url ?: args?.query ?: args?.message ?: "").trim()
                val targetFile = args?.targetFile ?: args?.destinationPath ?: args?.path
                val cloneLog = createLog(
                    "Clone Web UI",
                    "thinking",
                    "Cloning complete website UI (HTML, CSS, JS, assets) from: $targetUrl",
                    "web-clone"
                )
                addLog(cloneLog)
                setAgentStatus("Cloning complete website UI from $targetUrl...")

                val result = com.example.agent.WebsiteUiCloneEngine.cloneFullWebsite(
                    url = targetUrl,
                    targetFilePath = targetFile,
                    projectName = project.name,
                    repository = repository,
                    backgroundBrowser = backgroundBrowser,
                    normalizePath = normalizePath
                )

                val isSuccess = result.startsWith("Successfully")
                updateLog(cloneLog.id, if (isSuccess) "success" else "failed", result.take(300))
                result
            }
            "fetch_url", "read_url", "scrape_url" -> {
                val targetUrl = (args?.url ?: args?.query ?: args?.message ?: "").trim()
                val targetFile = args?.targetFile ?: args?.path
                val fetchLog = createLog(
                    "Fetch Web URL",
                    "thinking",
                    "Fetching web page content: $targetUrl",
                    "background-browser"
                )
                addLog(fetchLog)
                setAgentStatus("Fetching remote URL content: $targetUrl...")

                val result = com.example.agent.WebScraperAndUiCloneEngine.fetchUrlContent(
                    url = targetUrl,
                    projectName = project.name,
                    repository = repository,
                    saveToWorkspace = true,
                    targetFile = targetFile,
                    normalizePath = normalizePath
                )

                val isSuccess = result.startsWith("Successfully")
                updateLog(fetchLog.id, if (isSuccess) "success" else "failed", result.take(250))
                result
            }
            "skill_check", "list_skills", "inspect_skill" -> {
                val queryVal = args?.query ?: args?.prompt ?: args?.message
                val skillLog = createLog(
                    "Skill Check",
                    "thinking",
                    "Checking available skills for '${queryVal ?: "all"}'",
                    "skills"
                )
                addLog(skillLog)

                val result = com.example.agent.SkillCheckAndExecutionEngine.performSkillCheck(
                    query = queryVal,
                    activeSkills = activeSkills
                )

                updateLog(skillLog.id, "success", "Checked skills: ${result.take(150)}...")
                result
            }
            "ask_user", "ask_question", "clarify_with_user" -> {
                val question = args?.question ?: args?.message ?: args?.prompt ?: args?.query ?: "Could you please clarify your request?"
                val options = args?.options
                val askLog = createLog(
                    "Ask User Clarification",
                    "thinking",
                    question,
                    "user-input"
                )
                addLog(askLog)
                setAgentStatus("Waiting for user answer: $question")

                val formattedQuestion = com.example.agent.InteractiveUserClarificationEngine.formatClarificationQuestion(
                    question = question,
                    options = options
                )

                updateLog(askLog.id, "success", "Asked user: $formattedQuestion")
                "[USER QUESTION PROMPT]: $formattedQuestion\n\n(Agent paused awaiting user input. Once answered, continuation will proceed automatically.)"
            }
            "resize_image", "scale_image", "image_resize", "compress_image" -> {
                val sourcePath = normalizePath(args?.path ?: args?.targetFile ?: args?.sourcePath ?: args?.targetImage ?: "")
                val destPath = if (!args?.destinationPath.isNullOrBlank()) normalizePath(args.destinationPath) else if (!args?.newPath.isNullOrBlank()) normalizePath(args.newPath) else sourcePath
                val targetWidth = args?.width
                val targetHeight = args?.height
                val outputFormatStr = args?.format

                val resizeLog = createLog(
                    "Resize image",
                    "thinking",
                    "Resizing $sourcePath to $destPath (${targetWidth ?: "auto"} x ${targetHeight ?: "auto"})",
                    "image-resize"
                )
                addLog(resizeLog)
                setAgentStatus("Resizing image $sourcePath...")

                val result = com.example.agent.ImageResizeEngine.resizeImage(
                    projectName = project.name,
                    sourcePath = sourcePath,
                    destinationPath = destPath,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    format = outputFormatStr,
                    maintainAspectRatio = true,
                    quality = 90,
                    repository = repository,
                    normalizePath = normalizePath
                )

                val isSuccess = result.startsWith("Successfully")
                updateLog(resizeLog.id, if (isSuccess) "success" else "failed", result)
                result
            }
            "browser_search", "web_search", "online_search", "search_web", "google_search", "websearch", "internet_search", "search" -> {
                val queryVal = (args?.query ?: args?.message ?: args?.search ?: args?.url ?: "").trim()
                val isSearchTitle = tool.contains("web") || tool.contains("search") || tool.contains("google")
                val searchLog = createLog(
                    if (isSearchTitle) "Searched the web" else "Browser search/navigate",
                    "thinking",
                    "Searching: $queryVal",
                    "background-browser"
                )
                addLog(searchLog)
                setAgentStatus("Searching the web for: $queryVal...")

                val result = if (queryVal.isBlank()) {
                    "Error: 'query' argument cannot be empty. Please provide a search term or a URL."
                } else {
                    val isUrl = queryVal.startsWith("http://") || queryVal.startsWith("https://") || (queryVal.contains(".") && !queryVal.contains(" "))
                    if (isUrl) {
                        when (val browserResult = backgroundBrowser.navigate(queryVal)) {
                            is BrowserResult.Success -> {
                                "Successfully loaded page: ${browserResult.url}\nTitle: ${browserResult.title}\n\nContent Summary:\n${browserResult.content}"
                            }
                            is BrowserResult.Error -> {
                                "Error performing browser action: ${browserResult.message}"
                            }
                        }
                    } else {
                        // High-speed, crash-proof search via SafeWebSearchEngine
                        com.example.browser.SafeWebSearchEngine.performWebSearch(queryVal)
                    }
                }

                val isSuccess = !result.startsWith("Error")
                val logDetails = if (isSuccess && !result.startsWith("Query:")) {
                    "Query: $queryVal\n\n$result"
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

                updateLog(deleteLog.id, if (result.startsWith("Successfully")) "success" else "failed", result)
                result
            }
            "rename_file", "rename" -> {
                val oldPath = normalizePath(
                    if (!args?.oldPath.isNullOrBlank()) args.oldPath
                    else if (!args?.sourcePath.isNullOrBlank()) args.sourcePath
                    else args?.path ?: ""
                )
                val newPath = normalizePath(
                    if (!args?.newPath.isNullOrBlank()) args.newPath
                    else if (!args?.destinationPath.isNullOrBlank()) args.destinationPath
                    else args?.targetPath ?: ""
                )
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
                    "Error renaming file: ${e.localizedMessage ?: e.javaClass.simpleName}"
                }

                updateLog(renameLog.id, if (result.startsWith("Successfully")) "success" else "failed", result)
                result
            }
            "move_file", "move" -> {
                val oldPath = normalizePath(
                    if (!args?.oldPath.isNullOrBlank()) args.oldPath
                    else if (!args?.sourcePath.isNullOrBlank()) args.sourcePath
                    else args?.path ?: ""
                )
                val newPath = normalizePath(
                    if (!args?.newPath.isNullOrBlank()) args.newPath
                    else if (!args?.destinationPath.isNullOrBlank()) args.destinationPath
                    else args?.targetPath ?: ""
                )
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
                    "Error moving file: ${e.localizedMessage ?: e.javaClass.simpleName}"
                }

                updateLog(moveLog.id, if (result.startsWith("Successfully")) "success" else "failed", result)
                result
            }
            "transfer_code_chunk", "copy_code_chunk", "move_code_chunk", "copy_code_block", "move_code_block",
            "copy_chunk", "move_chunk", "transfer_chunk", "copy_block", "move_block", "transfer_block", "transfer_code_block", "cut_code_chunk", "cut_code_block" -> {
                val isMoveCall = tool.contains("move") || tool.contains("cut") || args?.isMove == true
                val sourcePath = if (!args?.sourcePath.isNullOrBlank()) args.sourcePath 
                    else if (!args?.sourceFile.isNullOrBlank()) args.sourceFile
                    else if (!args?.fromPath.isNullOrBlank()) args.fromPath
                    else if (!args?.oldPath.isNullOrBlank()) args.oldPath 
                    else args?.path ?: ""
                val targetPath = if (!args?.targetPath.isNullOrBlank()) args.targetPath 
                    else if (!args?.targetFile.isNullOrBlank()) args.targetFile
                    else if (!args?.toPath.isNullOrBlank()) args.toPath
                    else if (!args?.newPath.isNullOrBlank()) args.newPath 
                    else args?.destinationPath ?: ""
                val chunk = if (!args?.codeChunk.isNullOrBlank()) args.codeChunk 
                    else if (!args?.codeBlock.isNullOrBlank()) args.codeBlock
                    else if (!args?.chunk.isNullOrBlank()) args.chunk
                    else if (!args?.block.isNullOrBlank()) args.block
                    else if (!args?.code.isNullOrBlank()) args.code
                    else if (!args?.content.isNullOrBlank()) args.content
                    else if (!args?.sourceBlock.isNullOrBlank()) args.sourceBlock 
                    else args?.search ?: ""

                val actionName = if (isMoveCall) "Moved code block" else "Copied code block"
                val transferLog = createLog(
                    actionName,
                    "thinking",
                    "${if (isMoveCall) "Moving" else "Copying"} code block from '$sourcePath' to '$targetPath'",
                    null
                )
                addLog(transferLog)

                val result = when (val res = com.example.agent.CodeBlockOperationsEngine.transferBlock(
                    sourcePath = sourcePath,
                    targetPath = targetPath,
                    codeChunk = chunk.ifBlank { null },
                    startLine = args?.startLine,
                    endLine = args?.endLine,
                    targetAnchor = if (!args?.targetAnchor.isNullOrBlank()) args.targetAnchor else args?.anchor ?: args?.destinationSearch,
                    insertAt = if (!args?.insertAt.isNullOrBlank()) args.insertAt else args?.position,
                    isMove = isMoveCall,
                    createTargetIfMissing = true,
                    project = project,
                    repository = repository,
                    normalizePath = normalizePath
                )) {
                    is com.example.agent.CodeBlockOperationsEngine.OperationResult.Success -> res.message
                    is com.example.agent.CodeBlockOperationsEngine.OperationResult.Failure -> res.errorMessage
                }

                updateLog(transferLog.id, if (result.startsWith("Successfully")) "success" else "failed", result)
                result
            }
            "delete_code_chunk", "delete_code_block", "remove_code_chunk", "remove_code_block",
            "delete_chunk", "delete_block", "remove_chunk", "remove_block" -> {
                val filePath = if (!args?.path.isNullOrBlank()) args.path 
                    else if (!args?.filePath.isNullOrBlank()) args.filePath
                    else if (!args?.targetFile.isNullOrBlank()) args.targetFile 
                    else if (!args?.file.isNullOrBlank()) args.file
                    else args?.sourcePath ?: ""
                val chunk = if (!args?.codeChunk.isNullOrBlank()) args.codeChunk 
                    else if (!args?.codeBlock.isNullOrBlank()) args.codeBlock
                    else if (!args?.chunk.isNullOrBlank()) args.chunk
                    else if (!args?.block.isNullOrBlank()) args.block
                    else if (!args?.code.isNullOrBlank()) args.code
                    else if (!args?.content.isNullOrBlank()) args.content
                    else if (!args?.sourceBlock.isNullOrBlank()) args.sourceBlock 
                    else args?.search ?: ""
                val deleteAll = args?.deleteAllOccurrences ?: false

                val deleteLog = createLog(
                    "Deleted code block",
                    "thinking",
                    "Deleting code block from '$filePath'",
                    null
                )
                addLog(deleteLog)

                val result = when (val res = com.example.agent.CodeBlockOperationsEngine.deleteBlock(
                    filePath = filePath,
                    codeChunk = chunk.ifBlank { null },
                    startLine = args?.startLine,
                    endLine = args?.endLine,
                    deleteAllOccurrences = deleteAll,
                    project = project,
                    repository = repository,
                    normalizePath = normalizePath
                )) {
                    is com.example.agent.CodeBlockOperationsEngine.OperationResult.Success -> res.message
                    is com.example.agent.CodeBlockOperationsEngine.OperationResult.Failure -> res.errorMessage
                }

                updateLog(deleteLog.id, if (result.startsWith("Successfully")) "success" else "failed", result)
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
            "browser_snapshot", "browser_inspect_interactive", "inspect_interactive", "browser_elements" -> {
                val snapLog = createLog(
                    "Browser Interactive Snapshot",
                    "thinking",
                    "Scanning clickable, typable, and form elements on page...",
                    "web-clone"
                )
                addLog(snapLog)
                setAgentStatus("Scanning interactive elements on webpage...")

                val result = com.example.browser.BrowserControllerAgentEngine.getInteractiveSnapshot(backgroundBrowser)
                updateLog(snapLog.id, "success", "Captured interactive elements snapshot.")
                result
            }
            "browser_controller", "browser_interact", "browser_action" -> {
                val act = args?.action ?: if (!args?.text.isNullOrBlank()) "type" else if (args?.elementIndex != null || !args?.selector.isNullOrBlank()) "click" else "scroll"
                val actLog = createLog(
                    "Browser Controller Action",
                    "thinking",
                    "Executing browser action: $act",
                    "web-clone"
                )
                addLog(actLog)
                setAgentStatus("Performing browser action: $act...")

                val result = com.example.browser.BrowserControllerAgentEngine.executeBrowserAction(
                    action = act,
                    selector = args?.selector,
                    elementIndex = args?.elementIndex,
                    text = args?.text ?: args?.content,
                    clearBefore = args?.clearBefore ?: true,
                    pressEnter = args?.pressEnter ?: false,
                    direction = args?.direction,
                    amount = args?.amount,
                    backgroundBrowser = backgroundBrowser
                )

                val isSuccess = !result.startsWith("Error")
                updateLog(actLog.id, if (isSuccess) "success" else "failed", result)
                result
            }
            "deep_clone_web_ui" -> {
                val targetUrl = (args?.url ?: args?.query ?: args?.message ?: "").trim()
                val targetFile = args?.targetFile ?: args?.destinationPath ?: args?.path
                val cloneLog = createLog(
                    "Deep Clone Web UI",
                    "thinking",
                    "Deeply cloning complete website UI (HTML, CSS, JS, assets) from: $targetUrl",
                    "web-clone"
                )
                addLog(cloneLog)
                setAgentStatus("Deeply cloning UI from $targetUrl...")

                val result = com.example.agent.WebsiteUiCloneEngine.cloneFullWebsite(
                    url = targetUrl,
                    targetFilePath = targetFile,
                    projectName = project.name,
                    repository = repository,
                    backgroundBrowser = backgroundBrowser,
                    normalizePath = normalizePath
                )

                val isSuccess = result.startsWith("Successfully")
                updateLog(cloneLog.id, if (isSuccess) "success" else "failed", result.take(300))
                result
            }
            else -> "Error: Unknown tool '$tool'"
        }
    }
}
