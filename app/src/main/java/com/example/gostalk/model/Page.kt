package com.example.gostalk.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pages")
data class Page(
    @PrimaryKey val id: String,
    val bookId: String,
    val name: String,
    val rows: Int = 4,
    val columns: Int = 4,
    val buttonConfigs: List<ButtonConfig?> // Represents the grid, null for an empty/deactivated button
) {
    init {
        require(rows > 0) { "Rows must be a positive number." }
        require(columns > 0) { "Columns must be a positive number." }
        // Temporarily relaxing this constraint as buttonConfigs might not always fill the grid
        // require(buttonConfigs.size == rows * columns) {
        //     "The number of button configurations must match the total grid size (rows * columns). " +
        //     "Expected ${rows * columns}, but got ${buttonConfigs.size}."
        // }
    }
}



// Die folgende Definition wird entfernt, da sie in AuditoryCue.kt steht:
// sealed class AuditoryCue {
// data class TextToSpeechCue(val text: String) : AuditoryCue() // Text für TTS-Hinweis
// // data class SoundResourceCue(val resourceId: Int) : AuditoryCue() // Für Sound-Datei-Hinweis (später)
// }
