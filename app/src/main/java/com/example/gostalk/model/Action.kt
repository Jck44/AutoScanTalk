package com.example.gostalk.model

/**
 * Represents an action that can be executed when a button is triggered.
 */
sealed interface Action {
    fun execute() // Placeholder for actual execution logic

    /**
     * An action that speaks a given text using Text-to-Speech.
     */
    data class SpeakTextAction(val textToSpeech: String) : Action {
        override fun execute() {
            // TODO: Implement TTS playback for action
            println("Executing SpeakTextAction: $textToSpeech")
        }
    }

    /**
     * Future placeholder: An action to integrate with Philips Hue.
     * data class HueAction(val command: String) : Action
     */

    /**
     * Future placeholder: An action to send a command to Google Assistant.
     * data class AssistantAction(val command: String) : Action
     */
}
