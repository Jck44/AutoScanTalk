package com.andreas_kratzer.ghosttalk.util

object VoiceUtils {
    /**
     * Formats a technical TTS voice name into a human-readable display name.
     * Example: "de-de-x-deb-network" -> "Deb"
     */
    fun formatVoiceName(technicalName: String): String {
        var name = technicalName.lowercase()
        val prefixRegex = Regex("^[a-z]{2}-[a-z]{2}-")
        name = name.replace(prefixRegex, "")
            .replace("-network", "")
            .replace("-local", "")
            .replace("-x-", " ")
            .replace(Regex("\\bx\\b"), " ")
            .replace("-", " ")
        
        return name.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            .ifEmpty { "Stimme" }
    }
}
