package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import javax.inject.Inject

class ImportPageUseCase @Inject constructor(
    private val importExportManager: PageImportExportProvider
) {
    suspend fun execute(
        jsonString: String, 
        bookId: String, 
        regenerateIds: Boolean? = true,
        restoreSyncSettings: Boolean = true
    ): Result<Int> {
        return importExportManager.importFromJson(jsonString, bookId, regenerateIds ?: true, restoreSyncSettings)
    }
}
