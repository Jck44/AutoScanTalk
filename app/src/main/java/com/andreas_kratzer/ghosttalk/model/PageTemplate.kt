package com.andreas_kratzer.ghosttalk.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class PageTemplate(
    @PrimaryKey val id: String,
    val name: String,
    val rows: Int,
    val columns: Int,
    val buttonConfigs: List<ButtonConfig?>,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
