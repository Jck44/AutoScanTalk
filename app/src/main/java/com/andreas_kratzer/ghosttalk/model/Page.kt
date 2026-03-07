package com.andreas_kratzer.ghosttalk.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.andreas_kratzer.ghosttalk.ui.util.ListableItem

@Entity(tableName = "pages")
data class Page(
    @PrimaryKey override val id: String,
    val bookId: String,
    override val name: String,
    val templateId: String? = null,
    val rows: Int = 4,
    val columns: Int = 4,
    val scanPattern: String? = null,
    val rowNames: List<String> = emptyList(),
    val buttonConfigs: List<ButtonConfig?>, // Represents the grid, null for an empty/deactivated button
    override val orderIndex: Int = 0,
    override val createdAt: Long = System.currentTimeMillis()
) : ListableItem {
    init {
        require(rows in 1..7) { "Rows must be between 1 and 7." }
        require(columns in 1..7) { "Columns must be between 1 and 7." }
    }
}
