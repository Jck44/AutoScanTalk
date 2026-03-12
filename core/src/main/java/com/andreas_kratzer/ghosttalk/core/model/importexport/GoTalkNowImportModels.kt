package com.andreas_kratzer.ghosttalk.core.model.importexport

import kotlinx.serialization.Serializable

@Serializable
data class ImportExportData(
    val ghosttalk_import_version: String? = "1.1",
    val appName: String? = "GhosTTalk",
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
    val templates: List<ImportTemplate>? = null,
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
    val active: Boolean? = true,
    val playActionAsAuditoryCue: Boolean? = false,
    val action: ImportAction? = null
)

@Serializable
data class ImportAction(
    val type: String = "SPEAK", // "SPEAK", "NAVIGATE", "GEMINI", "GEMINI_SEARCH", "GEMINI_NANO", "SMART_PREDICTION", "DEVICE_CONTROL", "WEATHER"
    val textToSpeech: String? = null,
    val targetPageImportId: String? = null,
    val targetPageId: String? = null, // Legacy alias
    val ttsFeedback: String? = null,
    
    // GhosTTalk specific expansions
    val prompt: String? = null,
    val rank: Int? = null,
    val intent: String? = null,
    val deviceActionType: String? = null,
    val volumeValue: String? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val messageText: String? = null
)
