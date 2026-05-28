package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.model.Page
import kotlinx.coroutines.flow.Flow

interface PageRepository {
    fun getAllPagesFlow(): Flow<List<Page>>
    fun getPagesForBookFlow(bookId: String): Flow<List<Page>>
    suspend fun getPageById(id: String): Page?
    suspend fun getAllPages(): List<Page>
    suspend fun getPagesForBook(bookId: String): List<Page>
    suspend fun insertPage(page: Page)
    suspend fun updatePage(page: Page)
    suspend fun updatePageSettingsOnly(page: Page)
    suspend fun movePages(fromPage: Page, toPage: Page)
    suspend fun deletePage(page: Page)
    suspend fun deletePagesForBook(bookId: String)
    suspend fun duplicatePage(pageId: String, duplicateSuffix: String): String?
    fun getUsedTemplateIdsFlow(): Flow<Set<String>>
    suspend fun deleteEmptyButtons(): Int
    suspend fun updatePageName(pageId: String, name: String)
    suspend fun updatePageScanPattern(pageId: String, scanPattern: String?)
    suspend fun updatePageRowNames(pageId: String, rowNames: List<String>)
    suspend fun updatePageGridSize(pageId: String, rows: Int, columns: Int)
}
