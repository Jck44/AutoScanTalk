package com.andreas_kratzer.ghosttalk.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a trained vocal trigger ("Mini-Euphonia" profile).
 *
 * V2: Each profile stores multiple positive sample vectors (the target sound)
 * and optional negative sample vectors (known false-positive sounds to block).
 * The VocalPatternMatcher uses both lists to make a robust match decision.
 */
@Serializable
data class VocalProfile(
    val id: String,
    val name: String,
    /** All 5 raw YAMNet score vectors recorded during training (one per slot). */
    val positiveTemplates: List<List<Float>> = emptyList(),
    /** Vectors of sounds that should NOT trigger this profile (learned false-positives). */
    val negativeTemplates: List<List<Float>> = emptyList(),
    val buttonAction: ButtonAction? = null,
    val spokenText: String? = null,
    val isActive: Boolean = true,
    // Legacy field kept for migration – not used by the V2 matcher.
    @Deprecated("Use positiveTemplates instead")
    val referenceEmbedding: List<Float> = emptyList()
)
