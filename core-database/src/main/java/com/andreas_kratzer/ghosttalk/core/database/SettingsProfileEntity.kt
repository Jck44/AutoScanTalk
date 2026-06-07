package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.andreas_kratzer.ghosttalk.core.model.ProfileConfig
import com.andreas_kratzer.ghosttalk.core.model.SettingsProfile
import kotlinx.serialization.json.Json

@Entity(tableName = "settings_profiles")
data class SettingsProfileEntity(
    @PrimaryKey val id: String,          // profile_<UUID>
    val name: String,
    val configJson: String,             // Serialisierte JSON-Struktur aller synchronisierten Settings
    val profileVersionSequence: Long,   // Sequenznummer für den Loop-sicheren Cloud-Sync
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

fun SettingsProfileEntity.toDomain(json: Json = Json { ignoreUnknownKeys = true }): SettingsProfile {
    val config = try {
        json.decodeFromString<ProfileConfig>(configJson)
    } catch (_: Exception) {
        ProfileConfig()
    }
    return SettingsProfile(
        id = id,
        name = name,
        config = config,
        profileVersionSequence = profileVersionSequence,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )
}

fun SettingsProfile.toEntity(json: Json = Json { ignoreUnknownKeys = true }): SettingsProfileEntity {
    val configJsonString = json.encodeToString(ProfileConfig.serializer(), config)
    return SettingsProfileEntity(
        id = id,
        name = name,
        configJson = configJsonString,
        profileVersionSequence = profileVersionSequence,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )
}
