package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.core.pages.PageImportExportManager
import com.andreas_kratzer.ghosttalk.model.Page
import javax.inject.Inject

class ExportPageUseCase @Inject constructor(
    private val importExportManager: PageImportExportManager
) {
    suspend fun execute(pages: List<Page>): String {
        return importExportManager.exportToJson(pages)
    }
}
