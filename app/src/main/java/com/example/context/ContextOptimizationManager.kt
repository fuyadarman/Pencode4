package com.example.context

import com.example.api.Content

/**
 * Master Context Optimization Coordinator.
 * Combines Rolling History Summarization, Smart Code & Log Output Chunking, and Code Symbol Indexing
 * to dramatically reduce input/output token usage for AI model API calls.
 */
class ContextOptimizationManager {

    val symbolIndexer = SymbolCodeIndexer()

    /**
     * Executes full multi-stage context optimization pipeline on conversation history.
     */
    fun optimizeHistory(
        history: List<Content>,
        maxTokenThreshold: Int = 12000,
        activeTaskQuery: String? = null
    ): List<Content> {
        if (history.isEmpty()) return history

        // Stage 1: Smart File Chunking & Tool Output Pruning
        val prunedHistory = CodeChunkerAndPruner.pruneAndChunkContents(history)

        // Stage 2: Automatic Context Compaction & Summarization (Preserving recent 2.5k tokens)
        val compactedHistory = if (ContextCompactionEngine.shouldCompact(prunedHistory)) {
            ContextCompactionEngine.compactHistory(prunedHistory)
        } else {
            ContextSummarizer.compressHistory(prunedHistory, maxTokenThreshold)
        }

        // Stage 3: Optional Symbol Context Augmentation (RAG Indexing)
        var resultHistory = compactedHistory
        if (!activeTaskQuery.isNullOrBlank()) {
            val symbolBlock = symbolIndexer.buildSymbolContextBlock(activeTaskQuery)
            if (symbolBlock.isNotBlank() && resultHistory.isNotEmpty()) {
                val lastContent = resultHistory.last()
                if (lastContent.role == "user" && lastContent.parts.isNotEmpty()) {
                    val updatedText = (lastContent.parts.first().text ?: "") + "\n" + symbolBlock
                    val updatedParts = lastContent.parts.toMutableList()
                    updatedParts[0] = updatedParts[0].copy(text = updatedText)

                    val updatedHistory = resultHistory.toMutableList()
                    updatedHistory[updatedHistory.lastIndex] = lastContent.copy(parts = updatedParts)
                    resultHistory = updatedHistory
                }
            }

            // Stage 4: Reinforce Active Goal Anchor & Working Set Banner so model never drifts or loops
            if (resultHistory.isNotEmpty()) {
                val lastContent = resultHistory.last()
                if (lastContent.role == "user" && lastContent.parts.isNotEmpty()) {
                    val currentText = lastContent.parts.first().text ?: ""
                    val goalAnchor = if (!currentText.contains("[ACTIVE GOAL ANCHOR:") && !currentText.contains("[CURRENT ACTIVE USER REQUEST")) {
                        com.example.agent.AgentGoalAlignmentEngine.buildToolOutputGoalAnchor(activeTaskQuery)
                    } else ""
                    val contextBanner = com.example.agent.AgentContextResilienceEngine.buildContextStateBanner()

                    if (goalAnchor.isNotBlank() || contextBanner.isNotBlank()) {
                        val updatedText = currentText + goalAnchor + contextBanner
                        val updatedParts = lastContent.parts.toMutableList()
                        updatedParts[0] = updatedParts[0].copy(text = updatedText)
                        val updatedHistory = resultHistory.toMutableList()
                        updatedHistory[updatedHistory.lastIndex] = lastContent.copy(parts = updatedParts)
                        resultHistory = updatedHistory
                    }
                }
            }
        }

        return resultHistory
    }

    /**
     * Estimates total token count for current history.
     */
    fun getEstimatedTokens(history: List<Content>): Int {
        return ContextSummarizer.estimateTokenCount(history)
    }
}
