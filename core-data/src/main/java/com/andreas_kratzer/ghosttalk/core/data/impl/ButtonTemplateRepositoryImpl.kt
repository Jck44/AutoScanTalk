package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.data.ButtonTemplateRepository
import com.andreas_kratzer.ghosttalk.core.database.ButtonTemplateDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonTemplateEntity
import com.andreas_kratzer.ghosttalk.core.model.*
import com.andreas_kratzer.ghosttalk.core.settings.DatabaseSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class ButtonTemplateRepositoryImpl @Inject constructor(
    private val buttonTemplateDao: ButtonTemplateDao,
    private val settingsRepository: DatabaseSettings
) : ButtonTemplateRepository {

    override fun getTemplates(): Flow<List<ButtonTemplate>> {
        return buttonTemplateDao.getAllTemplatesFlow().map { entities ->
            entities.map { it.toModel() }
        }
    }

    override suspend fun saveTemplate(template: ButtonTemplate) {
        buttonTemplateDao.insertTemplate(template.toEntity())
    }

    override suspend fun deleteTemplate(template: ButtonTemplate) {
        if (template.isBuiltIn) return
        buttonTemplateDao.deleteTemplate(template.toEntity())
    }

    override suspend fun updateTemplateOrder(templates: List<ButtonTemplate>) {
        val updatedEntities = templates.mapIndexed { index, template ->
            template.copy(orderIndex = index).toEntity()
        }
        buttonTemplateDao.insertTemplates(updatedEntities)
    }

    override suspend fun ensureBuiltInTemplates() {
        // We only insert if the database templates are empty
        if (buttonTemplateDao.getTemplateCount() > 0) return

        val builtInTemplates = generateBuiltInTemplatesList()
        val entities = builtInTemplates.mapIndexed { index, template ->
            template.copy(orderIndex = index).toEntity()
        }
        buttonTemplateDao.insertTemplates(entities)
    }

    fun generateBuiltInTemplatesList(): List<ButtonTemplate> {
        val list = mutableListOf<ButtonTemplate>()

        // 1. SpeakTextButtonAction
        list.add(
            ButtonTemplate(
                id = "builtin_speak_text",
                name = "Hallo sprechen",
                isBuiltIn = true,
                buttonConfig = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "Hallo",
                    spokenText = "Hallo",
                    buttonAction = SpeakTextButtonAction()
                )
            )
        )

        // 2. NavigateToPageButtonAction
        list.add(
            ButtonTemplate(
                id = "builtin_navigate_page",
                name = "Seite aufrufen",
                isBuiltIn = true,
                buttonConfig = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "Seite",
                    buttonAction = NavigateToPageButtonAction("")
                )
            )
        )

        // 3. GeminiButtonAction
        list.add(
            ButtonTemplate(
                id = "builtin_gemini",
                name = "Gemini KI Frage",
                isBuiltIn = true,
                buttonConfig = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "Gemini",
                    buttonAction = GeminiButtonAction("Wie ist die Hauptstadt von Frankreich?")
                )
            )
        )

        // 4. GeminiSearchButtonAction
        list.add(
            ButtonTemplate(
                id = "builtin_gemini_search",
                name = "Gemini Websuche",
                isBuiltIn = true,
                buttonConfig = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "Suche",
                    buttonAction = GeminiSearchButtonAction("Aktuelle Nachrichten")
                )
            )
        )

        // 5. GeminiNanoButtonAction
        list.add(
            ButtonTemplate(
                id = "builtin_gemini_nano",
                name = "Gemini Nano (Lokal)",
                isBuiltIn = true,
                buttonConfig = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "Nano",
                    buttonAction = GeminiNanoButtonAction("Zusammenfassung")
                )
            )
        )

        // 6. GeminiVisionButtonAction
        list.add(
            ButtonTemplate(
                id = "builtin_gemini_vision",
                name = "KI Kamera (Vision)",
                isBuiltIn = true,
                buttonConfig = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "Kamera",
                    buttonAction = GeminiVisionButtonAction("Was siehst du?")
                )
            )
        )

        // 7. FrequentActionButtonAction
        list.add(
            ButtonTemplate(
                id = "builtin_frequent_action",
                name = "Häufigste Aktion",
                isBuiltIn = true,
                buttonConfig = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "Häufigste",
                    buttonAction = FrequentActionButtonAction(1)
                )
            )
        )

        // 8. SmartPredictionButtonAction
        list.add(
            ButtonTemplate(
                id = "builtin_smart_prediction",
                name = "Smarte KI Vorhersage",
                isBuiltIn = true,
                buttonConfig = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "Vorhersage",
                    buttonAction = SmartPredictionButtonAction(1)
                )
            )
        )

        // 9. PreviousActionButtonAction
        list.add(
            ButtonTemplate(
                id = "builtin_previous_action",
                name = "Letzte Aktion",
                isBuiltIn = true,
                buttonConfig = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "Zurück",
                    buttonAction = PreviousActionButtonAction(1)
                )
            )
        )

        // 10. WeatherButtonAction
        list.add(
            ButtonTemplate(
                id = "builtin_weather",
                name = "Wetter vorlesen",
                isBuiltIn = true,
                buttonConfig = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "Wetter",
                    buttonAction = WeatherButtonAction()
                )
            )
        )

        // 11. SmartHomeButtonAction
        list.add(
            ButtonTemplate(
                id = "builtin_smarthome",
                name = "Smart Home Steuerung",
                isBuiltIn = true,
                buttonConfig = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "Licht an",
                    buttonAction = SmartHomeButtonAction()
                )
            )
        )

        // 12. ControlDeviceButtonAction (For every DeviceActionType)
        DeviceActionType.entries.forEach { type ->
            val name = when (type) {
                DeviceActionType.READ_NOTIFICATIONS -> "Benachrichtigungen vorlesen"
                DeviceActionType.MEDIA_NEXT -> "Nächster Medientitel"
                DeviceActionType.MEDIA_PREVIOUS -> "Vorheriger Medientitel"
                DeviceActionType.MEDIA_PLAY_PAUSE -> "Medien Play/Pause"
                DeviceActionType.VOLUME_NOTIFICATION -> "System-Hinweis Lautstärke"
                DeviceActionType.VOLUME_ALARM -> "Wecker Lautstärke"
                DeviceActionType.VOLUME_MEDIA -> "Medien Lautstärke"
                DeviceActionType.VOLUME_CALL -> "Anruf Lautstärke"
                DeviceActionType.STATUS_SILENT -> "Modus Stumm"
                DeviceActionType.STATUS_VIBRATE -> "Modus Vibration"
                DeviceActionType.STATUS_LOUD -> "Modus Laut"
                DeviceActionType.CLEAR_NOTIFICATIONS -> "Benachrichtigungen löschen"
                DeviceActionType.SEND_MESSAGE -> "Nachricht senden"
                DeviceActionType.READ_BATTERY -> "Batteriestatus vorlesen"
                DeviceActionType.READ_TIME -> "Uhrzeit vorlesen"
                DeviceActionType.READ_DATE -> "Datum vorlesen"
                DeviceActionType.READ_CALENDAR_ENTRIES -> "Termine vorlesen"
                DeviceActionType.TOGGLE_SCANNING -> "Scannen an/aus"
            }
            val label = when (type) {
                DeviceActionType.READ_NOTIFICATIONS -> "Nachrichten"
                DeviceActionType.MEDIA_NEXT -> "Weiter"
                DeviceActionType.MEDIA_PREVIOUS -> "Zurück"
                DeviceActionType.MEDIA_PLAY_PAUSE -> "Play/Pause"
                DeviceActionType.VOLUME_NOTIFICATION -> "Lautstärke H."
                DeviceActionType.VOLUME_ALARM -> "Lautstärke W."
                DeviceActionType.VOLUME_MEDIA -> "Lautstärke M."
                DeviceActionType.VOLUME_CALL -> "Lautstärke A."
                DeviceActionType.STATUS_SILENT -> "Stumm"
                DeviceActionType.STATUS_VIBRATE -> "Vibration"
                DeviceActionType.STATUS_LOUD -> "Laut"
                DeviceActionType.CLEAR_NOTIFICATIONS -> "Löschen"
                DeviceActionType.SEND_MESSAGE -> "Nachricht"
                DeviceActionType.READ_BATTERY -> "Batterie"
                DeviceActionType.READ_TIME -> "Zeit"
                DeviceActionType.READ_DATE -> "Datum"
                DeviceActionType.READ_CALENDAR_ENTRIES -> "Kalender"
                DeviceActionType.TOGGLE_SCANNING -> "Scannen"
            }
            list.add(
                ButtonTemplate(
                    id = "builtin_device_${type.name.lowercase()}",
                    name = name,
                    isBuiltIn = true,
                    buttonConfig = ButtonConfig(
                        id = UUID.randomUUID().toString(),
                        label = label,
                        buttonAction = ControlDeviceButtonAction(
                            actionType = type,
                            volumeValue = if (type.name.startsWith("VOLUME_")) "50" else null
                        )
                    )
                )
            )
        }

        return list
    }

    private fun ButtonTemplateEntity.toModel(): ButtonTemplate {
        return ButtonTemplate(
            id = id,
            name = name,
            buttonConfig = buttonConfig,
            isBuiltIn = isBuiltIn,
            orderIndex = orderIndex
        )
    }

    private fun ButtonTemplate.toEntity(): ButtonTemplateEntity {
        return ButtonTemplateEntity(
            id = id,
            name = name,
            buttonConfig = buttonConfig,
            isBuiltIn = isBuiltIn,
            orderIndex = orderIndex
        )
    }
}
