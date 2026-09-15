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
            "recall_learned_patterns", "query_learned_patterns" -> true
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
            else -> ""
        }
    }
}
