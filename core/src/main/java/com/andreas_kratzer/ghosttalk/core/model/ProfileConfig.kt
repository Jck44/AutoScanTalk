package com.andreas_kratzer.ghosttalk.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ProfileConfig(
    // Startup & Standard-Buch
    val favoriteBookId: String? = null,
    val startupBehavior: String = "BOOK_SELECTION",
    val userModeScreenBehavior: String = "GRID",

    // Sicherheit & PIN
    val securityPinTimeoutMinutes: Long = 30,
    val isPinRequiredForDeletion: Boolean = false,
    val isSecurityRequiredForEdit: Boolean = false,
    val isSecurityRequiredForSettings: Boolean = false,
    val isSecurityRequiredForAnalytics: Boolean = false,

    // API-Schlüssel
    val useGeminiApiKey: Boolean = false,

    // Scanning & Eingabe
    val autoStartScanning: Boolean = true,
    val scanDelayMillis: Long = 3000,
    val holdingTimeMillis: Long = 250,
    val resumeScanningFromStart: Boolean = true,
    val switchActivationKey: String = "~3",
    val volumeKeysActivate: Boolean = false,
    val defaultScanPattern: String = "linear",
    val limitScanCycles: Boolean = false,
    val scanCycleLimit: Int = 2,
    val staticRowEnabled: Boolean = false,
    val lateClickThresholdMillis: Long = 250,
    val forceSoftKeyboard: Boolean = true,
    val vocalSwitchEnabled: Boolean = false,

    // Stimmen & TTS
    val ttsEngine: String? = null,
    val ttsLanguage: String? = null,
    val ttsVoiceName: String? = null,
    val googleTtsLanguage: String? = null,
    val googleTtsVoiceName: String? = null,
    val elevenLabsTtsLanguage: String? = null,
    val elevenLabsTtsVoiceName: String? = null,
    val elevenLabsModel: String = "eleven_multilingual_v2",
    val elevenLabsStability: Float = 0.5f,
    val elevenLabsSimilarityBoost: Float = 0.75f,
    val ttsPlaybackSpeed: Float = 1.0f,

    // Smart Prediction & KI
    val isSmartPredictionEnabled: Boolean = false,
    val smartPredictionDelay: Long = 2000,
    val isGeminiEnabled: Boolean = false,
    val useLocalGenerativeAi: Boolean = true,
    val geminiRedoPrediction: Boolean = false,
    val geminiTimeout: Long = 10000,

    // Telefonie & Benachrichtigungen
    val maxCallDurationSeconds: Int = 300,
    val callDurationFeedbackIntervalSeconds: Int = 60,
    val outgoingCallIntro: String = "",
    val incomingCallIntro: String = "",
    val incomingCallScanLimitUserModeActive: Int = 2,
    val incomingCallAutoActionUserModeActive: String = "NONE",
    val incomingCallDelayUserModeInactive: Int = 10,
    val incomingCallAutoActionUserModeInactive: String = "NONE",
    val callAnnouncementAsCue: Boolean = false,
    val autoEnableSpeakerphone: Boolean = true,
    val simulateCallsEnabled: Boolean = false,
    val hangUpPressesRequired: Int = 1,
    val filterCallsNotInContacts: Boolean = false,
    val isNotificationReadingEnabled: Boolean = false,
    val monitoredNotificationApps: Set<String> = emptySet(),
    val autoReadMode: String = "OFF",
    val autoReadOnlyInUserMode: Boolean = true,
    val autoReadInStandby: Boolean = false,

    // Optik, UI & Verhalten
    val themeMode: String = "LIGHT",
    val appLanguage: String? = null,
    val pageSortOrder: String = "MANUAL",
    val templateSortOrder: String = "MANUAL",
    val keepScreenOnUserMode: Boolean = true,

    // Logging & Statistik
    val actionLogLimit: Int = 100,
    val persistActionLogs: Boolean = true,
    val showPageIdInLog: Boolean = false,
    val onlyRecordHardwareStats: Boolean = false,
    val statsRetentionDays: Int = 30,
    val statsAggregationHours: Int = 24,
    val logIgnoredActions: Boolean = false,
    val logStopActions: Boolean = false,
    val weatherCacheTimeout: Long = 60,
    val backgroundLocationEnabled: Boolean = false,
    val backgroundLocationInterval: Long = 4,
    val backgroundWeatherEnabled: Boolean = false,
    val backgroundWeatherInterval: Long = 4,

    // Cloud Sync Verhalten
    val syncIntervalMinutes: Long = 60,
    val foregroundSyncIntervalMinutes: Long = 5,
    val syncModeBook: String = "TWO_WAY",
    val syncModeStats: String = "RESTORE_ONLY",
    val syncModeTts: String = "TWO_WAY",
    val syncModeLogs: String = "TWO_WAY",
    val syncLogsIntervalHours: Long = 24,


    // Semantisches Audio-Routing
    val preferredMainSpeakerName: String? = null,
    val preferredCueSpeakerName: String? = null,
    val fallbackToInternalAudio: Boolean = true,

    val bluetoothDelay: Long = 100,

    // Smart Home & Hue
    val hueBridgeIp: String = "",
    val hueCachedDevices: String = "",

    // Audio & Volume
    val speakerVolume: Int = 100,
    val headphoneVolume: Int = 100,
    val blockVolumeKeys: Boolean = false,

    // Cloud Sync
    val isDataCloudSyncEnabled: Boolean = false,
    val googleDriveFolderId: String? = null,
    val googleDriveFolderName: String? = null,
    val firebaseAnalyticsEnabled: Boolean = true
)
