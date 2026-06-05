package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.VocalProfile

@Entity(tableName = "vocal_profiles")
data class VocalProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** V2: JSON of List<List<Float>> – all 5 raw training vectors. */
    @ColumnInfo(defaultValue = "[]")
    val positiveTemplatesJson: String = "[]",
    /** V2: JSON of List<List<Float>> – learned false-positive vectors to block. */
    @ColumnInfo(defaultValue = "[]")
    val negativeTemplatesJson: String = "[]",
    val buttonAction: ButtonAction? = null,
    val spokenText: String? = null,
    @ColumnInfo(defaultValue = "1")
    val isActive: Boolean = true,
    /** Legacy column, kept for non-destructive migration. */
    @ColumnInfo(name = "referenceEmbedding")
    val referenceEmbeddingJson: String = "[]"
) {
    @Suppress("DEPRECATION")
    fun toDomain(): VocalProfile = VocalProfile(
        id = id,
        name = name,
        positiveTemplates = parseFloatMatrix(positiveTemplatesJson),
        negativeTemplates = parseFloatMatrix(negativeTemplatesJson),
        buttonAction = buttonAction,
        spokenText = spokenText,
        isActive = isActive
    )

    private fun parseFloatMatrix(json: String): List<List<Float>> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            kotlinx.serialization.json.Json.decodeFromString<List<List<Float>>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Suppress("DEPRECATION")
fun VocalProfile.toEntity(): VocalProfileEntity {
    val jsonEncoder = kotlinx.serialization.json.Json
    return VocalProfileEntity(
        id = id,
        name = name,
        positiveTemplatesJson = jsonEncoder.encodeToString<List<List<Float>>>(positiveTemplates),
        negativeTemplatesJson = jsonEncoder.encodeToString<List<List<Float>>>(negativeTemplates),
        buttonAction = buttonAction,
        spokenText = spokenText,
        isActive = isActive
    )
}
