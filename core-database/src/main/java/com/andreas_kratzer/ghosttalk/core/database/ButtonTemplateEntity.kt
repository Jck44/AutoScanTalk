package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig

@Entity(tableName = "button_templates")
data class ButtonTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val buttonConfig: ButtonConfig,
    val orderIndex: Int
)
