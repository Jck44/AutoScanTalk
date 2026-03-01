package com.example.gostalk.data

import androidx.room.TypeConverter
import com.example.gostalk.model.AuditoryCue
import com.example.gostalk.model.ButtonAction
import com.example.gostalk.model.ButtonConfig
import com.example.gostalk.model.NavigateToPageButtonAction
import com.example.gostalk.model.SpeakTextButtonAction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class ButtonActionAdapter : JsonSerializer<ButtonAction>, JsonDeserializer<ButtonAction> {
    override fun serialize(src: ButtonAction, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
        val jsonObject = JsonObject()
        when (src) {
            is SpeakTextButtonAction -> {
                jsonObject.addProperty("type", "SpeakTextButtonAction")
                jsonObject.add("data", context.serialize(src))
            }
            is NavigateToPageButtonAction -> {
                jsonObject.addProperty("type", "NavigateToPageButtonAction")
                jsonObject.add("data", context.serialize(src))
            }
        }
        return jsonObject
    }

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext): ButtonAction {
        val jsonObject = json.asJsonObject
        val type = jsonObject.get("type").asString
        val data = jsonObject.get("data")
        return when (type) {
            "SpeakTextButtonAction" -> context.deserialize(data, SpeakTextButtonAction::class.java)
            "NavigateToPageButtonAction" -> context.deserialize(data, NavigateToPageButtonAction::class.java)
            else -> throw JsonParseException("Unknown ButtonAction type: $type")
        }
    }
}

class AuditoryCueAdapter : JsonSerializer<AuditoryCue>, JsonDeserializer<AuditoryCue> {
    override fun serialize(src: AuditoryCue, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
        val jsonObject = JsonObject()
        when (src) {
            is AuditoryCue.TextToSpeechCue -> {
                jsonObject.addProperty("type", "TextToSpeechCue")
                jsonObject.add("data", context.serialize(src))
            }
        }
        return jsonObject
    }

    override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext): AuditoryCue {
        val jsonObject = json.asJsonObject
        val type = jsonObject.get("type").asString
        val data = jsonObject.get("data")
        return when (type) {
            "TextToSpeechCue" -> context.deserialize(data, AuditoryCue.TextToSpeechCue::class.java)
            else -> throw JsonParseException("Unknown AuditoryCue type: $type")
        }
    }
}

class Converters {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ButtonAction::class.java, ButtonActionAdapter())
        .registerTypeAdapter(AuditoryCue::class.java, AuditoryCueAdapter())
        .create()

    @TypeConverter
    fun fromButtonConfigList(buttonConfigs: List<ButtonConfig?>?): String? {
        if (buttonConfigs == null) return null
        val type = object : TypeToken<List<ButtonConfig?>>() {}.type
        return gson.toJson(buttonConfigs, type)
    }

    @TypeConverter
    fun toButtonConfigList(buttonConfigsString: String?): List<ButtonConfig?>? {
        if (buttonConfigsString == null) return null
        val type = object : TypeToken<List<ButtonConfig?>>() {}.type
        return gson.fromJson(buttonConfigsString, type)
    }

    @TypeConverter
    fun fromStringList(strings: List<String>?): String? {
        if (strings == null) return null
        val type = object : TypeToken<List<String>>() {}.type
        return gson.toJson(strings, type)
    }

    @TypeConverter
    fun toStringList(stringsString: String?): List<String>? {
        if (stringsString == null) return null
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(stringsString, type)
    }
}
