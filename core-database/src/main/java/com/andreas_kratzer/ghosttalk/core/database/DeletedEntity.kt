package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deleted_entities",
    indices = [Index(value = ["bookId"])]
)
data class DeletedEntity(
    @PrimaryKey val entityId: String,
    val entityType: String, // "PAGE" or "BUTTON"
    val bookId: String,
    val deletedAt: Long = System.currentTimeMillis()
)
