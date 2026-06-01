package com.andreas_kratzer.ghosttalk.core.model

import kotlinx.serialization.Serializable

/**
 * Metric analysis representing the physical/cognitive effort required to select a button,
 * weighted by how often it is actually clicked (Frustration Index).
 */
@Serializable
data class ButtonEffortMetrics(
    val buttonId: String,
    val accessTimeSec: Int,
    val usageCount: Int,
    val frustrationIndex: Float,
    val heatmapIntensity: Float = 0f
)
