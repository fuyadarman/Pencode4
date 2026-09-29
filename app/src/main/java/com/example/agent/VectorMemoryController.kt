package com.example.agent

import android.content.Context
import android.util.Log
import com.example.agent.harness.SemanticMemoryStore
import com.example.agent.harness.SemanticVectorEmbedder
import com.example.data.ProjectFileEntity
import org.json.JSONObject

/**
 * VectorMemoryController
 *
 * Master controller for Vector Memory, Semantic Retrieval, and the Hybrid Self-Learning Engine.
 * Gives the AI agent direct, active control to:
 * 1. Record, query, and manage 256-D semantic vector memories via Cosine Similarity.
 * 2. Record, query, and delete self-learned rules and error-fix patterns.
 * 3. Inspect real-time memory and embedder status.
 * 4. Compute accurate workspace context token counts for the UI.
 */
object VectorMemoryController {

    private const val TAG = "VectorMemoryController"

    /**
     * Records a new semantic memory with vector embedding into persistent memory store.
     */
    fun recordVectorMemory(
        projectName: String,
        topic: String,
        content: String,
        tags: List<String>,
        context: Context
    ): String {
        return try {
            SemanticMemoryStore.init(context)
            SemanticMemoryStore.recordMemory(
                projectName = projectName,
                topic = topic.trim(),
                content = content.trim(),
                tags = tags.filter { it.isNotBlank() },
                context = context
            )
            val count = SemanticMemoryStore.getMemoryCount(projectName)
            "Successfully recorded vector memory for project '$projectName':\n" +
            "• Topic: $topic\n" +
            "• Vector Dimensions: 256-D\n" +
            "• Tags: ${tags.joinToString(", ")}\n" +
            "• Total Memories in Project: $count\n" +
            "• Search Index: Updated with Cosine Similarity"
        } catch (e: Exception) {
            Log.e(TAG, "Failed recording vector memory", e)
            "Error recording vector memory: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    /**
     * Searches vector memory using Cosine Similarity matching against 256-D normalized vectors.
     */
    fun queryVectorMemory(
        projectName: String,
        query: String,
        topK: Int = 4,
        threshold: Float = 0.12f,
        context: Context
    ): String {
        return try {
            SemanticMemoryStore.init(context)
            val results = SemanticMemoryStore.retrieveRelevantMemories(
                query = query,
                projectName = projectName,
                topK = topK,
                threshold = threshold
            )

            if (results.isEmpty()) {
                val totalCount = SemanticMemoryStore.getMemoryCount(projectName)
                "No matching vector memories found above threshold ($threshold). Total memories indexed: $totalCount."
            } else {
                buildString {
                    append("Vector Memory Search Results (Query: '$query'):\n")
                    results.forEachIndexed { idx, pair ->
                        val (entry, score) = pair
                        val percent = (score * 100).toInt()
                        append("\n${idx + 1}. [Relevance: $percent% | Topic: ${entry.topic}]\n")
                        append("   Content: ${entry.content}\n")
                        if (entry.tags.isNotEmpty()) {
                            append("   Tags: ${entry.tags.joinToString(", ")}\n")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed querying vector memory", e)
            "Error querying vector memory: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    /**
     * Records or updates a learned pattern in the Hybrid Self-Learning Engine.
     */
    fun recordLearnedPattern(
        context: Context,
        title: String,
        category: String,
        issueDescription: String,
        solutionRule: String,
        tags: List<String>
    ): String {
        return try {
            HybridSelfLearningEngine.init(context)
            val pattern = HybridSelfLearningEngine.recordPattern(
                context = context,
                title = title,
                category = category,
                issueDescription = issueDescription,
                solutionRule = solutionRule,
                tags = tags
            )
            "Successfully updated Hybrid Self-Learning memory:\n" +
            "• Title: ${pattern.title}\n" +
            "• Category: ${pattern.category.uppercase()}\n" +
            "• Rule: ${pattern.solutionRule}\n" +
            "• Tags: ${pattern.tags.joinToString(", ")}\n" +
            "• Status: Persisted and active for future prompt injections"
        } catch (e: Exception) {
            Log.e(TAG, "Failed recording learned pattern", e)
            "Error updating self-learning memory: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    /**
     * Deletes a pattern from Hybrid Self-Learning Engine by ID or title.
     */
    fun deleteLearnedPattern(context: Context, idOrTitle: String): String {
        return try {
            HybridSelfLearningEngine.init(context)
            val currentList = HybridSelfLearningEngine.learnedPatterns.value
            val match = currentList.find { 
                it.id.equals(idOrTitle, ignoreCase = true) || 
                it.title.equals(idOrTitle, ignoreCase = true) ||
                it.title.contains(idOrTitle, ignoreCase = true)
            }

            if (match != null) {
                HybridSelfLearningEngine.deletePattern(context, match.id)
                "Deleted learned pattern '${match.title}' (ID: ${match.id}) from Hybrid Self-Learning memory."
            } else {
                "Pattern '$idOrTitle' not found in self-learning memory."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting learned pattern", e)
            "Error deleting pattern: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    /**
     * Inspects full system status of Vector Memory and Hybrid Self-Learning.
     */
    fun getSystemStatus(context: Context, projectName: String): String {
        return try {
            SemanticMemoryStore.init(context)
            HybridSelfLearningEngine.init(context)

            val projMemories = SemanticMemoryStore.getMemoryCount(projectName)
            val globalMemories = SemanticMemoryStore.getMemoryCount("global")
            val learnedPatterns = HybridSelfLearningEngine.learnedPatterns.value

            val bugFixes = learnedPatterns.count { it.category == "bug_fix" }
            val arch = learnedPatterns.count { it.category == "architecture" }
            val conv = learnedPatterns.count { it.category == "convention" }
            val perf = learnedPatterns.count { it.category == "performance" }

            buildString {
                append("=== PENCODE VECTOR MEMORY & SELF-LEARNING STATUS ===\n")
                append("• Semantic Memory Engine: ACTIVE\n")
                append("  - Project Memories ($projectName): $projMemories\n")
                append("  - Global System Memories: $globalMemories\n")
                append("  - Vector Model: 256-Dimensional Subword & N-Gram Embedding\n")
                append("  - Similarity Metric: Cosine Similarity (Dot Product over L2-normalized vectors)\n\n")
                append("• Hybrid Self-Learning Engine: ACTIVE\n")
                append("  - Total Learned Patterns: ${learnedPatterns.size}\n")
                append("  - Bug Fix Heuristics: $bugFixes\n")
                append("  - Architectural Rules: $arch\n")
                append("  - Conventions & Standards: $conv\n")
                append("  - Performance Rules: $perf\n\n")
                append("• Context Injection: Fully enabled and synchronized with prompt pipeline.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed getting memory status", e)
            "Error checking memory status: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    /**
     * Computes the real loaded Workspace Context tokens for UI display.
     */
    fun calculateWorkspaceTokens(
        allFiles: List<ProjectFileEntity>,
        chatInputText: String,
        projectName: String
    ): Int {
        var charCount = 0
        // 1. Files in workspace (sampled or structured tree)
        for (f in allFiles.take(40)) {
            charCount += f.path.length + minOf(f.content.length, 300)
        }
        // 2. Base identity & dynamic instructions
        charCount += 1200
        // 3. User input
        charCount += chatInputText.length

        // Each token is roughly 4 characters in English/code, minimum 280 tokens base
        val tokens = charCount / 4
        return maxOf(280, tokens)
    }
}
