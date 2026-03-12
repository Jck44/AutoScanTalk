package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface TtsSettings {
    var ttsLanguage: String?
    val ttsLanguageFlow: StateFlow<String?>
    
    var ttsVoiceName: String?
    val ttsVoiceNameFlow: StateFlow<String?>
    
    var appLanguage: String?
    val appLanguageFlow: StateFlow<String?>
}
