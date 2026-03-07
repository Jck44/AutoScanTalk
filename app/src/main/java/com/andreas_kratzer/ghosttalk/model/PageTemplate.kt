package com.andreas_kratzer.ghosttalk.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.andreas_kratzer.ghosttalk.ui.util.ListableItem

@Entity(tableName = "templates")
data class PageTemplate(
    @PrimaryKey override val id: String,
    override val name: String,
    val rows: Int,
    val columns: Int,
    val scanPattern: String? = null,
    val rowNames: List<String> = emptyList(),
    val buttonConfigs: List<ButtonConfig?>,
    val isBuiltIn: Boolean = false,
    override val orderIndex: Int = 0,
    override val createdAt: Long = System.currentTimeMillis()
) : ListableItem {
    init {
        require(rows in 1..7) { "Rows must be between 1 and 7." }
        require(columns in 1..7) { "Columns must be between 1 and 7." }
    }
}
