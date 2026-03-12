package com.andreas_kratzer.ghosttalk.core.settings

interface FeatureSettings {
    val isSmartPredictionEnabled: Boolean
    val isGeminiEnabled: Boolean
    val useLocalGenerativeAi: Boolean
    val isNotificationReadingEnabled: Boolean
}
