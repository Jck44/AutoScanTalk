package com.andreas_kratzer.ghosttalk.model.importexport

data class ImportExportData(
    val ghosttalk_import_version: String?,
    val appName: String?,
    val holdingTimeSeconds: Float? = null,
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
