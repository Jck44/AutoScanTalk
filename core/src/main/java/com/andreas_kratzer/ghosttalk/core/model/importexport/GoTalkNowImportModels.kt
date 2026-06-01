package com.andreas_kratzer.ghosttalk.core.model.importexport

import kotlinx.serialization.Serializable

@Serializable
data class ImportExportData(
    val ghosttalk_import_version: String? = null,
    val appName: String? = null,
    val holdingTimeSeconds: Float? = null,
    val autoStartScanning: Boolean? = null,
    val scanDelayMillis: Long? = null,
    val resumeScanningFromStart: Boolean? = null,
    val switchActivationKey: String? = null,
    val volumeKeysActivate: Boolean? = null,
    val defaultScanPattern: String? = null,
    val isSmartPredictionEnabled: Boolean? = null,
    val geminiRedoPrediction: Boolean? = null,
    val geminiTimeout: Long? = null,
    val isGeminiEnabled: Boolean? = null,
    val useLocalGenerativeAi: Boolean? = null,
    val isCloudSyncEnabled: Boolean? = null,
    val syncIntervalMinutes: Long? = null,
    val syncMode: String? = null,
    val ttsLanguage: String? = null,
    val ttsVoiceName: String? = null,
    val pageSortOrder: String? = null,
    val templateSortOrder: String? = null,
    val smartPredictionDelay: Long? = null,
    val keepScreenOnUserMode: Boolean? = null,
    val userModeScreenBehavior: String? = null,
    val bookName: String? = null,
    val bookId: String? = null,
    val bookCreatedAt: Long? = null,
    val bookUpdatedAt: Long? = null,
    val themeMode: String? = null,
    val securityPinTimeoutMinutes: Long? = null,
    val isPinRequiredForDeletion: Boolean? = null,
    val isBiometricEnabled: Boolean? = null,
    val isSecurityRequiredForEdit: Boolean? = null,
    val isSecurityRequiredForSettings: Boolean? = null,
    val startupBehavior: String? = null,
    val favoriteBookId: String? = null,
    val weatherCacheTimeout: Long? = null,
    val defaultStartPageId: String? = null,
    val securityPinHash: String? = null,
    val securityPinSalt: String? = null,
    val actionLogLimit: Int? = null,
    val limitScanCycles: Boolean? = null,
    val scanCycleLimit: Int? = null,
    val logIgnoredActions: Boolean? = null,
    val logStopActions: Boolean? = null,
    val appLanguage: String? = null,
    val isNotificationReadingEnabled: Boolean? = null,
    val monitoredNotificationApps: List<String>? = null,
    val showPageIdInLog: Boolean? = null,
    val bluetoothDelay: Long? = null,
    val hueClientId: String? = null,
    val hueClientSecret: String? = null,
    val ttsEngine: String? = null,
    val elevenLabsModel: String? = null,
    val elevenLabsApiKey: String? = null,
    val googleTtsLanguage: String? = null,
    val googleTtsVoiceName: String? = null,
    val elevenLabsTtsLanguage: String? = null,
    val elevenLabsTtsVoiceName: String? = null,
    val elevenLabsStability: Float? = null,
    val elevenLabsSimilarityBoost: Float? = null,
    val maxCallDurationSeconds: Int? = null,
    val callDurationFeedbackIntervalSeconds: Int? = null,
    val outgoingCallIntro: String? = null,
    val incomingCallIntro: String? = null,
    val incomingCallScanLimitUserModeActive: Int? = null,
    val incomingCallAutoActionUserModeActive: String? = null,
    val incomingCallDelayUserModeInactive: Int? = null,
    val incomingCallAutoActionUserModeInactive: String? = null,
    val callAnnouncementAsCue: Boolean? = null,
    val autoEnableSpeakerphone: Boolean? = null,
    val simulateCallsEnabled: Boolean? = null,
    val hueCachedDevices: String? = null,
    val templates: List<ImportTemplate>? = null,
    val buttonTemplates: List<ImportButtonTemplate>? = null,
    val pages: List<ImportPage> = emptyList()
)

@Serializable
data class ImportTemplate(
    val id: String = "",
    val name: String = "",
    val rows: Int = 4,
    val columns: Int = 4,
    val scanPattern: String? = null,
    val rowNames: List<String>? = null,
    val orderIndex: Int? = null,
    val createdAt: Long? = null,
    val isBuiltIn: Boolean = false,
    val buttons: List<ImportButton> = emptyList()
)

@Serializable
data class ImportPage(
    val importId: String = "",
    val name: String = "",
    val rows: Int = 4,
    val columns: Int = 4,
    val templateId: String? = null,
    val scanPattern: String? = null,
    val rowNames: List<String>? = null,
    val orderIndex: Int? = null,
    val createdAt: Long? = null,
    val buttons: List<ImportButton> = emptyList()
)

@Serializable
data class ImportButton(
    val id: String? = null,
    val index: Long = 0,
    val label: String = "",
    val auditoryCueText: String? = null,
    val spokenText: String? = null,
    val spokenTextMode: String? = null,
    val audioFileName: String? = null,
    val active: Boolean? = true,
    val playActionAsAuditoryCue: Boolean? = false,
    val action: ImportAction? = null
)

@Serializable
data class ImportButtonTemplate(
    val id: String = "",
    val name: String = "",
    val isBuiltIn: Boolean = false,
    val orderIndex: Int = 0,
    val button: ImportButton? = null
)

@Serializable
data class ImportAction(
    val type: String = "SPEAK", // "SPEAK", "NAVIGATE", "GEMINI", "GEMINI_SEARCH", "GEMINI_NANO", "GEMINI_VISION", "SMART_PREDICTION", "DEVICE_CONTROL", "WEATHER"
    val textToSpeech: String? = null,
    val targetPageImportId: String? = null,
    val targetPageId: String? = null, // Legacy alias
    val ttsFeedback: String? = null,
    
    // GhostTalk specific expansions
    val prompt: String? = null,
    val rank: Int? = null,
    val intent: String? = null,
    val useCloud: Boolean? = null,
    val deviceActionType: String? = null,
    val volumeValue: String? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val messageText: String? = null,
    val includeWeekday: Boolean? = null,
    val prefixText: String? = null,
    val suffixText: String? = null,
    val offsetValue: Int? = null,
    val ignoreEmojis: Boolean? = null,
    
    // Unified Smart Home
    val smartHomeProvider: String? = null,
    val smartHomeDeviceId: String? = null,
    val smartHomeDeviceName: String? = null,
    val smartHomeIntent: String? = null,
    val smartHomeValue: String? = null,
    
    // Legacy Google Home specific
    val googleHomeDeviceId: String? = null,
    val googleHomeTrait: String? = null,
    val googleHomeCommand: String? = null,
    val googleHomeValue: String? = null,

    // Play Media Integration
    val mediaProvider: String? = null,
    val mediaContentUri: String? = null,
    val mediaContentName: String? = null,
    val mediaReturnDelayMs: Long? = null
)

@Serializable
data class ExportedStatistics(
    val bookId: String,
    val history: List<ExportedHistoryEvent>,
    val stats: List<ExportedButtonStat>
)

@Serializable
data class ExportedHistoryEvent(
    val timestamp: Long,
    val label: String,
    val actionType: String,
    val buttonId: String?,
    val pageId: String?,
    val imagePath: String? = null,
    val geminiResponse: String? = null
)

@Serializable
data class ExportedButtonStat(
    val buttonConfigId: String,
    val pageId: String,
    val label: String,
    val actionJson: String,
    val usageCount: Long,
    val lastUsedAt: Long
)
