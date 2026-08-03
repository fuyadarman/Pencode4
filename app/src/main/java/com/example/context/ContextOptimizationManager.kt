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

        // Stage 2: Rolling History Summarization & Memory Compression
        val compressedHistory = ContextSummarizer.compressHistory(prunedHistory, maxTokenThreshold)

        // Stage 3: Optional Symbol Context Augmentation (RAG Indexing)
        if (!activeTaskQuery.isNullOrBlank()) {
            val symbolBlock = symbolIndexer.buildSymbolContextBlock(activeTaskQuery)
            if (symbolBlock.isNotBlank() && compressedHistory.isNotEmpty()) {
                val lastContent = compressedHistory.last()
                if (lastContent.role == "user" && lastContent.parts.isNotEmpty()) {
                    val updatedText = (lastContent.parts.first().text ?: "") + "\n" + symbolBlock
                    val updatedParts = lastContent.parts.toMutableList()
                    updatedParts[0] = updatedParts[0].copy(text = updatedText)

                    val updatedHistory = compressedHistory.toMutableList()
                    updatedHistory[updatedHistory.lastIndex] = lastContent.copy(parts = updatedParts)
                    return updatedHistory
                }
            }
        }

        return compressedHistory
    }

    /**
     * Estimates total token count for current history.
     */
    fun getEstimatedTokens(history: List<Content>): Int {
        return ContextSummarizer.estimateTokenCount(history)
    }
}
