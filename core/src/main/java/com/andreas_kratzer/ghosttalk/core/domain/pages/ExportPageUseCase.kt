package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.model.Page
import javax.inject.Inject

class ExportPageUseCase @Inject constructor(
    private val importExportManager: PageImportExportProvider
) {
    suspend fun execute(pages: List<Page>): String {
        return importExportManager.exportPageListToJson(pages)
    }
}
