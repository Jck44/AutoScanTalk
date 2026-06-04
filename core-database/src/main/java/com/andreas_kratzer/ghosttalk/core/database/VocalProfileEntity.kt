package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.VocalProfile

@Entity(tableName = "vocal_profiles")
data class VocalProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val referenceEmbedding: List<Float>,
    val buttonAction: ButtonAction? = null,
    val spokenText: String? = null,
    val isActive: Boolean = true
) {
    fun toDomain(): VocalProfile = VocalProfile(
        id = id,
        name = name,
        referenceEmbedding = referenceEmbedding,
        buttonAction = buttonAction,
        spokenText = spokenText,
        isActive = isActive
    )
}

fun VocalProfile.toEntity(): VocalProfileEntity = VocalProfileEntity(
    id = id,
    name = name,
    referenceEmbedding = referenceEmbedding,
    buttonAction = buttonAction,
    spokenText = spokenText,
    isActive = isActive
)
