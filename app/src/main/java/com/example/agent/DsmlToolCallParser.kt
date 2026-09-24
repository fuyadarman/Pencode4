package com.example.agent

import android.util.Log
import com.example.api.ToolArguments
import com.example.api.ToolCallItem
import com.example.api.ToolCallResponse

/**
 * DsmlToolCallParser
 *
 * Robust, production parser for DeepSeek Markup Language (DSML) and various XML-like tool call outputs.
 * Solves the issue where models (e.g., DeepSeek R1/V3, Ollama, Qwen) output:
 *
 * <｜DSML｜ calls>
 * <｜DSML｜ invoke name="scan_dir">
 * <｜DSML｜ parameter name="path" string="true">cloned_ui</｜DSML｜ parameter>
 * </｜DSML｜ invoke>
 * <｜DSML｜ invoke name="read_file">
 * <｜DSML｜ parameter name="path" string="true">index.html</｜DSML｜ parameter>
 * </｜DSML｜ invoke>
 * </｜DSML｜ calls>
 *
 * Supports:
 * - Fullwidth vertical bar `｜` (\uFF5C) and standard ASCII pipe `|` (\u007C)
 * - Single and multiple tool invocations in a single turn
 * - Extraction of thought / reasoning preceding or surrounding the tool tags
 * - Parameter typing (string, integer, boolean, JSON)
 */
object DsmlToolCallParser {

    private const val TAG = "DsmlToolCallParser"

    // Normalizes various pipe symbols (fullwidth \uFF5C and standard |) to standard pipe
    private fun normalizePipes(input: String): String {
        return input.replace('｜', '|')
    }

    /**
     * Checks if the raw text contains DSML or invoke tags.
     */
    fun isDsmlOrXmlToolCall(rawText: String): Boolean {
        val norm = normalizePipes(rawText)
        return norm.contains("<|DSML| calls>", ignoreCase = true) ||
                norm.contains("<|DSML| invoke", ignoreCase = true) ||
                norm.contains("<|tool_calls|>", ignoreCase = true) ||
                norm.contains("<tool_calls>", ignoreCase = true) ||
                (norm.contains("<invoke name=", ignoreCase = true) && norm.contains("<parameter name=", ignoreCase = true))
    }

    /**
     * Parses DSML formatted text into a structured ToolCallResponse.
     * Returns null if no valid invocations were found.
     */
    fun parse(rawText: String, finishReason: String? = null): ToolCallResponse? {
        if (!isDsmlOrXmlToolCall(rawText)) return null

        try {
            val normText = normalizePipes(rawText)

            // Extract thought / thinking from before the calls or inside <think> tags
            val thoughtText = extractThought(rawText, normText)

            val items = mutableListOf<ToolCallItem>()

            // Regex matching <|DSML| invoke name="TOOL_NAME"> ... </|DSML| invoke>
            // or <invoke name="TOOL_NAME"> ... </invoke>
            val invokeRegex = Regex(
                """<(?:\|DSML\|\s*)?invoke\s+name=["']([^"']+)["'][^>]*>(.*?)</(?:\|DSML\|\s*)?invoke>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )

            val matches = invokeRegex.findAll(normText).toList()

            for (match in matches) {
                val toolName = match.groupValues[1].trim()
                val body = match.groupValues[2]

                // Extract parameters: <|DSML| parameter name="KEY" ...>VALUE</|DSML| parameter>
                // or <parameter name="KEY" ...>VALUE</parameter>
                val paramRegex = Regex(
                    """<(?:\|DSML\|\s*)?parameter\s+name=["']([^"']+)["'][^>]*>(.*?)</(?:\|DSML\|\s*)?parameter>""",
                    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
                )

                val paramMap = mutableMapOf<String, String>()
                paramRegex.findAll(body).forEach { pMatch ->
                    val pName = pMatch.groupValues[1].trim()
                    val pValue = pMatch.groupValues[2].trim()
                    paramMap[pName] = pValue
                }

                val toolArgs = buildToolArguments(paramMap)
                items.add(ToolCallItem(tool = toolName, arguments = toolArgs))
            }

            if (items.isNotEmpty()) {
                Log.d(TAG, "Successfully parsed ${items.size} DSML tool invocation(s): ${items.map { it.tool }}")
                return if (items.size == 1) {
                    ToolCallResponse(
                        thought = thoughtText ?: "Parsed from DSML tool call.",
                        tool = items[0].tool,
                        arguments = items[0].arguments,
                        tools = items,
                        finishReason = finishReason
                    )
                } else {
                    ToolCallResponse(
                        thought = thoughtText ?: "Parsed ${items.size} DSML batch tool calls.",
                        tool = items[0].tool,
                        arguments = items[0].arguments,
                        tools = items,
                        finishReason = finishReason
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing DSML tool call: ${e.message}", e)
        }

        return null
    }

    private fun extractThought(rawText: String, normText: String): String? {
        // Check for <think>...</think>
        val thinkRegex = Regex("""<think>(.*?)</think>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val thinkMatch = thinkRegex.find(rawText)
        if (thinkMatch != null) {
            val t = thinkMatch.groupValues[1].trim()
            if (t.isNotBlank()) return t
        }

        // Text before the first DSML/invoke tag
        val firstTagIdx = normText.indexOfAny(listOf("<|DSML| calls>", "<|DSML| invoke", "<tool_calls>", "<invoke"))
        if (firstTagIdx > 0) {
            val prefix = normText.substring(0, firstTagIdx).replace(Regex("<[^>]+>"), "").trim()
            if (prefix.isNotBlank() && prefix.length > 5) {
                return prefix
            }
        }

        return null
    }

    private fun buildToolArguments(params: Map<String, String>): ToolArguments {
        val path = params["path"] ?: params["file"] ?: params["filePath"] ?: params["targetFile"] ?: params["sourceFile"]
        val content = params["content"] ?: params["code"] ?: params["text"]
        val search = params["search"] ?: params["targetContent"] ?: params["find"] ?: params["old"]
        val replace = params["replace"] ?: params["replacementContent"] ?: params["new"]
        val query = params["query"] ?: params["q"]
        val command = params["command"] ?: params["cmd"]
        val message = params["message"] ?: params["msg"] ?: params["reasoning"]
        val startLine = params["startLine"]?.toIntOrNull() ?: params["start_line"]?.toIntOrNull()
        val endLine = params["endLine"]?.toIntOrNull() ?: params["end_line"]?.toIntOrNull()
        val lineRange = params["lineRange"] ?: params["range"]
        val sourcePath = params["sourcePath"] ?: params["sourceFile"] ?: params["fromPath"] ?: params["oldPath"]
        val targetPath = params["targetPath"] ?: params["targetFile"] ?: params["toPath"] ?: params["newPath"] ?: params["destinationPath"]
        val codeChunk = params["codeChunk"] ?: params["codeBlock"] ?: params["chunk"] ?: params["block"] ?: params["sourceBlock"]
        val targetAnchor = params["targetAnchor"] ?: params["anchor"]
        val insertAt = params["insertAt"] ?: params["position"]
        val isMove = params["isMove"]?.toBooleanStrictOrNull() ?: params["move"]?.toBooleanStrictOrNull()
        val deleteAllOccurrences = params["deleteAllOccurrences"]?.toBooleanStrictOrNull() ?: params["deleteAll"]?.toBooleanStrictOrNull()
        val url = params["url"] ?: params["link"] ?: params["targetUrl"]
        val targetFilePath = params["targetFilePath"] ?: params["targetFile"] ?: params["path"]
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
