package com.andreas_kratzer.ghosttalk.core.model

enum class ActionCategory {
    SPEAK_TEXT,
    NAVIGATE_PAGE,
    NAVIGATE_BACK,
    NAVIGATE_TO_START_PAGE,
    GEMINI,
    GEMINI_SEARCH,
    GEMINI_NANO,
    GEMINI_VISION,
    FREQUENT_ACTION,
    SMART_PREDICTION,
    PREVIOUS_ACTION,
    CONTROL_DEVICE,
    WEATHER,
    SMART_HOME,
    PLAY_MEDIA,
    MARK_ACCIDENTAL
}


object ActionCategoryRegistry {
    const val GROUP_BASIS = "Basis & Seite"
    const val GROUP_KI_ASSISTENZ = "KI & Wetter"
    const val GROUP_KOMMUNIKATION = "Kommunikation"
    const val GROUP_MEDIEN_MUSIK = "Medien & Musik"
    const val GROUP_GERAETE_EINSTELLUNGEN = "Geräte & Einstellungen"
    const val GROUP_SMART_HOME = "Smart Home"
    const val GROUP_VERLAUF_VORHERSAGE = "Verlauf & Vorhersage"

    val ALL_GROUPS = listOf(
        GROUP_BASIS,
        GROUP_KI_ASSISTENZ,
        GROUP_KOMMUNIKATION,
        GROUP_MEDIEN_MUSIK,
        GROUP_GERAETE_EINSTELLUNGEN,
        GROUP_SMART_HOME,
        GROUP_VERLAUF_VORHERSAGE
    )

    fun getGroupForAction(action: ButtonAction): String {
        @Suppress("DEPRECATION")
        return when (action) {
            is SpeakTextButtonAction,
            is NavigateToPageButtonAction,
            is NavigateBackButtonAction,
            is NavigateToStartPageButtonAction -> GROUP_BASIS

            is GeminiButtonAction,
            is GeminiSearchButtonAction,
            is GeminiNanoButtonAction,
            is GeminiVisionButtonAction,
            is WeatherButtonAction -> GROUP_KI_ASSISTENZ

            is ControlDeviceButtonAction -> {
                when (action.actionType) {
                    DeviceActionType.READ_NOTIFICATIONS,
                    DeviceActionType.CLEAR_NOTIFICATIONS,
                    DeviceActionType.SEND_MESSAGE,
                    DeviceActionType.START_CALL -> GROUP_KOMMUNIKATION

                    DeviceActionType.MEDIA_PLAY_PAUSE,
                    DeviceActionType.MEDIA_NEXT,
                    DeviceActionType.MEDIA_PREVIOUS -> GROUP_MEDIEN_MUSIK

                    else -> GROUP_GERAETE_EINSTELLUNGEN
                }
            }

            is PlayMediaButtonAction -> GROUP_MEDIEN_MUSIK

            is SmartHomeButtonAction -> GROUP_SMART_HOME

            is FrequentActionButtonAction,
            is SmartPredictionButtonAction,
            is PreviousActionButtonAction -> GROUP_VERLAUF_VORHERSAGE
            is MarkAccidentalButtonAction -> GROUP_VERLAUF_VORHERSAGE
        }
    }

    fun getCategoryForAction(action: ButtonAction): ActionCategory {
        @Suppress("DEPRECATION")
        return when (action) {
            is SpeakTextButtonAction -> ActionCategory.SPEAK_TEXT
            is NavigateToPageButtonAction -> ActionCategory.NAVIGATE_PAGE
            is NavigateBackButtonAction -> ActionCategory.NAVIGATE_BACK
            is NavigateToStartPageButtonAction -> ActionCategory.NAVIGATE_TO_START_PAGE
            is GeminiButtonAction -> ActionCategory.GEMINI
            is GeminiSearchButtonAction -> ActionCategory.GEMINI_SEARCH
            is GeminiNanoButtonAction -> ActionCategory.GEMINI_NANO
            is GeminiVisionButtonAction -> ActionCategory.GEMINI_VISION
            is FrequentActionButtonAction -> ActionCategory.FREQUENT_ACTION
            is SmartPredictionButtonAction -> ActionCategory.SMART_PREDICTION
            is PreviousActionButtonAction -> ActionCategory.PREVIOUS_ACTION
            is ControlDeviceButtonAction -> ActionCategory.CONTROL_DEVICE
            is WeatherButtonAction -> ActionCategory.WEATHER
            is SmartHomeButtonAction -> ActionCategory.SMART_HOME
            is PlayMediaButtonAction -> ActionCategory.PLAY_MEDIA
            is MarkAccidentalButtonAction -> ActionCategory.MARK_ACCIDENTAL
        }
    }

}
