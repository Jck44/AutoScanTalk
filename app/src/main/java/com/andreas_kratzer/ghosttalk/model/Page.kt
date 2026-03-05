package com.andreas_kratzer.ghosttalk.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.andreas_kratzer.ghosttalk.ui.util.ListableItem

@Entity(tableName = "pages")
data class Page(
    @PrimaryKey override val id: String,
    val bookId: String,
    override val name: String,
    val rows: Int = 4,
    val columns: Int = 4,
    val scanPattern: String? = null,
    val rowNames: List<String> = emptyList(),
    val buttonConfigs: List<ButtonConfig?>, // Represents the grid, null for an empty/deactivated button
    override val orderIndex: Int = 0,
    override val createdAt: Long = System.currentTimeMillis()
) : ListableItem {
    init {
        require(rows > 0) { "Rows must be a positive number." }
        require(columns > 0) { "Columns must be a positive number." }
    }
}
