package com.example.gostalk.model.importexport

data class ImportExportData(
    val gostalk_import_version: String?,
    val appName: String?,
    val pages: List<ImportPage>
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
    val active: Boolean? = true,
    val action: ImportAction?
)

data class ImportAction(
    val type: String, // "SPEAK" or "NAVIGATE"
    val textToSpeech: String?,
    val targetPageImportId: String?,
    val ttsFeedback: String?
)
