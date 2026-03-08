package com.andreas_kratzer.ghosttalk.model.importexport

data class ImportExportData(
    val ghosttalk_import_version: String?,
    val appName: String?,
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
    val defaultStartPageId: String? = null,
    val templates: List<ImportTemplate>? = null,
    val pages: List<ImportPage>
)

data class ImportTemplate(
    val id: String,
    val name: String,
    val rows: Int,
    val columns: Int,
    val isBuiltIn: Boolean,
    val buttons: List<ImportButton>
)

data class ImportPage(
    val importId: String,
    val name: String,
    val rows: Int,
    val columns: Int,
    val buttons: List<ImportButton>
)

data class ImportButton(
    val index: Long,
    val label: String,
    val auditoryCueText: String?,
    val spokenText: String? = null,
    val active: Boolean? = true,
    val playActionAsAuditoryCue: Boolean? = false,
    val action: ImportAction?
)

data class ImportAction(
    val type: String, // "SPEAK" or "NAVIGATE"
    val textToSpeech: String?,
    val targetPageImportId: String?,
    val ttsFeedback: String?
)
