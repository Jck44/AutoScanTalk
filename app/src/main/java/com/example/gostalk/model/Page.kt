package com.example.gostalk.model

data class Page(
    val id: String,
    val name: String,
    val rows: Int = 4,
    val columns: Int = 4,
    val buttonConfigs: List<ButtonConfig?> // Represents the grid, null for an empty/deactivated button
) {
    init {
        require(rows > 0) { "Rows must be a positive number." }
        require(columns > 0) { "Columns must be a positive number." }
        require(buttonConfigs.size == rows * columns) {
            "The number of button configurations must match the total grid size (rows * columns). " +
            "Expected ${rows * columns}, but got ${buttonConfigs.size}."
        }
    }
}
