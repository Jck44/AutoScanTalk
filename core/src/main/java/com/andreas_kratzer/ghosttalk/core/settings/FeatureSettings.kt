package com.andreas_kratzer.ghosttalk.core.settings

interface FeatureSettings {
    var isSmartPredictionEnabled: Boolean
    var isGeminiEnabled: Boolean
    var useLocalGenerativeAi: Boolean
    var isNotificationReadingEnabled: Boolean
    var appLanguage: String?
    val appLanguageFlow: kotlinx.coroutines.flow.StateFlow<String?>
}
