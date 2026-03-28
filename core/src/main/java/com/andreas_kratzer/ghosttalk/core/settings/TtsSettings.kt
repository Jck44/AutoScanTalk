package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface TtsSettings {
    var ttsLanguage: String?
    val ttsLanguageFlow: StateFlow<String?>
    
    var ttsVoiceName: String?
    val ttsVoiceNameFlow: StateFlow<String?>
    
    var appLanguage: String?
    val appLanguageFlow: StateFlow<String?>

    var ttsEngine: String?
    val ttsEngineFlow: StateFlow<String?>

    var googleTtsLanguage: String?
    val googleTtsLanguageFlow: StateFlow<String?>
    var googleTtsVoiceName: String?
    val googleTtsVoiceNameFlow: StateFlow<String?>

    var elevenLabsTtsLanguage: String?
    val elevenLabsTtsLanguageFlow: StateFlow<String?>
    var elevenLabsTtsVoiceName: String?
    val elevenLabsTtsVoiceNameFlow: StateFlow<String?>
}
