package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.model.Page
import javax.inject.Inject

class ExportPageUseCase @Inject constructor(
    private val importExportManager: PageImportExportManager
) {
    suspend fun execute(pages: List<Page>): String {
        return importExportManager.exportPageListToJson(pages)
    }
}
