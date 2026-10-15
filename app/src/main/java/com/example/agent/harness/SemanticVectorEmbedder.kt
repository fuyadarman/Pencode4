package com.example.agent.harness

import kotlin.math.sqrt

/**
 * On-device Semantic Vector Embedder using Term-Frequency Subword/Character N-Gram Hashing.
 * Produces normalized sparse-dense feature vectors for fast, offline cosine similarity calculation.
 */
object SemanticVectorEmbedder {

    private const val VECTOR_DIM = 256

    /**
     * Converts a text string into a normalized dense vector of dimension [VECTOR_DIM].
     */
    fun embed(text: String): FloatArray {
        val vector = FloatArray(VECTOR_DIM)
        if (text.isBlank()) return vector

        val cleaned = text.lowercase()
        val tokens = cleaned.split(Regex("[^a-z0-9_]+")).filter { it.length > 1 }

        // 1. Word token hashing
        for (token in tokens) {
            val h = (token.hashCode() and 0x7fffffff) % VECTOR_DIM
            val weight = when {
                token.length > 6 -> 2.0f
                token.length > 3 -> 1.5f
                else -> 1.0f
            }
            vector[h] += weight
        }

        // 2. Character 3-gram hashing for sub-word and morphological matching (e.g. "auth" matching "authentication")
        val compact = cleaned.replace("\\s+".toRegex(), "")
        if (compact.length >= 3) {
            for (i in 0 until compact.length - 2) {
                val tri = compact.substring(i, i + 3)
                val h = (tri.hashCode() and 0x7fffffff) % VECTOR_DIM
                vector[h] += 0.5f
            }
        }

        // 3. L2 normalize vector for fast cosine similarity calculation
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }

        val norm = sqrt(sumSquares.toDouble()).toFloat()
        if (norm > 0.0f) {
            for (i in 0 until VECTOR_DIM) {
                vector[i] /= norm
            }
        }

        return vector
    }

    /**
     * Computes the Cosine Similarity between two normalized vectors (dot product).
     * Returns a score between -1.0 and 1.0 (typically 0.0 to 1.0 for positive term vectors).
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size || v1.isEmpty()) return 0.0f
        var dot = 0.0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
        }
        return dot.coerceIn(-1.0f, 1.0f)
    }
}
