package com.example.agent

import android.content.Context
import com.example.api.Content
import com.example.api.Part
import com.example.api.ToolArguments
import com.example.api.ToolCallResponse
import com.example.data.ProjectEntity
import com.example.ui.AgentSkill
import com.example.ui.AiActionLog
import com.squareup.moshi.Moshi

/**
 * AgentSelfLearningToolHandler
 *
 * Dedicated modular handler for Autonomous Self-Learning tools:
 * - learn_pattern: saves discovered fixes to vector memory
 * - synthesize_skill: autonomously registers new agent skill
 * - recall_learned_patterns: vector search over learned patterns
 */
object AgentSelfLearningToolHandler {

    fun isSelfLearningTool(tool: String): Boolean {
        return when (tool) {
            "learn_pattern", "memorize_pattern", "save_pattern",
            "synthesize_skill", "create_agent_skill",
            "recall_learned_patterns", "query_learned_patterns",
            "delete_learned_pattern", "remove_learned_pattern",
            "record_vector_memory", "save_vector_memory", "store_vector_memory", "record_memory",
            "query_vector_memory", "search_vector_memory", "retrieve_vector_memory", "search_memory",
            "get_vector_memory_status", "memory_status", "check_vector_memory" -> true
            else -> false
        }
    }

    fun handleTool(
        tool: String,
        args: ToolArguments?,
        stepResponse: ToolCallResponse,
        project: ProjectEntity,
        context: Context,
        history: MutableList<Content>,
        moshi: Moshi,
        createAiLog: (String, String, String?) -> AiActionLog,
        addAiLog: (AiActionLog) -> Unit,
        updateAiLog: (String, String, String?) -> Unit,
        onAddSkill: (AgentSkill) -> Unit
    ): String {
        return when (tool) {
            "learn_pattern", "memorize_pattern", "save_pattern" -> {
                val patternTitle = args?.title ?: args?.message ?: "Learned Fix Pattern"
                val patternCategory = args?.category ?: "bug_fix"
                val patternIssue = args?.issue ?: args?.query ?: ""
                val patternSolution = args?.solution ?: args?.content ?: ""
                val patternTags = args?.tags ?: listOf(project.name.lowercase())

                val logEntry = createAiLog(
                    "Memorize: $patternTitle",
                    "thinking",
                    "Self-Learning memory"
                )
                addAiLog(logEntry)

                val pattern = HybridSelfLearningEngine.recordPattern(
                    context = context,
                    title = patternTitle,
                    category = patternCategory,
                    issueDescription = patternIssue,
                    solutionRule = patternSolution,
                    tags = patternTags
                )

                updateAiLog(logEntry.id, "success", "Saved pattern '${pattern.title}'")
                val outputMsg = "Successfully registered pattern '${pattern.title}' in Hybrid Self-Learning memory."
                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool': $outputMsg"))))
                outputMsg
            }
            "synthesize_skill", "create_agent_skill" -> {
                val skillName = args?.name ?: args?.title ?: "Custom Skill"
                val skillDesc = args?.description ?: "Autonomously synthesized skill"
                val skillInstructions = args?.instructions ?: args?.prompt ?: args?.content ?: ""
                val skillCat = args?.category ?: "Synthesized"

                val logEntry = createAiLog(
                    "Synthesizing skill: $skillName",
                    "thinking",
                    "Skill Generator"
                )
                addAiLog(logEntry)

                val synthesized = HybridSelfLearningEngine.synthesizeSkill(
                    name = skillName,
                    description = skillDesc,
                    instructions = skillInstructions,
                    category = skillCat
                )
                onAddSkill(synthesized)

                updateAiLog(logEntry.id, "success", "Synthesized skill '${synthesized.name}'")
                val outputMsg = "Successfully synthesized and activated specialized Agent Skill '${synthesized.name}' in PenCode."
                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool': $outputMsg"))))
                outputMsg
            }
            "recall_learned_patterns", "query_learned_patterns" -> {
                val query = args?.query ?: args?.message ?: ""
                val logEntry = createAiLog(
                    "Recall learned patterns",
                    "thinking",
                    query.ifBlank { "All patterns" }
                )
                addAiLog(logEntry)

                val patterns = HybridSelfLearningEngine.retrieveRelevantPatterns(query, topK = 4)
                val resultText = if (patterns.isEmpty()) {
                    "No specific matching patterns found in self-learning memory."
                } else {
                    patterns.joinToString("\n\n") { p ->
                        "• [${p.title}] (${p.category.uppercase()}):\n  Rule: ${p.solutionRule}\n  Issue: ${p.issueDescription}"
                    }
                }

                updateAiLog(logEntry.id, "success", "Retrieved ${patterns.size} patterns")
                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool':\n$resultText"))))
                resultText
            }
            "delete_learned_pattern", "remove_learned_pattern" -> {
                val target = args?.title ?: args?.name ?: args?.query ?: args?.message ?: ""
                val logEntry = createAiLog(
                    "Delete learned pattern",
                    "thinking",
                    target.ifBlank { "Pattern" }
                )
                addAiLog(logEntry)

                val resultText = VectorMemoryController.deleteLearnedPattern(context, target)
                updateAiLog(logEntry.id, "success", resultText)
                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool':\n$resultText"))))
                resultText
            }
            "record_vector_memory", "save_vector_memory", "store_vector_memory", "record_memory" -> {
                val topic = args?.title ?: args?.name ?: args?.message ?: "Project Architectural Pattern"
                val content = args?.content ?: args?.solution ?: args?.query ?: ""
                val tags = args?.tags ?: listOf(project.name.lowercase())

                val logEntry = createAiLog(
                    "Record Vector Memory: $topic",
                    "thinking",
                    "Semantic Vector Indexing"
                )
                addAiLog(logEntry)

                val resultText = VectorMemoryController.recordVectorMemory(
                    projectName = project.name,
                    topic = topic,
                    content = content,
                    tags = tags,
                    context = context
                )

                updateAiLog(logEntry.id, "success", "Vector memory saved")
                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool':\n$resultText"))))
                resultText
            }
            "query_vector_memory", "search_vector_memory", "retrieve_vector_memory", "search_memory" -> {
                val query = args?.query ?: args?.message ?: ""
                val topK = args?.maxLines ?: 4

                val logEntry = createAiLog(
                    "Query Vector Memory",
                    "thinking",
                    query.ifBlank { "Project memories" }
                )
                addAiLog(logEntry)

                val resultText = VectorMemoryController.queryVectorMemory(
                    projectName = project.name,
                    query = query,
                    topK = topK,
                    context = context
                )

                updateAiLog(logEntry.id, "success", "Cosine search completed")
                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool':\n$resultText"))))
                resultText
            }
            "get_vector_memory_status", "memory_status", "check_vector_memory" -> {
                val logEntry = createAiLog(
                    "Check Vector Memory Status",
                    "thinking",
                    "Diagnostics"
                )
                addAiLog(logEntry)

                val resultText = VectorMemoryController.getSystemStatus(
                    context = context,
                    projectName = project.name
                )

                updateAiLog(logEntry.id, "success", "Status retrieved")
                history.add(Content(role = "model", parts = listOf(Part(text = moshi.adapter(ToolCallResponse::class.java).toJson(stepResponse)))))
                history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for '$tool':\n$resultText"))))
                resultText
            }
            else -> ""
        }
    }
}
