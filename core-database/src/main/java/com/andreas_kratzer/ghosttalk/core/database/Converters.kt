package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.TypeConverter
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromSpokenTextMode(mode: SpokenTextMode?): String? {
        return mode?.name
    }

    @TypeConverter
    fun toSpokenTextMode(modeString: String?): SpokenTextMode? {
        if (modeString == null) return null
        return try {
            SpokenTextMode.valueOf(modeString)
        } catch (e: IllegalArgumentException) {
            SpokenTextMode.TTS
        }
    }


    @TypeConverter
    fun fromButtonConfig(config: ButtonConfig?): String? {
        if (config == null) return null
        return json.encodeToString(config)
    }

    @TypeConverter
    fun toButtonConfig(configString: String?): ButtonConfig? {
        if (configString == null) return null
        return json.decodeFromString<ButtonConfig>(configString)
    }

    @TypeConverter
    fun fromButtonAction(action: ButtonAction?): String? {
        if (action == null) return null
        return json.encodeToString(action)
    }

    @TypeConverter
    fun toButtonAction(actionString: String?): ButtonAction? {
        if (actionString == null) return null
        return json.decodeFromString<ButtonAction>(actionString)
    }

    @TypeConverter
    fun fromAuditoryCue(cue: AuditoryCue?): String? {
        if (cue == null) return null
        return json.encodeToString(cue)
    }

    @TypeConverter
    fun toAuditoryCue(cueString: String?): AuditoryCue? {
        if (cueString == null) return null
        return json.decodeFromString<AuditoryCue>(cueString)
    }

    @TypeConverter
    fun fromStringList(strings: List<String>?): String? {
        if (strings == null) return null
        return json.encodeToString(strings)
    }

    @TypeConverter
    fun toStringList(stringsString: String?): List<String>? {
        if (stringsString == null) return null
        return json.decodeFromString<List<String>>(stringsString)
    }

    @TypeConverter
    fun fromButtonConfigList(buttonConfigs: List<ButtonConfig?>?): String? {
        if (buttonConfigs == null) return null
        return json.encodeToString(buttonConfigs)
    }

    @TypeConverter
    fun toButtonConfigList(buttonConfigsString: String?): List<ButtonConfig?>? {
        if (buttonConfigsString == null) return null
        return json.decodeFromString<List<ButtonConfig?>>(buttonConfigsString)
    }
}
