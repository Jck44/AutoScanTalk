package com.andreas_kratzer.ghosttalk.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.andreas_kratzer.ghosttalk.ui.util.ListableItem

import kotlinx.serialization.Serializable

@Entity(tableName = "templates")
@Serializable
data class PageTemplate(
    @PrimaryKey override val id: String,
    override val name: String,
    override val rows: Int,
    override val columns: Int,
    override val scanPattern: String? = null,
    override val rowNames: List<String> = emptyList(),
    override val buttonConfigs: List<ButtonConfig?>,
    val isBuiltIn: Boolean = false,
    override val orderIndex: Int = 0,
    override val createdAt: Long = System.currentTimeMillis()
) : GridItem {
    init {
        require(rows in 1..7) { "Rows must be between 1 and 7." }
        require(columns in 1..7) { "Columns must be between 1 and 7." }
    }
}
