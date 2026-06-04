package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.MediaProvider
import com.andreas_kratzer.ghosttalk.core.model.PredictionType

@Composable
fun PreviewTabContent(
    selectedActionType: String,
    spokenText: String,
    label: String,
    geminiPrompt: String,
    targetPageId: String,
    deviceActionType: DeviceActionType,
    includeWeekday: Boolean,
    offsetValue: String,
    prefixText: String,
    suffixText: String,
    contactName: String,
    contactPhone: String,
    messageText: String,
    smartHomeDeviceName: String,
    playActionAsAuditoryCue: Boolean,
    auditoryCueText: String,
    mediaProvider: MediaProvider = MediaProvider.SPOTIFY,
    mediaContentName: String = "",
    mediaReturnToAppDelaySec: String = "2",
    rank: Int = 1,
    predictionType: PredictionType = PredictionType.ALL,
    actionTypeSpeak: String = stringResource(R.string.button_action_speak_text),
    actionTypeNavigate: String = stringResource(R.string.button_action_navigate_page),
    actionTypeGemini: String = stringResource(R.string.button_action_gemini),
    actionTypeGeminiNano: String = stringResource(R.string.button_action_gemini_nano),
    actionTypeGeminiVision: String = stringResource(R.string.button_action_gemini_vision),
    actionTypeWeather: String = stringResource(R.string.button_action_weather),
    actionTypeDevice: String = stringResource(R.string.button_action_control_device),
    actionTypeSmartHome: String = stringResource(R.string.button_action_smart_home),
    actionTypePlayMedia: String = stringResource(R.string.button_action_play_media),
    actionTypeFrequent: String = stringResource(R.string.button_action_frequent_action),
    actionTypePrevious: String = stringResource(R.string.action_previous_action),
    actionTypeSmart: String = stringResource(R.string.button_action_smart_prediction)
) {
    val isSpeech = selectedActionType == actionTypeSpeak
    val speakTextToUse = if (isSpeech) {
        spokenText.takeIf { it.isNotBlank() } ?: label
    } else ""

    // Resolve string resources safely outside the remember block
    val frequentActionSpeak = stringResource(R.string.button_preview_frequent_action_speak, rank)
    val previousActionSpeak = stringResource(R.string.button_preview_previous_action_speak, rank)
    val smartTypeAll = stringResource(R.string.button_smart_prediction_type_all)
    val smartTypeAction = stringResource(R.string.button_smart_prediction_type_action)
    val smartTypeNav = stringResource(R.string.button_smart_prediction_type_navigation)
    val smartFilterText = when (predictionType) {
        PredictionType.ALL -> smartTypeAll
        PredictionType.ACTION -> smartTypeAction
        PredictionType.NAVIGATION -> smartTypeNav
    }
    val smartPredictionSpeak = stringResource(R.string.button_preview_smart_prediction_speak, rank, smartFilterText)

    val frequentActionCue1 = stringResource(R.string.button_preview_frequent_action_cue_1)
    val frequentActionCueN = stringResource(R.string.button_preview_frequent_action_cue_n, rank)
    val previousActionCue1 = stringResource(R.string.button_preview_previous_action_cue_1)
    val previousActionCueN = stringResource(R.string.button_preview_previous_action_cue_n, rank)
    val smartPredictionCue = stringResource(R.string.button_preview_smart_prediction_cue)

    val speakDescription = remember(
        selectedActionType, spokenText, label, geminiPrompt, targetPageId,
        deviceActionType, includeWeekday, offsetValue, prefixText, suffixText,
        contactName, contactPhone, messageText, smartHomeDeviceName,
        mediaProvider, mediaContentName, mediaReturnToAppDelaySec,
        frequentActionSpeak, previousActionSpeak, smartPredictionSpeak
    ) {
        when {
            isSpeech -> {
                if (spokenText.isNotBlank()) {
                    "🗣️ Text vorlesen:\n\"$spokenText\"\n\n(Eigener Sprechtext wird verwendet)"
                } else {
                    "🗣️ Text vorlesen (Fallback auf Label):\n\"$label\"\n\n(Da der Sprechtext leer ist, wird die Kachel-Beschriftung gesprochen)"
                }
            }
            else -> {
                when (selectedActionType) {
                    actionTypePlayMedia -> {
                        val nameStr = mediaContentName.ifBlank { mediaProvider.displayName }
                        val delayVal = mediaReturnToAppDelaySec.toIntOrNull() ?: 2
                        val delayDesc = if (delayVal > 0) "Kehrt nach $delayVal Sekunden automatisch zu GhostTalk zurück." else "Bleibt in der Medien-App."
                        "🎵 Medien abspielen (${mediaProvider.displayName}):\nSpielt $nameStr ab. $delayDesc"
                    }
                    actionTypeNavigate -> {
                        if (spokenText.isNotBlank()) {
                            "🗣️ Feedback vorlesen:\n\"$spokenText\"\n\n➡️ Navigation:\nÖffnet danach die Seite \"$targetPageId\""
                        } else {
                            "➡️ Navigation:\nÖffnet die Seite \"$targetPageId\""
                        }
                    }
                    actionTypeGemini -> "✨ KI (Gemini Cloud):\nSendet Prompt \"$geminiPrompt\" an Gemini und liest die Antwort vor."
                    actionTypeGeminiNano -> "📱 KI Nano (Offline):\nVerarbeitet Intent \"$geminiPrompt\" lokal auf dem Gerät."
                    actionTypeGeminiVision -> "📷 KI Vision (Auge):\nAnalysiert Kamerabild und liest die Beschreibung vor."
                    actionTypeDevice -> {
                        val actionName = when (deviceActionType) {
                            DeviceActionType.READ_TIME -> "Uhrzeit vorlesen"
                            DeviceActionType.READ_DATE -> "Datum vorlesen"
                            DeviceActionType.READ_BATTERY -> "Batteriestand vorlesen"
                            DeviceActionType.READ_CALENDAR_ENTRIES -> "Kalender vorlesen"
                            DeviceActionType.SEND_MESSAGE -> "SMS senden"
                            DeviceActionType.START_CALL -> "Anruf starten"
                            DeviceActionType.VOLUME_MEDIA -> "Medien-Lautstärke ändern"
                            DeviceActionType.VOLUME_IN_APP_TTS -> "In-App-Lautstärke: Laut Sprechen ändern"
                            DeviceActionType.VOLUME_IN_APP_CUES -> "In-App-Lautstärke: Audio-Hinweis ändern"
                            DeviceActionType.VOLUME_NOTIFICATION -> "Benachrichtigungs-Lautstärke ändern"
                            DeviceActionType.VOLUME_ALARM -> "Wecker-Lautstärke ändern"
                            DeviceActionType.VOLUME_CALL -> "Anruf-Lautstärke ändern"
                            DeviceActionType.STATUS_SILENT -> "Modus: Lautlos"
                            DeviceActionType.STATUS_VIBRATE -> "Modus: Vibration"
                            DeviceActionType.STATUS_LOUD -> "Modus: Laut"
                            DeviceActionType.MEDIA_PLAY_PAUSE -> "Musik abspielen/pausieren"
                            DeviceActionType.MEDIA_NEXT -> "Nächstes Lied abspielen"
                            DeviceActionType.MEDIA_PREVIOUS -> "Vorheriges Lied abspielen"
                            DeviceActionType.TOGGLE_SCANNING -> "Scannen pausieren/fortsetzen"
                            DeviceActionType.INSTALL_UPDATE -> "App aktualisieren"
                            DeviceActionType.START_SYNC -> "Synchronisation starten"
                            DeviceActionType.READ_NOTIFICATIONS -> "Benachrichtigungen vorlesen"
                            DeviceActionType.CLEAR_NOTIFICATIONS -> "Benachrichtigungen löschen"
                        }
                        val specificText = try {
                            val calendar = java.util.Calendar.getInstance()
                            val offsetInt = offsetValue.toIntOrNull() ?: 0
                            if (deviceActionType == DeviceActionType.READ_TIME) {
                                if (offsetInt != 0) {
                                    calendar.add(java.util.Calendar.MINUTE, offsetInt)
                                }
                                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                val timeString = sdf.format(calendar.time)
                                val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                                val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                                "\n\nGesprochener Text:\n\"$prefix$timeString$suffix\""
                            } else if (deviceActionType == DeviceActionType.READ_DATE) {
                                if (offsetInt != 0) {
                                    calendar.add(java.util.Calendar.DAY_OF_YEAR, offsetInt)
                                }
                                val pattern = if (includeWeekday) "EEEE, dd. MMMM yyyy" else "dd. MMMM yyyy"
                                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                                val dateString = sdf.format(calendar.time)
                                val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                                val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                                "\n\nGesprochener Text:\n\"$prefix$dateString$suffix\""
                            } else if (deviceActionType == DeviceActionType.READ_CALENDAR_ENTRIES) {
                                val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                                val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                                "\n\nGesprochener Text:\n\"${prefix}[Termine]${suffix}\" (Liest $offsetInt Kalendereinträge vor)"
                            } else if (deviceActionType == DeviceActionType.READ_BATTERY) {
                                "\n\nGesprochener Text:\n\"Batteriestand ist bei 85 Prozent\""
                            } else if (deviceActionType == DeviceActionType.SEND_MESSAGE) {
                                "\n\nSendet SMS an $contactName:\n\"$messageText\""
                            } else if (deviceActionType == DeviceActionType.START_CALL) {
                                "\n\nRuft $contactName an ($contactPhone)"
                            } else {
                                ""
                            }
                        } catch (_: Exception) {
                            ""
                        }
                        "📱 Geräte-Funktion: $actionName$specificText"
                    }
                    actionTypeWeather -> "🌤️ Wetteransage:\nRuft aktuellen Wetterbericht ab und spricht ihn laut vor."
                    actionTypeSmartHome -> "🏠 Smart Home:\nSchaltet Gerät \"$smartHomeDeviceName\"."
                    actionTypeFrequent -> frequentActionSpeak
                    actionTypePrevious -> previousActionSpeak
                    actionTypeSmart -> smartPredictionSpeak
                    else -> "🔄 Führt dynamische Aktion aus."
                }
            }
        }
    }

    val cueDescription = remember(
        playActionAsAuditoryCue, isSpeech, speakTextToUse, auditoryCueText, label, selectedActionType,
        frequentActionCue1, frequentActionCueN, previousActionCue1, previousActionCueN, smartPredictionCue
    ) {
        when {
            playActionAsAuditoryCue -> {
                if (isSpeech) {
                    "🔊 Spielt Aktionstext leise vor (Cue):\n\"$speakTextToUse\"\n\n(Option \"Aktionstext direkt vorlesen\" ist aktiv. Spricht denselben Text wie beim Tippen, aber leise im Scanning-Kanal)"
                } else {
                    "🔊 Spielt Aktion leise vor (Cue):\nFührt die Aktion (z.B. Navigations-Beschreibung) leise im Scanning-Kanal aus."
                }
            }
            auditoryCueText.isNotBlank() -> {
                "🔊 Spricht leise (Benutzerdefinierter Cue):\n\"$auditoryCueText\"\n\n(Eigener Hinweistext wird verwendet)"
            }
            else -> {
                when (selectedActionType) {
                    actionTypeFrequent -> {
                        if (rank == 1) {
                            "🔊 $frequentActionCue1"
                        } else {
                            "🔊 $frequentActionCueN"
                        }
                    }
                    actionTypePrevious -> {
                        if (rank == 1) {
                            "🔊 $previousActionCue1"
                        } else {
                            "🔊 $previousActionCueN"
                        }
                    }
                    actionTypeSmart -> {
                        "🔊 $smartPredictionCue"
                    }
                    else -> {
                        "🔊 Spricht leise (Fallback auf Label):\n\"$label\"\n\n(Da der Hinweistext leer ist, wird die Kachel-Beschriftung als Scanning-Cue verwendet)"
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "📢 Laut sprechen / Aktion ausführen",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = speakDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "🎧 Flüsterton / Scanning-Hinweis",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = cueDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

