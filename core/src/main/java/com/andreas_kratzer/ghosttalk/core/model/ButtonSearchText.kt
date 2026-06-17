package com.andreas_kratzer.ghosttalk.core.model

/** Alle für die Volltextsuche relevanten Textfelder eines Buttons, leere/null gefiltert. */
@Suppress("DEPRECATION")
fun ButtonConfig.searchableText(): List<String> {
    val texts = mutableListOf<String?>()
    texts += label
    texts += spokenText
    (auditoryCue as? AuditoryCue.TextToSpeechCue)?.let { texts += it.text }
    when (val a = buttonAction) {
        is GeminiButtonAction       -> texts += a.prompt
        is GeminiSearchButtonAction -> texts += a.prompt
        is GeminiNanoButtonAction   -> texts += a.intent
        is GeminiVisionButtonAction -> texts += a.prompt
        is ControlDeviceButtonAction -> {
            texts += a.messageText
            texts += a.prefixText
            texts += a.suffixText
            texts += a.contactName
        }
        is SmartHomeButtonAction    -> {
            texts += a.deviceName
            texts += a.intent
            texts += a.value
        }
        is PlayMediaButtonAction    -> texts += a.contentName
        else -> { /* andere Actions tragen keinen durchsuchbaren Freitext */ }
    }
    return texts.filterNot { it.isNullOrBlank() }.map { it!! }
}
