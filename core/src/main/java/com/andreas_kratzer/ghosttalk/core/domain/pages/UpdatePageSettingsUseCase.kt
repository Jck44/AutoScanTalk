package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.OptionalProperty
import javax.inject.Inject

class UpdatePageSettingsUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository
) {
    suspend fun execute(
        pageId: String, 
        newName: String? = null, 
        newScanPattern: OptionalProperty<String?>? = null, 
        newRowNames: List<String>? = null,
        newRows: Int? = null,
        newColumns: Int? = null
    ): Page? {
        val page = pageRepository.getPageById(pageId)
        if (page != null) {
            if (newName != null) {
                pageRepository.updatePageName(pageId, newName)
            }
            if (newScanPattern != null) {
                pageRepository.updatePageScanPattern(pageId, newScanPattern.value)
            }
            if (newRowNames != null) {
                pageRepository.updatePageRowNames(pageId, newRowNames)
            }
            if (newRows != null || newColumns != null) {
                pageRepository.updatePageGridSize(
                    pageId, 
                    newRows ?: page.rows, 
                    newColumns ?: page.columns
                )
            }

            bookRepository.updateLastModified(page.bookId)
            return page.copy(
                name = newName ?: page.name,
                scanPattern = if (newScanPattern != null) newScanPattern.value else page.scanPattern,
                rowNames = newRowNames ?: page.rowNames,
                rows = newRows ?: page.rows,
                columns = newColumns ?: page.columns
            )
        }
        return null
    }
}
