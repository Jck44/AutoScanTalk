package com.andreas_kratzer.ghosttalk.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val actionLogLimit: Int = 100,
    val limitScanCycles: Boolean = false,
    val scanCycleLimit: Int = 2
)
