package com.andreas_kratzer.ghosttalk.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonAction

@Entity(
    tableName = "buttons",
    foreignKeys = [
        ForeignKey(
            entity = com.andreas_kratzer.ghosttalk.model.Page::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class ButtonEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val globalIndex: Int,
    val label: String,
    val spokenText: String? = null,
    val auditoryCue: AuditoryCue? = null,
    val buttonAction: ButtonAction,
    val isActive: Boolean = true,
    val playActionAsAuditoryCue: Boolean = false
)
