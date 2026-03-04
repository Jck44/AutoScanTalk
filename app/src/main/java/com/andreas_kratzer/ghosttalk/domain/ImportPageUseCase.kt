package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.core.pages.PageImportExportManager
import javax.inject.Inject

class ImportPageUseCase @Inject constructor(
    private val importExportManager: PageImportExportManager
) {
    suspend fun execute(jsonString: String, bookId: String): Result<Int> {
        return importExportManager.importFromJson(jsonString, bookId)
    }
}
