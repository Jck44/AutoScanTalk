package com.andreas_kratzer.ghosttalk.ui.util

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import kotlinx.coroutines.flow.StateFlow

interface GridEditorActions {
    fun updateGridSettings(
        itemId: String,
        update: GridSettingsUpdate
    )
    fun updateButtonConfig(itemId: String, index: Int, newConfig: ButtonConfig?)
    fun insertButtonConfig(itemId: String, index: Int, newConfig: ButtonConfig, forceShift: Boolean = false, onResult: (Boolean) -> Unit = {})
    fun updateRowName(itemId: String, rowIndex: Int, newName: String)
    fun suggestRowName(itemId: String, rowIndex: Int, onResult: (String) -> Unit) {}
    fun moveRow(itemId: String, fromRow: Int, toRow: Int)
    fun moveButton(itemId: String, fromIndex: Int, toIndex: Int)
    fun moveButtonWithInsert(itemId: String, fromIndex: Int, toIndex: Int)
    fun undo(onSuccess: (String) -> Unit)
    val canUndo: StateFlow<Boolean>
    
    // For ButtonConfigDialog
    val isExecuting: StateFlow<Boolean>
    fun createNewPage(name: String, rows: Int, columns: Int, bookId: String, templateId: String? = null, onCreated: (String) -> Unit)
    fun executeButtonAction(config: ButtonConfig)
    fun moveButtonToPage(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean = false,
        onResult: (com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult) -> Unit
    )
    fun duplicateButtonToPage(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean = false,
        onResult: (com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult) -> Unit
    )
    val availableGeminiTools: List<com.andreas_kratzer.ghosttalk.core.ai.domain.AiTool>
    val isGeminiEnabled: Boolean get() = false
    fun suggestButtonLabel(config: ButtonConfig, onResult: (String) -> Unit) {}
    
    fun isTextCached(text: String): Boolean = false
    fun prefetchText(text: String, onComplete: () -> Unit = {}) {}
    fun speakTtsPreview(text: String, onDone: () -> Unit = {}) {}
    fun stopTtsPreview() {}
    fun isTtsElevenLabs(): Boolean = false
}

suspend fun generateSuggestButtonLabelPrompt(config: ButtonConfig, pageNameResolver: suspend (String) -> String?): String {
    val action = config.buttonAction
    val spokenText = config.spokenText
    val details = StringBuilder()
    
    details.append("Aktionstyp: ")
    when (action) {
        is SpeakTextButtonAction -> {
            details.append("Text sprechen. Gesprochener Text: \"${spokenText ?: ""}\"")
        }
        is NavigateToPageButtonAction -> {
            val pageName = pageNameResolver(action.pageId) ?: "eine andere Seite"
            details.append("Zu Seite navigieren. Zielseite Name: \"$pageName\"")
        }
        is NavigateToStartPageButtonAction -> {
            details.append("Zur Startseite navigieren")
        }
        is NavigateBackButtonAction -> {
            details.append("Zur vorherigen Seite navigieren / Zurück")
        }
        is GeminiButtonAction -> {
            details.append("Gemini KI-Assistent fragen. Prompt: \"${action.prompt}\"")
        }
        is GeminiSearchButtonAction -> {
            details.append("Gemini KI-Suche fragen. Prompt: \"${action.prompt}\"")
        }
        is GeminiVisionButtonAction -> {
            details.append("Gemini KI-Auge / Vision verwenden. Prompt: \"${action.prompt}\"")
        }
        is WeatherButtonAction -> {
            details.append("Aktuelles Wetter vorlesen")
        }
        is ControlDeviceButtonAction -> {
            details.append("Gerät steuern. Aktion: ")
            when (action.actionType) {
                DeviceActionType.READ_NOTIFICATIONS -> details.append("Benachrichtigungen vorlesen")
                DeviceActionType.TOGGLE_AUTO_READ_NOTIFICATIONS -> details.append("Automatisches Vorlesen der Benachrichtigungen umschalten")
                DeviceActionType.CLEAR_NOTIFICATIONS -> details.append("Benachrichtigungen löschen")
                DeviceActionType.SEND_MESSAGE -> details.append("Nachricht per SMS/WhatsApp senden an \"${action.contactName ?: ""}\" (Tel: ${action.contactPhone ?: ""}) mit Text: \"${action.messageText ?: ""}\"")
                DeviceActionType.SEND_LAST_SPOKEN_SMS -> details.append("Letzten gesprochenen Text per SMS senden an \"${action.contactName ?: ""}\" (Tel: ${action.contactPhone ?: ""})")
                DeviceActionType.START_CALL -> details.append("Anruf starten zu \"${action.contactName ?: ""}\" (Tel: ${action.contactPhone ?: ""})")
                DeviceActionType.MEDIA_PLAY_PAUSE -> details.append("Medien Wiedergabe Abspielen/Pause")
                DeviceActionType.MEDIA_NEXT -> details.append("Nächster Medientitel")
                DeviceActionType.MEDIA_PREVIOUS -> details.append("Vorheriger Medientitel")
                DeviceActionType.READ_TIME -> details.append("Uhrzeit vorlesen")
                DeviceActionType.READ_DATE -> details.append("Datum vorlesen")
                DeviceActionType.READ_CALENDAR_ENTRIES -> details.append("Kalendereinträge vorlesen")
                DeviceActionType.READ_BATTERY -> details.append("Akkustand vorlesen")
                DeviceActionType.VOLUME_MEDIA -> details.append("Lautstärke Medien auf ${action.volumeValue ?: "50"}% setzen")
                DeviceActionType.VOLUME_NOTIFICATION -> details.append("Lautstärke Benachrichtigungen auf ${action.volumeValue ?: "50"}% setzen")
                DeviceActionType.VOLUME_ALARM -> details.append("Lautstärke Wecker auf ${action.volumeValue ?: "50"}% setzen")
                DeviceActionType.VOLUME_CALL -> details.append("Lautstärke Anruf auf ${action.volumeValue ?: "50"}% setzen")
                DeviceActionType.VOLUME_IN_APP_TTS -> details.append("Lautstärke In-App Sprachausgabe auf ${action.volumeValue ?: "50"}% setzen")
                DeviceActionType.VOLUME_IN_APP_CUES -> details.append("Lautstärke In-App Cues auf ${action.volumeValue ?: "50"}% setzen")
                DeviceActionType.STATUS_SILENT -> details.append("Tonmodus auf Stumm schalten")
                DeviceActionType.STATUS_VIBRATE -> details.append("Tonmodus auf Vibration schalten")
                DeviceActionType.STATUS_LOUD -> details.append("Tonmodus auf Laut schalten")
                DeviceActionType.TOGGLE_SCANNING -> details.append("Scanning-Steuerung umschalten")
                DeviceActionType.INSTALL_UPDATE -> details.append("Update installieren")
                DeviceActionType.START_SYNC -> details.append("Synchronisierung starten")
            }
        }
        is PlayMediaButtonAction -> {
            details.append("Musik/Medium abspielen von ${action.provider.name}. Name: \"${action.contentName}\"")
        }
        is SmartHomeButtonAction -> {
            details.append("Smart Home Gerät steuern von ${action.provider.name}. Gerät: \"${action.deviceName}\", Intent: \"${action.intent}\", Wert: \"${action.value ?: ""}\"")
        }
        is FrequentActionButtonAction -> {
            details.append("Häufigste Aktion (Rang ${action.rank}) anzeigen")
        }
        is PreviousActionButtonAction -> {
            details.append("Vorherige Aktion (Rang ${action.rank}) anzeigen")
        }
        is SmartPredictionButtonAction -> {
            details.append("Smarte Vorhersage (Rang ${action.rank}, Typ ${action.predictionType.name}) anzeigen")
        }
        else -> details.append("Unbekannte Aktion")
    }

    return "Schlage eine kurze, prägnante Beschriftung (maximal 3 Wörter) für einen Button vor, der folgende Einstellungen hat:\n" +
            "$details\n\n" +
            "Antworte in der Sprache, in der die Angaben verfasst sind (z.B. Deutsch oder Englisch). Wenn die Angaben Deutsch sind, antworte auf Deutsch. Wenn sie Englisch sind, antworte auf Englisch.\n" +
            "Antworte NUR mit der vorgeschlagenen Beschriftung (maximal 3 Wörter), ohne Anführungszeichen, ohne Satzzeichen, ohne zusätzliche Erklärungen oder Einleitung."
}

