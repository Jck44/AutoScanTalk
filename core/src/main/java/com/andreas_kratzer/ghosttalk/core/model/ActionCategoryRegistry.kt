package com.andreas_kratzer.ghosttalk.core.model

enum class ActionCategory {
    SPEAK_TEXT,
    NAVIGATE_PAGE,
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
    PLAY_MEDIA
}

object ActionCategoryRegistry {
    const val GROUP_BASIS = "Basis"
    const val GROUP_KI_ASSISTENZ = "KI & Assistenz"
    const val GROUP_GERAETE_SMART_HOME = "Geräte & Smart Home"
    const val GROUP_DYNAMISCHE_AKTIONEN = "Dynamische Aktionen"

    val ALL_GROUPS = listOf(
        GROUP_BASIS,
        GROUP_KI_ASSISTENZ,
        GROUP_GERAETE_SMART_HOME,
        GROUP_DYNAMISCHE_AKTIONEN
    )

    fun getGroupForAction(action: ButtonAction): String {
        return when (action) {
            is SpeakTextButtonAction,
            is NavigateToPageButtonAction -> GROUP_BASIS

            is GeminiButtonAction,
            is GeminiSearchButtonAction,
            is GeminiNanoButtonAction,
            is GeminiVisionButtonAction -> GROUP_KI_ASSISTENZ

            is ControlDeviceButtonAction,
            is WeatherButtonAction,
            is SmartHomeButtonAction,
            is PlayMediaButtonAction -> GROUP_GERAETE_SMART_HOME

            is FrequentActionButtonAction,
            is SmartPredictionButtonAction,
            is PreviousActionButtonAction -> GROUP_DYNAMISCHE_AKTIONEN
        }
    }

    fun getCategoryForAction(action: ButtonAction): ActionCategory {
        return when (action) {
            is SpeakTextButtonAction -> ActionCategory.SPEAK_TEXT
            is NavigateToPageButtonAction -> ActionCategory.NAVIGATE_PAGE
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
        }
    }
}
