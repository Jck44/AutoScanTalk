package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.andreas_kratzer.ghosttalk.core.model.UserModeSession

@Entity(
    tableName = "user_mode_sessions",
    indices = [Index(value = ["bookId"])]
)
data class UserModeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val startTime: Long,
    val endTime: Long
) {
    fun toDomain() = UserModeSession(
        id = id,
        bookId = bookId,
        startTime = startTime,
        endTime = endTime
    )

    companion object {
        fun fromDomain(domain: UserModeSession) = UserModeSessionEntity(
            id = domain.id,
            bookId = domain.bookId,
            startTime = domain.startTime,
            endTime = domain.endTime
        )
    }
}
