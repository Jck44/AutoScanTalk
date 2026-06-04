package com.andreas_kratzer.ghosttalk.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a trained vocal trigger ("Mini-Euphonia" profile).
 */
@Serializable
data class VocalProfile(
    val id: String,
    val name: String,
    val referenceEmbedding: List<Float>,
    val buttonAction: ButtonAction? = null,
    val spokenText: String? = null,
    val isActive: Boolean = true
)
