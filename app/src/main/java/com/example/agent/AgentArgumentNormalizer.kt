package com.example.agent

import com.example.api.ToolArguments
import org.json.JSONObject

/**
 * AgentArgumentNormalizer
 * Normalizes tool arguments across all LLM conventions (AI Studio, Claude, OpenAI, DeepSeek, Ollama).
 * Guarantees that search, replace, path, and content are extracted from any alias key.
 */
object AgentArgumentNormalizer {

    private val SEARCH_ALIASES = listOf(
        "search", "TargetContent", "targetContent", "target_content", "old_string",
        "old_str", "old_text", "search_block", "searchStr", "oldContent", "OldContent",
        "old_content", "find", "pattern", "match", "before", "sourceBlock", "codeChunk"
    )

    private val REPLACE_ALIASES = listOf(
        "replace", "ReplacementContent", "replacementContent", "replacement_content",
        "new_string", "new_str", "new_text", "replace_block", "replaceStr",
        "newContent", "NewContent", "new_content", "after", "with", "content", "code"
    )

    private val PATH_ALIASES = listOf(
        "path", "targetFile", "TargetFile", "target_file", "filePath", "file_path",
        "file", "name", "targetPath", "target_path"
    )

    fun resolveSearch(args: ToolArguments?): String {
        if (args == null) return ""
        if (!args.search.isNullOrEmpty()) return args.search
        if (!args.targetContentPascal.isNullOrEmpty()) return args.targetContentPascal
        if (!args.targetContent.isNullOrEmpty()) return args.targetContent
        if (!args.old_string.isNullOrEmpty()) return args.old_string
        if (!args.old_str.isNullOrEmpty()) return args.old_str
        if (!args.old_text.isNullOrEmpty()) return args.old_text
        if (!args.search_block.isNullOrEmpty()) return args.search_block
        if (!args.searchStr.isNullOrEmpty()) return args.searchStr
        if (!args.find.isNullOrEmpty()) return args.find
        if (!args.pattern.isNullOrEmpty()) return args.pattern
        if (!args.sourceBlock.isNullOrEmpty()) return args.sourceBlock
        if (!args.codeChunk.isNullOrEmpty()) return args.codeChunk
        return ""
    }

    fun resolveReplace(args: ToolArguments?): String {
        if (args == null) return ""
        if (args.replace != null) return args.replace
        if (args.replacementContentPascal != null) return args.replacementContentPascal
        if (args.replacementContent != null) return args.replacementContent
        if (args.new_string != null) return args.new_string
        if (args.new_str != null) return args.new_str
        if (args.new_text != null) return args.new_text
        if (args.replace_block != null) return args.replace_block
        if (args.replaceStr != null) return args.replaceStr
        if (args.content != null) return args.content
        if (args.code != null) return args.code
        if (args.text != null) return args.text
        return ""
    }

    fun resolvePath(args: ToolArguments?): String {
        if (args == null) return ""
        return (args.path
            ?: args.targetFile
            ?: args.targetFilePascal
            ?: args.target_file
            ?: args.filePath
            ?: args.file_path
            ?: args.file
            ?: args.name
            ?: args.targetPath
            ?: "").trim()
    }

    fun resolveContent(args: ToolArguments?): String {
        if (args == null) return ""
        return args.content
            ?: args.code
            ?: args.text
            ?: resolveReplace(args)
    }

    fun resolveOverwrite(args: ToolArguments?): Boolean {
        if (args == null) return false
        return args.overwrite == true || args.overwritePascal == true
    }
}
