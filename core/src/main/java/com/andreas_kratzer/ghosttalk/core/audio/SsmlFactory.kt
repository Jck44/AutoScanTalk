package com.andreas_kratzer.ghosttalk.core.audio

object SsmlFactory {
    fun wrap(text: String): String {
        if (text.startsWith("<speak>")) return text
        return "<speak>$text</speak>"
    }
}
