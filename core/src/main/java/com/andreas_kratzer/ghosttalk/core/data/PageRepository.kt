package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.model.Page
import kotlinx.coroutines.flow.Flow

interface PageRepository {
    fun getAllPagesFlow(): Flow<List<Page>>
    fun getPagesForBookFlow(bookId: String): Flow<List<Page>>
    suspend fun getPageById(id: String): Page?
    suspend fun getAllPages(): List<Page>
    suspend fun getPagesForBook(bookId: String): List<Page>
    /**
     * Inserts a page. Overwrites timestamps on the page and all buttons with the current time.
     * Primarily used for UI flows and creating new pages.
     */
    suspend fun insertPage(page: Page)

    /**
     * Inserts a page as is. Keeps the page's and its buttons' original timestamps without overwriting them.
     * Primarily used for synchronisation and importing backups.
     */
    suspend fun insertPageRaw(page: Page)
    suspend fun updatePage(page: Page)
    suspend fun updatePageSettingsOnly(page: Page)
    suspend fun movePages(fromPage: Page, toPage: Page)
    suspend fun deletePage(page: Page)
    // IMPORTANT: Only use for import! Does NOT create tombstones.
    suspend fun deletePagesForBook(bookId: String)
    suspend fun duplicatePage(pageId: String, duplicateSuffix: String): String?
    fun getUsedTemplateIdsFlow(): Flow<Set<String>>
    suspend fun deleteEmptyButtons(): Int
    suspend fun updatePageName(pageId: String, name: String)
    suspend fun updatePageScanPattern(pageId: String, scanPattern: String?)
    suspend fun updatePageRowNames(pageId: String, rowNames: List<String>)
    suspend fun updatePageGridSize(pageId: String, rows: Int, columns: Int)
    suspend fun <R> runInTransaction(block: suspend () -> R): R
    suspend fun purgeInstallUpdateButtons()
}
