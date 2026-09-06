package com.example.agent

import android.util.Log
import com.example.api.EditChunk
import com.example.api.ReadRangeItem
import com.example.api.ToolArguments
import org.json.JSONArray
import org.json.JSONObject

/**
 * MultiEditChunkParser
 * Robust, universal parser for multi_edit_file chunks across all AI models
 * (OpenAI, Claude, DeepSeek, Gemini, Qwen, Ollama, Groq, etc.).
 * Handles all naming variations, camelCase, snake_case, PascalCase, stringified JSON, and top-level fallbacks.
 */
object MultiEditChunkParser {
    private const val TAG = "MultiEditChunkParser"

    private val CHUNK_KEYS = listOf(
        "chunks", "replacementChunks", "replacement_chunks", "ReplacementChunks",
        "edits", "changes", "replacements", "patches", "diffs", "items", "edit_chunks"
    )

    private val SEARCH_KEYS = listOf(
        "search", "targetContent", "TargetContent", "target_content", "target",
        "find", "old", "oldContent", "OldContent", "old_content", "original",
        "from", "before", "match", "pattern", "source"
    )

    private val REPLACE_KEYS = listOf(
        "replace", "replacementContent", "ReplacementContent", "replacement_content",
        "replacement", "new", "newContent", "NewContent", "new_content",
        "to", "after", "with", "insert"
    )

    /**
     * Resolves the list of EditChunks from ToolArguments and optional raw fallback text.
     */
    fun resolveChunks(args: ToolArguments?, rawFallbackText: String? = null): List<EditChunk> {
        if (args != null) {
            val list = mutableListOf<EditChunk>()

            val candidates = listOfNotNull(
                args.chunks,
                args.replacementChunks,
                args.edits
            )

            for (candidate in candidates) {
                if (candidate.isNotEmpty()) {
                    val valid = candidate.filter { getEffectiveSearch(it).isNotBlank() }
                    if (valid.isNotEmpty()) {
                        return valid
                    }
                }
            }

            // Check if search/replace was provided directly at top-level
            val topSearch = args.search ?: ""
            val topReplace = args.replace ?: ""

            if (topSearch.isNotBlank()) {
                return listOf(EditChunk(search = topSearch, replace = topReplace))
            }
        }

        // Fallback to parsing from raw fallback text (XML, JSON, regex)
        if (!rawFallbackText.isNullOrBlank()) {
            return parseChunksFromRawText(rawFallbackText)
        }

        return emptyList()
    }

    /**
     * Parses EditChunk list from a JSONObject (e.g. from native tool_calls in CustomModelResponseParser).
     */
    fun parseChunksFromJsonObject(obj: JSONObject): List<EditChunk> {
        val result = mutableListOf<EditChunk>()

        // 1. Check known array keys
        for (key in CHUNK_KEYS) {
            if (obj.has(key)) {
                val value = obj.opt(key)
                when (value) {
                    is JSONArray -> {
                        for (i in 0 until value.length()) {
                            val item = value.opt(i)
                            if (item is JSONObject) {
                                val chunk = extractChunkFromObject(item)
                                if (chunk != null) result.add(chunk)
                            } else if (item is String) {
                                val chunk = extractChunkFromString(item)
                                if (chunk != null) result.add(chunk)
                            }
                        }
                    }
                    is String -> {
                        val parsed = parseChunksFromRawString(value)
                        result.addAll(parsed)
                    }
                    is JSONObject -> {
                        val chunk = extractChunkFromObject(value)
                        if (chunk != null) result.add(chunk)
                    }
                }
                if (result.isNotEmpty()) return result
            }
        }

        // 2. Check top-level search/replace
        val topSearch = extractFirstString(obj, SEARCH_KEYS)
        val topReplace = extractFirstString(obj, REPLACE_KEYS)
        if (!topSearch.isNullOrBlank()) {
            result.add(EditChunk(search = topSearch, replace = topReplace ?: ""))
            return result
        }

        return result
    }

    /**
     * Parses EditChunk list from a stringified JSON array or XML blocks.
     */
    fun parseChunksFromRawString(rawStr: String): List<EditChunk> {
        val trimmed = rawStr.trim()
        if (trimmed.isBlank()) return emptyList()

        try {
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val arr = JSONArray(trimmed)
                val list = mutableListOf<EditChunk>()
                for (i in 0 until arr.length()) {
                    val item = arr.opt(i)
                    if (item is JSONObject) {
                        val chunk = extractChunkFromObject(item)
                        if (chunk != null) list.add(chunk)
                    }
                }
                if (list.isNotEmpty()) return list
            } else if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val obj = JSONObject(trimmed)
                return parseChunksFromJsonObject(obj)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Direct JSON array parse failed, attempting regex extraction: ${e.message}")
        }

        return parseChunksFromRawText(trimmed)
    }

    /**
     * Regex/XML fallback for extracting chunks when JSON is malformed or in XML format.
     */
    fun parseChunksFromRawText(rawText: String): List<EditChunk> {
        val chunks = mutableListOf<EditChunk>()

        // 1. XML <chunk> or <replacementChunk> pattern
        val chunkBlockRegex = Regex("""<(?:chunk|replacement_chunk|replacementChunk|edit)>(.*?)</(?:chunk|replacement_chunk|replacementChunk|edit)>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val matches = chunkBlockRegex.findAll(rawText).toList()
        if (matches.isNotEmpty()) {
            for (match in matches) {
                val block = match.groupValues[1]
                val s = extractXmlValue(block, listOf("search", "targetContent", "target_content", "target", "find", "old"))
                val r = extractXmlValue(block, listOf("replace", "replacementContent", "replacement_content", "replacement", "new"))
                if (!s.isNullOrBlank()) {
                    chunks.add(EditChunk(search = s, replace = r ?: ""))
                }
            }
            if (chunks.isNotEmpty()) return chunks
        }

        // 2. Arg key / value pairs
        val searchInText = extractXmlValue(rawText, listOf("search", "targetContent", "target_content", "target", "find", "old"))
        val replaceInText = extractXmlValue(rawText, listOf("replace", "replacementContent", "replacement_content", "replacement", "new"))
        if (!searchInText.isNullOrBlank()) {
            chunks.add(EditChunk(search = searchInText, replace = replaceInText ?: ""))
            return chunks
        }

        return chunks
    }

    fun getEffectiveSearch(chunk: EditChunk): String {
        return chunk.search ?: chunk.targetContent ?: ""
    }

    fun getEffectiveReplace(chunk: EditChunk): String {
        return chunk.replace ?: chunk.replacementContent ?: ""
    }

    /**
     * Builds a complete, robust ToolArguments object from a JSONObject (e.g. from CustomModelResponseParser).
     */
    fun parseFullArguments(argsObj: JSONObject): ToolArguments {
        val path = argsObj.optString("path", "").ifBlank {
            argsObj.optString("targetFile", "").ifBlank {
                argsObj.optString("filePath", "").ifBlank {
                    argsObj.optString("file", "").ifBlank { null }
                }
            }
        }
        val targetFile = argsObj.optString("targetFile", "").ifBlank {
            argsObj.optString("TargetFile", "").ifBlank { null }
        }
        val content = argsObj.optString("content", "").ifBlank {
            argsObj.optString("code", "").ifBlank {
                argsObj.optString("text", "").ifBlank { null }
            }
        }
        val oldPath = argsObj.optString("oldPath", "").ifBlank {
            argsObj.optString("old_path", "").ifBlank { null }
        }
        val newPath = argsObj.optString("newPath", "").ifBlank {
            argsObj.optString("new_path", "").ifBlank { null }
        }
        val command = argsObj.optString("command", "").ifBlank {
            argsObj.optString("cmd", "").ifBlank { null }
        }
        val message = argsObj.optString("message", "").ifBlank {
            argsObj.optString("msg", "").ifBlank {
                argsObj.optString("reasoning", "").ifBlank { null }
            }
        }
        val search = extractFirstString(argsObj, SEARCH_KEYS)
        val replace = extractFirstString(argsObj, REPLACE_KEYS)
        val query = argsObj.optString("query", "").ifBlank { null }
        val destinationPath = argsObj.optString("destinationPath", "").ifBlank {
            argsObj.optString("destination_path", "").ifBlank {
                argsObj.optString("dest", "").ifBlank { null }
            }
        }
        val destinationSearch = argsObj.optString("destinationSearch", "").ifBlank {
            argsObj.optString("destination_search", "").ifBlank { null }
        }
        val lineRange = argsObj.optString("lineRange", "").ifBlank {
            argsObj.optString("line_range", "").ifBlank {
                argsObj.optString("range", "").ifBlank { null }
            }
        }
        val startLine = if (argsObj.has("startLine")) argsObj.optInt("startLine")
            else if (argsObj.has("start_line")) argsObj.optInt("start_line")
            else null
        val endLine = if (argsObj.has("endLine")) argsObj.optInt("endLine")
            else if (argsObj.has("end_line")) argsObj.optInt("end_line")
            else null

        val prompt = argsObj.optString("prompt", "").ifBlank { null }
        val width = if (argsObj.has("width")) argsObj.optInt("width") else null
        val height = if (argsObj.has("height")) argsObj.optInt("height") else null
        val format = argsObj.optString("format", "").ifBlank { null }

        // Extract chunks robustly
        val chunks = parseChunksFromJsonObject(argsObj).ifEmpty { null }

        // Extract ranges for multi_read_file
        val ranges = mutableListOf<ReadRangeItem>()
        val rangesArr = argsObj.optJSONArray("ranges") ?: argsObj.optJSONArray("rangeList")
        if (rangesArr != null) {
            for (i in 0 until rangesArr.length()) {
                val rObj = rangesArr.optJSONObject(i)
                if (rObj != null) {
                    val s = if (rObj.has("startLine")) rObj.optInt("startLine") else if (rObj.has("start_line")) rObj.optInt("start_line") else null
                    val e = if (rObj.has("endLine")) rObj.optInt("endLine") else if (rObj.has("end_line")) rObj.optInt("end_line") else null
                    val rg = rObj.optString("range", "").ifBlank { null }
                    ranges.add(ReadRangeItem(startLine = s, endLine = e, range = rg))
                }
            }
        }

        return ToolArguments(
            path = path,
            targetFile = targetFile,
            content = content,
            oldPath = oldPath,
            newPath = newPath,
            command = command,
            message = message,
            startLine = startLine,
            endLine = endLine,
            search = search,
            replace = replace,
            query = query,
            destinationPath = destinationPath,
            destinationSearch = destinationSearch,
            lineRange = lineRange,
            prompt = prompt,
            width = width,
            height = height,
            format = format,
            chunks = chunks,
            replacementChunks = chunks,
            ranges = if (ranges.isNotEmpty()) ranges else null,
            url = argsObj.optString("url", "").ifBlank { null },
            selector = argsObj.optString("selector", "").ifBlank { null },
            properties = argsObj.optString("properties", "").ifBlank { null },
            script = argsObj.optString("script", "").ifBlank { null },
            direction = argsObj.optString("direction", "").ifBlank { null },
            amount = if (argsObj.has("amount")) argsObj.optInt("amount") else null,
            text = argsObj.optString("text", "").ifBlank { null },
            targetImage = argsObj.optString("targetImage", "").ifBlank { null },
            mcpServerId = argsObj.optString("mcpServerId", "").ifBlank { null },
            mcpServerName = argsObj.optString("mcpServerName", "").ifBlank { null },
            toolName = argsObj.optString("toolName", "").ifBlank { null },
            mcpArgsJson = argsObj.optString("mcpArgsJson", "").ifBlank { null },
            resourceUri = argsObj.optString("resourceUri", "").ifBlank { null }
        )
    }

    private fun extractChunkFromObject(obj: JSONObject): EditChunk? {
        val s = extractFirstString(obj, SEARCH_KEYS)
        val r = extractFirstString(obj, REPLACE_KEYS)
        if (!s.isNullOrBlank()) {
            return EditChunk(search = s, replace = r ?: "")
        }
        return null
    }

    private fun extractChunkFromString(str: String): EditChunk? {
        val trimmed = str.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return try {
                val obj = JSONObject(trimmed)
                extractChunkFromObject(obj)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private fun extractFirstString(obj: JSONObject, keys: List<String>): String? {
        for (k in keys) {
            if (obj.has(k)) {
                val v = obj.optString(k, "")
                if (v.isNotEmpty()) return v
            }
        }
        return null
    }

    private fun extractXmlValue(xml: String, keys: List<String>): String? {
        for (k in keys) {
            val r = Regex("<$k>(.*?)</$k>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val m = r.find(xml)
            if (m != null) {
                return m.groupValues[1]
            }
        }
        return null
    }
}
