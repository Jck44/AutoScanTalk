package com.andreas_kratzer.ghosttalk.core.settings

interface FeatureSettings {
    var isSmartPredictionEnabled: Boolean
    var isGeminiEnabled: Boolean
    var isNotificationReadingEnabled: Boolean
    var appLanguage: String?
    val appLanguageFlow: kotlinx.coroutines.flow.StateFlow<String?>
    var isVocalSwitchEnabled: Boolean
    val isVocalSwitchEnabledFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
}
