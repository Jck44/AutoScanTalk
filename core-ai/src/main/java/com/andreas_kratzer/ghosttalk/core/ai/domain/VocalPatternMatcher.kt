package com.andreas_kratzer.ghosttalk.core.ai.domain

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Evaluates whether an audio fingerprint matches a trained vocal profile.
 *
 * V2 – False-Positive Suppression:
 * In addition to checking similarity against positive training samples, the matcher
 * also checks against learned negative samples (sounds that are NOT the target but
 * have accidentally triggered the profile in the past).
 *
 * Decision rule:
 *  - maxPositiveSim ≥ [threshold]  → candidate passes the positive gate
 *  - maxPositiveSim > maxNegativeSim → candidate is closer to target than to any known noise
 *  - Both conditions must be true for isMatch = true
 *
 * Example – Ballpoint pen vs. Tongue-click:
 *  If the pen-clicking sound was registered as a false-positive (negative template),
 *  the matcher will reject it even if it accidentally scores above the threshold,
 *  because its negative confidence will be ≥ positive confidence.
 */
@Singleton
class VocalPatternMatcher @Inject constructor() {

    data class MatchResult(
        val isMatch: Boolean,
        /** Cosine similarity to the best-matching positive training sample [0..1]. */
        val positiveConfidence: Float,
        /** Cosine similarity to the best-matching negative (noise) sample [0..1]. */
        val negativeConfidence: Float
    )

    /**
     * Calculates an adaptive threshold based on positive templates.
     * minIntraSim is the minimum similarity among pairs of templates.
     * Formula: max(0.65f, min(0.88f, minIntraSim - 0.05f))
     */
    fun calculateAdaptiveThreshold(
        positives: List<FloatArray>,
        fallbackThreshold: Float = 0.82f
    ): Float {
        if (positives.size < 2) return fallbackThreshold
        var minIntraSim = Float.MAX_VALUE
        var count = 0
        for (i in positives.indices) {
            for (j in i + 1 until positives.size) {
                val sim = cosineSimilarity(positives[i], positives[j])
                if (sim < minIntraSim) {
                    minIntraSim = sim
                }
                count++
            }
        }
        if (count == 0) return fallbackThreshold
        return maxOf(0.65f, minOf(0.88f, minIntraSim - 0.05f))
    }

    /**
     * Calculates an adaptive threshold from a list of list of floats.
     */
    fun calculateAdaptiveThresholdFromList(
        positives: List<List<Float>>,
        fallbackThreshold: Float = 0.82f
    ): Float {
        return calculateAdaptiveThreshold(positives.map { it.toFloatArray() }, fallbackThreshold)
    }

    /**
     * Evaluates [inputVector] against [positives] and [negatives].
     *
     * @param inputVector  521-dim YAMNet score vector of the current audio window.
     * @param positives    Training vectors of the desired sound (typically 5 samples).
     * @param negatives    Vectors of known false-positive sounds (may be empty).
     * @param threshold    Minimum positive cosine similarity to pass the positive gate. If null, uses the adaptive threshold.
     */
    fun evaluate(
        inputVector: FloatArray,
        positives: List<FloatArray>,
        negatives: List<FloatArray>,
        threshold: Float? = null
    ): MatchResult {
        if (positives.isEmpty()) return MatchResult(false, 0f, 0f)

        val resolvedThreshold = threshold ?: calculateAdaptiveThreshold(positives)

        val maxPositiveSim = positives
            .map { cosineSimilarity(inputVector, it) }
            .maxOrNull() ?: 0f

        val maxNegativeSim = if (negatives.isNotEmpty()) {
            negatives.map { cosineSimilarity(inputVector, it) }.maxOrNull() ?: 0f
        } else {
            0f
        }

        val meetsThreshold = maxPositiveSim >= resolvedThreshold
        val closerToPositive = maxPositiveSim > maxNegativeSim

        return MatchResult(
            isMatch = meetsThreshold && closerToPositive,
            positiveConfidence = maxPositiveSim,
            negativeConfidence = maxNegativeSim
        )
    }

    /**
     * Convenience overload accepting [List<Float>] instead of [FloatArray].
     */
    fun evaluate(
        inputVector: List<Float>,
        positives: List<List<Float>>,
        negatives: List<List<Float>>,
        threshold: Float? = null
    ): MatchResult = evaluate(
        inputVector = inputVector.toFloatArray(),
        positives = positives.map { it.toFloatArray() },
        negatives = negatives.map { it.toFloatArray() },
        threshold = threshold
    )

    // ── Legacy API (V1 compatibility) ─────────────────────────────────────────
    fun calculateCosineSimilarity(vectorA: List<Float>, vectorB: List<Float>): Float {
        return cosineSimilarity(vectorA.toFloatArray(), vectorB.toFloatArray())
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA <= 0.0 || normB <= 0.0) return 0f
        return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
    }
}
