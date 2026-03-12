package com.andreas_kratzer.ghosttalk.core.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "pages")
data class Page(
    @PrimaryKey override val id: String,
    val bookId: String,
    override val name: String,
    val templateId: String? = null,
    override val rows: Int = 4,
    override val columns: Int = 4,
    override val scanPattern: String? = null,
    override val rowNames: List<String> = emptyList(),
    @Ignore override val buttonConfigs: List<ButtonConfig?> = emptyList(),
    override val orderIndex: Int = 0,
    override val createdAt: Long = System.currentTimeMillis(),
    val legacy_buttonConfigs: String? = null
) : GridItem {
    
    // Primary constructor with buttonConfigs ignored
    constructor(
        id: String,
        bookId: String,
        name: String,
        templateId: String?,
        rows: Int,
        columns: Int,
        scanPattern: String?,
        rowNames: List<String>,
        orderIndex: Int,
        createdAt: Long,
        legacy_buttonConfigs: String? = null
    ) : this(id, bookId, name, templateId, rows, columns, scanPattern, rowNames, emptyList(), orderIndex, createdAt, legacy_buttonConfigs)

    init {
        require(rows in 1..7) { "Rows must be between 1 and 7." }
        require(columns in 1..7) { "Columns must be between 1 and 7." }
    }
}
