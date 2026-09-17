package com.example.agent

import com.example.api.Content
import com.example.api.InlineData
import com.example.api.Part
import com.example.data.ChatMessageEntity
import com.example.ui.AiActionLog
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File

object AgentHistoryBuilder {

    fun buildHistory(
        historyEntities: List<ChatMessageEntity>,
        moshi: Moshi,
        editRecords: List<com.example.ui.EditRecord> = emptyList()
    ): MutableList<Content> {
        val history = mutableListOf<Content>()

        if (historyEntities.size > 1) {
            val historicalMessages = historyEntities.dropLast(1)
            val lastAssistantEntity = historicalMessages.lastOrNull { it.role == "assistant" }

            if (lastAssistantEntity != null) {
                val lastUserEntity = historicalMessages.lastOrNull {
                    it.role == "user" && it.timestamp <= lastAssistantEntity.timestamp
                }
                val rawPrevPrompt = lastUserEntity?.content ?: "[Previous Request]"
                val previousUserPrompt = rawPrevPrompt.replace("""\[IMAGE_BASE64: data:.*?;base64,.*?\]""".toRegex(), "[Attached Image]")

                // Generate token-bounded session memory ledger (capped at ~300 tokens)
                val sessionLedger = AgentSessionMemoryEngine.buildSessionMemoryLedger(
                    historyEntities = historyEntities,
                    editRecords = editRecords,
                    moshi = moshi
                )

                history.add(Content(
                    role = "user",
                    parts = listOf(Part(text = "$sessionLedger\n\n[IMMEDIATE PREVIOUS REQUEST]\n$previousUserPrompt"))
                ))

                val fileOperations = mutableListOf<String>()
                if (lastAssistantEntity.aiActionLogsJson != null) {
                    try {
                        val listType = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
                        val logs = moshi.adapter<List<AiActionLog>>(listType).fromJson(lastAssistantEntity.aiActionLogsJson)
                        if (logs != null) {
                            for (log in logs) {
                                val titleLower = log.title.lowercase()
                                val filePath = log.details?.lineSequence()?.firstOrNull()?.let { line ->
                                    val match = """(?:from|to|file|written to|read from|for)?\s*([a-zA-Z0-9_\-\./]+)""".toRegex(RegexOption.IGNORE_CASE).find(line)
                                    match?.groupValues?.get(1) ?: line
                                } ?: ""
                                val fileName = File(filePath).name.ifEmpty { filePath }

                                if (fileName.isNotEmpty()) {
                                    when {
                                        titleLower.contains("read_file") || titleLower.contains("read file") -> {
                                            fileOperations.add("read:$fileName")
                                        }
                                        titleLower.contains("edit_file") || titleLower.contains("edit file") || titleLower.contains("multi_edit") || titleLower.contains("modify") -> {
                                            fileOperations.add("modified:$fileName")
                                        }
                                        titleLower.contains("patch_file") || titleLower.contains("patch file") || titleLower.contains("patch") -> {
                                            fileOperations.add("modified:$fileName")
                                        }
                                        titleLower.contains("append") -> {
                                            fileOperations.add("append:$fileName")
                                        }
                                        titleLower.contains("write_file") || titleLower.contains("write file") || titleLower.contains("create") -> {
                                            fileOperations.add("create:$fileName")
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                val actionsStr = if (fileOperations.isNotEmpty()) {
                    fileOperations.joinToString(", ")
                } else {
                    ""
                }

                val assistantText = buildString {
                    append("[PREVIOUS OUTCOME - FINISHED]\n")
                    if (actionsStr.isNotEmpty()) {
                        append(actionsStr)
                        append("\n\n")
                    }
                    append(lastAssistantEntity.content)
                }

                history.add(Content(
                    role = "model",
                    parts = listOf(Part(text = assistantText))
                ))
            }
        }

        val currentPromptEntity = historyEntities.lastOrNull { it.role == "user" }
        if (currentPromptEntity != null) {
            val textParts = mutableListOf<Part>()
            var remainingText = currentPromptEntity.content

            val regex = """\[IMAGE_BASE64: data:(.*?);base64,(.*?)\]""".toRegex()
            var match = regex.find(remainingText)
            while (match != null) {
                val textBefore = remainingText.substring(0, match.range.first)
                if (textBefore.isNotBlank()) textParts.add(Part(text = textBefore))
                textParts.add(Part(inlineData = InlineData(mimeType = match.groupValues[1], data = match.groupValues[2])))
                remainingText = remainingText.substring(match.range.last + 1)
                match = regex.find(remainingText)
            }
            if (remainingText.isNotBlank() || textParts.isEmpty()) {
                textParts.add(Part(text = remainingText))
            }

            history.add(Content(
                role = "user",
                parts = textParts
            ))
        }

        return history
    }
}
