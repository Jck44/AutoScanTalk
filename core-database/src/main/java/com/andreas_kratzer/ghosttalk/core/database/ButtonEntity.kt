package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode

@Entity(
    tableName = "buttons",
    foreignKeys = [
        ForeignKey(
            entity = Page::class,
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
    val spokenTextMode: SpokenTextMode = SpokenTextMode.TTS,
    val audioFileName: String? = null,
    val auditoryCue: AuditoryCue? = null,
    val buttonAction: ButtonAction,
    val isActive: Boolean = true,
    val playActionAsAuditoryCue: Boolean = false
)

