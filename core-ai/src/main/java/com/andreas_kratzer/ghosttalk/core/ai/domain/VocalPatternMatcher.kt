package com.andreas_kratzer.ghosttalk.core.ai.domain

import kotlin.math.sqrt

object VocalPatternMatcher {

    /**
     * Calculates cosine similarity between two float vectors.
     * Returns a float value in the range [-1.0, 1.0].
     * Returns 0.0f if the vectors are empty, of different sizes, or have zero magnitude.
     */
    fun calculateCosineSimilarity(vectorA: List<Float>, vectorB: List<Float>): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) {
            return 0.0f
        }

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in vectorA.indices) {
            val valA = vectorA[i].toDouble()
            val valB = vectorB[i].toDouble()
            dotProduct += valA * valB
            normA += valA * valA
            normB += valB * valB
        }

        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0f
        }

        return (dotProduct / (sqrt(normA) * sqrt(normB))).toFloat()
    }
}
