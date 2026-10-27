package com.example.agent

import android.util.Log
import com.example.api.ToolArguments
import com.example.api.ToolCallItem
import com.example.api.ToolCallResponse
import org.json.JSONObject

/**
 * DsmlToolCallEngine
 *
 * Comprehensive engine for detecting and parsing DeepSeek Markup Language (DSML)
 * and XML-style tool invocations produced by LLMs (DeepSeek V3/R1, Qwen, Ollama, OpenRouter, etc.).
 *
 * Handles:
 * - Double fullwidth vertical bar: <｜｜DSML｜｜ calls>, <｜｜DSML｜｜ invoke name="...">
 * - Single fullwidth vertical bar: <｜DSML｜ calls>, <｜DSML｜ invoke name="...">
 * - Standard double/single pipes: <||DSML|| calls>, <|DSML| calls>
 * - Plain XML invokes: <invoke name="...">, <tool_calls>, <function_calls>
 * - CDATA enclosed values and XML escaping
 * - Tool name normalization (e.g. read, view_file, readFile -> read_file)
 */
object DsmlToolCallEngine {

    private const val TAG = "DsmlToolCallEngine"

    /**
     * Checks if the text contains any DSML, invoke, or XML tool calling syntax.
     */
    fun hasDsmlOrXmlToolCalls(rawText: String): Boolean {
        if (rawText.isBlank()) return false
        val normalized = normalizePipes(rawText)
        return normalized.contains("DSML", ignoreCase = true) ||
                normalized.contains("<invoke", ignoreCase = true) ||
                normalized.contains("<tool_calls>", ignoreCase = true) ||
                normalized.contains("<tool_call>", ignoreCase = true) ||
                normalized.contains("<function_calls>", ignoreCase = true) ||
                (normalized.contains("invoke name=", ignoreCase = true) && normalized.contains("parameter", ignoreCase = true))
    }

    /**
     * Normalizes all Unicode pipe variations (\uFF5C, \u2502, etc.) to standard ASCII pipe.
     */
    fun normalizePipes(input: String): String {
        return input
            .replace('｜', '|') // \uFF5C Fullwidth vertical line
            .replace('│', '|') // \u2502 Box drawings light vertical
            .replace('┃', '|') // \u2503 Box drawings heavy vertical
            .replace('¦', '|') // \u00A6 Broken bar
    }

    /**
     * Parses DSML formatted text into a structured ToolCallResponse.
     */
    fun parse(rawText: String, finishReason: String? = null): ToolCallResponse? {
        if (!hasDsmlOrXmlToolCalls(rawText)) return null

        try {
            val normText = normalizePipes(rawText)
            val thoughtText = extractThought(rawText, normText)

            // Regex matching:
            // <[| ]*DSML[| ]* invoke name="..."> ... </[| ]*DSML[| ]* invoke>
            // or <invoke name="..."> ... </invoke>
            val invokeRegex = Regex(
                """<(?:\s*\|*\s*DSML\s*\|*\s*)?invoke\s+name=["']([^"']+)["'][^>]*>(.*?)</(?:\s*\|*\s*DSML\s*\|*\s*)?invoke>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )

            val matches = invokeRegex.findAll(normText).toList()
            if (matches.isEmpty()) {
                // Secondary check: self-closing or unclosed invoke tags
                return parseFallbackXmlInvokes(normText, thoughtText, finishReason)
            }

            val items = mutableListOf<ToolCallItem>()

            for (match in matches) {
                val rawToolName = match.groupValues[1].trim()
                val body = match.groupValues[2]

                // Extract parameters:
                // <[| ]*DSML[| ]* parameter name="KEY" ...>VALUE</[| ]*DSML[| ]* parameter>
                // or <parameter name="KEY" ...>VALUE</parameter>
                val paramRegex = Regex(
                    """<(?:\s*\|*\s*DSML\s*\|*\s*)?parameter\s+name=["']([^"']+)["'][^>]*>(.*?)</(?:\s*\|*\s*DSML\s*\|*\s*)?parameter>""",
                    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
                )

                val paramMap = mutableMapOf<String, String>()
                paramRegex.findAll(body).forEach { pMatch ->
                    val pName = pMatch.groupValues[1].trim()
                    var pValue = pMatch.groupValues[2].trim()

                    // Unwrap CDATA if present
                    if (pValue.startsWith("<![CDATA[") && pValue.endsWith("]]>")) {
                        pValue = pValue.removePrefix("<![CDATA[").removeSuffix("]]>").trim()
                    }

                    // If parameter contains nested JSON object (e.g. name="args" or name="arguments")
                    if ((pName.equals("args", ignoreCase = true) ||
                         pName.equals("arguments", ignoreCase = true) ||
                         pName.equals("parameters", ignoreCase = true) ||
                         pName.equals("input", ignoreCase = true)) &&
                         pValue.startsWith("{") && pValue.endsWith("}")) {
                        try {
                            val jsonObj = JSONObject(pValue)
                            val keys = jsonObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                paramMap[k] = jsonObj.optString(k, "")
                            }
                        } catch (_: Exception) {}
                    }

                    paramMap[pName] = pValue
                }

                // If no parameter tags were found, check if body is direct JSON
                if (paramMap.isEmpty() && body.trim().startsWith("{") && body.trim().endsWith("}")) {
                    try {
                        val jsonObj = JSONObject(body.trim())
                        val keys = jsonObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            paramMap[k] = jsonObj.optString(k, "")
                        }
                    } catch (_: Exception) {}
                }

                val normalizedTool = normalizeToolName(rawToolName)
                val toolArgs = buildToolArguments(paramMap)

                items.add(ToolCallItem(tool = normalizedTool, arguments = toolArgs))
            }

            if (items.isNotEmpty()) {
                Log.d(TAG, "Parsed ${items.size} DSML/XML tool call(s): ${items.map { it.tool }}")
                val primaryTool = items[0].tool
                val primaryArgs = items[0].arguments

                return ToolCallResponse(
                    thought = thoughtText ?: (if (items.size > 1) "Parsed ${items.size} batch tool calls." else "Parsed from DSML tool call."),
                    tool = primaryTool,
                    arguments = primaryArgs,
                    tools = items,
                    finishReason = finishReason
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing DSML tool call: ${e.message}", e)
        }

        return null
    }

    /**
     * Fallback for models emitting simpler XML tool calls without matching tags.
     */
    private fun parseFallbackXmlInvokes(normText: String, thoughtText: String?, finishReason: String?): ToolCallResponse? {
        val simpleInvokeRegex = Regex(
            """<invoke\s+name=["']([^"']+)["'][^>]*\s*(?:/>|>(.*?)(?:</invoke>|$))""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val simpleMatches = simpleInvokeRegex.findAll(normText).toList()
        if (simpleMatches.isEmpty()) return null

        val items = mutableListOf<ToolCallItem>()
        for (m in simpleMatches) {
            val toolName = normalizeToolName(m.groupValues[1].trim())
            val body = m.groupValues.getOrNull(2) ?: ""
            val paramMap = mutableMapOf<String, String>()

            val paramRegex = Regex("""<parameter\s+name=["']([^"']+)["'][^>]*>(.*?)</parameter>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            paramRegex.findAll(body).forEach { p ->
                paramMap[p.groupValues[1].trim()] = p.groupValues[2].trim()
            }

            items.add(ToolCallItem(tool = toolName, arguments = buildToolArguments(paramMap)))
        }

        if (items.isNotEmpty()) {
            return ToolCallResponse(
                thought = thoughtText ?: "Parsed ${items.size} XML tool calls.",
                tool = items[0].tool,
                arguments = items[0].arguments,
                tools = items,
                finishReason = finishReason
            )
        }
        return null
    }

    /**
     * Extracts model thoughts/reasoning preceding the tool calls or enclosed in <think> tags.
     */
    private fun extractThought(rawText: String, normText: String): String? {
        // 1. Check for <think>...</think>
        val thinkRegex = Regex("""<think>(.*?)</think>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val thinkMatch = thinkRegex.find(rawText)
        if (thinkMatch != null) {
            val t = thinkMatch.groupValues[1].trim()
            if (t.isNotBlank()) return t
        }

        // 2. Text before first DSML / invoke tag
        val firstTagIdx = findFirstToolTagIndex(normText)
        if (firstTagIdx > 0) {
            val prefix = normText.substring(0, firstTagIdx).replace(Regex("<[^>]+>"), "").trim()
            if (prefix.isNotBlank() && prefix.length > 3) {
                return prefix
            }
        }

        return null
    }

    private fun findFirstToolTagIndex(text: String): Int {
        val candidates = listOf("<|", "<DSML", "<invoke", "<tool_calls", "<tool_call", "<function_calls")
        var lowest = -1
        for (c in candidates) {
            val idx = text.indexOf(c, ignoreCase = true)
            if (idx != -1 && (lowest == -1 || idx < lowest)) {
                lowest = idx
            }
        }
        return lowest
    }

    /**
     * Normalizes various model aliases for tools to Pencode's standard tools.
     */
    fun normalizeToolName(raw: String): String {
        val lower = raw.trim().lowercase()
        return when {
            lower in setOf("read_file", "view_file", "read", "readfile", "open_file", "get_file", "show_file", "cat") -> "read_file"
            lower in setOf("read_file_range", "read_range", "view_range") -> "read_file_range"
            lower in setOf("multi_read_file", "multi_read", "batch_read") -> "multi_read_file"
            lower in setOf("write_to_file", "create_file", "write_file", "create", "write", "new_file", "make_file") -> "create_file"
            lower in setOf("replace_file_content", "edit_file", "edit", "patch_file", "modify_file", "update_file") -> "edit_file"
            lower in setOf("multi_replace_file_content", "multi_edit_file", "multi_edit", "multi_patch") -> "multi_edit_file"
            lower in setOf("delete_file", "remove_file", "delete", "rm") -> "delete_file"
            lower in setOf("list_dir", "scan_dir", "list_files", "list_directory", "ls", "dir") -> "list_dir"
            lower in setOf("run_command", "execute_command", "bash", "shell", "terminal", "exec", "cmd") -> "run_command"
            lower in setOf("grep", "search", "search_code", "find_in_files", "find") -> "grep"
            lower in setOf("compile_applet", "compile", "build", "trigger_build") -> "trigger_build"
            lower in setOf("complete", "finish", "done", "task_complete", "answer") -> "complete"
            else -> raw.trim()
        }
    }

    /**
     * Builds typed ToolArguments from parameter key-values.
     */
    private fun buildToolArguments(params: Map<String, String>): ToolArguments {
        val path = params["path"] ?: params["file"] ?: params["filePath"] ?: params["filename"] ?: params["targetFile"] ?: params["sourceFile"] ?: params["targetFilePath"]
        val content = params["content"] ?: params["code"] ?: params["text"] ?: params["body"] ?: params["file_content"]
        val search = params["search"] ?: params["targetContent"] ?: params["find"] ?: params["old"] ?: params["target"]
        val replace = params["replace"] ?: params["replacementContent"] ?: params["new"] ?: params["replacement"]
        val query = params["query"] ?: params["q"] ?: params["search_query"]
        val command = params["command"] ?: params["cmd"] ?: params["command_line"] ?: params["commandLine"]
        val message = params["message"] ?: params["msg"] ?: params["reasoning"] ?: params["summary"] ?: params["text"]
        val startLine = params["startLine"]?.toIntOrNull() ?: params["start_line"]?.toIntOrNull() ?: params["start"]?.toIntOrNull()
        val endLine = params["endLine"]?.toIntOrNull() ?: params["end_line"]?.toIntOrNull() ?: params["end"]?.toIntOrNull()
        val lineRange = params["lineRange"] ?: params["range"]
        val sourcePath = params["sourcePath"] ?: params["sourceFile"] ?: params["fromPath"] ?: params["oldPath"]
        val targetPath = params["targetPath"] ?: params["targetFile"] ?: params["toPath"] ?: params["newPath"] ?: params["destinationPath"] ?: path
        val codeChunk = params["codeChunk"] ?: params["codeBlock"] ?: params["chunk"] ?: params["block"] ?: params["sourceBlock"]
        val targetAnchor = params["targetAnchor"] ?: params["anchor"]
        val insertAt = params["insertAt"] ?: params["position"]
        val isMove = params["isMove"]?.toBooleanStrictOrNull() ?: params["move"]?.toBooleanStrictOrNull()
        val deleteAllOccurrences = params["deleteAllOccurrences"]?.toBooleanStrictOrNull() ?: params["deleteAll"]?.toBooleanStrictOrNull()
        val url = params["url"] ?: params["link"] ?: params["targetUrl"]
        val selector = params["selector"] ?: params["css"]
        val prompt = params["prompt"]
        val width = params["width"]?.toIntOrNull()
        val height = params["height"]?.toIntOrNull()
        val format = params["format"]
        val action = params["action"]
        val elementIndex = params["elementIndex"]?.toIntOrNull()

        return ToolArguments(
            path = path,
            targetFile = targetPath ?: path,
            content = content,
            code = content,
            search = search,
            replace = replace,
            query = query,
            command = command,
            message = message,
            startLine = startLine,
            endLine = endLine,
            lineRange = lineRange,
            sourcePath = sourcePath,
            sourceFile = sourcePath,
            targetPath = targetPath,
            destinationPath = targetPath,
            codeChunk = codeChunk,
            codeBlock = codeChunk,
            targetAnchor = targetAnchor,
            anchor = targetAnchor,
            insertAt = insertAt,
            position = insertAt,
            isMove = isMove,
            deleteAllOccurrences = deleteAllOccurrences,
            url = url,
            selector = selector,
            prompt = prompt,
            width = width,
            height = height,
            format = format,
            action = action,
            elementIndex = elementIndex
        )
    }
}
