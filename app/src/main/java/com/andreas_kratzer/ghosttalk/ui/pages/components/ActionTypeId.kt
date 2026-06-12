package com.andreas_kratzer.ghosttalk.ui.pages.components

enum class ActionTypeId {
    SPEAK,
    NAVIGATE,
    NAVIGATE_BACK,
    NAVIGATE_TO_START_PAGE,
    GEMINI,
    GEMINI_SEARCH,
    GEMINI_VISION,
    WEATHER,
    
    // Kommunikation
    READ_NOTIFICATIONS,
    TOGGLE_AUTO_READ,
    CLEAR_NOTIFICATIONS,
    SEND_MESSAGE,
    SEND_LAST_SPOKEN_SMS,
    START_CALL,

    // Medien & Musik
    SPOTIFY,
    YOUTUBE,
    YOUTUBE_MUSIC,
    AUDIBLE,
    MEDIA_PLAY_PAUSE,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,

    // Geräte & Einstellungen
    READ_TIME,
    READ_DATE,
    READ_CALENDAR_ENTRIES,
    READ_BATTERY,
    VOLUME_MEDIA,
    VOLUME_NOTIFICATION,
    VOLUME_ALARM,
    VOLUME_CALL,
    VOLUME_IN_APP_TTS,
    VOLUME_IN_APP_CUES,
    STATUS_SILENT,
    STATUS_VIBRATE,
    STATUS_LOUD,
    TOGGLE_SCANNING,
    INSTALL_UPDATE,
    START_SYNC,

    // Smart Home
    PHILIPS_HUE,
    GOOGLE_HOME,

    // Verlauf & Vorhersage
    FREQUENT,
    PREVIOUS,
    SMART
}
